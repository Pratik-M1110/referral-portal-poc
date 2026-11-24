package com.referralportal.referral;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.referralportal.service.ReferralRequestMaintenance;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@EnabledIfEnvironmentVariable(named = "SESSION_TEST_DB_URL", matches = ".+")
@SpringBootTest(properties = {
        "app.referral-request-scheduler-enabled=false",
        "app.referral-reminder-retry-enabled=true",
        "app.frontend-url=https://portal.example.test",
        "app.mail-from=sender@example.test"
})
class ReferralRequestMaintenanceIntegrationTests {

    @Autowired
    private ReferralRequestMaintenance maintenance;
    @Autowired
    private JdbcClient jdbc;
    @MockitoBean
    private JavaMailSender mailSender;

    private long seekerId;
    private long referrerId;
    private long companyId;
    private long responseId;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        String url = System.getenv("SESSION_TEST_DB_URL");
        if (url == null || !url.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/spring_session_test\\?.+")) {
            throw new IllegalStateException("Tests require an isolated local spring_session_test database.");
        }
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> "root");
        registry.add("spring.datasource.password", () -> "local-session-test-only");
    }

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        seekerId = insert("INSERT INTO users (name, email, auth_provider) VALUES ('Seeker', ?, 'google')",
                "seeker-" + suffix + "@example.test");
        referrerId = insert("INSERT INTO users (name, email, auth_provider) VALUES ('Referrer', ?, 'github')",
                "referrer-" + suffix + "@example.test");
        companyId = insert("INSERT INTO companies (name, email_domain) VALUES ('Example', ?)",
                "company-" + suffix + ".test");
        jdbc.sql("INSERT INTO referrer_profiles (user_id, company_id, work_email, verified_at) "
                + "VALUES (?, ?, ?, CURRENT_TIMESTAMP)")
                .params(referrerId, companyId, "work-" + suffix + "@example.test").update();
        long requestId = insert("INSERT INTO company_referral_requests "
                + "(seeker_id, company_id, role_title, created_at, expires_at) "
                + "VALUES (?, ?, 'Engineer', CURRENT_TIMESTAMP, DATE_ADD(CURRENT_TIMESTAMP, INTERVAL 1 DAY))",
                seekerId, companyId);
        responseId = insert("INSERT INTO referral_request_responses "
                + "(request_id, referrer_id, status, accepted_at) "
                + "VALUES (?, ?, 'accepted', DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 3 DAY))",
                requestId, referrerId);
    }

    @AfterEach
    void cleanUp() {
        jdbc.sql("DELETE FROM users WHERE id IN (?, ?)").params(seekerId, referrerId).update();
        jdbc.sql("DELETE FROM companies WHERE id = ?").param(companyId).update();
    }

    @Test
    void failedReminderRollsBackAndIsRetriedOnNextRun() {
        doThrow(new MailSendException("synthetic failure")).doNothing()
                .when(mailSender).send(any(SimpleMailMessage.class));

        assertThatThrownBy(maintenance::processDueRequests).isInstanceOf(IllegalStateException.class);
        assertThat(reminderCount()).isZero();
        assertThat(notificationCount()).isZero();

        maintenance.processDueRequests();
        maintenance.processDueRequests();
        assertThat(reminderCount()).isEqualTo(1);
        assertThat(notificationCount()).isEqualTo(1);
        verify(mailSender, times(2)).send(any(SimpleMailMessage.class));
    }

    @Test
    void overlappingRunsSendOnlyOneReminderForTheSameDueWindow() throws Exception {
        runConcurrently();
        assertThat(reminderCount()).isEqualTo(1);
        assertThat(notificationCount()).isEqualTo(1);
        verify(mailSender, times(1)).send(any(SimpleMailMessage.class));
    }

    @Test
    void overlappingRunsExpireARequestOnlyOnce() throws Exception {
        jdbc.sql("UPDATE referral_request_responses SET status = 'pending', accepted_at = NULL WHERE id = ?")
                .param(responseId).update();
        jdbc.sql("UPDATE company_referral_requests SET expires_at = DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 1 MINUTE) "
                + "WHERE id = (SELECT request_id FROM referral_request_responses WHERE id = ?)")
                .param(responseId).update();

        runConcurrently();

        assertThat(jdbc.sql("SELECT status FROM referral_request_responses WHERE id = ?")
                .param(responseId).query(String.class).single()).isEqualTo("expired");
        assertThat(jdbc.sql("SELECT COUNT(*) FROM notifications WHERE user_id = ? AND type = 'referral_request_expired'")
                .param(seekerId).query(Integer.class).single()).isEqualTo(1);
    }

    private void runConcurrently() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                start.await();
                maintenance.processDueRequests();
                return null;
            });
            var second = executor.submit(() -> {
                start.await();
                maintenance.processDueRequests();
                return null;
            });
            start.countDown();
            first.get(30, TimeUnit.SECONDS);
            second.get(30, TimeUnit.SECONDS);
        }
    }

    private long insert(String sql, Object... parameters) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.sql(sql).params(parameters).update(key);
        return key.getKey().longValue();
    }

    private int reminderCount() {
        return jdbc.sql("SELECT reminders_sent FROM referral_request_responses WHERE id = ?")
                .param(responseId).query(Integer.class).single();
    }

    private int notificationCount() {
        return jdbc.sql("SELECT COUNT(*) FROM notifications WHERE user_id = ? AND type = 'referral_reminder'")
                .param(referrerId).query(Integer.class).single();
    }
}
