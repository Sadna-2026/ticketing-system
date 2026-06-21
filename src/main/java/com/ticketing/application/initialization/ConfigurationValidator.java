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

        assertNotBlank("spring.datasource.url");
        assertNotBlank("spring.datasource.driver-class-name");
        assertNotBlank("security.jwt.secret");

        if (!env.containsProperty("ticketing.persistence")) {
            throw new IllegalStateException("Missing config: ticketing.persistence");
        }
        String persistence = env.getProperty("ticketing.persistence");
        if (!"memory".equals(persistence) && !"jpa".equals(persistence)) {
            throw new IllegalStateException("Invalid config: ticketing.persistence must be 'memory' or 'jpa'");
        }

        assertPositiveInt("ticketing.queue.threshold");
        assertPositiveInt("ticketing.queue.flow-rate");

        if (!env.containsProperty("ticketing.external.base-url")) {
            throw new IllegalStateException("Missing config: ticketing.external.base-url");
        }
        
        assertPositiveInt("ticketing.external.connect-timeout-ms");
        assertPositiveInt("ticketing.external.read-timeout-ms");

        if (!env.containsProperty("ticketing.bootstrap.dataset")) {
            throw new IllegalStateException("Missing config: ticketing.bootstrap.dataset");
        }
        String dataset = env.getProperty("ticketing.bootstrap.dataset");
        if (dataset != null && !dataset.isBlank()) {
            if (!"dev-seed".equals(dataset) && !"initial-state-file".equals(dataset) && !"none".equals(dataset)) {
                throw new IllegalStateException("Invalid config: ticketing.bootstrap.dataset must be 'dev-seed', 'initial-state-file', or 'none'");
            }
        }

        assertNotBlank("ticketing.admin.username");
        assertNotBlank("ticketing.admin.password");

        log.info("Configuration validation passed successfully.");
    }

    private void assertNotBlank(String property) {
        String value = env.getProperty(property);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing or empty config: " + property);
        }
    }

    private void assertPositiveInt(String property) {
        String value = env.getProperty(property);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing or empty config: " + property);
        }
        try {
            int parsed = Integer.parseInt(value);
            if (parsed <= 0) {
                throw new IllegalStateException("Invalid config: " + property + " must be a positive integer");
            }
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Invalid config: " + property + " must be a valid integer");
        }
    }
}
