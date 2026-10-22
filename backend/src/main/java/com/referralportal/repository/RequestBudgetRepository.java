package com.referralportal.repository;

import com.referralportal.repository.jpa.ConversationJpaRepository;
import com.referralportal.repository.jpa.CreditLedgerJpaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository
public class RequestBudgetRepository {

    @Autowired
    private ConversationJpaRepository conversations;
    @Autowired
    private CreditLedgerJpaRepository creditLedger;

    public long countRequestsStartedBy(long seekerId) {
        return conversations.countBySeekerIdAndInitiatedBy(seekerId, "seeker");
    }

    public long totalCredits(long userId) {
        return creditLedger.sumDeltaByUserId(userId);
    }
}
