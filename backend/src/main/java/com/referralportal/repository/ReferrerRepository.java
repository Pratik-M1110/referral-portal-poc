package com.referralportal.repository;

import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import static com.referralportal.referrer.ReferrerDtos.Settings;

@Repository
public class ReferrerRepository {

    private final JdbcClient jdbc;

    public ReferrerRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public long countOtpRequestsInLast15Minutes(long userId) {
        return jdbc.sql("SELECT COUNT(*) FROM email_otps WHERE user_id = ? AND created_at >= DATE_SUB(NOW(), INTERVAL 15 MINUTE)")
                .param(userId).query(Long.class).single();
    }

    public boolean hasOtpRequestInLastMinute(long userId) {
        return jdbc.sql("SELECT COUNT(*) FROM email_otps WHERE user_id = ? AND created_at >= DATE_SUB(NOW(), INTERVAL 1 MINUTE)")
                .param(userId).query(Long.class).single() > 0;
    }

    public void deleteOtps(long userId) {
        jdbc.sql("DELETE FROM email_otps WHERE user_id = ?").param(userId).update();
    }

    public void insertOtp(long userId, String workEmail, String codeHash) {
        jdbc.sql("INSERT INTO email_otps (user_id, work_email, code_hash, expires_at) VALUES (?, ?, ?, DATE_ADD(NOW(), INTERVAL 10 MINUTE))")
                .params(userId, workEmail, codeHash).update();
    }

    public Optional<EmailOtp> latestOtpForUpdate(long userId) {
        return jdbc.sql("SELECT id, work_email, code_hash, expires_at <= NOW() AS expired, attempts FROM email_otps WHERE user_id = ? ORDER BY created_at DESC, id DESC LIMIT 1 FOR UPDATE")
            .param(userId)
            .query((rs, row) -> new EmailOtp(rs.getLong("id"), rs.getString("work_email"),
                rs.getString("code_hash"), rs.getBoolean("expired"), rs.getInt("attempts")))
                .optional();
    }

    public void incrementAttempts(long otpId) {
        jdbc.sql("UPDATE email_otps SET attempts = attempts + 1 WHERE id = ?").param(otpId).update();
    }

    public void deleteOtp(long otpId) {
        jdbc.sql("DELETE FROM email_otps WHERE id = ?").param(otpId).update();
    }

    public boolean emailAlreadyClaimed(long userId, String workEmail) {
        long referrerOwner = jdbc.sql("SELECT COUNT(*) FROM referrer_profiles WHERE work_email = ? AND user_id <> ?")
            .params(workEmail, userId).query(Long.class).single();
        long accountOwner = jdbc.sql("SELECT COUNT(*) FROM users WHERE email = ? AND id <> ?")
            .params(workEmail, userId).query(Long.class).single();
        return referrerOwner > 0 || accountOwner > 0;
    }

    public long findOrCreateCompany(String emailDomain, String companyName) {
        jdbc.sql("INSERT IGNORE INTO companies (name, email_domain) VALUES (?, ?)")
                .params(companyName, emailDomain).update();
        return jdbc.sql("SELECT id FROM companies WHERE email_domain = ?").param(emailDomain).query(Long.class).single();
    }

    public void verifyReferrer(long userId, long companyId, String workEmail) {
        jdbc.sql("""
                INSERT INTO referrer_profiles (user_id, company_id, work_email, verified_at)
                VALUES (?, ?, ?, NOW())
                ON DUPLICATE KEY UPDATE company_id = VALUES(company_id), work_email = VALUES(work_email),
                  verified_at = NOW(), is_referrer = TRUE""")
                .params(userId, companyId, workEmail).update();
    }

    public Settings settings(long userId) {
        return jdbc.sql("""
                SELECT rp.verified_at, rp.work_email, c.name AS company_name, rp.open_to_meet,
                       rp.slot_capacity, rp.referral_limit
                FROM referrer_profiles rp JOIN companies c ON c.id = rp.company_id WHERE rp.user_id = ?""")
                .param(userId)
                .query((rs, row) -> new Settings(rs.getTimestamp("verified_at") != null, rs.getString("work_email"),
                        rs.getString("company_name"), rs.getBoolean("open_to_meet"), rs.getInt("slot_capacity"),
                        rs.getInt("referral_limit")))
                .optional()
                .orElse(new Settings(false, null, null, false, 3, 10));
    }

    public void updateSettings(long userId, boolean openToMeet, int slotCapacity, int referralLimit) {
        jdbc.sql("UPDATE referrer_profiles SET open_to_meet = ?, slot_capacity = ?, referral_limit = ? WHERE user_id = ? AND verified_at IS NOT NULL")
                .params(openToMeet, slotCapacity, referralLimit, userId).update();
    }

    public record EmailOtp(long id, String workEmail, String codeHash, boolean expired, int attempts) {}
}