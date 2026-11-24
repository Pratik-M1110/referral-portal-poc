package com.referralportal.service;

import com.referralportal.entity.CompanyReferralRequest;
import com.referralportal.entity.ReferralRequestResponse;
import com.referralportal.repository.jpa.CompanyReferralRequestJpaRepository;
import com.referralportal.repository.jpa.ReferralRequestResponseJpaRepository;
import com.referralportal.repository.jpa.ReferralRequestResumeJpaRepository;
import com.referralportal.repository.jpa.PendingPasswordRegistrationJpaRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.function.Consumer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ReferralRequestMaintenance {

    private static final int REMINDER_INTERVAL_DAYS = 1;
    private static final int MAX_REMINDERS = 3;
    private static final int ASSIGNMENT_TIMEOUT_DAYS = 4;
    private static final int CONTINUATION_DECISION_DAYS = 4;

    private final CompanyReferralRequestJpaRepository requests;
    private final ReferralRequestResponseJpaRepository responses;
    private final ReferralRequestBudget budget;
    private final ReferralNotificationService notifications;
    private final ReferralRequestResumeJpaRepository requestResumes;
    private final PendingPasswordRegistrationJpaRepository pendingPasswordRegistrations;
    private final TransactionTemplate transactions;
    private final boolean retryReminderFailures;
    private final int requestResumeSnapshotRetentionDays;

    public ReferralRequestMaintenance(CompanyReferralRequestJpaRepository requests,
            ReferralRequestResponseJpaRepository responses, ReferralRequestBudget budget,
            ReferralNotificationService notifications, ReferralRequestResumeJpaRepository requestResumes,
            PendingPasswordRegistrationJpaRepository pendingPasswordRegistrations,
            TransactionTemplate transactions,
            @Value("${app.referral-reminder-retry-enabled:false}") boolean retryReminderFailures,
            @Value("${app.resume-request-snapshot-retention-days:30}") int requestResumeSnapshotRetentionDays) {
        this.requests = requests;
        this.responses = responses;
        this.budget = budget;
        this.notifications = notifications;
        this.requestResumes = requestResumes;
        this.pendingPasswordRegistrations = pendingPasswordRegistrations;
        this.transactions = transactions;
        this.retryReminderFailures = retryReminderFailures;
        this.requestResumeSnapshotRetentionDays = requestResumeSnapshotRetentionDays;
    }

    public void processDueRequests() {
        Instant now = Instant.now();
        for (Long requestId : requests.findExpiredOpenRequestIds(now)) {
            transactions.executeWithoutResult(status -> expireRequest(requestId, now));
        }
        Instant staleBefore = now.minus(ASSIGNMENT_TIMEOUT_DAYS, ChronoUnit.DAYS);
        for (Long responseId : responses.findStaleAcceptedIds(staleBefore)) {
            withLockedResponse(responseId, response -> closeStaleAssignment(response, staleBefore, now));
        }
        Instant continuationDecisionBefore = now.minus(CONTINUATION_DECISION_DAYS, ChronoUnit.DAYS);
        for (Long requestId : requests.findContinuationRequestsDueForExpiry(continuationDecisionBefore)) {
            expireContinuation(requestId, continuationDecisionBefore, now);
        }
        requestResumes.deleteSnapshotsForClosedRequestsBefore(
            now.minus(requestResumeSnapshotRetentionDays, ChronoUnit.DAYS));
        pendingPasswordRegistrations.deleteExpiredBefore(now);
        Instant dueBefore = now.minus(REMINDER_INTERVAL_DAYS, ChronoUnit.DAYS);
        int failures = 0;
        for (Long responseId : responses.findDueForReminderIds(dueBefore)) {
            try {
                withLockedResponse(responseId, response -> sendReminder(response, dueBefore, now));
            } catch (MailException failure) {
                failures++;
            }
        }
        if (failures > 0) {
            throw new IllegalStateException(failures + " reminder email(s) failed and will be retried.");
        }
    }

    private void expireRequest(long requestId, Instant now) {
        CompanyReferralRequest request = requests.findByIdForUpdate(requestId).orElse(null);
        if (request == null || !"open".equals(request.getStatus()) || request.getExpiresAt().isAfter(now)) {
            return;
        }
        List<ReferralRequestResponse> requestResponses = responses.findByRequestIdOrderByCreatedAt(request.getId());
        boolean hasAccepted = requestResponses.stream().anyMatch(response ->
            java.util.Set.of("accepted", "submitted", "confirmed").contains(response.getStatus()));
        boolean hasActiveAssignment = requestResponses.stream().anyMatch(response ->
            "accepted".equals(response.getStatus()) && response.getSubmittedAt() == null);
        request.setStatus(hasAccepted ? "closed" : "expired");
        request.setClosedAt(now);
        if (!hasActiveAssignment) {
            for (ReferralRequestResponse response : requestResponses) {
                if ("pending".equals(response.getStatus()) || "accepted_elsewhere".equals(response.getStatus())) {
                    response.setStatus("expired");
                    response.setClosedAt(now);
                }
            }
        }
        if (!hasAccepted) {
            budget.refundCreditIfUsed(request);
            notifications.expired(request);
        }
    }

    private void withLockedResponse(long responseId, Consumer<ReferralRequestResponse> action) {
        transactions.executeWithoutResult(status -> {
            Long requestId = responses.findRequestId(responseId).orElse(null);
            if (requestId == null || requests.findByIdForUpdate(requestId).isEmpty()) {
                return;
            }
            responses.findByIdForUpdate(responseId).ifPresent(action);
        });
    }

    private void closeStaleAssignment(ReferralRequestResponse response, Instant staleBefore, Instant now) {
        if (!"accepted".equals(response.getStatus()) || response.getSubmittedAt() != null
                || response.getAcceptedAt() == null || response.getAcceptedAt().isAfter(staleBefore)) {
            return;
        }
        response.setStatus("closed");
        response.setClosedAt(now);
        CompanyReferralRequest request = response.getRequest();
        request.setClosedAt(now);
        List<ReferralRequestResponse> requestResponses = responses.findByRequestIdOrderByCreatedAt(request.getId());
        boolean hasOtherReferrers = requestResponses.stream().anyMatch(other -> !other.getId().equals(response.getId())
            && ("accepted_elsewhere".equals(other.getStatus()) || "pending".equals(other.getStatus())));
        boolean anotherAssignmentActive = requestResponses.stream().anyMatch(other -> !other.getId().equals(response.getId())
            && java.util.Set.of("accepted", "submitted", "confirmed").contains(other.getStatus()));
        request.setStatus(hasOtherReferrers || anotherAssignmentActive ? "closed" : "expired");
        requests.save(request);
        notifications.closed(response);
        if (!hasOtherReferrers && !anotherAssignmentActive) {
            expireAndRefund(request, requestResponses, now);
        }
    }

    private void expireContinuation(long requestId, Instant decisionBefore, Instant now) {
        transactions.executeWithoutResult(status -> {
            CompanyReferralRequest request = requests.findByIdForUpdate(requestId).orElse(null);
            if (request == null || !"closed".equals(request.getStatus()) || request.getClosedAt() == null
                    || request.getClosedAt().isAfter(decisionBefore)) {
                return;
            }
            List<ReferralRequestResponse> requestResponses = responses.findByRequestIdOrderByCreatedAt(requestId);
            boolean timedOutAssignment = requestResponses.stream().anyMatch(response ->
                    "closed".equals(response.getStatus()) && response.getAcceptedAt() != null
                            && response.getSubmittedAt() == null);
            boolean hasOtherReferrers = requestResponses.stream().anyMatch(response ->
                    "accepted_elsewhere".equals(response.getStatus()) || "pending".equals(response.getStatus()));
                boolean anotherAssignmentActive = requestResponses.stream().anyMatch(response ->
                    java.util.Set.of("accepted", "submitted", "confirmed").contains(response.getStatus()));
                if (timedOutAssignment && hasOtherReferrers && !anotherAssignmentActive) {
                expireAndRefund(request, requestResponses, now);
            }
        });
    }

    private void expireAndRefund(CompanyReferralRequest request, List<ReferralRequestResponse> requestResponses,
            Instant now) {
        request.setStatus("expired");
        request.setClosedAt(now);
        requests.save(request);
        for (ReferralRequestResponse response : requestResponses) {
            if ("accepted_elsewhere".equals(response.getStatus()) || "pending".equals(response.getStatus())) {
                response.setStatus("expired");
                response.setClosedAt(now);
                responses.save(response);
            }
        }
        budget.refundCreditIfUsed(request);
        notifications.expired(request);
    }

    private void sendReminder(ReferralRequestResponse response, Instant dueBefore, Instant now) {
        Instant lastReminder = response.getRemindersSent() == 0 ? response.getAcceptedAt() : response.getLastRemindedAt();
        if (!"accepted".equals(response.getStatus()) || response.getSubmittedAt() != null
                || response.getRemindersSent() >= MAX_REMINDERS || lastReminder == null || lastReminder.isAfter(dueBefore)) {
            return;
        }
        response.setRemindersSent((short) (response.getRemindersSent() + 1));
        response.setLastRemindedAt(now);
        notifications.reminder(response, retryReminderFailures);
    }
}
