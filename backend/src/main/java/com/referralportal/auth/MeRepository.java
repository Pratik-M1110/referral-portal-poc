package com.referralportal.auth;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class MeRepository {

    private final JdbcClient jdbc;

    MeRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    boolean hasProfile(long userId) {
        return jdbc.sql("SELECT COUNT(*) FROM profiles WHERE user_id = ?").param(userId).query(Long.class).single() > 0;
    }

    boolean isVerifiedReferrer(long userId) {
        return jdbc.sql("SELECT COUNT(*) FROM referrer_profiles WHERE user_id = ? AND verified_at IS NOT NULL AND is_referrer = TRUE")
                .param(userId).query(Long.class).single() > 0;
    }
}
