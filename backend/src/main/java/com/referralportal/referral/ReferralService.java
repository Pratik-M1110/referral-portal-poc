package com.referralportal.referral;

import com.referralportal.user.CurrentUser;
import com.referralportal.user.UserService;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import static com.referralportal.referral.ReferralDtos.ReferralView;

@Service
class ReferralService {

    private final UserService users;
    private final ReferralRepository referrals;

    ReferralService(UserService users, ReferralRepository referrals) {
        this.users = users;
        this.referrals = referrals;
    }

    List<ReferralView> received(Authentication auth) {
        CurrentUser user = users.current(auth);
        return referrals.findReceivedByCandidateId(user.id());
    }
}
