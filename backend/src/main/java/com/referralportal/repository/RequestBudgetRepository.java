package com.referralportal.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class RequestBudgetRepository {

    private final JdbcClient jdbc;

    public RequestBudgetRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public long countRequestsStartedBy(long seekerId) {
        return jdbc.sql("SELECT COUNT(*) FROM conversations WHERE seeker_id = ? AND initiated_by = 'seeker'")
                .param(seekerId).query(Long.class).single();
    }

    public long totalCredits(long userId) {
        return jdbc.sql("SELECT COALESCE(SUM(delta), 0) FROM credit_ledger WHERE user_id = ?").param(userId)
                .query(Long.class).single();
    }
}
