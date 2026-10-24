package com.referralportal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class ReferralRequestDtos {

    private ReferralRequestDtos() {}

    public record CreateRequest(
            @NotNull Long companyId,
            @NotBlank @Size(max = 160) String roleTitle,
            @Size(max = 500) @Pattern(regexp = "^$|^https://.+", message = "Use an https:// job link") String jobUrl,
            @Size(max = 1000) String seekerNote,
            @NotNull Boolean shareProfile,
            @NotNull Boolean shareSeekerEmail,
            @NotNull Boolean shareSeekerPhone) {}

    public record AcceptRequest(@NotNull Boolean shareReferrerEmail, @NotNull Boolean shareReferrerPhone) {}

    public record SeekerResponse(long responseId, String status, String referrerName, String referrerTitle,
            String referrerEmail, String referrerPhone, String acceptedAt, String submittedAt, String confirmedAt) {}

    public record SeekerRequest(long id, String companyName, String roleTitle, String jobUrl, String seekerNote,
            String status, String createdAt, String expiresAt, int pendingCount, int declinedCount,
            List<SeekerResponse> responses) {}

    public record ReferrerInboxItem(long responseId, long requestId, String companyName, String roleTitle,
            String jobUrl, String seekerName, String experienceLevel, List<String> skills, List<String> rolesWanted,
            List<String> referralRolePreferences, String pitch, String githubUrl, String linkedinUrl, String seekerNote,
            boolean canSharePhone, String seekerEmail, String seekerPhone, String status, String createdAt,
            String acceptedAt, String submittedAt, short remindersSent) {}

    public record RequestAction(long responseId, String status) {}
}