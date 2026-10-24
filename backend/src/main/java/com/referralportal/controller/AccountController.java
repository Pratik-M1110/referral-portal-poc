package com.referralportal.controller;

import com.referralportal.service.AccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;

@RestController
@RequestMapping("/api")
public class AccountController {

    @Autowired
    private AccountService accounts;

    private final SecurityContextLogoutHandler logoutHandler = new SecurityContextLogoutHandler();

    @DeleteMapping("/me/account")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        accounts.deleteCurrent(authentication);
        logoutHandler.logout(request, response, authentication);
    }
}