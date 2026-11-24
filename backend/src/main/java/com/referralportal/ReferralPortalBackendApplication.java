package com.referralportal;

import com.referralportal.service.ReferralRequestMaintenance;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Profiles;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;

@SpringBootApplication
@EnableScheduling
public class ReferralPortalBackendApplication {

	private static final System.Logger LOGGER = System.getLogger(ReferralPortalBackendApplication.class.getName());

	public static void main(String[] args) {
		ConfigurableApplicationContext context = SpringApplication.run(ReferralPortalBackendApplication.class, args);
		if (context.getEnvironment().acceptsProfiles(Profiles.of("maintenance"))) {
			try {
				LOGGER.log(System.Logger.Level.INFO, "Starting scheduled maintenance.");
				context.getBean(JdbcIndexedSessionRepository.class).cleanUpExpiredSessions();
				context.getBean(ReferralRequestMaintenance.class).processDueRequests();
				LOGGER.log(System.Logger.Level.INFO, "Scheduled maintenance completed.");
			} finally {
				context.close();
			}
		}
	}

}
