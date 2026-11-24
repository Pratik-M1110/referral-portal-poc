package com.referralportal.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.referral-request-scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class ReferralRequestScheduler {

    private final ReferralRequestMaintenance maintenance;

    public ReferralRequestScheduler(ReferralRequestMaintenance maintenance) {
        this.maintenance = maintenance;
    }

    @Scheduled(fixedDelayString = "${app.referral-request-scheduler-delay:3600000}")
    public void processDueRequests() {
        maintenance.processDueRequests();
    }
}
