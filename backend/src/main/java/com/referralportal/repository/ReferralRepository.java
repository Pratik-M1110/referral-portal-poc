package com.referralportal.repository;

import com.referralportal.entity.Referral;
import com.referralportal.repository.jpa.ReferralJpaRepository;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import static com.referralportal.dto.ReferralDtos.ReferralView;

@Repository
public class ReferralRepository {

    @Autowired
    private ReferralJpaRepository referrals;

    public List<ReferralView> findReceivedByCandidateId(long candidateId) {
        return referrals.findReceivedByCandidateId(candidateId).stream().map(ReferralRepository::view).toList();
    }

    private static ReferralView view(Referral r) {
        return new ReferralView(r.getId(), r.getCompany().getName(), r.getReferrer().getName(),
                r.getRole() == null ? null : r.getRole().getTitle(), r.getStatus(), r.getUpdatedAt().toString());
    }
}
