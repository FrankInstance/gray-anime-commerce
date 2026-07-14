package com.gray.anime.common.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.core.env.Environment;
import org.springframework.context.annotation.Profile;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

@AutoConfiguration
@Profile("prod")
public class ProductionConfigurationGuard {
    private static final Set<String> DATASOURCE_SERVICES = Set.of(
            "user-service",
            "content-service",
            "shop-service",
            "inventory-service",
            "order-service",
            "payment-service",
            "ingestion-service"
    );
    private static final Set<String> RABBITMQ_SERVICES = Set.of(
            "user-service",
            "order-service",
            "payment-service",
            "ingestion-service"
    );
    private static final Set<String> DEVELOPMENT_PASSWORDS = Set.of(
            "gray_pass",
            "root_pass",
            "minioadmin",
            "password"
    );

    public ProductionConfigurationGuard(Environment environment) {
        validate(environment);
    }

    static void validate(Environment environment) {
        rejectMixedProfiles(environment);

        String serviceName = required(environment, "spring.application.name");
        required(environment, "JWT_ISSUER");
        required(environment, "JWT_AUDIENCE");
        required(environment, "JWT_KEY_ID");
        requireManagedKey(environment, "JWT_PUBLIC_KEY_LOCATION");

        if (DATASOURCE_SERVICES.contains(serviceName)) {
            requireSecret(environment, "MYSQL_PASSWORD");
        }
        if (RABBITMQ_SERVICES.contains(serviceName)) {
            requireSecret(environment, "RABBITMQ_PASSWORD");
        }
        if ("user-service".equals(serviceName)) {
            requireManagedKey(environment, "JWT_PRIVATE_KEY_LOCATION");
            if (!Boolean.parseBoolean(required(environment, "AUTH_COOKIE_SECURE"))) {
                throw invalid("AUTH_COOKIE_SECURE must be true in the prod profile");
            }
            if ("development".equalsIgnoreCase(environment.getProperty("security.password-reset.mode", "disabled"))) {
                throw invalid("development password reset must not be enabled in the prod profile");
            }
        }
        if ("gateway-service".equals(serviceName)) {
            validateCors(environment);
        }
    }

    private static void rejectMixedProfiles(Environment environment) {
        Set<String> activeProfiles = Set.copyOf(Arrays.asList(environment.getActiveProfiles()));
        if (activeProfiles.contains("local") || activeProfiles.contains("test")) {
            throw invalid("prod cannot be combined with local or test profiles");
        }
    }

    private static void requireSecret(Environment environment, String name) {
        String value = required(environment, name);
        if (value.length() < 12 || DEVELOPMENT_PASSWORDS.contains(value.toLowerCase(Locale.ROOT))) {
            throw invalid(name + " must be supplied by production secret storage and must not use a development value");
        }
    }

    private static void requireManagedKey(Environment environment, String name) {
        String value = required(environment, name).replace('\\', '/').toLowerCase(Locale.ROOT);
        if (value.contains("deploy/keys/") || value.contains("access-token-private.pem") && value.startsWith("file:./")) {
            throw invalid(name + " must reference a production-managed key");
        }
    }

    private static void validateCors(Environment environment) {
        String value = required(environment, "CORS_ALLOWED_ORIGIN_PATTERNS");
        for (String configuredOrigin : value.split(",")) {
            String origin = configuredOrigin.trim().toLowerCase(Locale.ROOT);
            if (!origin.startsWith("https://")
                    || origin.contains("*")
                    || origin.contains("localhost")
                    || origin.contains("127.0.0.1")) {
                throw invalid("CORS_ALLOWED_ORIGIN_PATTERNS must contain exact HTTPS production origins");
            }
        }
    }

    private static String required(Environment environment, String name) {
        String value = environment.getProperty(name);
        if (value == null || value.isBlank()) {
            throw invalid(name + " is required in the prod profile");
        }
        return value.trim();
    }

    private static IllegalStateException invalid(String message) {
        return new IllegalStateException("Unsafe production configuration: " + message);
    }
}
