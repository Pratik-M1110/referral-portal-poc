package com.referralportal.service;

import com.referralportal.repository.BadgeRepository;
import com.referralportal.repository.BadgeRepository;
import com.referralportal.user.CurrentUser;
import com.referralportal.service.UserService;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class BadgeService {

    private final UserService users;
    private final BadgeRepository badges;

    public BadgeService(UserService users, BadgeRepository badges) {
        this.users = users;
        this.badges = badges;
    }

    public Map<String, Object> get(Authentication auth) {
        CurrentUser user = users.current(auth);
        return Map.of("name", user.name(), "token", badges.findTokenByUserId(user.id()));
    }
}
