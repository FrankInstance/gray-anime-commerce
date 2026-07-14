package com.gray.anime.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionConfigurationGuardTest {
    @Test
    void acceptsExplicitGatewayProductionConfiguration() {
        MockEnvironment environment = validBase("gateway-service")
                .withProperty("CORS_ALLOWED_ORIGIN_PATTERNS", "https://gray.example.com");

        assertThatCode(() -> ProductionConfigurationGuard.validate(environment)).doesNotThrowAnyException();
    }

    @Test
    void rejectsLocalCorsAndMixedProfiles() {
        MockEnvironment localCors = validBase("gateway-service")
                .withProperty("CORS_ALLOWED_ORIGIN_PATTERNS", "http://localhost:5173");
        assertThatThrownBy(() -> ProductionConfigurationGuard.validate(localCors))
                .hasMessageContaining("exact HTTPS production origins");

        MockEnvironment mixedProfiles = validBase("gateway-service")
                .withProperty("CORS_ALLOWED_ORIGIN_PATTERNS", "https://gray.example.com");
        mixedProfiles.setActiveProfiles("prod", "local");
        assertThatThrownBy(() -> ProductionConfigurationGuard.validate(mixedProfiles))
                .hasMessageContaining("cannot be combined");
    }

    @Test
    void rejectsDevelopmentCredentialsAndInsecureRefreshCookie() {
        MockEnvironment developmentPassword = validBase("user-service")
                .withProperty("MYSQL_PASSWORD", "gray_pass")
                .withProperty("RABBITMQ_PASSWORD", "production-rabbit-password")
                .withProperty("JWT_PRIVATE_KEY_LOCATION", "file:/run/secrets/jwt-private-key")
                .withProperty("AUTH_COOKIE_SECURE", "true");
        assertThatThrownBy(() -> ProductionConfigurationGuard.validate(developmentPassword))
                .hasMessageContaining("MYSQL_PASSWORD");

        MockEnvironment insecureCookie = validUserEnvironment().withProperty("AUTH_COOKIE_SECURE", "false");
        assertThatThrownBy(() -> ProductionConfigurationGuard.validate(insecureCookie))
                .hasMessageContaining("AUTH_COOKIE_SECURE must be true");

        MockEnvironment exposedResetToken = validUserEnvironment()
                .withProperty("security.password-reset.mode", "development");
        assertThatThrownBy(() -> ProductionConfigurationGuard.validate(exposedResetToken))
                .hasMessageContaining("development password reset");
    }

    @Test
    void acceptsExplicitUserServiceSecrets() {
        assertThatCode(() -> ProductionConfigurationGuard.validate(validUserEnvironment()))
                .doesNotThrowAnyException();
    }

    private MockEnvironment validUserEnvironment() {
        return validBase("user-service")
                .withProperty("MYSQL_PASSWORD", "production-mysql-password")
                .withProperty("RABBITMQ_PASSWORD", "production-rabbit-password")
                .withProperty("JWT_PRIVATE_KEY_LOCATION", "file:/run/secrets/jwt-private-key")
                .withProperty("AUTH_COOKIE_SECURE", "true");
    }

    private MockEnvironment validBase(String serviceName) {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.application.name", serviceName)
                .withProperty("JWT_ISSUER", "gray-production-auth")
                .withProperty("JWT_AUDIENCE", "gray-production-api")
                .withProperty("JWT_KEY_ID", "gray-production-key-1")
                .withProperty("JWT_PUBLIC_KEY_LOCATION", "file:/run/secrets/jwt-public-key");
        environment.setActiveProfiles("prod");
        return environment;
    }
}
