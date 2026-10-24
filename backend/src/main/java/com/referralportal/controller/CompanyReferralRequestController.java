package com.referralportal.controller;

import com.referralportal.dto.ReferralRequestDtos;
import com.referralportal.service.CompanyReferralRequestService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.referralportal.dto.ReferralRequestDtos.ReferrerInboxItem;
import static com.referralportal.dto.ReferralRequestDtos.SeekerRequest;

@RestController
@RequestMapping("/api")
public class CompanyReferralRequestController {

    @Autowired
    private CompanyReferralRequestService referralRequests;

    @PostMapping("/referral-requests")
    public SeekerRequest create(Authentication auth, @Valid @RequestBody ReferralRequestDtos.CreateRequest input) {
        return referralRequests.create(auth, input);
    }

    @GetMapping("/referral-requests")
    public List<SeekerRequest> seekerRequests(Authentication auth) {
        return referralRequests.seekerRequests(auth);
    }

    @GetMapping("/referral-requests/inbox")
    public List<ReferrerInboxItem> inbox(Authentication auth) {
        return referralRequests.inbox(auth);
    }

    @PostMapping("/referral-requests/{requestId}/accept")
    public Map<String, Object> accept(Authentication auth, @PathVariable long requestId,
            @Valid @RequestBody ReferralRequestDtos.AcceptRequest input) {
        return referralRequests.accept(auth, requestId, input);
    }

    @PostMapping("/referral-requests/{requestId}/decline")
    public Map<String, Object> decline(Authentication auth, @PathVariable long requestId) {
        return referralRequests.decline(auth, requestId);
    }

    @PostMapping("/referral-requests/{requestId}/cancel")
    public Map<String, Object> cancel(Authentication auth, @PathVariable long requestId) {
        return referralRequests.cancel(auth, requestId);
    }

    @PostMapping("/referral-requests/{requestId}/submitted")
    public Map<String, Object> submitted(Authentication auth, @PathVariable long requestId) {
        return referralRequests.markSubmitted(auth, requestId);
    }

    @PostMapping("/referral-requests/{requestId}/responses/{responseId}/confirm")
    public Map<String, Object> confirm(Authentication auth, @PathVariable long requestId, @PathVariable long responseId) {
        return referralRequests.confirmSubmission(auth, requestId, responseId);
    }
}