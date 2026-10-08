package com.referralportal.auth;

import com.referralportal.user.CurrentUser;
import com.referralportal.user.UserService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class MeController {

    private final UserService users;
    private final JdbcClient jdbc;

    MeController(UserService users, JdbcClient jdbc) {
        this.users = users;
        this.jdbc = jdbc;
    }

    @GetMapping("/api/me")
    Map<String, Object> me(Authentication auth) {
        CurrentUser user = users.current(auth);
        boolean hasProfile = jdbc.sql("SELECT COUNT(*) FROM profiles WHERE user_id = ?").param(user.id())
                .query(Long.class).single() > 0;
        boolean isReferrer = jdbc.sql(
                "SELECT COUNT(*) FROM referrer_profiles WHERE user_id = ? AND verified_at IS NOT NULL AND is_referrer = TRUE")
                .param(user.id()).query(Long.class).single() > 0;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", user.id());
        body.put("name", user.name());
        body.put("email", user.email());
        body.put("provider", user.provider());
        body.put("hasProfile", hasProfile);
        body.put("isReferrer", isReferrer);
        return body;
    }
}
