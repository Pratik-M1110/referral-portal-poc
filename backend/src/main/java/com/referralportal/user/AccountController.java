package com.referralportal.user;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class AccountController {

    private final AccountService accounts;
    private final SecurityContextLogoutHandler logoutHandler = new SecurityContextLogoutHandler();

    AccountController(AccountService accounts) {
        this.accounts = accounts;
    }

    @DeleteMapping("/api/me/account")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        accounts.deleteCurrent(authentication);
        logoutHandler.logout(request, response, authentication);
    }
}