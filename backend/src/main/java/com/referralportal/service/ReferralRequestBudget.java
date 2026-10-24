package com.referralportal.service;

import com.referralportal.entity.CompanyReferralRequest;
import com.referralportal.entity.ReferralRequestCredit;
import com.referralportal.repository.jpa.CompanyReferralRequestJpaRepository;
import com.referralportal.repository.jpa.ReferralRequestCreditJpaRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReferralRequestBudget {

    public static final int BASE_MAX = 5;
    public static final int EARNED_CREDIT_CAP = 2;

    @Autowired
    private CompanyReferralRequestJpaRepository requests;
    @Autowired
    private ReferralRequestCreditJpaRepository credits;
    public Allowance allowance(long userId, Instant now) {
        long baseUsed = requests.countBudgetedBySeekerSince(userId, now.minus(30, ChronoUnit.DAYS));
        int baseLeft = (int) Math.max(0, BASE_MAX - baseUsed);
        int bonusAvailable = (int) credits.countAvailable(userId, now);
        return new Allowance(baseLeft + bonusAvailable, BASE_MAX, baseLeft, bonusAvailable);
    }

    @Transactional
    public void charge(long userId, CompanyReferralRequest request, Instant now) {
        long baseUsedIncludingNewRequest = requests.countBudgetedBySeekerSince(userId, now.minus(30, ChronoUnit.DAYS));
        if (baseUsedIncludingNewRequest <= BASE_MAX) {
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

    public record Allowance(int left, int baseMax, int baseLeft, int bonusAvailable) {}
}