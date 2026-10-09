package com.referralportal.service;

import com.referralportal.referral.ReferralDtos;
import com.referralportal.repository.ReferralRepository;
import com.referralportal.repository.ReferralRepository;
import com.referralportal.user.CurrentUser;
import com.referralportal.service.UserService;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import static com.referralportal.referral.ReferralDtos.ReferralView;

@Service
public class ReferralService {

    private final UserService users;
    private final ReferralRepository referrals;

    public ReferralService(UserService users, ReferralRepository referrals) {
        this.users = users;
        this.referrals = referrals;
    }

    public List<ReferralView> received(Authentication auth) {
        CurrentUser user = users.current(auth);
        return referrals.findReceivedByCandidateId(user.id());
    }
}
