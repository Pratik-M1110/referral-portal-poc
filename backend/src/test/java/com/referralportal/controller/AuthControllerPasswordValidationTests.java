package com.referralportal.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.referralportal.dto.AuthDtos.LoginRequest;
import com.referralportal.service.AuthService;
import com.referralportal.service.LoginAttemptRateLimiter;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

class AuthControllerPasswordValidationTests {

    @Test
    void loginRejectsPasswordOver72Utf8BytesBeforeAuthentication() {
        AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
        AuthController controller = new AuthController();
        ReflectionTestUtils.setField(controller, "authenticationManager", authenticationManager);
        ReflectionTestUtils.setField(controller, "auth", new AuthService());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> controller.login(new LoginRequest("user@example.com", "é".repeat(37)),
                        new MockHttpServletRequest(), new MockHttpServletResponse()));

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(authenticationManager);
    }

    @Test
    void loginRateLimitRejectsBeforeAuthentication() {
        AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
        LoginAttemptRateLimiter loginAttemptRateLimiter = mock(LoginAttemptRateLimiter.class);
        doThrow(new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS))
                .when(loginAttemptRateLimiter).checkAllowed(anyString(), anyString());

        AuthController controller = new AuthController();
        ReflectionTestUtils.setField(controller, "authenticationManager", authenticationManager);
        ReflectionTestUtils.setField(controller, "auth", new AuthService());
        ReflectionTestUtils.setField(controller, "loginAttemptRateLimiter", loginAttemptRateLimiter);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> controller.login(new LoginRequest("user@example.com", "valid-password"),
                request, new MockHttpServletResponse()));

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        verifyNoInteractions(authenticationManager);
    }
}