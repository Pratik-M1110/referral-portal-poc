package com.referralportal.company;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class RequestBudgetRepository {

    private final JdbcClient jdbc;

    RequestBudgetRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    long countRequestsStartedBy(long seekerId) {
        return jdbc.sql("SELECT COUNT(*) FROM conversations WHERE seeker_id = ? AND initiated_by = 'seeker'")
                .param(seekerId).query(Long.class).single();
    }

    long totalCredits(long userId) {
        return jdbc.sql("SELECT COALESCE(SUM(delta), 0) FROM credit_ledger WHERE user_id = ?").param(userId)
                .query(Long.class).single();
    }
}
