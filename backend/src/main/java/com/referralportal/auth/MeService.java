package com.referralportal.auth;

import com.referralportal.user.CurrentUser;
import com.referralportal.user.UserService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
class MeService {

    private final UserService users;
    private final MeRepository meRepository;

    MeService(UserService users, MeRepository meRepository) {
        this.users = users;
        this.meRepository = meRepository;
    }

    Map<String, Object> get(Authentication auth) {
        CurrentUser user = users.current(auth);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", user.id());
        body.put("name", user.name());
        body.put("email", user.email());
        body.put("provider", user.provider());
        body.put("hasProfile", meRepository.hasProfile(user.id()));
        body.put("isReferrer", meRepository.isVerifiedReferrer(user.id()));
        return body;
    }
}
