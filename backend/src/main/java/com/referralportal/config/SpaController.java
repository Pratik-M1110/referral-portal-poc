package com.referralportal.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
class SpaController {

    @GetMapping({"/companies", "/chats", "/chats/{id}", "/badge", "/referrals", "/profile"})
    String forwardToFrontend() {
        return "forward:/index.html";
    }
}