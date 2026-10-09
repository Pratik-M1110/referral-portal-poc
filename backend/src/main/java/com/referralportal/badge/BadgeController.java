package com.referralportal.badge;

import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class BadgeController {

    private final BadgeService badges;

    BadgeController(BadgeService badges) {
        this.badges = badges;
    }

    /** The QR encodes only this random token, never the user id. */
    @GetMapping("/api/me/badge")
    Map<String, Object> badge(Authentication auth) {
        return badges.get(auth);
    }
}
