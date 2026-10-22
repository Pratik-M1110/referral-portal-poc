package com.referralportal.service;

import com.referralportal.repository.MeRepository;
import com.referralportal.user.CurrentUser;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class MeService {

    @Autowired
    private UserService users;
    @Autowired
    private MeRepository meRepository;

    public Map<String, Object> get(Authentication auth) {
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
