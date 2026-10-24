package com.referralportal.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/** Referrer side of a user's account, unlocked only after work-email verification. */
@Entity
@Table(name = "referrer_profiles")
public class ReferrerProfile {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @OneToOne
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "work_email", nullable = false, unique = true, length = 190)
    private String workEmail;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "is_referrer", nullable = false)
    private boolean referrer = true;

    @Column(name = "open_to_meet", nullable = false)
    private boolean openToMeet;

    @Column(name = "slot_capacity", nullable = false)
    private short slotCapacity = 3;

    @Column(name = "referral_limit", nullable = false)
    private short referralLimit = 10;

    @Column(name = "accepts_referral_requests", nullable = false)
    private boolean acceptsReferralRequests;

    @Column(name = "referral_role_preferences", columnDefinition = "json")
    private String referralRolePreferences = "[]";

    @Column(name = "job_title", length = 120)
    private String jobTitle;

    public Long getUserId() {
        return userId;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Company getCompany() {
        return company;
    }

    public void setCompany(Company company) {
        this.company = company;
    }

    public String getWorkEmail() {
        return workEmail;
    }

    public void setWorkEmail(String workEmail) {
        this.workEmail = workEmail;
    }

    public Instant getVerifiedAt() {
        return verifiedAt;
    }

    public void setVerifiedAt(Instant verifiedAt) {
        this.verifiedAt = verifiedAt;
    }

    public boolean isReferrer() {
        return referrer;
    }

    public void setReferrer(boolean referrer) {
        this.referrer = referrer;
    }

    public boolean isOpenToMeet() {
        return openToMeet;
    }

    public void setOpenToMeet(boolean openToMeet) {
        this.openToMeet = openToMeet;
    }

    public short getSlotCapacity() {
        return slotCapacity;
    }

    public void setSlotCapacity(short slotCapacity) {
        this.slotCapacity = slotCapacity;
    }

    public short getReferralLimit() {
        return referralLimit;
    }

    public void setReferralLimit(short referralLimit) {
        this.referralLimit = referralLimit;
    }

    public boolean isAcceptsReferralRequests() {
        return acceptsReferralRequests;
    }

    public void setAcceptsReferralRequests(boolean acceptsReferralRequests) {
        this.acceptsReferralRequests = acceptsReferralRequests;
    }

    public String getReferralRolePreferences() {
        return referralRolePreferences;
    }

    public void setReferralRolePreferences(String referralRolePreferences) {
        this.referralRolePreferences = referralRolePreferences;
    }

    public String getJobTitle() {
        return jobTitle;
    }

    public void setJobTitle(String jobTitle) {
        this.jobTitle = jobTitle;
    }
}
