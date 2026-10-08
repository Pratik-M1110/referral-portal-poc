package com.referralportal.company;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class RequestBudget {

    public static final int REQUESTS_MAX = 3;

    private final JdbcClient jdbc;

    RequestBudget(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** 3 plus credits (always 0 for now) minus meet requests this person started themselves. */
    public int left(long seekerId) {
        long used = jdbc.sql("SELECT COUNT(*) FROM conversations WHERE seeker_id = ? AND initiated_by = 'seeker'")
                .param(seekerId).query(Long.class).single();
        long credits = jdbc.sql("SELECT COALESCE(SUM(delta), 0) FROM credit_ledger WHERE user_id = ?").param(seekerId)
                .query(Long.class).single();
        return (int) Math.max(0, REQUESTS_MAX + credits - used);
    }
}
