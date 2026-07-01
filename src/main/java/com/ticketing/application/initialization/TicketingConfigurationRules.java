package com.ticketing.application.initialization;

import java.util.Map;
import java.util.Set;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;

/**
 * Shared ticketing configuration validation rules. Invoked at environment-prepare time
 * so invalid settings halt startup before JPA or other heavy infrastructure initializes.
 */
public final class TicketingConfigurationRules {

    private static final String BORDER = "*************************************************************";

    private static final Set<String> ALLOWED_DDL_AUTO = Set.of(
            "none", "validate", "update", "create", "create-drop", "drop");

    private static final int MIN_ADMIN_PASSWORD_LENGTH = 6;

    private TicketingConfigurationRules() {
    }

    /**
     * Spring hook: validate after property sources load, before the application context (and JPA) starts.
     */
    public static final class EarlyValidationInitializer
            implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        @Override
        public void initialize(ConfigurableApplicationContext applicationContext) {
            ConfigurableEnvironment environment = applicationContext.getEnvironment();
            enableLazyInitializationWhenJpa(environment);
            validate(environment);
            DatabaseConnectivityPreflight.verify(environment);
        }

        /**
         * Req 5: defer bean creation until first use so JPA/EMF bootstrap does not run during
         * context refresh when the database is temporarily unreachable at boot.
         */
        private static void enableLazyInitializationWhenJpa(ConfigurableEnvironment environment) {
            if (!"jpa".equals(environment.getProperty("ticketing.persistence"))) {
                return;
            }
            if (Boolean.parseBoolean(environment.getProperty("spring.main.lazy-initialization", "false"))) {
                return;
            }
            environment.getPropertySources().addFirst(new MapPropertySource(
                    "ticketingJpaLazyInitialization",
                    Map.of("spring.main.lazy-initialization", true)));
        }
    }

    public static void validate(Environment env) {
        detectMangledCommandLineArguments(env);

        assertNotBlank(env, "spring.datasource.operational.url");
        assertNotBlank(env, "spring.datasource.operational.driver-class-name");
        assertNotBlank(env, "spring.datasource.config.url");
        assertNotBlank(env, "spring.datasource.config.driver-class-name");
        assertNotBlank(env, "security.jwt.secret");
        assertPositiveInt(env, "security.jwt.expiration-minutes");

        if (!env.containsProperty("ticketing.persistence")) {
            failValidation("ticketing.persistence", "null", "'memory' or 'jpa'");
        }
        String persistence = env.getProperty("ticketing.persistence");
        if (!"memory".equals(persistence) && !"jpa".equals(persistence)) {
            failValidation("ticketing.persistence", persistence, "'memory' or 'jpa'");
        }

        if ("jpa".equals(persistence)) {
            validateJpaDatabaseSettings(env);
        }

        assertPositiveInt(env, "ticketing.queue.threshold");
        assertPositiveInt(env, "ticketing.queue.flow-rate");

        if (!env.containsProperty("ticketing.external.base-url")) {
            failValidation("ticketing.external.base-url", "null", "present (can be empty)");
        }
        String externalBaseUrl = env.getProperty("ticketing.external.base-url", "");
        if (externalBaseUrl != null && !externalBaseUrl.isBlank()) {
            validateExternalPaymentSettings(env);
        }

        assertPositiveInt(env, "ticketing.external.connect-timeout-ms");
        assertPositiveInt(env, "ticketing.external.read-timeout-ms");

        if (!env.containsProperty("ticketing.bootstrap.dataset")) {
            failValidation("ticketing.bootstrap.dataset", "null", "present (can be empty)");
        }
        assertBoolean(env, "ticketing.bootstrap.clear-db-on-start");
        String dataset = env.getProperty("ticketing.bootstrap.dataset");
        if (dataset != null && !dataset.isBlank()) {
            if (!"dev-seed".equals(dataset) && !"initial-state-file".equals(dataset) && !"none".equals(dataset)) {
                failValidation("ticketing.bootstrap.dataset", dataset, "'dev-seed', 'initial-state-file', or 'none'");
            }
            if ("initial-state-file".equals(dataset)) {
                assertNotBlank(env, "ticketing.initial-state.file");
            }
        }

        assertNotBlank(env, "ticketing.admin.username");
        assertAdminPassword(env);
    }

    private static void validateJpaDatabaseSettings(Environment env) {
        assertNotBlank(env, "spring.datasource.operational.username");
        assertPropertyPresent(env, "spring.datasource.operational.password", true);
        assertNotBlank(env, "spring.datasource.config.username");
        assertPropertyPresent(env, "spring.datasource.config.password", true);
        assertNotBlank(env, "spring.jpa.database-platform");

        if (!env.containsProperty("spring.jpa.hibernate.ddl-auto")) {
            failValidation("spring.jpa.hibernate.ddl-auto", "null",
                    "'none', 'validate', 'update', 'create', 'create-drop', or 'drop'");
        }
        String ddlAuto = env.getProperty("spring.jpa.hibernate.ddl-auto");
        if (ddlAuto == null || ddlAuto.isBlank()) {
            failValidation("spring.jpa.hibernate.ddl-auto", ddlAuto == null ? "null" : "blank",
                    "'none', 'validate', 'update', 'create', 'create-drop', or 'drop'");
        }
        if (!ALLOWED_DDL_AUTO.contains(ddlAuto.trim().toLowerCase())) {
            failValidation("spring.jpa.hibernate.ddl-auto", ddlAuto,
                    "'none', 'validate', 'update', 'create', 'create-drop', or 'drop'");
        }
    }

    private static void validateExternalPaymentSettings(Environment env) {
        assertNotBlank(env, "ticketing.external.payment.currency");
        assertNotBlank(env, "ticketing.external.payment.card-number");
        assertNotBlank(env, "ticketing.external.payment.card-holder");
        assertNotBlank(env, "ticketing.external.payment.card-cvv");
        assertNotBlank(env, "ticketing.external.payment.card-id");
        assertMonth(env, "ticketing.external.payment.card-month");
        assertYear(env, "ticketing.external.payment.card-year");
    }

    private static void assertAdminPassword(Environment env) {
        if (!env.containsProperty("ticketing.admin.password")) {
            failValidation("ticketing.admin.password", "null",
                    "non-blank string of at least " + MIN_ADMIN_PASSWORD_LENGTH + " characters");
        }
        String password = env.getProperty("ticketing.admin.password");
        if (password == null || password.isBlank()) {
            failValidation("ticketing.admin.password", password == null ? "null" : "blank",
                    "non-blank string of at least " + MIN_ADMIN_PASSWORD_LENGTH + " characters");
        }
        if (password.length() < MIN_ADMIN_PASSWORD_LENGTH) {
            failValidation("ticketing.admin.password", password,
                    "string of at least " + MIN_ADMIN_PASSWORD_LENGTH + " characters");
        }
    }

    private static void assertPropertyPresent(Environment env, String property, boolean allowBlank) {
        if (!env.containsProperty(property)) {
            failValidation(property, "null", allowBlank ? "present (can be empty)" : "non-blank string");
        }
        if (!allowBlank) {
            String value = env.getProperty(property);
            if (value == null || value.isBlank()) {
                failValidation(property, value == null ? "null" : "blank", "non-blank string");
            }
        }
    }

    private static void assertMonth(Environment env, String property) {
        int month = parsePositiveInt(env, property);
        if (month < 1 || month > 12) {
            failValidation(property, String.valueOf(month), "integer from 1 to 12");
        }
    }

    private static void assertYear(Environment env, String property) {
        int year = parsePositiveInt(env, property);
        if (year < 2000) {
            failValidation(property, String.valueOf(year), "integer year >= 2000");
        }
    }

    private static int parsePositiveInt(Environment env, String property) {
        if (!env.containsProperty(property)) {
            failValidation(property, "null", "positive integer");
        }
        String value = env.getProperty(property);
        if (value == null || value.isBlank()) {
            failValidation(property, value == null ? "null" : "blank", "positive integer");
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            if (parsed <= 0) {
                failValidation(property, value, "positive integer");
            }
            return parsed;
        } catch (NumberFormatException e) {
            failValidation(property, value, "positive integer");
            return 0;
        }
    }

    private static void failValidation(String key, String badValue, String expected) {
        String detail = String.format(
                "Key '%s' has invalid value '%s'. Expected: %s", key, badValue, expected);
        throw new StartupHaltException(framedConfigurationError(detail));
    }

    private static String framedConfigurationError(String detail) {
        return """

                %s
                  APPLICATION STARTUP HALTED — CONFIGURATION ERROR
                  %s
                %s
                """.formatted(BORDER, detail, BORDER);
    }

    /**
     * Detects a common mistake when passing multiple {@code --key=value} overrides via
     * {@code spring-boot.run.arguments} with commas on Windows — Spring receives one glued value
     * such as {@code false,--ticketing.bootstrap.dataset=...} instead of separate arguments.
     */
    private static void detectMangledCommandLineArguments(Environment env) {
        if (!(env instanceof ConfigurableEnvironment configurable)) {
            return;
        }
        for (PropertySource<?> source : configurable.getPropertySources()) {
            if (!(source instanceof EnumerablePropertySource<?> enumerable)) {
                continue;
            }
            for (String name : enumerable.getPropertyNames()) {
                Object raw = enumerable.getProperty(name);
                if (!(raw instanceof String value) || !value.contains(",--")) {
                    continue;
                }
                String detail = """
                        Property '%s' looks like several command-line arguments merged into one value:
                          %s
                        Pass each override as its own argument (space-separated), for example:
                          mvn spring-boot:run "-Dspring-boot.run.arguments=--ticketing.seed.enabled=false --ticketing.bootstrap.dataset=initial-state-file"
                        Comma-separated lists inside spring-boot.run.arguments are not split on Windows."""
                        .formatted(name, abbreviate(value, 100));
                throw new StartupHaltException(framedConfigurationError(detail));
            }
        }
    }

    private static String abbreviate(String value, int maxLen) {
        if (value.length() <= maxLen) {
            return value;
        }
        return value.substring(0, maxLen) + "...";
    }

    private static void assertNotBlank(Environment env, String property) {
        if (!env.containsProperty(property)) {
            failValidation(property, "null", "non-blank string");
        }
        String value = env.getProperty(property);
        if (value == null || value.isBlank()) {
            failValidation(property, value == null ? "null" : "blank", "non-blank string");
        }
    }

    private static void assertPositiveInt(Environment env, String property) {
        parsePositiveInt(env, property);
    }

    private static void assertBoolean(Environment env, String property) {
        if (!env.containsProperty(property)) {
            failValidation(property, "null", "'true' or 'false'");
        }
        String value = env.getProperty(property);
        if (value == null) {
            failValidation(property, "null", "'true' or 'false'");
        }
        String trimmed = value.trim();
        if (!"true".equalsIgnoreCase(trimmed) && !"false".equalsIgnoreCase(trimmed)) {
            failValidation(property, value, "'true' or 'false'");
        }
    }
}
