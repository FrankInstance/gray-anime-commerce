package com.gray.anime.common.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.core.env.Environment;
import org.springframework.context.annotation.Profile;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

@AutoConfiguration
@Profile({"prod", "demo"})
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
            required(environment, "REDIS_HOST");
            requireStrongSecret(environment, "AUTH_LOGIN_THROTTLE_SECRET", 32);
            if (!Boolean.parseBoolean(required(environment, "AUTH_COOKIE_SECURE"))) {
                throw invalid("AUTH_COOKIE_SECURE must be true in the prod profile");
            }
            if ("development".equalsIgnoreCase(environment.getProperty("security.password-reset.mode", "disabled"))) {
                throw invalid("development password reset must not be enabled in the prod profile");
            }
            validateDemoCleanup(environment);
        }
        if ("gateway-service".equals(serviceName)) {
            validateCors(environment);
            required(environment, "REDIS_HOST");
        }
        if ("payment-service".equals(serviceName)) {
            validatePaymentMode(environment);
        }
        if ("assistant-service".equals(serviceName)) {
            validateAssistant(environment);
        }
    }

    private static void rejectMixedProfiles(Environment environment) {
        Set<String> activeProfiles = Set.copyOf(Arrays.asList(environment.getActiveProfiles()));
        boolean production = activeProfiles.contains("prod");
        boolean demo = activeProfiles.contains("demo");
        if (activeProfiles.contains("local") || activeProfiles.contains("test") || production && demo) {
            throw invalid("prod and demo cannot be combined with local, test, or each other");
        }
    }

    private static void validatePaymentMode(Environment environment) {
        Set<String> activeProfiles = Set.copyOf(Arrays.asList(environment.getActiveProfiles()));
        String provider = environment.getProperty("payment.provider", "disabled").trim().toLowerCase(Locale.ROOT);
        if (activeProfiles.contains("prod") && "demo".equals(provider)) {
            throw invalid("demo payment must not be enabled in the prod profile");
        }
        if (activeProfiles.contains("demo")) {
            if (!"demo".equals(provider)) {
                throw invalid("the demo profile requires the demo payment provider");
            }
            if (!Boolean.parseBoolean(required(environment, "PAYMENT_DEMO_ENABLED"))) {
                throw invalid("PAYMENT_DEMO_ENABLED must be explicitly true in the demo profile");
            }
        }
    }

    private static void validateAssistant(Environment environment) {
        required(environment, "REDIS_HOST");
        required(environment, "QDRANT_HOST");
        if (!Boolean.parseBoolean(environment.getProperty("AI_ENABLED", "false"))) {
            return;
        }
        requireSecret(environment, "AI_API_KEY");
        String baseUrl = required(environment, "AI_BASE_URL").toLowerCase(Locale.ROOT);
        if (!baseUrl.startsWith("https://") || baseUrl.contains("example.com")) {
            throw invalid("AI_BASE_URL must use the provider HTTPS endpoint");
        }
    }

    private static void validateDemoCleanup(Environment environment) {
        Set<String> activeProfiles = Set.copyOf(Arrays.asList(environment.getActiveProfiles()));
        boolean enabled = Boolean.parseBoolean(environment.getProperty("DEMO_CLEANUP_ENABLED", "false"));
        if (activeProfiles.contains("prod") && enabled) {
            throw invalid("demo data cleanup must not be enabled in the prod profile");
        }
        if (activeProfiles.contains("demo") && !enabled) {
            throw invalid("DEMO_CLEANUP_ENABLED must be explicitly true in the demo profile");
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

    private static void requireStrongSecret(Environment environment, String name, int minimumLength) {
        String value = required(environment, name);
        if (value.length() < minimumLength || value.toLowerCase(Locale.ROOT).contains("local-login-throttle")) {
            throw invalid(name + " must be supplied by production secret storage and contain at least " + minimumLength + " characters");
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
