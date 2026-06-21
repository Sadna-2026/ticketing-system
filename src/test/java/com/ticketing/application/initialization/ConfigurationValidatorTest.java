package com.ticketing.application.initialization;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.env.MockEnvironment;

class ConfigurationValidatorTest {

    private MockEnvironment createValidEnvironment() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("spring.datasource.url", "jdbc:h2:mem:test");
        env.setProperty("spring.datasource.driver-class-name", "org.h2.Driver");
        env.setProperty("security.jwt.secret", "secret");
        env.setProperty("ticketing.persistence", "memory");
        env.setProperty("ticketing.queue.threshold", "100");
        env.setProperty("ticketing.queue.flow-rate", "10");
        env.setProperty("ticketing.external.base-url", "http://localhost:8080");
        env.setProperty("ticketing.external.connect-timeout-ms", "5000");
        env.setProperty("ticketing.external.read-timeout-ms", "5000");
        env.setProperty("ticketing.bootstrap.dataset", "dev-seed");
        env.setProperty("ticketing.admin.username", "admin");
        env.setProperty("ticketing.admin.password", "password");
        return env;
    }

    @Test
    void testValidConfiguration() {
        MockEnvironment env = createValidEnvironment();
        ConfigurationValidator validator = new ConfigurationValidator(env);
        assertDoesNotThrow(() -> validator.run(null));
    }

    @Test
    void testValidConfigurationWithEmptyOptionals() {
        MockEnvironment env = createValidEnvironment();
        env.setProperty("ticketing.external.base-url", "");
        env.setProperty("ticketing.bootstrap.dataset", "");
        ConfigurationValidator validator = new ConfigurationValidator(env);
        assertDoesNotThrow(() -> validator.run(null));
    }

    @ParameterizedTest
    @MethodSource("invalidConfigurations")
    void testInvalidConfiguration(String propertyToSet, String valueToSet, String propertyToClear, String expectedMessagePart) {
        MockEnvironment env = new MockEnvironment();
        if (!"spring.datasource.url".equals(propertyToClear)) env.setProperty("spring.datasource.url", "jdbc:h2:mem:test");
        if (!"spring.datasource.driver-class-name".equals(propertyToClear)) env.setProperty("spring.datasource.driver-class-name", "org.h2.Driver");
        if (!"security.jwt.secret".equals(propertyToClear)) env.setProperty("security.jwt.secret", "secret");
        if (!"ticketing.persistence".equals(propertyToClear)) env.setProperty("ticketing.persistence", "memory");
        if (!"ticketing.queue.threshold".equals(propertyToClear)) env.setProperty("ticketing.queue.threshold", "100");
        if (!"ticketing.queue.flow-rate".equals(propertyToClear)) env.setProperty("ticketing.queue.flow-rate", "10");
        if (!"ticketing.external.base-url".equals(propertyToClear)) env.setProperty("ticketing.external.base-url", "http://localhost:8080");
        if (!"ticketing.external.connect-timeout-ms".equals(propertyToClear)) env.setProperty("ticketing.external.connect-timeout-ms", "5000");
        if (!"ticketing.external.read-timeout-ms".equals(propertyToClear)) env.setProperty("ticketing.external.read-timeout-ms", "5000");
        if (!"ticketing.bootstrap.dataset".equals(propertyToClear)) env.setProperty("ticketing.bootstrap.dataset", "dev-seed");
        if (!"ticketing.admin.username".equals(propertyToClear)) env.setProperty("ticketing.admin.username", "admin");
        if (!"ticketing.admin.password".equals(propertyToClear)) env.setProperty("ticketing.admin.password", "password");
        
        if (propertyToSet != null) {
            env.setProperty(propertyToSet, valueToSet);
        }

        ConfigurationValidator validator = new ConfigurationValidator(env);
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> validator.run(null));
        assertTrue(exception.getMessage().contains(expectedMessagePart), 
                "Expected message to contain '" + expectedMessagePart + "' but was '" + exception.getMessage() + "'");
    }

    private static Stream<Arguments> invalidConfigurations() {
        return Stream.of(
            // Missing/Empty properties
            Arguments.of(null, null, "spring.datasource.url", "spring.datasource.url"),
            Arguments.of(null, null, "spring.datasource.driver-class-name", "spring.datasource.driver-class-name"),
            Arguments.of(null, null, "security.jwt.secret", "security.jwt.secret"),
            Arguments.of(null, null, "ticketing.queue.threshold", "ticketing.queue.threshold"),
            Arguments.of(null, null, "ticketing.queue.flow-rate", "ticketing.queue.flow-rate"),
            Arguments.of(null, null, "ticketing.admin.username", "ticketing.admin.username"),
            Arguments.of(null, null, "ticketing.admin.password", "ticketing.admin.password"),
            Arguments.of(null, null, "ticketing.persistence", "ticketing.persistence"),
            Arguments.of(null, null, "ticketing.external.base-url", "ticketing.external.base-url"),
            Arguments.of(null, null, "ticketing.bootstrap.dataset", "ticketing.bootstrap.dataset"),
            Arguments.of(null, null, "ticketing.external.connect-timeout-ms", "ticketing.external.connect-timeout-ms"),
            Arguments.of(null, null, "ticketing.external.read-timeout-ms", "ticketing.external.read-timeout-ms"),

            // Wrong type or value
            Arguments.of("ticketing.persistence", "redis", null, "ticketing.persistence must be 'memory' or 'jpa'"),
            Arguments.of("ticketing.queue.threshold", "abc", null, "must be a valid integer"),
            Arguments.of("ticketing.queue.threshold", "-5", null, "must be a positive integer"),
            Arguments.of("ticketing.queue.flow-rate", "0", null, "must be a positive integer"),
            Arguments.of("ticketing.queue.flow-rate", "5.5", null, "must be a valid integer"),
            
            Arguments.of("ticketing.external.connect-timeout-ms", "0", null, "must be a positive integer"),
            Arguments.of("ticketing.external.read-timeout-ms", "-1", null, "must be a positive integer"),
            
            Arguments.of("ticketing.bootstrap.dataset", "invalid", null, "ticketing.bootstrap.dataset must be 'dev-seed', 'initial-state-file', or 'none'")
        );
    }
}
