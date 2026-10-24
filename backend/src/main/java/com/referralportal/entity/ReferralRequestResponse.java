package com.referralportal.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "referral_request_responses")
public class ReferralRequestResponse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "request_id", nullable = false)
    private CompanyReferralRequest request;

    @ManyToOne
    @JoinColumn(name = "referrer_id", nullable = false)
    private User referrer;

    @Column(nullable = false, length = 20)
    private String status = "pending";

    @Column(name = "share_referrer_email", nullable = false)
    private boolean shareReferrerEmail;

    @Column(name = "share_referrer_phone", nullable = false)
    private boolean shareReferrerPhone;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "reminders_sent", nullable = false)
    private short remindersSent;

    @Column(name = "last_reminded_at")
    private Instant lastRemindedAt;

    public Long getId() {
        return id;
    }

    public CompanyReferralRequest getRequest() {
        return request;
    }

    public void setRequest(CompanyReferralRequest request) {
        this.request = request;
    }

    public User getReferrer() {
        return referrer;
    }

    public void setReferrer(User referrer) {
        this.referrer = referrer;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isShareReferrerEmail() {
        return shareReferrerEmail;
    }

    public void setShareReferrerEmail(boolean shareReferrerEmail) {
        this.shareReferrerEmail = shareReferrerEmail;
    }

    public boolean isShareReferrerPhone() {
        return shareReferrerPhone;
    }

    public void setShareReferrerPhone(boolean shareReferrerPhone) {
        this.shareReferrerPhone = shareReferrerPhone;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getAcceptedAt() {
        return acceptedAt;
    }

    public void setAcceptedAt(Instant acceptedAt) {
        this.acceptedAt = acceptedAt;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(Instant submittedAt) {
        this.submittedAt = submittedAt;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public void setConfirmedAt(Instant confirmedAt) {
        this.confirmedAt = confirmedAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(Instant closedAt) {
        this.closedAt = closedAt;
    }

    public short getRemindersSent() {
        return remindersSent;
    }

    public void setRemindersSent(short remindersSent) {
        this.remindersSent = remindersSent;
    }

    public Instant getLastRemindedAt() {
        return lastRemindedAt;
    }

    public void setLastRemindedAt(Instant lastRemindedAt) {
        this.lastRemindedAt = lastRemindedAt;
    }
}