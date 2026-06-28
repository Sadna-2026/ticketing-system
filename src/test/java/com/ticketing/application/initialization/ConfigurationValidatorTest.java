package com.ticketing.application.initialization;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.mock.env.MockEnvironment;

class ConfigurationValidatorTest {

    private MockEnvironment createValidEnvironment() {
        MockEnvironment env = new MockEnvironment();
        applyCommonValidSettings(env);
        env.setProperty("ticketing.persistence", "memory");
        return env;
    }

    private static void applyCommonValidSettings(MockEnvironment env) {
        env.setProperty("spring.datasource.operational.url", "jdbc:h2:mem:test");
        env.setProperty("spring.datasource.operational.driver-class-name", "org.h2.Driver");
        env.setProperty("spring.datasource.config.url", "jdbc:h2:mem:test_cfg");
        env.setProperty("spring.datasource.config.driver-class-name", "org.h2.Driver");
        env.setProperty("security.jwt.secret", "secret");
        env.setProperty("security.jwt.expiration-minutes", "120");
        env.setProperty("ticketing.queue.threshold", "100");
        env.setProperty("ticketing.queue.flow-rate", "10");
        env.setProperty("ticketing.external.base-url", "http://localhost:8080");
        env.setProperty("ticketing.external.connect-timeout-ms", "5000");
        env.setProperty("ticketing.external.read-timeout-ms", "5000");
        env.setProperty("ticketing.external.payment.currency", "USD");
        env.setProperty("ticketing.external.payment.card-number", "2222333344445555");
        env.setProperty("ticketing.external.payment.card-month", "12");
        env.setProperty("ticketing.external.payment.card-year", "2030");
        env.setProperty("ticketing.external.payment.card-holder", "Ticketing System");
        env.setProperty("ticketing.external.payment.card-cvv", "123");
        env.setProperty("ticketing.external.payment.card-id", "000000000");
        env.setProperty("ticketing.bootstrap.dataset", "dev-seed");
        env.setProperty("ticketing.admin.username", "admin");
        env.setProperty("ticketing.admin.password", "password");
    }

    private static void applyJpaValidSettings(MockEnvironment env) {
        env.setProperty("spring.datasource.operational.username", "sa");
        env.setProperty("spring.datasource.operational.password", "");
        env.setProperty("spring.datasource.config.username", "sa");
        env.setProperty("spring.datasource.config.password", "");
        env.setProperty("spring.jpa.database-platform", "org.hibernate.dialect.H2Dialect");
        env.setProperty("spring.jpa.hibernate.ddl-auto", "create-drop");
    }

    @Test
    void testValidConfiguration() {
        MockEnvironment env = createValidEnvironment();
        assertDoesNotThrow(() -> TicketingConfigurationRules.validate(env));
    }

    @Test
    void testValidConfigurationWithEmptyOptionals() {
        MockEnvironment env = createValidEnvironment();
        env.setProperty("ticketing.external.base-url", "");
        env.setProperty("ticketing.bootstrap.dataset", "");
        assertDoesNotThrow(() -> TicketingConfigurationRules.validate(env));
    }

    @Test
    void testValidJpaConfiguration() {
        MockEnvironment env = createValidEnvironment();
        env.setProperty("ticketing.persistence", "jpa");
        applyJpaValidSettings(env);
        assertDoesNotThrow(() -> TicketingConfigurationRules.validate(env));
    }

    @Test
    void givenMangledCommandLineValue_whenValidate_thenFailsWithReadableMessage() {
        MockEnvironment env = createValidEnvironment();
        env.setProperty("ticketing.seed.enabled",
                "false,--ticketing.bootstrap.dataset=initial-state-file,--ticketing.initial-state.file=/tmp/x.txt");

        StartupHaltException ex =
                assertThrows(StartupHaltException.class, () -> TicketingConfigurationRules.validate(env));
        assertTrue(ex.getMessage().contains("merged into one value"),
                "message was: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("space-separated"),
                "message was: " + ex.getMessage());
    }

