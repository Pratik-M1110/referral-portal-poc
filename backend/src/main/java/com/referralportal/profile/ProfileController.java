package com.referralportal.profile;

import com.referralportal.common.ChipLists;
import com.referralportal.user.CurrentUser;
import com.referralportal.user.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
class ProfileController {

    private static final Set<String> LEVELS = Set.of("student", "fresher", "1-3", "3-6", "6+");

    record ProfileView(String pitch, String experienceLevel, List<String> skills, List<String> rolesWanted,
            String githubUrl, String linkedinUrl, boolean hideFromMyCompany, boolean exists) {}

    record ProfileInput(
            @Size(max = 200) String pitch,
            @NotBlank String experienceLevel,
            List<String> skills,
            List<String> rolesWanted,
            @Size(max = 300) @Pattern(regexp = "^$|^https://github\\.com/.+", message = "Use a https://github.com/... link") String githubUrl,
            @Size(max = 300) @Pattern(regexp = "^$|^https://([a-z]{2,3}\\.)?linkedin\\.com/.+", message = "Use a https://linkedin.com/... link") String linkedinUrl,
            boolean hideFromMyCompany) {}

    private final UserService users;
    private final JdbcClient jdbc;

    ProfileController(UserService users, JdbcClient jdbc) {
        this.users = users;
        this.jdbc = jdbc;
    }

    @GetMapping("/api/me/profile")
    ProfileView get(Authentication auth) {
        CurrentUser user = users.current(auth);
        return jdbc.sql("""
                SELECT pitch, experience_level, CAST(skills AS CHAR) AS skills, CAST(roles_wanted AS CHAR) AS roles_wanted,
                       github_url, linkedin_url, hide_from_my_company FROM profiles WHERE user_id = ?""")
                .param(user.id())
                .query((rs, n) -> new ProfileView(rs.getString("pitch"), rs.getString("experience_level"),
                        ChipLists.fromJson(rs.getString("skills")), ChipLists.fromJson(rs.getString("roles_wanted")),
                        rs.getString("github_url"), rs.getString("linkedin_url"),
                        rs.getBoolean("hide_from_my_company"), true))
                .optional()
                .orElse(new ProfileView("", "fresher", List.of(), List.of(), "", "", true, false));
    }

    @PutMapping("/api/me/profile")
    Map<String, Boolean> save(Authentication auth, @Valid @RequestBody ProfileInput in) {
        CurrentUser user = users.current(auth);
        if (!LEVELS.contains(in.experienceLevel())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pick an experience level.");
        }
        List<String> skills = ChipLists.clean(in.skills(), 12, "skills");
        List<String> roles = ChipLists.clean(in.rolesWanted(), 6, "roles");
        jdbc.sql("""
                INSERT INTO profiles (user_id, pitch, experience_level, skills, roles_wanted, github_url, linkedin_url, hide_from_my_company)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE pitch = VALUES(pitch), experience_level = VALUES(experience_level),
                  skills = VALUES(skills), roles_wanted = VALUES(roles_wanted), github_url = VALUES(github_url),
                  linkedin_url = VALUES(linkedin_url), hide_from_my_company = VALUES(hide_from_my_company)""")
                .params(user.id(), blankToNull(in.pitch()), in.experienceLevel(), ChipLists.toJson(skills),
                        ChipLists.toJson(roles), blankToNull(in.githubUrl()), blankToNull(in.linkedinUrl()),
                        in.hideFromMyCompany())
                .update();
        return Map.of("saved", true);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
