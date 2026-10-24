package com.referralportal.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/** Seeker side of a user's account: looking-for chips, skills, pitch, resume link. */
@Entity
@Table(name = "profiles")
public class Profile {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @OneToOne
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @Column(length = 200)
    private String pitch;

    @Column(name = "experience_level", nullable = false, length = 10)
    private String experienceLevel;

    @Column(columnDefinition = "json")
    private String skills;

    @Column(name = "roles_wanted", columnDefinition = "json")
    private String rolesWanted;

    @Column(name = "resume_url", length = 500)
    private String resumeUrl;

    @Column(name = "github_url", length = 300)
    private String githubUrl;

    @Column(name = "linkedin_url", length = 300)
    private String linkedinUrl;

    @Column(name = "phone_number", length = 32)
    private String phoneNumber;

    @Column(name = "is_seeker", nullable = false)
    private boolean seeker = true;

    @Column(name = "hide_from_my_company", nullable = false)
    private boolean hideFromMyCompany = true;

    public Long getUserId() {
        return userId;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getPitch() {
        return pitch;
    }

    public void setPitch(String pitch) {
        this.pitch = pitch;
    }

    public String getExperienceLevel() {
        return experienceLevel;
    }

    public void setExperienceLevel(String experienceLevel) {
        this.experienceLevel = experienceLevel;
    }

    public String getSkills() {
        return skills;
    }

    public void setSkills(String skills) {
        this.skills = skills;
    }

    public String getRolesWanted() {
        return rolesWanted;
    }

    public void setRolesWanted(String rolesWanted) {
        this.rolesWanted = rolesWanted;
    }

    public String getResumeUrl() {
        return resumeUrl;
    }

    public void setResumeUrl(String resumeUrl) {
        this.resumeUrl = resumeUrl;
    }

    public String getGithubUrl() {
        return githubUrl;
    }

    public void setGithubUrl(String githubUrl) {
        this.githubUrl = githubUrl;
    }

    public String getLinkedinUrl() {
        return linkedinUrl;
    }

    public void setLinkedinUrl(String linkedinUrl) {
        this.linkedinUrl = linkedinUrl;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public boolean isSeeker() {
        return seeker;
    }

    public void setSeeker(boolean seeker) {
        this.seeker = seeker;
    }

    public boolean isHideFromMyCompany() {
        return hideFromMyCompany;
    }

    public void setHideFromMyCompany(boolean hideFromMyCompany) {
        this.hideFromMyCompany = hideFromMyCompany;
    }
}
