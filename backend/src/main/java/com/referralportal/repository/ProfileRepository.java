package com.referralportal.repository;

import com.referralportal.common.ChipLists;
import com.referralportal.profile.ProfileDtos;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import static com.referralportal.profile.ProfileDtos.ProfileView;

@Repository
public class ProfileRepository {

    private final JdbcClient jdbc;

    public ProfileRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public ProfileView findByUserId(long userId) {
        return jdbc.sql("""
                SELECT pitch, experience_level, CAST(skills AS CHAR) AS skills, CAST(roles_wanted AS CHAR) AS roles_wanted,
                       github_url, linkedin_url, hide_from_my_company FROM profiles WHERE user_id = ?""")
                .param(userId)
                .query((rs, n) -> new ProfileView(rs.getString("pitch"), rs.getString("experience_level"),
                        ChipLists.fromJson(rs.getString("skills")), ChipLists.fromJson(rs.getString("roles_wanted")),
                        rs.getString("github_url"), rs.getString("linkedin_url"),
                        rs.getBoolean("hide_from_my_company"), true))
                .optional()
                .orElse(new ProfileView("", "fresher", List.of(), List.of(), "", "", true, false));
    }

    public void save(long userId, ProfileDtos.ProfileInput input, List<String> skills, List<String> roles) {
        jdbc.sql("""
                INSERT INTO profiles (user_id, pitch, experience_level, skills, roles_wanted, github_url, linkedin_url, hide_from_my_company)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE pitch = VALUES(pitch), experience_level = VALUES(experience_level),
                  skills = VALUES(skills), roles_wanted = VALUES(roles_wanted), github_url = VALUES(github_url),
                  linkedin_url = VALUES(linkedin_url), hide_from_my_company = VALUES(hide_from_my_company)""")
                .params(userId, blankToNull(input.pitch()), input.experienceLevel(), ChipLists.toJson(skills),
                        ChipLists.toJson(roles), blankToNull(input.githubUrl()), blankToNull(input.linkedinUrl()),
                        input.hideFromMyCompany())
                .update();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
