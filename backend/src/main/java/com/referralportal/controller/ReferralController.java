package com.referralportal.controller;

import com.referralportal.service.ReferralService;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;

import static com.referralportal.dto.ReferralDtos.ReferralView;

@RestController
@RequestMapping("/api")
public class ReferralController {

    @Autowired
    private ReferralService referrals;

    /** Referrals received by the signed-in person. */
    @GetMapping("/referrals")
    public List<ReferralView> list(Authentication auth, @RequestParam(name = "as", defaultValue = "candidate") String as) {
        return referrals.received(auth);
    }
}
