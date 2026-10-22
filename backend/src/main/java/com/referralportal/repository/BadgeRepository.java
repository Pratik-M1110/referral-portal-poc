package com.referralportal.repository;

import com.referralportal.entity.Badge;
import com.referralportal.repository.jpa.BadgeJpaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository
public class BadgeRepository {

    @Autowired
    private BadgeJpaRepository badges;

    public String findTokenByUserId(long userId) {
        return badges.findById(userId).map(Badge::getQrToken).orElse(null);
    }
}
