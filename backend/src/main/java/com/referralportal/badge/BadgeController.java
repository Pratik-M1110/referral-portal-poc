package com.referralportal.badge;

import com.referralportal.user.CurrentUser;
import com.referralportal.user.UserService;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class BadgeController {

    private final UserService users;
    private final JdbcClient jdbc;

    BadgeController(UserService users, JdbcClient jdbc) {
        this.users = users;
        this.jdbc = jdbc;
    }

    /** The QR encodes only this random token, never the user id. */
    @GetMapping("/api/me/badge")
    Map<String, Object> badge(Authentication auth) {
        CurrentUser user = users.current(auth);
        String token = jdbc.sql("SELECT qr_token FROM badges WHERE user_id = ?").param(user.id())
                .query(String.class).single();
        return Map.of("name", user.name(), "token", token);
    }
}
