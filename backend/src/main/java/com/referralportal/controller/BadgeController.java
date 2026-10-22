package com.referralportal.controller;

import com.referralportal.service.BadgeService;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BadgeController {

    @Autowired
    private BadgeService badges;

    /** The QR encodes only this random token, never the user id. */
    @GetMapping("/api/me/badge")
    public Map<String, Object> badge(Authentication auth) {
        return badges.get(auth);
    }
}
