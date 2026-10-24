package com.referralportal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class ProfileDtos {

    private ProfileDtos() {}

    public record ProfileView(String pitch, String experienceLevel, List<String> skills, List<String> rolesWanted,
            String githubUrl, String linkedinUrl, String phoneNumber, boolean hideFromMyCompany, boolean exists) {}

    public record ProfileInput(
            @Size(max = 200) String pitch,
            @NotBlank String experienceLevel,
            List<String> skills,
            List<String> rolesWanted,
            @Size(max = 300) @Pattern(regexp = "^$|^https://github\\.com/.+", message = "Use a https://github.com/... link") String githubUrl,
            @Size(max = 300) @Pattern(regexp = "^$|^https://([a-z]{2,3}\\.)?linkedin\\.com/.+", message = "Use a https://linkedin.com/... link") String linkedinUrl,
            @Size(max = 32) @Pattern(regexp = "^$|^\\+?[0-9 ()-]{7,32}$", message = "Enter a valid phone number") String phoneNumber,
            boolean hideFromMyCompany) {}
}
