package com.referralportal.company;

import com.referralportal.common.ChipLists;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import static com.referralportal.company.CompanyDtos.CompanyView;
import static com.referralportal.company.CompanyDtos.Person;
import static com.referralportal.company.CompanyDtos.RoleView;

@Repository
class CompanyRepository {

    private final JdbcClient jdbc;

    CompanyRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Set<Long> findWishlistedCompanyIds(long seekerId) {
        return Set.copyOf(jdbc.sql("SELECT company_id FROM wishlists WHERE seeker_id = ?").param(seekerId)
                .query(Long.class).list());
    }

    List<CompanyView> findCompanies(long seekerId, Set<Long> wishlistedIds) {
        List<CompanyView> companies = new ArrayList<>();
        List<CompanyRow> rows = jdbc.sql("SELECT id, name, email_domain, booth_label, is_sponsor FROM companies ORDER BY is_sponsor DESC, name")
                .query((rs, n) -> new CompanyRow(rs.getLong("id"), rs.getString("name"), rs.getString("email_domain"),
                        rs.getString("booth_label"), rs.getBoolean("is_sponsor")))
                .list();
        for (CompanyRow row : rows) {
            List<RoleView> roles = jdbc.sql("SELECT id, title, level, CAST(skills AS CHAR) AS skills FROM open_roles WHERE company_id = ? ORDER BY id")
                    .param(row.id())
                    .query((rs, n) -> new RoleView(rs.getLong("id"), rs.getString("title"), rs.getString("level"),
                            ChipLists.fromJson(rs.getString("skills"))))
                    .list();
            List<Person> people = jdbc.sql("""
                    SELECT u.id, u.name, rp.job_title, rp.slot_capacity,
                      (SELECT COUNT(*) FROM conversations c WHERE c.referrer_id = u.id AND c.status IN ('accepted','completed')) AS used
                    FROM referrer_profiles rp JOIN users u ON u.id = rp.user_id
                    WHERE rp.company_id = ? AND rp.verified_at IS NOT NULL AND rp.is_referrer = TRUE AND rp.open_to_meet = TRUE
                      AND u.id <> ? ORDER BY u.name""")
                    .params(row.id(), seekerId)
                    .query((rs, n) -> new Person(rs.getLong("id"), rs.getString("name"), rs.getString("job_title"),
                            rs.getInt("used") >= rs.getInt("slot_capacity")))
                    .list();
            companies.add(new CompanyView(row.id(), row.name(), row.domain(), row.boothLabel(), row.sponsor(),
                    wishlistedIds.contains(row.id()), roles, people));
        }
        return companies;
    }

    boolean existsById(long companyId) {
        return jdbc.sql("SELECT COUNT(*) FROM companies WHERE id = ?").param(companyId).query(Long.class).single() > 0;
    }

    boolean isVerifiedOwnCompany(long userId, long companyId) {
        return jdbc.sql("SELECT COUNT(*) FROM referrer_profiles WHERE user_id = ? AND company_id = ? AND verified_at IS NOT NULL")
                .params(userId, companyId).query(Long.class).single() > 0;
    }

    boolean isWishlisted(long seekerId, long companyId) {
        return jdbc.sql("SELECT COUNT(*) FROM wishlists WHERE seeker_id = ? AND company_id = ?")
                .params(seekerId, companyId).query(Long.class).single() > 0;
    }

    long countWishlistItems(long seekerId) {
        return jdbc.sql("SELECT COUNT(*) FROM wishlists WHERE seeker_id = ?").param(seekerId).query(Long.class).single();
    }

    void addToWishlist(long seekerId, long companyId) {
        jdbc.sql("INSERT IGNORE INTO wishlists (seeker_id, company_id) VALUES (?, ?)").params(seekerId, companyId).update();
    }

    void removeFromWishlist(long seekerId, long companyId) {
        jdbc.sql("DELETE FROM wishlists WHERE seeker_id = ? AND company_id = ?").params(seekerId, companyId).update();
    }

    private record CompanyRow(long id, String name, String domain, String boothLabel, boolean sponsor) {}
}
