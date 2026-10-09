package com.referralportal.service;

import com.referralportal.common.ChipLists;
import com.referralportal.profile.ProfileDtos;
import com.referralportal.repository.ProfileRepository;
import com.referralportal.repository.ProfileRepository;
import com.referralportal.user.CurrentUser;
import com.referralportal.service.UserService;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import static com.referralportal.profile.ProfileDtos.ProfileInput;
import static com.referralportal.profile.ProfileDtos.ProfileView;

@Service
public class ProfileService {

    private static final Set<String> LEVELS = Set.of("student", "fresher", "1-3", "3-6", "6+");

    private final UserService users;
    private final ProfileRepository profiles;

    public ProfileService(UserService users, ProfileRepository profiles) {
        this.users = users;
        this.profiles = profiles;
    }

    public ProfileView get(Authentication auth) {
        CurrentUser user = users.current(auth);
        return profiles.findByUserId(user.id());
    }

    public Map<String, Boolean> save(Authentication auth, ProfileInput input) {
        CurrentUser user = users.current(auth);
        if (!LEVELS.contains(input.experienceLevel())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pick an experience level.");
        }
        List<String> skills = ChipLists.clean(input.skills(), 12, "skills");
        List<String> roles = ChipLists.clean(input.rolesWanted(), 6, "roles");
        profiles.save(user.id(), input, skills, roles);
        return Map.of("saved", true);
    }
}
