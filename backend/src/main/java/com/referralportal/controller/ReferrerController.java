package com.referralportal.controller;

import com.referralportal.service.ReferrerService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;

import static com.referralportal.dto.ReferrerDtos.ConfirmVerificationRequest;
import static com.referralportal.dto.ReferrerDtos.Settings;
import static com.referralportal.dto.ReferrerDtos.SettingsRequest;
import static com.referralportal.dto.ReferrerDtos.StartVerificationRequest;
import static com.referralportal.dto.ReferrerDtos.VerificationStarted;

@RestController
@RequestMapping("/api")
public class ReferrerController {

    @Autowired
    private ReferrerService referrers;

    @PostMapping("/referrer/verify/start")
    public VerificationStarted start(Authentication authentication, @Valid @RequestBody StartVerificationRequest request) {
        return referrers.startVerification(authentication, request.workEmail());
    }

    @PostMapping("/referrer/verify/confirm")
    public Settings confirm(Authentication authentication, @Valid @RequestBody ConfirmVerificationRequest request) {
        return referrers.confirmVerification(authentication, request.workEmail(), request.code());
    }

    @GetMapping("/referrer/settings")
    public Settings settings(Authentication authentication) {
        return referrers.getSettings(authentication);
    }

    @PutMapping("/referrer/settings")
    public Settings update(Authentication authentication, @Valid @RequestBody SettingsRequest request) {
        return referrers.updateSettings(authentication, request);
    }
}