package com.referralportal.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@SpringBootTest(properties = {
        "spring.security.oauth2.client.registration.google.client-id=test-google-client",
        "spring.security.oauth2.client.registration.google.client-secret=test-google-secret",
        "spring.security.oauth2.client.registration.github.client-id=test-github-client",
        "spring.security.oauth2.client.registration.github.client-secret=test-github-secret",
        "spring.datasource.url=jdbc:mysql://localhost:3307/referral_portal?allowPublicKeyRetrieval=true&useSSL=false",
        "spring.datasource.username=referral",
        "spring.datasource.password=referral_local"
})
class ApiControllerRouteTests {

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Test
    void restRoutesRemainUnderApiPrefixAndSpaRoutesDoNotMove() {
        var paths = handlerMapping.getHandlerMethods().keySet().stream()
                .map(RequestMappingInfo::getPatternValues)
                .flatMap(java.util.Set::stream)
                .collect(java.util.stream.Collectors.toSet());
        assertTrue(paths.contains("/api/me"));
        assertTrue(paths.contains("/api/me/account"));
        assertTrue(paths.contains("/api/me/badge"));
        assertTrue(paths.contains("/api/me/profile"));
        assertTrue(paths.contains("/api/companies"));
        assertTrue(paths.contains("/api/wishlist/{companyId}"));
        assertTrue(paths.contains("/api/conversations"));
        assertTrue(paths.contains("/api/referrals"));
        assertTrue(paths.contains("/api/referrer/settings"));
        assertTrue(paths.contains("/api/referrer/verify/start"));
        assertTrue(paths.contains("/api/referral-requests"));
        assertTrue(paths.contains("/companies"));
        assertFalse(paths.contains("/api/api/me"));
    }
}