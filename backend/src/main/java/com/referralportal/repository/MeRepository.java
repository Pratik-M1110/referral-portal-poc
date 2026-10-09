package com.referralportal.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class MeRepository {

    private final JdbcClient jdbc;

    public MeRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public boolean hasProfile(long userId) {
        return jdbc.sql("SELECT COUNT(*) FROM profiles WHERE user_id = ?").param(userId).query(Long.class).single() > 0;
    }

    public boolean isVerifiedReferrer(long userId) {
        return jdbc.sql("SELECT COUNT(*) FROM referrer_profiles WHERE user_id = ? AND verified_at IS NOT NULL AND is_referrer = TRUE")
                .param(userId).query(Long.class).single() > 0;
    }
}
