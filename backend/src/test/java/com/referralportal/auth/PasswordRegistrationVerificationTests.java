package com.referralportal.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.referralportal.repository.UserRepository;
import com.referralportal.repository.jpa.PendingPasswordRegistrationJpaRepository;
import com.referralportal.repository.jpa.UserJpaRepository;
import com.referralportal.service.AuthService;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest(properties = {
        "spring.security.oauth2.client.registration.google.client-id=test-google-client",
        "spring.security.oauth2.client.registration.google.client-secret=test-google-secret",
        "spring.security.oauth2.client.registration.github.client-id=test-github-client",
        "spring.security.oauth2.client.registration.github.client-secret=test-github-secret",
        "spring.datasource.url=jdbc:mysql://localhost:3307/referral_portal?allowPublicKeyRetrieval=true&useSSL=false",
        "spring.datasource.username=referral",
        "spring.datasource.password=referral_local"
})
class PasswordRegistrationVerificationTests {

    private static final Pattern CODE_PATTERN = Pattern.compile("code is ([0-9]{6})\\.");

    @Autowired
    private AuthService auth;
    @Autowired
    private UserJpaRepository users;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PendingPasswordRegistrationJpaRepository pendingRegistrations;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @MockitoBean
    private JavaMailSender mailSender;

    @Test
    void createsPasswordAccountOnlyAfterCorrectEmailCode() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String email = "password-registration-" + suffix + "@example.test";
        String password = "correct horse 42";

        auth.beginRegistration("Password Seeker", email, password, "127.0.0.1");

        assertThat(users.findByEmail(email)).isEmpty();
        assertThat(pendingRegistrations.findById(email)).isPresent();
        SimpleMailMessage message = lastSentEmail();
        String code = codeFrom(message);
        String wrongCode = "000000".equals(code) ? "000001" : "000000";

        ResponseStatusException invalidCode = assertThrows(ResponseStatusException.class,
                () -> auth.verifyRegistration(email, wrongCode, password));
        assertThat(invalidCode.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(users.findByEmail(email)).isEmpty();

        auth.verifyRegistration(email, code, password);

        var user = users.findByEmail(email).orElseThrow();
        assertThat(user.getAuthProvider()).isEqualTo("password");
        assertThat(passwordEncoder.matches(password, user.getPasswordHash())).isTrue();
        assertThat(pendingRegistrations.findById(email)).isEmpty();
        deleteUser(user.getId());
    }

    @Test
    void wrongPasswordsShareTheFiveAttemptLimit() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String email = "password-attempts-" + suffix + "@example.test";

        auth.beginRegistration("Password Seeker", email, "correct horse 42", "127.0.0.1");

        SimpleMailMessage message = lastSentEmail();
        String code = codeFrom(message);

        for (int attempt = 1; attempt <= 4; attempt++) {
            String wrongPassword = "wrong password " + attempt;
            ResponseStatusException invalidPassword = assertThrows(ResponseStatusException.class,
                    () -> auth.verifyRegistration(email, code, wrongPassword));
            assertThat(invalidPassword.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        ResponseStatusException exhausted = assertThrows(ResponseStatusException.class,
                () -> auth.verifyRegistration(email, code, "wrong password 5"));

        assertThat(exhausted.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(users.findByEmail(email)).isEmpty();
        assertThat(pendingRegistrations.findById(email)).isEmpty();
    }

    @Test
    void existingOAuthEmailCannotStartPasswordRegistration() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String email = "oauth-owner-" + suffix + "@example.test";
        userRepository.createAccount("OAuth Owner", email, "google", "oauth-" + suffix.substring(0, 12));

        auth.beginRegistration("Impersonator", email, "correct horse 42", "127.0.0.1");

        var existing = users.findByEmail(email).orElseThrow();
        assertThat(existing.getAuthProvider()).isEqualTo("google");
        assertThat(existing.getPasswordHash()).isNull();
        assertThat(pendingRegistrations.findById(email)).isEmpty();
        verify(mailSender, never()).send(org.mockito.ArgumentMatchers.any(SimpleMailMessage.class));
        deleteUser(existing.getId());
    }

    private void deleteUser(long userId) {
        transactionTemplate.executeWithoutResult(status -> userRepository.deleteAccount(userId));
    }

    private SimpleMailMessage lastSentEmail() {
        var captor = org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        return captor.getValue();
    }

    private static String codeFrom(SimpleMailMessage email) {
        Matcher matcher = CODE_PATTERN.matcher(email.getText());
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }
}