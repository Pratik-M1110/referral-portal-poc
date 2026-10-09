package com.referralportal.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaController {

    @GetMapping({"/companies", "/chats", "/chats/{id}", "/badge", "/referrals", "/profile"})
    public String forwardToFrontend() {
        return "forward:/index.html";
    }
}