    @Test
    void givenVeryLongMangledCommandLineValue_whenValidate_thenAbbreviatesInMessage() {
        MockEnvironment env = createValidEnvironment();
        String padding = "x".repeat(120);
        env.setProperty("ticketing.seed.enabled", "false,--" + padding);

        StartupHaltException ex =
                assertThrows(StartupHaltException.class, () -> TicketingConfigurationRules.validate(env));
        assertTrue(ex.getMessage().contains("..."),
                "message was: " + ex.getMessage());
    }

    @Test
    void givenValidEnvironment_whenEarlyInitializerRuns_thenPasses() {
        ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
        MockEnvironment env = createValidEnvironment();
        when(context.getEnvironment()).thenReturn(env);

        assertDoesNotThrow(() ->
                new TicketingConfigurationRules.EarlyValidationInitializer().initialize(context));
    }

    @Test
    void givenExplicitNoneDataset_whenValidate_thenPasses() {
        MockEnvironment env = createValidEnvironment();
        env.setProperty("ticketing.bootstrap.dataset", "none");
        assertDoesNotThrow(() -> TicketingConfigurationRules.validate(env));
    }

    @ParameterizedTest
    @MethodSource("invalidConfigurations")
    void testInvalidConfiguration(String propertyToSet, String valueToSet, String propertyToClear, String expectedMessagePart) {
        MockEnvironment env = new MockEnvironment();
        if (!"spring.datasource.operational.url".equals(propertyToClear)) {
            env.setProperty("spring.datasource.operational.url", "jdbc:h2:mem:test");
        }
        if (!"spring.datasource.operational.driver-class-name".equals(propertyToClear)) {
            env.setProperty("spring.datasource.operational.driver-class-name", "org.h2.Driver");
        }
        if (!"spring.datasource.config.url".equals(propertyToClear)) {
            env.setProperty("spring.datasource.config.url", "jdbc:h2:mem:test_cfg");
        }
        if (!"spring.datasource.config.driver-class-name".equals(propertyToClear)) {
            env.setProperty("spring.datasource.config.driver-class-name", "org.h2.Driver");
        }
        if (!"security.jwt.secret".equals(propertyToClear)) {
            env.setProperty("security.jwt.secret", "secret");
        }
        if (!"security.jwt.expiration-minutes".equals(propertyToClear)) {
            env.setProperty("security.jwt.expiration-minutes", "120");
        }
        if (!"ticketing.persistence".equals(propertyToClear)) {
            env.setProperty("ticketing.persistence", "memory");
        }
        if (!"ticketing.queue.threshold".equals(propertyToClear)) {
            env.setProperty("ticketing.queue.threshold", "100");
        }
        if (!"ticketing.queue.flow-rate".equals(propertyToClear)) {
            env.setProperty("ticketing.queue.flow-rate", "10");
        }
        if (!"ticketing.external.base-url".equals(propertyToClear)) {
            env.setProperty("ticketing.external.base-url", "http://localhost:8080");
        }
        if (!"ticketing.external.connect-timeout-ms".equals(propertyToClear)) {
            env.setProperty("ticketing.external.connect-timeout-ms", "5000");
        }
        if (!"ticketing.external.read-timeout-ms".equals(propertyToClear)) {
            env.setProperty("ticketing.external.read-timeout-ms", "5000");
        }
        if (!"ticketing.external.payment.currency".equals(propertyToClear)) {
            env.setProperty("ticketing.external.payment.currency", "USD");
        }
        if (!"ticketing.external.payment.card-number".equals(propertyToClear)) {
            env.setProperty("ticketing.external.payment.card-number", "2222333344445555");
        }
        if (!"ticketing.external.payment.card-month".equals(propertyToClear)) {
            env.setProperty("ticketing.external.payment.card-month", "12");
        }
        if (!"ticketing.external.payment.card-year".equals(propertyToClear)) {
            env.setProperty("ticketing.external.payment.card-year", "2030");
        }
        if (!"ticketing.external.payment.card-holder".equals(propertyToClear)) {
            env.setProperty("ticketing.external.payment.card-holder", "Ticketing System");
        }
        if (!"ticketing.external.payment.card-cvv".equals(propertyToClear)) {
            env.setProperty("ticketing.external.payment.card-cvv", "123");
        }
        if (!"ticketing.external.payment.card-id".equals(propertyToClear)) {
            env.setProperty("ticketing.external.payment.card-id", "000000000");
        }
        if (!"ticketing.bootstrap.dataset".equals(propertyToClear)) {
            env.setProperty("ticketing.bootstrap.dataset", "dev-seed");
        }
        if (!"ticketing.admin.username".equals(propertyToClear)) {
            env.setProperty("ticketing.admin.username", "admin");
        }
        if (!"ticketing.admin.password".equals(propertyToClear)) {
            env.setProperty("ticketing.admin.password", "password");
        }

        if (propertyToSet != null) {
            env.setProperty(propertyToSet, valueToSet);
        }

        StartupHaltException exception =
                assertThrows(StartupHaltException.class, () -> TicketingConfigurationRules.validate(env));
        assertTrue(exception.getMessage().contains(expectedMessagePart),
                "Expected message to contain '" + expectedMessagePart + "' but was '" + exception.getMessage() + "'");
        assertTrue(exception.getMessage().contains("APPLICATION STARTUP HALTED"));
    }

