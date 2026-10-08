package com.referralportal.company;

import com.referralportal.common.ChipLists;
import com.referralportal.user.CurrentUser;
import com.referralportal.user.UserService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
class CompanyController {

    static final int WISHLIST_MAX = 5;

    record Person(long userId, String name, String jobTitle, boolean fullyBooked) {}

    record RoleView(long id, String title, String level, List<String> skills) {}

    record CompanyView(long id, String name, String domain, String boothLabel, boolean sponsor, boolean wishlisted,
            List<RoleView> roles, List<Person> people) {}

    record CompaniesResponse(int wishlistCount, int wishlistMax, int requestsLeft, int requestsMax,
            List<CompanyView> companies) {}

    private final UserService users;
    private final JdbcClient jdbc;
    private final RequestBudget budget;

    CompanyController(UserService users, JdbcClient jdbc, RequestBudget budget) {
        this.users = users;
        this.jdbc = jdbc;
        this.budget = budget;
    }

    @GetMapping("/api/companies")
    CompaniesResponse list(Authentication auth) {
        CurrentUser user = users.current(auth);
        List<Long> wished = jdbc.sql("SELECT company_id FROM wishlists WHERE seeker_id = ?").param(user.id())
                .query(Long.class).list();
        List<CompanyView> companies = new ArrayList<>();
        var rows = jdbc.sql("SELECT id, name, email_domain, booth_label, is_sponsor FROM companies ORDER BY is_sponsor DESC, name")
                .query((rs, n) -> new Object[] {rs.getLong("id"), rs.getString("name"), rs.getString("email_domain"),
                        rs.getString("booth_label"), rs.getBoolean("is_sponsor")})
                .list();
        for (Object[] row : rows) {
            long companyId = (Long) row[0];
            List<RoleView> roles = jdbc.sql("SELECT id, title, level, CAST(skills AS CHAR) AS skills FROM open_roles WHERE company_id = ? ORDER BY id")
                    .param(companyId)
                    .query((rs, n) -> new RoleView(rs.getLong("id"), rs.getString("title"), rs.getString("level"),
                            ChipLists.fromJson(rs.getString("skills"))))
                    .list();
            List<Person> people = jdbc.sql("""
                    SELECT u.id, u.name, rp.job_title, rp.slot_capacity,
                      (SELECT COUNT(*) FROM conversations c WHERE c.referrer_id = u.id AND c.status IN ('accepted','completed')) AS used
                    FROM referrer_profiles rp JOIN users u ON u.id = rp.user_id
                    WHERE rp.company_id = ? AND rp.verified_at IS NOT NULL AND rp.is_referrer = TRUE AND rp.open_to_meet = TRUE
                      AND u.id <> ? ORDER BY u.name""")
                    .params(companyId, user.id())
                    .query((rs, n) -> new Person(rs.getLong("id"), rs.getString("name"), rs.getString("job_title"),
                            rs.getInt("used") >= rs.getInt("slot_capacity")))
                    .list();
            companies.add(new CompanyView(companyId, (String) row[1], (String) row[2], (String) row[3], (Boolean) row[4],
                    wished.contains(companyId), roles, people));
        }
        return new CompaniesResponse(wished.size(), WISHLIST_MAX, budget.left(user.id()), RequestBudget.REQUESTS_MAX, companies);
    }

    @PutMapping("/api/wishlist/{companyId}")
    Map<String, Object> add(Authentication auth, @PathVariable long companyId) {
        CurrentUser user = users.current(auth);
        long exists = jdbc.sql("SELECT COUNT(*) FROM companies WHERE id = ?").param(companyId).query(Long.class).single();
        if (exists == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Company not found.");
        }
        long own = jdbc.sql("SELECT COUNT(*) FROM referrer_profiles WHERE user_id = ? AND company_id = ? AND verified_at IS NOT NULL")
                .params(user.id(), companyId).query(Long.class).single();
        if (own > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You cannot wishlist your own company.");
        }
        long already = jdbc.sql("SELECT COUNT(*) FROM wishlists WHERE seeker_id = ? AND company_id = ?")
                .params(user.id(), companyId).query(Long.class).single();
        if (already == 0) {
            long count = jdbc.sql("SELECT COUNT(*) FROM wishlists WHERE seeker_id = ?").param(user.id())
                    .query(Long.class).single();
            if (count >= WISHLIST_MAX) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Your wishlist is full (" + WISHLIST_MAX + " companies).");
            }
            jdbc.sql("INSERT IGNORE INTO wishlists (seeker_id, company_id) VALUES (?, ?)").params(user.id(), companyId).update();
        }
        return Map.of("wishlisted", true);
    }

    @DeleteMapping("/api/wishlist/{companyId}")
    Map<String, Object> remove(Authentication auth, @PathVariable long companyId) {
        CurrentUser user = users.current(auth);
        jdbc.sql("DELETE FROM wishlists WHERE seeker_id = ? AND company_id = ?").params(user.id(), companyId).update();
        return Map.of("wishlisted", false);
    }
}
