package com.ticketing.application.initialization;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class DatabaseConnectivityPreflightTest {

    @Test
    void verifySkipsWhenPersistenceIsNotJpa() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("ticketing.persistence", "memory")
                .withProperty("spring.datasource.operational.url", "jdbc:nodriverxyz://example:5432/ticketing");

        assertDoesNotThrow(() -> DatabaseConnectivityPreflight.verify(env));
    }

    @Test
    void verifySkipsH2UrlsInJpaMode() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("ticketing.persistence", "jpa")
                .withProperty("spring.datasource.operational.url", "jdbc:h2:mem:ticketing")
                .withProperty("spring.datasource.config.url", "jdbc:h2:mem:ticketing_cfg");

        assertDoesNotThrow(() -> DatabaseConnectivityPreflight.verify(env));
    }

    @Test
    void verifySkipsMissingUrlsInJpaMode() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("ticketing.persistence", "jpa");

        assertDoesNotThrow(() -> DatabaseConnectivityPreflight.verify(env));
    }

    @Test
    void verifyHaltsWithHelpfulMessageWhenDriverDoesNotMatchUrl() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("ticketing.persistence", "jpa")
                .withProperty("ticketing.external.connect-timeout-ms", "1000")
                .withProperty("spring.datasource.operational.url", "jdbc:nodriverxyz://db-host:5432/ticketing")
                .withProperty("spring.datasource.operational.username", "ticketing")
                .withProperty("spring.datasource.operational.password", "secret")
                .withProperty("spring.datasource.config.url", "jdbc:h2:mem:ticketing_cfg");

        StartupHaltException ex = assertThrows(
                StartupHaltException.class,
                () -> DatabaseConnectivityPreflight.verify(env));

        assertTrue(ex.getMessage().contains("DATABASE CONNECTION ERROR"));
        assertTrue(ex.getMessage().contains("operational database"));
        assertTrue(ex.getMessage().contains("db-host:5432"));
        assertTrue(ex.getMessage().contains("DB_DRIVER does not match"));
    }

    @Test
    void diagnoseWrongPassword() {
        String result = DatabaseConnectivityPreflight.diagnose(
                "28P01",
                "FATAL: password authentication failed for user ticketing");

        assertTrue(result.contains("wrong credentials"));
        assertTrue(result.contains("DB_USERNAME"));
        assertTrue(result.contains("DB_PASSWORD"));
    }

    @Test
    void diagnoseMissingDatabase() {
        String result = DatabaseConnectivityPreflight.diagnose(
                "3D000",
                "database \"ticketing_cfg\" does not exist");

        assertTrue(result.contains("database name does not exist"));
        assertTrue(result.contains("gcloud sql databases create"));
    }

    @Test
    void diagnoseWrongDriver() {
        String result = DatabaseConnectivityPreflight.diagnose(
                null,
                "No suitable driver found for jdbc:postgresql://host:5432/ticketing");

        assertTrue(result.contains("DB_DRIVER does not match"));
    }

    @Test
    void diagnoseNetworkTimeoutByMessage() {
        String result = DatabaseConnectivityPreflight.diagnose(
                null,
                "Connection timed out");

        assertTrue(result.contains("Authorized networks"));
    }

    @Test
    void diagnoseNetworkBySqlState08001() {
        String result = DatabaseConnectivityPreflight.diagnose(
                "08001",
                "could not connect");

        assertTrue(result.contains("Authorized networks"));
    }

    @Test
    void diagnoseNetworkBySqlState08006() {
        String result = DatabaseConnectivityPreflight.diagnose(
                "08006",
                "connection failure");

        assertTrue(result.contains("Authorized networks"));
    }

    @Test
    void diagnoseFallbackMessage() {
        String result = DatabaseConnectivityPreflight.diagnose(
                "99999",
                "some unknown database problem");

        assertTrue(result.contains("Check: instance is running"));
    }

    @Test
    void diagnoseHandlesNullMessage() {
        String result = DatabaseConnectivityPreflight.diagnose(
                "99999",
                null);

        assertTrue(result.contains("Check: instance is running"));
    }

    @Test
    void extractHostAndPortFromJdbcUrl() {
        assertEquals(
                "34.66.18.105:5432",
                DatabaseConnectivityPreflight.extractHostPort(
                        "jdbc:postgresql://34.66.18.105:5432/ticketing"));
    }

    @Test
    void extractHostPortFallsBackToWholeUrlWhenPatternDoesNotMatch() {
        assertEquals(
                "jdbc:h2:mem:ticketing",
                DatabaseConnectivityPreflight.extractHostPort("jdbc:h2:mem:ticketing"));
    }
}
