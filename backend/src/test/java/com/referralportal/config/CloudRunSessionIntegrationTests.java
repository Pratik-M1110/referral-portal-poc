package com.referralportal.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.transaction.PlatformTransactionManager;

@EnabledIfEnvironmentVariable(named = "SESSION_TEST_DB_URL", matches = ".+")
class CloudRunSessionIntegrationTests {

    @Test
    void sessionsSurviveRepositoryReplacementAndRetainOAuthAuthentication() {
        String databaseUrl = System.getenv("SESSION_TEST_DB_URL");
        assertThat(databaseUrl).matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/spring_session_test\\?.+");
        DataSource dataSource = new DriverManagerDataSource(databaseUrl, "root", "local-session-test-only");
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();

        WebApplicationContextRunner contexts = new WebApplicationContextRunner()
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("cloudrun"))
                .withBean(DataSource.class, () -> dataSource)
                .withBean(PlatformTransactionManager.class, () -> new DataSourceTransactionManager(dataSource))
                .withUserConfiguration(CloudRunSessionConfiguration.class);

        contexts.run(first -> {
            assertThat(first).hasNotFailed();
            JdbcIndexedSessionRepository writer = first.getBean(JdbcIndexedSessionRepository.class);
            String googleId = saveSession(writer, "google", googleUser());
            String githubId = saveSession(writer, "github", githubUser());

            CookieSerializer serializer = first.getBean(CookieSerializer.class);
            MockHttpServletResponse response = new MockHttpServletResponse();
            serializer.writeCookieValue(new CookieSerializer.CookieValue(new MockHttpServletRequest(), response, googleId));
            assertThat(response.getHeader("Set-Cookie"))
                    .contains("Secure", "HttpOnly", "SameSite=Lax");

            contexts.run(second -> {
                assertThat(second).hasNotFailed();
                JdbcIndexedSessionRepository reader = second.getBean(JdbcIndexedSessionRepository.class);
                assertAuthentication(reader, googleId, "google", "google-test");
                assertAuthentication(reader, githubId, "github", "github-test");
                Session restored = reader.findById(googleId);
                OAuth2AuthorizationRequest authorizationRequest = restored.getAttribute("test_oauth_request");
                assertThat(authorizationRequest.getState()).isEqualTo("synthetic-state");
                reader.deleteById(googleId);
                assertThat(writer.findById(googleId)).isNull();

                String expiredId = saveExpiredSession(reader);
                assertThat(writer.findById(expiredId)).isNull();
                reader.cleanUpExpiredSessions();
                Integer remaining = new JdbcTemplate(dataSource).queryForObject(
                        "SELECT COUNT(*) FROM SPRING_SESSION WHERE SESSION_ID = ?", Integer.class, expiredId);
                assertThat(remaining).isZero();
                reader.deleteById(githubId);
            });
        });
    }

    private <S extends Session> String saveSession(SessionRepository<S> repository, String provider, OAuth2User principal) {
        S session = repository.createSession();
        var authentication = new OAuth2AuthenticationToken(principal, principal.getAuthorities(), provider);
        session.setAttribute("SPRING_SECURITY_CONTEXT", new SecurityContextImpl(authentication));
        session.setAttribute("test_oauth_request", OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri("https://provider.example.test/authorize")
                .clientId("synthetic-client")
                .redirectUri("https://portal.example.test/login/oauth2/code/test")
                .state("synthetic-state")
                .build());
        repository.save(session);
        return session.getId();
    }

    private <S extends Session> String saveExpiredSession(SessionRepository<S> repository) {
        S session = repository.createSession();
        session.setMaxInactiveInterval(Duration.ZERO);
        repository.save(session);
        return session.getId();
    }

    private void assertAuthentication(JdbcIndexedSessionRepository repository, String id, String provider, String name) {
        Session session = repository.findById(id);
        assertThat(session).isNotNull();
        SecurityContextImpl context = session.getAttribute("SPRING_SECURITY_CONTEXT");
        OAuth2AuthenticationToken authentication = (OAuth2AuthenticationToken) context.getAuthentication();
        assertThat(authentication.getAuthorizedClientRegistrationId()).isEqualTo(provider);
        assertThat(authentication.getPrincipal().getName()).isEqualTo(name);
    }

    private OAuth2User googleUser() {
        Instant now = Instant.now();
        OidcIdToken token = new OidcIdToken("synthetic-token", now, now.plusSeconds(600),
                Map.of("sub", "google-test", "email", "google@example.test"));
        return new DefaultOidcUser(List.of(new SimpleGrantedAuthority("ROLE_USER")), token);
    }

    private OAuth2User githubUser() {
        return new DefaultOAuth2User(List.of(new SimpleGrantedAuthority("ROLE_USER")),
                Map.of("id", "github-test", "login", "test-user"), "id");
    }
}
