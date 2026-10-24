package com.referralportal;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"spring.security.oauth2.client.registration.google.client-id=test-google-client",
		"spring.security.oauth2.client.registration.google.client-secret=test-google-secret",
		"spring.security.oauth2.client.registration.github.client-id=test-github-client",
		"spring.security.oauth2.client.registration.github.client-secret=test-github-secret",
		"spring.datasource.url=jdbc:mysql://localhost:3307/referral_portal?allowPublicKeyRetrieval=true&useSSL=false",
		"spring.datasource.username=referral",
		"spring.datasource.password=referral_local",
		"app.seed-demo=false"
})
class ReferralPortalBackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
