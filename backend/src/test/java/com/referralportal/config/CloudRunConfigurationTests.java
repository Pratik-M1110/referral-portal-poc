package com.referralportal.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.referralportal.entity.Company;
import com.referralportal.entity.CompanyReferralRequest;
import com.referralportal.entity.ReferrerProfile;
import com.referralportal.entity.User;
import com.referralportal.repository.jpa.NotificationJpaRepository;
import com.referralportal.service.ReferralNotificationService;
import com.referralportal.service.ReferralRequestMaintenance;
import com.referralportal.service.ReferralRequestScheduler;
import com.zaxxer.hikari.HikariDataSource;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

class CloudRunConfigurationTests {

    @Test
    void cloudRunBindsConnectorInsteadOfDevelopmentDatabaseWithoutOpeningConnections() throws IOException {
        MockEnvironment environment = environment(true)
                .withProperty("DB_URL", "jdbc:mysql://development.invalid/dev")
                .withProperty("DB_USERNAME", "test-user")
                .withProperty("DB_PASSWORD", "synthetic-test-password")
                .withProperty("CLOUD_SQL_INSTANCE", "test-project:asia-south1:test-instance");

        dataSourceContext(environment).run(context -> {
            assertThat(context).hasNotFailed();
            HikariDataSource dataSource = context.getBean(HikariDataSource.class);
            assertThat(dataSource.getJdbcUrl()).isEqualTo("jdbc:mysql:///referral_portal");
            assertThat(dataSource.getUsername()).isEqualTo("test-user");
            assertThat(dataSource.getPassword()).isEqualTo("synthetic-test-password");
            assertThat(dataSource.getDataSourceProperties())
                    .containsEntry("socketFactory", "com.google.cloud.sql.mysql.SocketFactory")
                    .containsEntry("cloudSqlInstance", "test-project:asia-south1:test-instance")
                    .containsEntry("cloudSqlRefreshStrategy", "lazy")
                    .containsEntry("ipTypes", "PUBLIC");
            assertThat(dataSource.getMaximumPoolSize()).isEqualTo(5);
            assertThat(dataSource.getMinimumIdle()).isZero();
            assertThat(dataSource.getHikariPoolMXBean()).isNull();
            assertThat(Class.forName("com.google.cloud.sql.mysql.SocketFactory")).isNotNull();
            assertThat(context.getEnvironment().getProperty("server.servlet.session.cookie.secure"))
                    .isEqualTo("true");
        });
    }

    @Test
    void developmentStillUsesItsConfiguredDatabaseWithoutConnectorProperties() throws IOException {
        MockEnvironment environment = environment(false)
                .withProperty("DB_URL", "jdbc:mysql://development.invalid/dev")
                .withProperty("DB_USERNAME", "development-user")
                .withProperty("DB_PASSWORD", "synthetic-development-password");

        dataSourceContext(environment).run(context -> {
            assertThat(context).hasNotFailed();
            HikariDataSource dataSource = context.getBean(HikariDataSource.class);
            assertThat(dataSource.getJdbcUrl()).isEqualTo("jdbc:mysql://development.invalid/dev");
            assertThat(dataSource.getUsername()).isEqualTo("development-user");
            assertThat(dataSource.getDataSourceProperties()).doesNotContainKey("socketFactory");
            assertThat(context.getEnvironment().getProperty("server.servlet.session.cookie.secure"))
                    .isEqualTo("false");
        });
    }

    @Test
    void cloudRunRequiresExplicitCredentialsAndInstanceInsteadOfLocalDefaults() throws IOException {
        MockEnvironment environment = environment(true);

        assertThrows(IllegalArgumentException.class,
                () -> environment.getRequiredProperty("spring.datasource.username"));
        assertThrows(IllegalArgumentException.class,
                () -> environment.getRequiredProperty("spring.datasource.password"));
        assertThrows(IllegalArgumentException.class,
                () -> environment.getRequiredProperty("spring.datasource.hikari.data-source-properties.cloudSqlInstance"));
        assertThrows(IllegalArgumentException.class,
                () -> environment.getRequiredProperty("app.frontend-url"));
    }

