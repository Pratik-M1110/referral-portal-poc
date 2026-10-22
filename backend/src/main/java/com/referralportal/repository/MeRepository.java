package com.referralportal.repository;

import com.referralportal.repository.jpa.ProfileJpaRepository;
import com.referralportal.repository.jpa.ReferrerProfileJpaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository
public class MeRepository {

    @Autowired
    private ProfileJpaRepository profiles;
    @Autowired
    private ReferrerProfileJpaRepository referrerProfiles;

    public boolean hasProfile(long userId) {
        return profiles.existsById(userId);
    }

    public boolean isVerifiedReferrer(long userId) {
        return referrerProfiles.findById(userId)
                .map(rp -> rp.getVerifiedAt() != null && rp.isReferrer())
                .orElse(false);
    }
}
