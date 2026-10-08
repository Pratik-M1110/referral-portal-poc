package com.referralportal.auth;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class MeController {

    @GetMapping("/api/me")
    Map<String, Object> me(@AuthenticationPrincipal OAuth2User user) {
        Map<String, Object> attributes = user.getAttributes();
        Object name = attributes.get("name") != null ? attributes.get("name") : attributes.get("login");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("email", attributes.get("email"));
        return body;
    }
}
