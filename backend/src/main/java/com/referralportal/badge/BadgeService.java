package com.referralportal.badge;

import com.referralportal.user.CurrentUser;
import com.referralportal.user.UserService;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
class BadgeService {

    private final UserService users;
    private final BadgeRepository badges;

    BadgeService(UserService users, BadgeRepository badges) {
        this.users = users;
        this.badges = badges;
    }

    Map<String, Object> get(Authentication auth) {
        CurrentUser user = users.current(auth);
        return Map.of("name", user.name(), "token", badges.findTokenByUserId(user.id()));
    }
}
