package com.referralportal.user;

import com.referralportal.demo.DemoDataSeeder;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/** Maps the OAuth login to one row in users (one account per person, matched by email). */
@Service
public class UserService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final TransactionTemplate tx;
    private final DemoDataSeeder demo;
    private final boolean seedDemo;

    public UserService(UserRepository users, TransactionTemplate tx, DemoDataSeeder demo,
            @Value("${app.seed-demo:false}") boolean seedDemo) {
        this.users = users;
        this.tx = tx;
        this.demo = demo;
        this.seedDemo = seedDemo;
    }

    public CurrentUser current(Authentication auth) {
        if (!(auth instanceof OAuth2AuthenticationToken token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        OAuth2User principal = token.getPrincipal();
        String provider = token.getAuthorizedClientRegistrationId();
        String email = emailOf(principal, provider);
        String name = nameOf(principal);
        Long id = users.findIdByEmail(email);
        if (id == null) {
            id = create(name, email, provider);
        }
        return new CurrentUser(id, name, email, provider);
    }

    private long create(String name, String email, String provider) {
        try {
            return Objects.requireNonNull(tx.execute(status -> {
                long id = users.createAccount(name, email, provider, newToken());
                if (seedDemo) {
                    demo.seedWelcome(id);
                }
                return id;
            }));
        } catch (DuplicateKeyException e) {
            return Objects.requireNonNull(users.findIdByEmail(email));
        }
    }

    private static String newToken() {
        byte[] bytes = new byte[16]; // 128 bits, rendered as 32 hex characters
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private static String emailOf(OAuth2User principal, String provider) {
        Object email = principal.getAttribute("email");
        if (email instanceof String s && !s.isBlank()) {
            return s.trim().toLowerCase();
        }
        Object login = principal.getAttribute("login");
        Object id = principal.getAttribute("id");
        if ("github".equals(provider) && login != null && id != null) {
            return id + "+" + login + "@users.noreply.github.com"; // GitHub hides private emails
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Your sign-in provider did not share an email address.");
    }

    private static String nameOf(OAuth2User principal) {
        Object name = principal.getAttribute("name");
        if (!(name instanceof String s) || s.isBlank()) {
            name = principal.getAttribute("login");
        }
        String result = name == null ? "Attendee" : name.toString().trim();
        return result.length() > 120 ? result.substring(0, 120) : result;
    }
}
