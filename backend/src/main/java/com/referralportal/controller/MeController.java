package com.referralportal.controller;

import com.referralportal.service.MeService;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MeController {

    @Autowired
    private MeService me;

    @GetMapping("/api/me")
    public Map<String, Object> me(Authentication auth) {
        return me.get(auth);
    }
}
