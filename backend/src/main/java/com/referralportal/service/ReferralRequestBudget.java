package com.referralportal.service;

import com.referralportal.entity.CompanyReferralRequest;
import com.referralportal.entity.ReferralRequestCredit;
import com.referralportal.repository.jpa.CompanyReferralRequestJpaRepository;
import com.referralportal.repository.jpa.ReferralRequestCreditJpaRepository;
import com.referralportal.repository.jpa.ReferrerProfileJpaRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReferralRequestBudget {

    public static final int UNVERIFIED_BASE_MAX = 2;
    public static final int VERIFIED_BASE_BONUS = 3;
    public static final int EARNED_CREDIT_CAP = 2;

    @Autowired
    private CompanyReferralRequestJpaRepository requests;
    @Autowired
    private ReferralRequestCreditJpaRepository credits;
    @Autowired
    private ReferrerProfileJpaRepository referrerProfiles;

    public Allowance allowance(long userId, Instant now) {
        long baseUsed = requests.countBudgetedBySeekerSince(userId, now.minus(30, ChronoUnit.DAYS));
        int baseMax = baseMax(userId);
        int baseLeft = (int) Math.max(0, baseMax - baseUsed);
        int bonusAvailable = (int) credits.countAvailable(userId, now);
        return new Allowance(baseLeft + bonusAvailable, baseMax, baseLeft, bonusAvailable);
    }

    @Transactional
    public void charge(long userId, CompanyReferralRequest request, Instant now) {
        long baseUsedIncludingNewRequest = requests.countBudgetedBySeekerSince(userId, now.minus(30, ChronoUnit.DAYS));
        if (baseUsedIncludingNewRequest <= baseMax(userId)) {
            return;
        }
        request.setBaseCharge(false);
        List<ReferralRequestCredit> available = credits.findAvailableForUpdate(userId, now);
        if (available.isEmpty()) {
            throw new IllegalStateException("No referral request allowance is available.");
        }
        ReferralRequestCredit credit = available.get(0);
        credit.setConsumedByRequest(request);
        credit.setConsumedAt(now);
        credits.save(credit);
    }

    @Transactional
    public void refundCreditIfUsed(CompanyReferralRequest request) {
        credits.findConsumedByRequestId(request.getId())
                .ifPresent(credit -> {
                    credit.setConsumedByRequest(null);
                    credit.setConsumedAt(null);
                    credits.save(credit);
                });
    }

    public boolean mayEarnAnother(long userId, Instant now) {
        return credits.countEarnedSince(userId, now.minus(30, ChronoUnit.DAYS)) < EARNED_CREDIT_CAP;
    }

    private int baseMax(long userId) {
        boolean verified = referrerProfiles.findById(userId)
                .map(profile -> profile.getVerifiedAt() != null)
                .orElse(false);
        return UNVERIFIED_BASE_MAX + (verified ? VERIFIED_BASE_BONUS : 0);
    }

    public record Allowance(int left, int baseMax, int baseLeft, int bonusAvailable) {}
}