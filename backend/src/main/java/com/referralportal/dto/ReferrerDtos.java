package com.referralportal.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class ReferrerDtos {

    private ReferrerDtos() {}

    public record StartVerificationRequest(@NotBlank @Email @Size(max = 190) String workEmail) {}

    public record ConfirmVerificationRequest(
            @NotBlank @Email @Size(max = 190) String workEmail,
            @NotBlank @Pattern(regexp = "[0-9]{6}", message = "Enter the 6-digit code.") String code) {}

    public record SettingsRequest(
            @NotNull Boolean openToMeet,
            @NotNull @Min(1) @Max(10) Integer slotCapacity,
            @NotNull @Min(1) @Max(10) Integer referralLimit,
            @NotNull Boolean acceptsReferralRequests,
            @Size(max = 10) List<String> referralRolePreferences) {}

    public record Settings(boolean verified, String workEmail, String companyName, boolean openToMeet,
            int slotCapacity, int referralLimit, boolean acceptsReferralRequests, List<String> referralRolePreferences) {}

    public record VerificationStarted(boolean sent) {}
}
