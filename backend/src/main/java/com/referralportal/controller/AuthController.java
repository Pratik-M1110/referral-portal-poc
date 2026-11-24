package com.referralportal.controller;

import com.referralportal.dto.AuthDtos.LoginRequest;
import com.referralportal.dto.AuthDtos.RegisterRequest;
import com.referralportal.dto.AuthDtos.VerifyRegistrationRequest;
import com.referralportal.service.AuthService;
import com.referralportal.service.LoginAttemptRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Email/password registration and sign-in. Establishes the same session cookie as OAuth login. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthenticationManager authenticationManager;
    @Autowired
    private AuthService auth;
    @Autowired
    private LoginAttemptRateLimiter loginAttemptRateLimiter;
    @Autowired
    private SessionAuthenticationStrategy sessionAuthenticationStrategy;

    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    /**
     * Primes the CSRF cookie so a signed-out client can send the token on register/login.
     * Reading the token triggers the repository to write the XSRF-TOKEN cookie on the response.
     */
    @GetMapping("/csrf")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void csrf(CsrfToken token) {
        token.getToken();
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void register(@Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
        auth.beginRegistration(request.name(), request.email(), request.password(), httpRequest.getRemoteAddr());
    }

    @PostMapping("/register/verify")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void verifyRegistration(@Valid @RequestBody VerifyRegistrationRequest request,
            HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        auth.verifyRegistration(request.email(), request.code(), request.password());
        startSession(request.email(), request.password(), httpRequest, httpResponse);
    }

    @PostMapping("/login")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void login(@Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        auth.validatePasswordLength(request.password());
        loginAttemptRateLimiter.checkAllowed(request.email(), httpRequest.getRemoteAddr());
        startSession(request.email(), request.password(), httpRequest, httpResponse);
    }

    private void startSession(String email, String password,
            HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(email.trim().toLowerCase(), password));
        } catch (AuthenticationException failed) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password.");
        }
        loginAttemptRateLimiter.recordSuccessfulLogin(email);
        sessionAuthenticationStrategy.onAuthentication(authentication, request, response);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }
}
