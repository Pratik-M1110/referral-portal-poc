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
@Table(name = "company_referral_requests")
public class CompanyReferralRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "seeker_id", nullable = false)
    private User seeker;

    @ManyToOne
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "role_title", nullable = false, length = 160)
    private String roleTitle;

    @Column(name = "job_url", length = 500)
    private String jobUrl;

    @Column(name = "seeker_note", length = 1000)
    private String seekerNote;

    @Column(name = "share_profile", nullable = false)
    private boolean shareProfile;

    @Column(name = "share_seeker_email", nullable = false)
    private boolean shareSeekerEmail;

    @Column(name = "share_seeker_phone", nullable = false)
    private boolean shareSeekerPhone;

    @Column(name = "base_charge", nullable = false)
    private boolean baseCharge = true;

    @Column(nullable = false, length = 20)
    private String status = "open";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    public Long getId() {
        return id;
    }

    public User getSeeker() {
        return seeker;
    }

    public void setSeeker(User seeker) {
        this.seeker = seeker;
    }

    public Company getCompany() {
        return company;
    }

    public void setCompany(Company company) {
        this.company = company;
    }

    public String getRoleTitle() {
        return roleTitle;
    }

    public void setRoleTitle(String roleTitle) {
        this.roleTitle = roleTitle;
    }

    public String getJobUrl() {
        return jobUrl;
    }

    public void setJobUrl(String jobUrl) {
        this.jobUrl = jobUrl;
    }

    public String getSeekerNote() {
        return seekerNote;
    }

    public void setSeekerNote(String seekerNote) {
        this.seekerNote = seekerNote;
    }

    public boolean isShareProfile() {
        return shareProfile;
    }

    public void setShareProfile(boolean shareProfile) {
        this.shareProfile = shareProfile;
    }

    public boolean isShareSeekerEmail() {
        return shareSeekerEmail;
    }

    public void setShareSeekerEmail(boolean shareSeekerEmail) {
        this.shareSeekerEmail = shareSeekerEmail;
    }

    public boolean isShareSeekerPhone() {
        return shareSeekerPhone;
    }

    public void setShareSeekerPhone(boolean shareSeekerPhone) {
        this.shareSeekerPhone = shareSeekerPhone;
    }

    public boolean isBaseCharge() {
        return baseCharge;
    }

    public void setBaseCharge(boolean baseCharge) {
        this.baseCharge = baseCharge;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(Instant closedAt) {
        this.closedAt = closedAt;
    }
}