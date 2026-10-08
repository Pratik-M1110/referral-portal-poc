package com.referralportal.referral;

import com.referralportal.user.CurrentUser;
import com.referralportal.user.UserService;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ReferralController {

    record ReferralView(long id, String companyName, String referrerName, String roleTitle, String status, String updatedAt) {}

    private final UserService users;
    private final JdbcClient jdbc;

    ReferralController(UserService users, JdbcClient jdbc) {
        this.users = users;
        this.jdbc = jdbc;
    }

    /** Referrals received by the signed-in person. */
    @GetMapping("/api/referrals")
    List<ReferralView> list(Authentication auth, @RequestParam(name = "as", defaultValue = "candidate") String as) {
        CurrentUser me = users.current(auth);
        return jdbc.sql("""
                SELECT r.id, co.name AS company_name, u.name AS referrer_name, ro.title AS role_title, r.status, r.updated_at
                FROM referrals r
                JOIN companies co ON co.id = r.company_id
                JOIN users u ON u.id = r.referrer_id
                LEFT JOIN open_roles ro ON ro.id = r.role_id
                WHERE r.candidate_id = ? ORDER BY r.updated_at DESC""")
                .param(me.id())
                .query((rs, n) -> new ReferralView(rs.getLong("id"), rs.getString("company_name"),
                        rs.getString("referrer_name"), rs.getString("role_title"), rs.getString("status"),
                        rs.getTimestamp("updated_at").toInstant().toString()))
                .list();
    }
}
