package com.referralportal.referral;

import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import static com.referralportal.referral.ReferralDtos.ReferralView;

@Repository
class ReferralRepository {

    private final JdbcClient jdbc;

    ReferralRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    List<ReferralView> findReceivedByCandidateId(long candidateId) {
        return jdbc.sql("""
                SELECT r.id, co.name AS company_name, u.name AS referrer_name, ro.title AS role_title, r.status, r.updated_at
                FROM referrals r
                JOIN companies co ON co.id = r.company_id
                JOIN users u ON u.id = r.referrer_id
                LEFT JOIN open_roles ro ON ro.id = r.role_id
                WHERE r.candidate_id = ? ORDER BY r.updated_at DESC""")
                .param(candidateId)
                .query((rs, n) -> new ReferralView(rs.getLong("id"), rs.getString("company_name"),
                        rs.getString("referrer_name"), rs.getString("role_title"), rs.getString("status"),
                        rs.getTimestamp("updated_at").toInstant().toString()))
                .list();
    }
}
