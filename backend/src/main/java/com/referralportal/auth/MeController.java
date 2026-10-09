package com.referralportal.auth;

import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class MeController {

    private final MeService me;

    MeController(MeService me) {
        this.me = me;
    }

    @GetMapping("/api/me")
    Map<String, Object> me(Authentication auth) {
        return me.get(auth);
    }
}
