package com.referralportal.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class LoginAttemptRateLimiter {

    private static final String UPSERT_ATTEMPT = """
            INSERT INTO auth_login_rate_limits (bucket_hash, window_started_at, attempt_count)
            VALUES (?, UTC_TIMESTAMP(6), 1)
            ON DUPLICATE KEY UPDATE
                attempt_count = IF(window_started_at < TIMESTAMPADD(SECOND, -?, UTC_TIMESTAMP(6)),
                    1, attempt_count + 1),
                window_started_at = IF(window_started_at < TIMESTAMPADD(SECOND, -?, UTC_TIMESTAMP(6)),
                    UTC_TIMESTAMP(6), window_started_at)
            """;
    private static final String SELECT_ATTEMPTS =
            "SELECT attempt_count FROM auth_login_rate_limits WHERE bucket_hash = ?";
    private static final String INVALID_CREDENTIALS = "Invalid email or password.";

    private final JdbcTemplate jdbcTemplate;
    private final int windowSeconds;
    private final int accountAttemptLimit;
    private final int ipAttemptLimit;

    public LoginAttemptRateLimiter(JdbcTemplate jdbcTemplate,
            @Value("${app.auth.login-rate-limit.window-seconds:900}") int windowSeconds,
            @Value("${app.auth.login-rate-limit.account-attempts:10}") int accountAttemptLimit,
            @Value("${app.auth.login-rate-limit.ip-attempts:100}") int ipAttemptLimit) {
        this.jdbcTemplate = jdbcTemplate;
        this.windowSeconds = windowSeconds;
        this.accountAttemptLimit = accountAttemptLimit;
        this.ipAttemptLimit = ipAttemptLimit;
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public void checkAllowed(String email, String clientIp) {
        if (increment("ip", clientIp) > ipAttemptLimit) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    retryMessage());
        }
        if (increment("email", normalizeEmail(email)) > accountAttemptLimit) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, INVALID_CREDENTIALS);
        }
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public void checkRegistrationAllowed(String email, String clientIp) {
        if (increment("registration-ip", clientIp) > ipAttemptLimit) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many registration attempts from this network. Try again later.");
        }
        if (increment("registration-email", normalizeEmail(email)) > accountAttemptLimit) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many verification codes requested. Try again in 15 minutes.");
        }
    }

    @Transactional
    public void recordSuccessfulLogin(String email) {
        jdbcTemplate.update("""
                UPDATE auth_login_rate_limits
                SET window_started_at = UTC_TIMESTAMP(6), attempt_count = 0
                WHERE bucket_hash = ?
                """, fingerprint("email", normalizeEmail(email)));
    }

    @Transactional
    public void recordSuccessfulRegistration(String email) {
        jdbcTemplate.update("""
                UPDATE auth_login_rate_limits
                SET window_started_at = UTC_TIMESTAMP(6), attempt_count = 0
                WHERE bucket_hash = ?
                """, fingerprint("registration-email", normalizeEmail(email)));
    }

    @Scheduled(fixedDelayString = "${app.auth.login-rate-limit.cleanup-delay:21600000}")
    @Transactional
    public void removeExpiredBuckets() {
        jdbcTemplate.update("""
                DELETE FROM auth_login_rate_limits
                WHERE window_started_at < TIMESTAMPADD(SECOND, -?, UTC_TIMESTAMP(6))
                """, windowSeconds);
    }

    private int increment(String type, String value) {
        String bucketHash = fingerprint(type, value);
        jdbcTemplate.update(UPSERT_ATTEMPT, bucketHash, windowSeconds, windowSeconds);
        return jdbcTemplate.queryForObject(SELECT_ATTEMPTS, Integer.class, bucketHash);
    }

    private String retryMessage() {
        int retryMinutes = Math.max(1, (windowSeconds + 59) / 60);
        String unit = retryMinutes == 1 ? "minute" : "minutes";
        return "Too many sign-in attempts from this network. Try again in " + retryMinutes + " " + unit + ".";
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String fingerprint(String type, String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((type + ":" + value).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable.", impossible);
        }
    }
}