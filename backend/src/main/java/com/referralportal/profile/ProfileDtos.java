package com.referralportal.profile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class ProfileDtos {

    private ProfileDtos() {}

    public record ProfileView(String pitch, String experienceLevel, List<String> skills, List<String> rolesWanted,
            String githubUrl, String linkedinUrl, boolean hideFromMyCompany, boolean exists) {}

    public record ProfileInput(
            @Size(max = 200) String pitch,
            @NotBlank String experienceLevel,
            List<String> skills,
            List<String> rolesWanted,
            @Size(max = 300) @Pattern(regexp = "^$|^https://github\\.com/.+", message = "Use a https://github.com/... link") String githubUrl,
            @Size(max = 300) @Pattern(regexp = "^$|^https://([a-z]{2,3}\\.)?linkedin\\.com/.+", message = "Use a https://linkedin.com/... link") String linkedinUrl,
            boolean hideFromMyCompany) {}
}
