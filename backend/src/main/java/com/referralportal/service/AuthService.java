package com.referralportal.service;

import com.referralportal.entity.PendingPasswordRegistration;
import com.referralportal.repository.UserRepository;
import com.referralportal.repository.jpa.PendingPasswordRegistrationJpaRepository;
import com.referralportal.repository.jpa.UserJpaRepository;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/** Registration for email/password accounts. Sign-in itself goes through AuthenticationManager. */
@Service
public class AuthService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int BCRYPT_MAX_PASSWORD_BYTES = 72;
    private static final int MAX_REGISTRATION_ATTEMPTS = 5;
    private static final String INVALID_REGISTRATION = "Invalid or expired verification code. Start registration again or sign in.";

    @Autowired
    private UserRepository users;
    @Autowired
    private UserJpaRepository userJpa;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private PendingPasswordRegistrationJpaRepository pendingRegistrations;
    @Autowired
    private LoginAttemptRateLimiter loginAttemptRateLimiter;
    @Autowired
    private JavaMailSender mailSender;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Value("${app.mail-from:}")
    private String mailFrom;

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public void beginRegistration(String name, String email, String rawPassword, String clientIp) {
        validatePasswordLength(rawPassword);
        String normalizedEmail = normalizeEmail(email);
        loginAttemptRateLimiter.checkRegistrationAllowed(normalizedEmail, clientIp);
        if (userJpa.findByEmail(normalizedEmail).isPresent()) {
            return;
        }

        String code = String.format(Locale.ROOT, "%06d", RANDOM.nextInt(1_000_000));
        Instant now = Instant.now();
        pendingRegistrations.upsert(normalizedEmail, name.trim(), passwordEncoder.encode(rawPassword),
                passwordEncoder.encode(code), now.plusSeconds(600), now);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(normalizedEmail);
        if (mailFrom != null && !mailFrom.isBlank()) {
            message.setFrom(mailFrom);
        }
        message.setSubject("Verify your Referral Portal account");
        message.setText("Your email verification code is " + code + ". It expires in 10 minutes.");
        try {
            mailSender.send(message);
        } catch (MailException failure) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Could not send the verification code. Please try again later.");
        }
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public void verifyRegistration(String email, String code, String rawPassword) {
        validatePasswordLength(rawPassword);
        String normalizedEmail = normalizeEmail(email);
        PendingPasswordRegistration pending = pendingRegistrations.findByEmailForUpdate(normalizedEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, INVALID_REGISTRATION));

        if (!pending.getExpiresAt().isAfter(Instant.now()) || userJpa.findByEmail(normalizedEmail).isPresent()) {
            pendingRegistrations.delete(pending);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, INVALID_REGISTRATION);
        }
        if (!passwordEncoder.matches(code, pending.getCodeHash())
                || !passwordEncoder.matches(rawPassword, pending.getPasswordHash())) {
            rejectFailedAttempt(pending);
        }
        TransactionTemplate accountTransaction = new TransactionTemplate(transactionManager);
        accountTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        try {
            accountTransaction.executeWithoutResult(status ->
                    users.createPasswordAccount(pending.getName(), normalizedEmail,
                            pending.getPasswordHash(), newToken()));
        } catch (DataIntegrityViolationException concurrentInsert) {
            pendingRegistrations.delete(pending);
            throw emailTaken();
        }
        pendingRegistrations.delete(pending);
        loginAttemptRateLimiter.recordSuccessfulRegistration(normalizedEmail);
    }

    public void validatePasswordLength(String rawPassword) {
        if (rawPassword.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_PASSWORD_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Password must be no more than 72 UTF-8 bytes.");
        }
    }

    private void rejectFailedAttempt(PendingPasswordRegistration pending) {
        if (pending.getAttempts() + 1 >= MAX_REGISTRATION_ATTEMPTS) {
            pendingRegistrations.delete(pending);
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many incorrect attempts. Start registration again.");
        }
        pending.setAttempts((short) (pending.getAttempts() + 1));
        pendingRegistrations.save(pending);
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, INVALID_REGISTRATION);
    }

    private static ResponseStatusException emailTaken() {
        return new ResponseStatusException(HttpStatus.CONFLICT,
                "An account with this email already exists. Try signing in instead.");
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String newToken() {
        byte[] bytes = new byte[16]; // 128 bits, rendered as 32 hex characters
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
