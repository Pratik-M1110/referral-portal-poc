package com.referralportal.referrer;

import com.referralportal.service.ReferrerService;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.server.ResponseStatusException;

import static com.referralportal.dto.ReferrerDtos.Settings;

@SpringBootTest(properties = {
        "spring.security.oauth2.client.registration.google.client-id=test-google-client",
        "spring.security.oauth2.client.registration.google.client-secret=test-google-secret",
        "spring.security.oauth2.client.registration.github.client-id=test-github-client",
        "spring.security.oauth2.client.registration.github.client-secret=test-github-secret",
        "app.seed-demo=false"
})
@Transactional
class ReferrerVerificationTests {

    private static final Pattern CODE_PATTERN = Pattern.compile("is ([0-9]{6})\\.");

    @Autowired
    private ReferrerService referrers;

    @Autowired
    private JdbcClient jdbc;

    @MockitoBean
    private JavaMailSender mailSender;

    @Test
    void verifiesWorkEmailAndCreatesReferrerSettings() {
        OAuth2AuthenticationToken authentication = authentication();
        String workEmail = "person@bright-company.test";

        referrers.startVerification(authentication, workEmail);
        SimpleMailMessage email = lastSentEmail();
        String code = codeFrom(email);

        Settings settings = referrers.confirmVerification(authentication, workEmail, code);

        assertTrue(settings.verified());
        assertEquals(workEmail, settings.workEmail());
        assertEquals("Bright-company", settings.companyName());
        assertFalse(settings.openToMeet());
        assertEquals(3, settings.slotCapacity());
        assertEquals(10, settings.referralLimit());
        assertEquals(0, count("email_otps", "work_email = ?", workEmail));
    }

    @Test
    void rejectsFreeEmailDomainsBeforeSendingCode() {
        OAuth2AuthenticationToken authentication = authentication();
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> referrers.startVerification(authentication, "person@gmail.com"));

        assertEquals(HttpStatus.BAD_REQUEST.value(), exception.getStatusCode().value());
    }

    @Test
    void limitsCodeRequestsAcrossWorkEmailAddresses() {
        OAuth2AuthenticationToken authentication = authentication();
        String accountEmail = (String) authentication.getPrincipal().getAttribute("email");

        for (int request = 1; request <= 3; request++) {
            referrers.startVerification(authentication, "person" + request + "@company" + request + ".test");
            jdbc.sql("UPDATE email_otps SET created_at = DATE_SUB(NOW(), INTERVAL 2 MINUTE) WHERE user_id = (SELECT id FROM users WHERE email = ?)")
                    .param(accountEmail).update();
        }

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> referrers.startVerification(authentication, "another@different-company.test"));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS.value(), exception.getStatusCode().value());
    }

    @Test
    void locksCodeAfterFiveIncorrectAttempts() {
        OAuth2AuthenticationToken authentication = authentication();
        String workEmail = "person@attempt-company.test";
        referrers.startVerification(authentication, workEmail);
        String correctCode = codeFrom(lastSentEmail());
        String wrongCode = "000000".equals(correctCode) ? "000001" : "000000";

        for (int attempt = 1; attempt <= 5; attempt++) {
            ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                    () -> referrers.confirmVerification(authentication, workEmail, wrongCode));
            int expectedStatus = attempt == 5 ? HttpStatus.TOO_MANY_REQUESTS.value() : HttpStatus.BAD_REQUEST.value();
            assertEquals(expectedStatus, exception.getStatusCode().value());
        }

        assertEquals(0, count("email_otps", "work_email = ?", workEmail));
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> referrers.confirmVerification(authentication, workEmail, correctCode));
        assertEquals(HttpStatus.BAD_REQUEST.value(), exception.getStatusCode().value());
    }

    private OAuth2AuthenticationToken authentication() {
        String email = "test-" + UUID.randomUUID().toString().replace("-", "") + "@example.test";
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
        var principal = new DefaultOAuth2User(authorities, Map.of("email", email, "name", "Test Attendee"), "email");
        return new OAuth2AuthenticationToken(principal, authorities, "google");
    }

    private SimpleMailMessage lastSentEmail() {
        var captor = org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, org.mockito.Mockito.atLeastOnce()).send(captor.capture());
        return captor.getValue();
    }

    private static String codeFrom(SimpleMailMessage email) {
        Matcher matcher = CODE_PATTERN.matcher(email.getText());
        assertTrue(matcher.find());
        return matcher.group(1);
    }

    private long count(String table, String predicate, Object... parameters) {
        return jdbc.sql("SELECT COUNT(*) FROM " + table + " WHERE " + predicate).params(parameters)
                .query(Long.class).single();
    }
}