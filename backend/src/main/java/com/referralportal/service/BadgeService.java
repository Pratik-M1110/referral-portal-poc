package com.referralportal.service;

import com.referralportal.repository.BadgeRepository;
import com.referralportal.user.CurrentUser;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class BadgeService {

    @Autowired
    private UserService users;
    @Autowired
    private BadgeRepository badges;

    public Map<String, Object> get(Authentication auth) {
        CurrentUser user = users.current(auth);
        return Map.of("name", user.name(), "token", badges.findTokenByUserId(user.id()));
    }
}