    @Test
    void cloudRunUsesExplicitFrontendUrlWhileDevelopmentRetainsItsDefault() throws IOException {
        MockEnvironment cloudRun = environment(true)
                .withProperty("APP_FRONTEND_URL", "https://portal.example.test");

        assertThat(cloudRun.getRequiredProperty("app.frontend-url")).isEqualTo("https://portal.example.test");
        assertThat(environment(false).getRequiredProperty("app.frontend-url")).isEqualTo("/");
    }

    @Test
    void cloudRunNotificationUsesAbsoluteReferralUrlWithOrWithoutTrailingSlash() throws IOException {
        for (String origin : new String[] {"https://portal.example.test", "https://portal.example.test/"}) {
            MockEnvironment environment = environment(true).withProperty("APP_FRONTEND_URL", origin);
            ReferralNotificationService service = new ReferralNotificationService();
            JavaMailSender mailSender = mock(JavaMailSender.class);
            ObjectMapper objectMapper = mock(ObjectMapper.class);
            ReflectionTestUtils.setField(service, "notifications", mock(NotificationJpaRepository.class));
            ReflectionTestUtils.setField(service, "mailSender", mailSender);
            ReflectionTestUtils.setField(service, "objectMapper", objectMapper);
            ReflectionTestUtils.setField(service, "mailFrom", "sender@example.test");
            ReflectionTestUtils.setField(service, "frontendUrl", environment.getRequiredProperty("app.frontend-url"));

            CompanyReferralRequest request = mock(CompanyReferralRequest.class);
            Company company = mock(Company.class);
            User seeker = mock(User.class);
            User referrer = mock(User.class);
            ReferrerProfile profile = mock(ReferrerProfile.class);
            when(request.getId()).thenReturn(1L);
            when(request.getCompany()).thenReturn(company);
            when(company.getName()).thenReturn("Example");
            when(request.getRoleTitle()).thenReturn("Engineer");
            when(request.getSeeker()).thenReturn(seeker);
            when(seeker.getName()).thenReturn("Test seeker");
            when(referrer.getAuthProvider()).thenReturn("google");
            when(referrer.getReferrerProfile()).thenReturn(profile);
            when(profile.getWorkEmail()).thenReturn("referrer@example.test");

            service.invite(request, referrer);

            ArgumentCaptor<SimpleMailMessage> message = ArgumentCaptor.forClass(SimpleMailMessage.class);
            verify(mailSender).send(message.capture());
            assertThat(message.getValue().getText()).contains("https://portal.example.test/referrals");
        }
    }

    @Test
    void cloudRunDisablesInternalMaintenanceWhileDevelopmentRetainsIt() throws IOException {
        schedulerContext(environment(true)).run(context ->
                assertThat(context).doesNotHaveBean(ReferralRequestScheduler.class));

        schedulerContext(environment(false)).run(context ->
                assertThat(context).hasSingleBean(ReferralRequestScheduler.class));
    }

    @Test
    void developmentDoesNotEnableCloudRunSessionConfiguration() {
        new ApplicationContextRunner().withUserConfiguration(CloudRunSessionConfiguration.class)
                .run(context -> assertThat(context).doesNotHaveBean(CloudRunSessionConfiguration.class));
    }

    private ApplicationContextRunner dataSourceContext(MockEnvironment environment) {
        return new ApplicationContextRunner()
                .withInitializer(context -> context.setEnvironment(environment))
                .withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class));
    }

    private ApplicationContextRunner schedulerContext(MockEnvironment environment) {
        return new ApplicationContextRunner()
                .withInitializer(context -> {
                    context.setEnvironment(environment);
                    var beans = context.getBeanFactory();
                    beans.registerSingleton("maintenance", mock(ReferralRequestMaintenance.class));
                })
                .withUserConfiguration(ReferralRequestScheduler.class);
    }

    private MockEnvironment environment(boolean cloudRun) throws IOException {
        MockEnvironment environment = new MockEnvironment();
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        for (var source : loader.load("base", new ClassPathResource("application.yml"))) {
            environment.getPropertySources().addFirst(source);
        }
        if (cloudRun) {
            for (var source : loader.load("cloudrun", new ClassPathResource("application-cloudrun.yml"))) {
                environment.getPropertySources().addFirst(source);
            }
        }
        return environment;
    }
}
