package com.referralportal.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class BadgeRepository {

    private final JdbcClient jdbc;

    public BadgeRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public String findTokenByUserId(long userId) {
        return jdbc.sql("SELECT qr_token FROM badges WHERE user_id = ?").param(userId).query(String.class).single();
    }
}
