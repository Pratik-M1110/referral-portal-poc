package com.referralportal.service;

import com.referralportal.entity.CompanyReferralRequest;
import com.referralportal.entity.ReferralRequestResponse;
import com.referralportal.repository.jpa.CompanyReferralRequestJpaRepository;
import com.referralportal.repository.jpa.ReferralRequestResponseJpaRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ReferralRequestScheduler {

    private static final int REMINDER_INTERVAL_DAYS = 2;
    private static final int MAX_REMINDERS = 3;
    private static final int ASSIGNMENT_TIMEOUT_DAYS = 10;

    @Autowired
    private CompanyReferralRequestJpaRepository requests;
    @Autowired
    private ReferralRequestResponseJpaRepository responses;
    @Autowired
    private ReferralRequestBudget budget;
    @Autowired
    private ReferralNotificationService notifications;

    @Scheduled(fixedDelayString = "${app.referral-request-scheduler-delay:3600000}")
    @Transactional
    public void processDueRequests() {
        Instant now = Instant.now();
        expireRequests(now);
        closeStaleAssignments(now);
        sendReminders(now);
    }

    private void expireRequests(Instant now) {
        for (CompanyReferralRequest request : requests.findExpiredOpenRequests(now)) {
            List<ReferralRequestResponse> requestResponses = responses.findByRequestIdOrderByCreatedAt(request.getId());
            boolean hasAccepted = requestResponses.stream().anyMatch(response -> response.getAcceptedAt() != null);
            request.setStatus(hasAccepted ? "closed" : "expired");
            request.setClosedAt(now);
            requests.save(request);
            for (ReferralRequestResponse response : requestResponses) {
                if ("pending".equals(response.getStatus())) {
                    response.setStatus("expired");
                    response.setClosedAt(now);
                    responses.save(response);
                }
            }
            if (!hasAccepted) {
                budget.refundCreditIfUsed(request);
                notifications.expired(request);
            }
        }
    }

    private void sendReminders(Instant now) {
        Instant dueBefore = now.minus(REMINDER_INTERVAL_DAYS, ChronoUnit.DAYS);
        for (ReferralRequestResponse response : responses.findDueForReminder(dueBefore)) {
            if (!"accepted".equals(response.getStatus()) || response.getSubmittedAt() != null
                    || response.getRemindersSent() >= MAX_REMINDERS) {
                continue;
            }
            response.setRemindersSent((short) (response.getRemindersSent() + 1));
            response.setLastRemindedAt(now);
            responses.save(response);
            notifications.reminder(response);
        }
    }

    private void closeStaleAssignments(Instant now) {
        Instant staleBefore = now.minus(ASSIGNMENT_TIMEOUT_DAYS, ChronoUnit.DAYS);
        List<ReferralRequestResponse> staleAssignments = responses.findStaleAccepted(staleBefore);
        if (staleAssignments.isEmpty()) {
            return;
        }
        responses.closeStaleAccepted(staleBefore, now);
        for (ReferralRequestResponse response : staleAssignments) {
            response.setStatus("closed");
            response.setClosedAt(now);
            notifications.closed(response);
        }
    }
}