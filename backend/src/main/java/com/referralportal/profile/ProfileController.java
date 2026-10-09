package com.referralportal.profile;

import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static com.referralportal.profile.ProfileDtos.ProfileInput;
import static com.referralportal.profile.ProfileDtos.ProfileView;

@RestController
@Validated
class ProfileController {

    private final ProfileService profiles;

    ProfileController(ProfileService profiles) {
        this.profiles = profiles;
    }

    @GetMapping("/api/me/profile")
    ProfileView get(Authentication auth) {
        return profiles.get(auth);
    }

    @PutMapping("/api/me/profile")
    Map<String, Boolean> save(Authentication auth, @jakarta.validation.Valid @RequestBody ProfileInput input) {
        return profiles.save(auth, input);
    }
}
