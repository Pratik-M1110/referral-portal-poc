package com.referralportal.controller;

import com.referralportal.service.ReferrerService;
import com.referralportal.service.ReferrerService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static com.referralportal.referrer.ReferrerDtos.ConfirmVerificationRequest;
import static com.referralportal.referrer.ReferrerDtos.Settings;
import static com.referralportal.referrer.ReferrerDtos.SettingsRequest;
import static com.referralportal.referrer.ReferrerDtos.StartVerificationRequest;
import static com.referralportal.referrer.ReferrerDtos.VerificationStarted;

@RestController
public class ReferrerController {

    private final ReferrerService referrers;

    public ReferrerController(ReferrerService referrers) {
        this.referrers = referrers;
    }

    @PostMapping("/api/referrer/verify/start")
    public VerificationStarted start(Authentication authentication, @Valid @RequestBody StartVerificationRequest request) {
        return referrers.startVerification(authentication, request.workEmail());
    }

    @PostMapping("/api/referrer/verify/confirm")
    public Settings confirm(Authentication authentication, @Valid @RequestBody ConfirmVerificationRequest request) {
        return referrers.confirmVerification(authentication, request.workEmail(), request.code());
    }

    @GetMapping("/api/referrer/settings")
    public Settings settings(Authentication authentication) {
        return referrers.getSettings(authentication);
    }

    @PutMapping("/api/referrer/settings")
    public Settings update(Authentication authentication, @Valid @RequestBody SettingsRequest request) {
        return referrers.updateSettings(authentication, request);
    }
}