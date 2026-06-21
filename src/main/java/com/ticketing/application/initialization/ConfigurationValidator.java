package com.ticketing.application.initialization;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@Order(10)
public class ConfigurationValidator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ConfigurationValidator.class);

    private final Environment env;

    public ConfigurationValidator(Environment env) {
        this.env = env;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("Validating configuration parameters before initialization...");

        assertNotBlank("spring.datasource.operational.url");
        assertNotBlank("spring.datasource.operational.driver-class-name");
        assertNotBlank("spring.datasource.config.url");
        assertNotBlank("spring.datasource.config.driver-class-name");
        assertNotBlank("security.jwt.secret");

        if (!env.containsProperty("ticketing.persistence")) {
            failValidation("ticketing.persistence", "null", "'memory' or 'jpa'");
        }
        String persistence = env.getProperty("ticketing.persistence");
        if (!"memory".equals(persistence) && !"jpa".equals(persistence)) {
            failValidation("ticketing.persistence", persistence, "'memory' or 'jpa'");
        }

        assertPositiveInt("ticketing.queue.threshold");
        assertPositiveInt("ticketing.queue.flow-rate");

        if (!env.containsProperty("ticketing.external.base-url")) {
            failValidation("ticketing.external.base-url", "null", "present (can be empty)");
        }
        
        assertPositiveInt("ticketing.external.connect-timeout-ms");
        assertPositiveInt("ticketing.external.read-timeout-ms");

        if (!env.containsProperty("ticketing.bootstrap.dataset")) {
            failValidation("ticketing.bootstrap.dataset", "null", "present (can be empty)");
        }
        String dataset = env.getProperty("ticketing.bootstrap.dataset");
        if (dataset != null && !dataset.isBlank()) {
            if (!"dev-seed".equals(dataset) && !"initial-state-file".equals(dataset) && !"none".equals(dataset)) {
                failValidation("ticketing.bootstrap.dataset", dataset, "'dev-seed', 'initial-state-file', or 'none'");
            }
        }

        assertNotBlank("ticketing.admin.username");
        assertNotBlank("ticketing.admin.password");

        log.info("Configuration validation passed successfully.");
    }

    private void failValidation(String key, String badValue, String expected) {
        String msg = String.format("Configuration Error: Key '%s' has invalid value '%s'. Expected: %s", key, badValue, expected);
        log.error(msg);
        throw new ConfigurationValidationException(msg, key, badValue, expected);
    }

    private void assertNotBlank(String property) {
        if (!env.containsProperty(property)) {
            failValidation(property, "null", "non-blank string");
        }
        String value = env.getProperty(property);
        if (value == null || value.isBlank()) {
            failValidation(property, value == null ? "null" : "blank", "non-blank string");
        }
    }

    private void assertPositiveInt(String property) {
        if (!env.containsProperty(property)) {
            failValidation(property, "null", "positive integer");
        }
        String value = env.getProperty(property);
        if (value == null || value.isBlank()) {
            failValidation(property, value == null ? "null" : "blank", "positive integer");
        }
        try {
            int parsed = Integer.parseInt(value);
            if (parsed <= 0) {
                failValidation(property, value, "positive integer");
            }
        } catch (NumberFormatException e) {
            failValidation(property, value, "positive integer");
        }
    }
}
