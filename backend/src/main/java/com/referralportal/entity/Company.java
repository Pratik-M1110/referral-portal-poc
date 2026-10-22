package com.referralportal.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.List;

/** A company row is created automatically from a verified work-email domain; no manual onboarding. */
@Entity
@Table(name = "companies")
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "email_domain", nullable = false, unique = true, length = 190)
    private String emailDomain;

    @Column(name = "booth_label", length = 40)
    private String boothLabel;

    @Column(name = "is_sponsor", nullable = false)
    private boolean sponsor;

    @OneToMany(mappedBy = "company")
    private List<OpenRole> openRoles;

    @OneToMany(mappedBy = "company")
    private List<ReferrerProfile> referrerProfiles;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmailDomain() {
        return emailDomain;
    }

    public void setEmailDomain(String emailDomain) {
        this.emailDomain = emailDomain;
    }

    public String getBoothLabel() {
        return boothLabel;
    }

    public void setBoothLabel(String boothLabel) {
        this.boothLabel = boothLabel;
    }

    public boolean isSponsor() {
        return sponsor;
    }

    public void setSponsor(boolean sponsor) {
        this.sponsor = sponsor;
    }

    public List<OpenRole> getOpenRoles() {
        return openRoles;
    }

    public List<ReferrerProfile> getReferrerProfiles() {
        return referrerProfiles;
    }
}