    @ParameterizedTest
    @MethodSource("invalidJpaConfigurations")
    void testInvalidJpaConfiguration(String propertyToSet, String valueToSet, String propertyToClear, String expectedMessagePart) {
        MockEnvironment env = new MockEnvironment();
        applyCommonValidSettings(env);
        env.setProperty("ticketing.persistence", "jpa");
        if (!"spring.datasource.operational.username".equals(propertyToClear)) {
            env.setProperty("spring.datasource.operational.username", "sa");
        }
        if (!"spring.datasource.operational.password".equals(propertyToClear)) {
            env.setProperty("spring.datasource.operational.password", "");
        }
        if (!"spring.datasource.config.username".equals(propertyToClear)) {
            env.setProperty("spring.datasource.config.username", "sa");
        }
        if (!"spring.datasource.config.password".equals(propertyToClear)) {
            env.setProperty("spring.datasource.config.password", "");
        }
        if (!"spring.jpa.database-platform".equals(propertyToClear)) {
            env.setProperty("spring.jpa.database-platform", "org.hibernate.dialect.H2Dialect");
        }
        if (!"spring.jpa.hibernate.ddl-auto".equals(propertyToClear)) {
            env.setProperty("spring.jpa.hibernate.ddl-auto", "create-drop");
        }
        if (propertyToSet != null) {
            env.setProperty(propertyToSet, valueToSet);
        }

        StartupHaltException exception =
                assertThrows(StartupHaltException.class, () -> TicketingConfigurationRules.validate(env));
        assertTrue(exception.getMessage().contains(expectedMessagePart),
                "Expected message to contain '" + expectedMessagePart + "' but was '" + exception.getMessage() + "'");
    }

