package com.referralportal;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ReferralPortalBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(ReferralPortalBackendApplication.class, args);
	}

}
