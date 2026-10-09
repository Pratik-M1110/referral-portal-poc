package com.referralportal.service;

import com.referralportal.referrer.ReferrerDtos;
import com.referralportal.repository.ReferrerRepository;
import com.referralportal.repository.ReferrerRepository;
import com.referralportal.user.CurrentUser;
import com.referralportal.service.UserService;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import static com.referralportal.referrer.ReferrerDtos.Settings;
import static com.referralportal.referrer.ReferrerDtos.SettingsRequest;

@Service
public class ReferrerService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int MAX_ATTEMPTS = 5;
    private static final Set<String> FREE_EMAIL_DOMAINS = Set.of(
            "gmail.com", "googlemail.com", "yahoo.com", "yahoo.co.in", "outlook.com", "hotmail.com",
            "hotmail.co.uk", "live.com", "icloud.com", "me.com", "mac.com", "proton.me", "protonmail.com",
            "aol.com", "gmx.com", "yandex.com", "zoho.com");

    private final UserService users;
    private final ReferrerRepository referrers;
    private final JavaMailSender mailSender;
    private final PasswordEncoder passwordEncoder;
    private final TransactionTemplate transactions;
    private final String mailFrom;

    public ReferrerService(UserService users, ReferrerRepository referrers, JavaMailSender mailSender,
            PasswordEncoder passwordEncoder, TransactionTemplate transactions,
            @Value("${app.mail-from:}") String mailFrom) {
        this.users = users;
        this.referrers = referrers;
        this.mailSender = mailSender;
        this.passwordEncoder = passwordEncoder;
        this.transactions = transactions;
        this.mailFrom = mailFrom;
    }

    public ReferrerDtos.VerificationStarted startVerification(Authentication authentication, String rawEmail) {
        CurrentUser user = users.current(authentication);
        String workEmail = rawEmail.trim().toLowerCase(Locale.ROOT);
        int separator = workEmail.lastIndexOf('@');
        if (separator < 1 || FREE_EMAIL_DOMAINS.contains(workEmail.substring(separator + 1))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use your company work email address.");
        }
        if (referrers.emailAlreadyClaimed(user.id(), workEmail)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That work email is already linked to another account.");
        }
        if (referrers.settings(user.id()).verified()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Your work email is already verified.");
        }
        if (referrers.hasOtpRequestInLastMinute(user.id())) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Wait a minute before requesting another code.");
        }
        if (referrers.countOtpRequestsInLast15Minutes(user.id()) >= 3) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many codes requested. Try again in 15 minutes.");
        }

        String code = String.format(Locale.ROOT, "%06d", RANDOM.nextInt(1_000_000));
        String codeHash = passwordEncoder.encode(code);
        transactions.executeWithoutResult(status -> {
            referrers.insertOtp(user.id(), workEmail, codeHash);
        });

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(workEmail);
        if (!mailFrom.isBlank()) {
            message.setFrom(mailFrom);
        }
        message.setSubject("Your Referral Portal verification code");
        message.setText("Your work-email verification code is " + code + ". It expires in 10 minutes.");
        try {
            mailSender.send(message);
        } catch (MailException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Could not send the code. Please try again later.");
        }
        return new ReferrerDtos.VerificationStarted(true);
    }

    public Settings confirmVerification(Authentication authentication, String rawEmail, String code) {
        CurrentUser user = users.current(authentication);
        String workEmail = rawEmail.trim().toLowerCase(Locale.ROOT);
        ConfirmOutcome outcome = transactions.execute(status -> confirmInTransaction(user.id(), workEmail, code));
        if (outcome == null) {
            throw new IllegalStateException("Verification transaction returned no result.");
        }
        switch (outcome.result()) {
            case INVALID -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That code is incorrect.");
            case EXPIRED -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That code expired. Request a new one.");
            case ATTEMPTS_EXCEEDED -> throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many incorrect codes. Request a new one.");
            case EMAIL_IN_USE -> throw new ResponseStatusException(HttpStatus.CONFLICT, "That work email is already linked to another account.");
            case VERIFIED -> {
                return outcome.settings();
            }
        }
        throw new IllegalStateException("Unknown verification result.");
    }

    public Settings getSettings(Authentication authentication) {
        CurrentUser user = users.current(authentication);
        return referrers.settings(user.id());
    }

    public Settings updateSettings(Authentication authentication, SettingsRequest request) {
        CurrentUser user = users.current(authentication);
        if (!referrers.settings(user.id()).verified()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Verify your work email first.");
        }
        referrers.updateSettings(user.id(), request.openToMeet(), request.slotCapacity(), request.referralLimit());
        return referrers.settings(user.id());
    }

    private ConfirmOutcome confirmInTransaction(long userId, String workEmail, String code) {
        ReferrerRepository.EmailOtp otp = referrers.latestOtpForUpdate(userId).orElse(null);
        if (otp == null || !otp.workEmail().equals(workEmail)) {
            return new ConfirmOutcome(ConfirmResult.EXPIRED, null);
        }
        if (otp.expired()) {
            referrers.deleteOtp(otp.id());
            return new ConfirmOutcome(ConfirmResult.EXPIRED, null);
        }
        if (!passwordEncoder.matches(code, otp.codeHash())) {
            if (otp.attempts() + 1 >= MAX_ATTEMPTS) {
                referrers.deleteOtps(userId);
                return new ConfirmOutcome(ConfirmResult.ATTEMPTS_EXCEEDED, null);
            }
            referrers.incrementAttempts(otp.id());
            return new ConfirmOutcome(ConfirmResult.INVALID, null);
        }
        if (referrers.emailAlreadyClaimed(userId, workEmail)) {
            return new ConfirmOutcome(ConfirmResult.EMAIL_IN_USE, null);
        }
        String domain = workEmail.substring(workEmail.lastIndexOf('@') + 1);
        String companyName = companyNameFromDomain(domain);
        try {
            long companyId = referrers.findOrCreateCompany(domain, companyName);
            referrers.verifyReferrer(userId, companyId, workEmail);
        } catch (DuplicateKeyException e) {
            return new ConfirmOutcome(ConfirmResult.EMAIL_IN_USE, null);
        }
        referrers.deleteOtps(userId);
        return new ConfirmOutcome(ConfirmResult.VERIFIED, referrers.settings(userId));
    }

    private static String companyNameFromDomain(String domain) {
        String label = domain.substring(0, domain.indexOf('.') < 0 ? domain.length() : domain.indexOf('.'));
        return label.isEmpty() ? domain : label.substring(0, 1).toUpperCase(Locale.ROOT) + label.substring(1);
    }

    private enum ConfirmResult { INVALID, EXPIRED, ATTEMPTS_EXCEEDED, EMAIL_IN_USE, VERIFIED }

    private record ConfirmOutcome(ConfirmResult result, Settings settings) {}
}