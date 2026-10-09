package com.referralportal.referral;

import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static com.referralportal.referral.ReferralDtos.ReferralView;

@RestController
class ReferralController {

    private final ReferralService referrals;

    ReferralController(ReferralService referrals) {
        this.referrals = referrals;
    }

    /** Referrals received by the signed-in person. */
    @GetMapping("/api/referrals")
    List<ReferralView> list(Authentication auth, @RequestParam(name = "as", defaultValue = "candidate") String as) {
        return referrals.received(auth);
    }
}
