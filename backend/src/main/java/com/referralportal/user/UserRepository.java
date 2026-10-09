package com.referralportal.user;

import java.util.Objects;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
class UserRepository {

    private final JdbcClient jdbc;

    UserRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Long findIdByEmail(String email) {
        return jdbc.sql("SELECT id FROM users WHERE email = ?").param(email).query(Long.class).optional().orElse(null);
    }

    long createAccount(String name, String email, String provider, String qrToken) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.sql("INSERT INTO users (name, email, auth_provider, consent_at) VALUES (?, ?, ?, NOW())")
                .params(name, email, provider).update(keys);
        long id = Objects.requireNonNull(keys.getKey()).longValue();
        jdbc.sql("INSERT INTO badges (user_id, qr_token) VALUES (?, ?)").params(id, qrToken).update();
        return id;
    }

    void deleteAccount(long userId) {
        jdbc.sql("DELETE FROM conversations WHERE seeker_id = ? OR referrer_id = ?").params(userId, userId).update();
        jdbc.sql("DELETE FROM reviews WHERE referrer_id = ? OR candidate_id = ?").params(userId, userId).update();
        jdbc.sql("DELETE FROM referrals WHERE referrer_id = ? OR candidate_id = ?").params(userId, userId).update();
        jdbc.sql("DELETE FROM users WHERE id = ?").param(userId).update();
    }
}
