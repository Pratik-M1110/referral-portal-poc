package com.referralportal.user;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
        "spring.security.oauth2.client.registration.google.client-id=test-google-client",
        "spring.security.oauth2.client.registration.google.client-secret=test-google-secret",
        "spring.security.oauth2.client.registration.github.client-id=test-github-client",
        "spring.security.oauth2.client.registration.github.client-secret=test-github-secret"
})
@Transactional
class UserRepositoryDeletionTests {

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private UserRepository users;

    @Test
    void deletesAccountAndRelationsWithoutDeletingOtherUser() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        long accountId = insertUser("delete-target-" + suffix + "@example.test");
        long otherId = insertUser("delete-other-" + suffix + "@example.test");
        long companyId = insertCompany(suffix);

        jdbc.sql("INSERT INTO profiles (user_id, experience_level) VALUES (?, 'fresher')").param(accountId).update();
        jdbc.sql("INSERT INTO referrer_profiles (user_id, company_id, work_email) VALUES (?, ?, ?)")
                .params(accountId, companyId, "work-" + suffix + "@example.test").update();
        jdbc.sql("INSERT INTO badges (user_id, qr_token) VALUES (?, ?)").params(accountId, suffix).update();
        jdbc.sql("INSERT INTO resume_files (user_id, filename, content_type, size_bytes, sha256, data) VALUES (?, 'resume.pdf', 'application/pdf', 1, ?, ?) ")
                .params(accountId, "a".repeat(64), new byte[] {1}).update();
        jdbc.sql("INSERT INTO email_otps (user_id, work_email, code_hash, expires_at) VALUES (?, ?, ?, DATE_ADD(NOW(), INTERVAL 10 MINUTE))")
                .params(accountId, "work-" + suffix + "@example.test", "b".repeat(64)).update();
        jdbc.sql("INSERT INTO wishlists (seeker_id, company_id) VALUES (?, ?)").params(accountId, companyId).update();
        jdbc.sql("INSERT INTO reviews (referrer_id, candidate_id, action) VALUES (?, ?, 'save'), (?, ?, 'pass')")
                .params(accountId, otherId, otherId, accountId).update();
        jdbc.sql("INSERT INTO referrals (referrer_id, candidate_id, company_id) VALUES (?, ?, ?), (?, ?, ?)")
                .params(accountId, otherId, companyId, otherId, accountId, companyId).update();
        jdbc.sql("INSERT INTO scans (scanner_id, scanned_user_id, type) VALUES (?, ?, 'referrer_scan'), (?, ?, 'referrer_scan')")
                .params(accountId, otherId, otherId, accountId).update();
        long seekerConversation = insertConversation(accountId, otherId);
        long referrerConversation = insertConversation(otherId, accountId);
        jdbc.sql("INSERT INTO messages (conversation_id, sender_id, body) VALUES (?, ?, 'hello'), (?, ?, 'reply')")
                .params(seekerConversation, accountId, referrerConversation, accountId).update();
        jdbc.sql("INSERT INTO credit_ledger (user_id, delta, reason) VALUES (?, 1, 'test')").param(accountId).update();
        jdbc.sql("INSERT INTO reports_blocks (reporter_id, target_id, type) VALUES (?, ?, 'block')")
                .params(accountId, otherId).update();
        jdbc.sql("INSERT INTO notifications (user_id, type) VALUES (?, 'test')").param(accountId).update();

        users.deleteAccount(accountId);

        assertEquals(0, count("users", "id = ?", accountId));
        assertEquals(1, count("users", "id = ?", otherId));
        assertEquals(0, count("profiles", "user_id = ?", accountId));
        assertEquals(0, count("referrer_profiles", "user_id = ?", accountId));
        assertEquals(0, count("resume_files", "user_id = ?", accountId));
        assertEquals(0, count("email_otps", "user_id = ?", accountId));
        assertEquals(0, count("badges", "user_id = ?", accountId));
        assertEquals(0, count("wishlists", "seeker_id = ?", accountId));
        assertEquals(0, count("reviews", "referrer_id = ? OR candidate_id = ?", accountId, accountId));
        assertEquals(0, count("referrals", "referrer_id = ? OR candidate_id = ?", accountId, accountId));
        assertEquals(0, count("scans", "scanner_id = ? OR scanned_user_id = ?", accountId, accountId));
        assertEquals(0, count("conversations", "seeker_id = ? OR referrer_id = ?", accountId, accountId));
        assertEquals(0, count("messages", "sender_id = ?", accountId));
        assertEquals(0, count("credit_ledger", "user_id = ?", accountId));
        assertEquals(0, count("reports_blocks", "reporter_id = ? OR target_id = ?", accountId, accountId));
        assertEquals(0, count("notifications", "user_id = ?", accountId));
    }

    private long insertUser(String email) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.sql("INSERT INTO users (name, email, auth_provider) VALUES ('Delete Test', ?, 'test')")
                .param(email).update(keys);
        return keys.getKey().longValue();
    }

    private long insertCompany(String suffix) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.sql("INSERT INTO companies (name, email_domain) VALUES ('Delete Test Company', ?)")
                .param("delete-" + suffix + ".example.test").update(keys);
        return keys.getKey().longValue();
    }

    private long insertConversation(long seekerId, long referrerId) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.sql("INSERT INTO conversations (seeker_id, referrer_id) VALUES (?, ?)")
                .params(seekerId, referrerId).update(keys);
        return keys.getKey().longValue();
    }

    private long count(String table, String predicate, Object... parameters) {
        return jdbc.sql("SELECT COUNT(*) FROM " + table + " WHERE " + predicate)
                .params(parameters).query(Long.class).single();
    }
}