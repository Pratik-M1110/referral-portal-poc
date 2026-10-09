package com.referralportal.controller;

import com.referralportal.service.ReferralService;
import com.referralportal.service.ReferralService;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static com.referralportal.referral.ReferralDtos.ReferralView;

@RestController
public class ReferralController {

    private final ReferralService referrals;

    public ReferralController(ReferralService referrals) {
        this.referrals = referrals;
    }

    /** Referrals received by the signed-in person. */
    @GetMapping("/api/referrals")
    public List<ReferralView> list(Authentication auth, @RequestParam(name = "as", defaultValue = "candidate") String as) {
        return referrals.received(auth);
    }
}
