package com.referralportal.service;

import com.referralportal.dto.ReferralDtos;
import com.referralportal.repository.ReferralRepository;
import com.referralportal.user.CurrentUser;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import static com.referralportal.dto.ReferralDtos.ReferralView;

@Service
public class ReferralService {

    @Autowired
    private UserService users;
    @Autowired
    private ReferralRepository referrals;

    public List<ReferralView> received(Authentication auth) {
        CurrentUser user = users.current(auth);
        return referrals.findReceivedByCandidateId(user.id());
    }
}
