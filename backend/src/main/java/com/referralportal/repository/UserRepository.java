package com.referralportal.repository;

import com.referralportal.entity.Badge;
import com.referralportal.entity.User;
import com.referralportal.repository.jpa.BadgeJpaRepository;
import com.referralportal.repository.jpa.ConversationJpaRepository;
import com.referralportal.repository.jpa.ReferralJpaRepository;
import com.referralportal.repository.jpa.ReviewJpaRepository;
import com.referralportal.repository.jpa.UserJpaRepository;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {

    @Autowired
    private UserJpaRepository users;
    @Autowired
    private BadgeJpaRepository badges;
    @Autowired
    private ConversationJpaRepository conversations;
    @Autowired
    private ReviewJpaRepository reviews;
    @Autowired
    private ReferralJpaRepository referrals;

    public Long findIdByEmail(String email) {
        return users.findByEmail(email).map(User::getId).orElse(null);
    }

    public long createAccount(String name, String email, String provider, String qrToken) {
        User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setAuthProvider(provider);
        user.setConsentAt(Instant.now());
        user.setCreatedAt(Instant.now());
        user = users.save(user);

        Badge badge = new Badge();
        badge.setUser(user);
        badge.setQrToken(qrToken);
        badges.save(badge);
        return user.getId();
    }

    public void deleteAccount(long userId) {
        conversations.deleteBySeekerIdOrReferrerId(userId);
        reviews.deleteByReferrerIdOrCandidateId(userId);
        referrals.deleteByReferrerIdOrCandidateId(userId);
        users.deleteUserById(userId);
    }
}