    private static Stream<Arguments> invalidConfigurations() {
        return Stream.of(
            Arguments.of(null, null, "spring.datasource.operational.url", "spring.datasource.operational.url"),
            Arguments.of(null, null, "spring.datasource.operational.driver-class-name", "spring.datasource.operational.driver-class-name"),
            Arguments.of(null, null, "spring.datasource.config.url", "spring.datasource.config.url"),
            Arguments.of(null, null, "spring.datasource.config.driver-class-name", "spring.datasource.config.driver-class-name"),
            Arguments.of(null, null, "security.jwt.secret", "security.jwt.secret"),
            Arguments.of(null, null, "security.jwt.expiration-minutes", "security.jwt.expiration-minutes"),
            Arguments.of(null, null, "ticketing.queue.threshold", "ticketing.queue.threshold"),
            Arguments.of(null, null, "ticketing.queue.flow-rate", "ticketing.queue.flow-rate"),
            Arguments.of(null, null, "ticketing.admin.username", "ticketing.admin.username"),
            Arguments.of(null, null, "ticketing.admin.password", "ticketing.admin.password"),
            Arguments.of(null, null, "ticketing.persistence", "ticketing.persistence"),
            Arguments.of(null, null, "ticketing.external.base-url", "ticketing.external.base-url"),
            Arguments.of(null, null, "ticketing.bootstrap.dataset", "ticketing.bootstrap.dataset"),
            Arguments.of(null, null, "ticketing.external.connect-timeout-ms", "ticketing.external.connect-timeout-ms"),
            Arguments.of(null, null, "ticketing.external.read-timeout-ms", "ticketing.external.read-timeout-ms"),

            Arguments.of("ticketing.persistence", "redis", null, "Expected: 'memory' or 'jpa'"),
            Arguments.of("ticketing.queue.threshold", "abc", null, "Expected: positive integer"),
            Arguments.of("ticketing.queue.threshold", "-5", null, "Expected: positive integer"),
            Arguments.of("ticketing.queue.flow-rate", "0", null, "Expected: positive integer"),
            Arguments.of("ticketing.queue.flow-rate", "5.5", null, "Expected: positive integer"),
            Arguments.of("ticketing.external.connect-timeout-ms", "0", null, "Expected: positive integer"),
            Arguments.of("ticketing.external.read-timeout-ms", "-1", null, "Expected: positive integer"),
            Arguments.of("ticketing.bootstrap.dataset", "invalid", null, "Expected: 'dev-seed', 'initial-state-file', or 'none'"),
            Arguments.of("security.jwt.expiration-minutes", "abc", null, "Expected: positive integer"),
            Arguments.of("ticketing.admin.password", "short", null, "at least 6 characters"),
            Arguments.of("ticketing.bootstrap.dataset", "initial-state-file", null, "ticketing.initial-state.file"),
            Arguments.of("ticketing.external.payment.card-month", "13", null, "integer from 1 to 12"),
            Arguments.of("ticketing.external.payment.card-year", "1999", null, "integer year >= 2000"),
            Arguments.of("spring.datasource.operational.url", "", null, "spring.datasource.operational.url"),
            Arguments.of("ticketing.admin.password", "", null, "ticketing.admin.password"),
            Arguments.of("security.jwt.expiration-minutes", "", null, "security.jwt.expiration-minutes"),
            Arguments.of("ticketing.external.payment.card-month", "0", null, "positive integer"),
            Arguments.of("ticketing.external.payment.card-holder", "", null, "ticketing.external.payment.card-holder"),
            Arguments.of("ticketing.external.payment.card-cvv", "", null, "ticketing.external.payment.card-cvv"),
            Arguments.of("ticketing.external.payment.card-id", "", null, "ticketing.external.payment.card-id"),
            Arguments.of("ticketing.external.payment.currency", "", null, "ticketing.external.payment.currency"),
            Arguments.of("ticketing.external.payment.card-number", "", null, "ticketing.external.payment.card-number"),
            Arguments.of("ticketing.external.payment.card-year", "", null, "ticketing.external.payment.card-year")
        );
    }

    private static Stream<Arguments> invalidJpaConfigurations() {
        return Stream.of(
            Arguments.of(null, null, "spring.datasource.operational.username", "spring.datasource.operational.username"),
            Arguments.of(null, null, "spring.jpa.database-platform", "spring.jpa.database-platform"),
            Arguments.of(null, null, "spring.jpa.hibernate.ddl-auto", "spring.jpa.hibernate.ddl-auto"),
            Arguments.of("spring.jpa.hibernate.ddl-auto", "bogus", null, "Expected: 'none', 'validate'"),
            Arguments.of("spring.datasource.operational.username", "", null, "spring.datasource.operational.username"),
            Arguments.of("spring.jpa.hibernate.ddl-auto", "   ", null, "spring.jpa.hibernate.ddl-auto"),
            Arguments.of(null, null, "spring.datasource.operational.password", "spring.datasource.operational.password"),
            Arguments.of(null, null, "spring.datasource.config.password", "spring.datasource.config.password"),
            Arguments.of(null, null, "spring.datasource.config.username", "spring.datasource.config.username")
        );
    }
}
