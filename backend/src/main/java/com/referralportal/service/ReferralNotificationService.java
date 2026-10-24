package com.referralportal.service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.referralportal.entity.CompanyReferralRequest;
import com.referralportal.entity.Notification;
import com.referralportal.entity.ReferralRequestResponse;
import com.referralportal.entity.User;
import com.referralportal.repository.jpa.NotificationJpaRepository;
import java.time.Instant;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReferralNotificationService {

    private static final System.Logger LOGGER = System.getLogger(ReferralNotificationService.class.getName());

    @Autowired
    private NotificationJpaRepository notifications;
    @Autowired
    private JavaMailSender mailSender;
    @Autowired
    private ObjectMapper objectMapper;

    @Value("${app.mail-from:}")
    private String mailFrom;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    @Transactional
    public void invite(CompanyReferralRequest request, User referrer) {
        save(referrer, "referral_request", Map.of("requestId", request.getId(), "company", request.getCompany().getName(),
                "roleTitle", request.getRoleTitle()));
        if (!"demo".equals(referrer.getAuthProvider())) {
            send(referrer.getReferrerProfile().getWorkEmail(), "Referral request at " + request.getCompany().getName(),
                request.getSeeker().getName() + " asked for help with " + request.getRoleTitle() + ".\n\n"
                    + "Review the request in Referral Portal: " + pageUrl());
        }
    }

    @Transactional
    public void accepted(CompanyReferralRequest request, ReferralRequestResponse response) {
        save(request.getSeeker(), "referral_accepted", Map.of("requestId", request.getId(),
                "company", request.getCompany().getName(), "roleTitle", request.getRoleTitle()));
    }

    @Transactional
    public void submitted(CompanyReferralRequest request, ReferralRequestResponse response) {
        save(request.getSeeker(), "referral_submitted", Map.of("requestId", request.getId(),
                "responseId", response.getId(), "company", request.getCompany().getName()));
    }

    @Transactional
    public void confirmed(CompanyReferralRequest request, ReferralRequestResponse response) {
        save(response.getReferrer(), "referral_confirmed", Map.of("requestId", request.getId(),
                "responseId", response.getId(), "company", request.getCompany().getName()));
    }

    @Transactional
    public void reminder(ReferralRequestResponse response) {
        CompanyReferralRequest request = response.getRequest();
        User referrer = response.getReferrer();
        save(referrer, "referral_reminder", Map.of("requestId", request.getId(), "responseId", response.getId(),
                "company", request.getCompany().getName(), "roleTitle", request.getRoleTitle()));
        if (!"demo".equals(referrer.getAuthProvider())) {
            send(referrer.getReferrerProfile().getWorkEmail(), "Reminder: referral at " + request.getCompany().getName(),
                "Your accepted referral request for " + request.getRoleTitle() + " is still awaiting an update.\n\n"
                    + "Open Referral Portal: " + pageUrl());
        }
    }

    @Transactional
    public void closed(ReferralRequestResponse response) {
        CompanyReferralRequest request = response.getRequest();
        save(response.getReferrer(), "referral_closed", Map.of("requestId", request.getId(),
                "responseId", response.getId(), "company", request.getCompany().getName()));
        save(request.getSeeker(), "referral_closed", Map.of("requestId", request.getId(),
                "responseId", response.getId(), "company", request.getCompany().getName()));
    }

    @Transactional
    public void expired(CompanyReferralRequest request) {
        save(request.getSeeker(), "referral_request_expired", Map.of("requestId", request.getId(),
                "company", request.getCompany().getName(), "roleTitle", request.getRoleTitle()));
    }

    private void save(User user, String type, Map<String, Object> payload) {
        Notification notification = new Notification();
        notification.setUser(user);
        notification.setType(type);
        try {
            notification.setPayload(objectMapper.writeValueAsString(payload));
        } catch (JacksonException exception) {
            throw new IllegalStateException("Could not create referral notification.", exception);
        }
        notification.setCreatedAt(Instant.now());
        notifications.save(notification);
    }

    private void send(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        if (!mailFrom.isBlank()) {
            message.setFrom(mailFrom);
        }
        message.setSubject(subject);
        message.setText(body);
        try {
            mailSender.send(message);
        } catch (MailException exception) {
            LOGGER.log(System.Logger.Level.WARNING, "Could not send a referral notification email.", exception);
        }
    }

    private String pageUrl() {
        String base = frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
        return base + "/referrals";
    }
}