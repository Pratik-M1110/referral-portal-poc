package com.referralportal.badge;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class BadgeRepository {

    private final JdbcClient jdbc;

    BadgeRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    String findTokenByUserId(long userId) {
        return jdbc.sql("SELECT qr_token FROM badges WHERE user_id = ?").param(userId).query(String.class).single();
    }
}
