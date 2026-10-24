package com.referralportal.repository;

import com.referralportal.common.ChipLists;
import com.referralportal.dto.ProfileDtos;
import com.referralportal.entity.Profile;
import com.referralportal.repository.jpa.ProfileJpaRepository;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import static com.referralportal.dto.ProfileDtos.ProfileView;

@Repository
public class ProfileRepository {

    @Autowired
    private ProfileJpaRepository profiles;

    public ProfileView findByUserId(long userId) {
        return profiles.findById(userId)
                .map(p -> new ProfileView(p.getPitch(), p.getExperienceLevel(), ChipLists.fromJson(p.getSkills()),
                    ChipLists.fromJson(p.getRolesWanted()), p.getGithubUrl(), p.getLinkedinUrl(), p.getPhoneNumber(),
                        p.isHideFromMyCompany(), true))
                .orElse(new ProfileView("", "fresher", List.of(), List.of(), "", "", "", true, false));
    }

    public void save(long userId, ProfileDtos.ProfileInput input, List<String> skills, List<String> roles) {
        Profile profile = profiles.findById(userId).orElseGet(Profile::new);
        if (profile.getUserId() == null) {
            // Attach the owning side so JPA's @MapsId populates the primary key from the user association.
            com.referralportal.entity.User user = new com.referralportal.entity.User();
            user.setId(userId);
            profile.setUser(user);
        }
        profile.setPitch(blankToNull(input.pitch()));
        profile.setExperienceLevel(input.experienceLevel());
        profile.setSkills(ChipLists.toJson(skills));
        profile.setRolesWanted(ChipLists.toJson(roles));
        profile.setGithubUrl(blankToNull(input.githubUrl()));
        profile.setLinkedinUrl(blankToNull(input.linkedinUrl()));
        profile.setPhoneNumber(blankToNull(input.phoneNumber()));
        profile.setHideFromMyCompany(input.hideFromMyCompany());
        profiles.save(profile);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
