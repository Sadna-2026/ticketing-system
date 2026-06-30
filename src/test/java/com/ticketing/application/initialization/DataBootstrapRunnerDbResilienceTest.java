package com.ticketing.application.initialization;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.transaction.CannotCreateTransactionException;

import com.ticketing.application.services.PlatformInitializationService;
import com.ticketing.infrastructure.persistence.DatabaseConnectivityProbe;

@DisplayName("DataBootstrapRunner DB resilience (req 5)")
class DataBootstrapRunnerDbResilienceTest {

    @Test
    @DisplayName("Platform init DB failure is deferred — boot continues with pending work")
    void givenDbDownDuringPlatformInit_whenRunAtStartup_thenPendingWorkRemains() {
        PlatformInitializationService platformInit = mock(PlatformInitializationService.class);
        when(platformInit.initialize()).thenThrow(new CannotCreateTransactionException("no connection"));

        DataBootstrapRunner runner = newRunner(true, false, "none", platformInit, mock(OperationalDataWiper.class));

        assertDoesNotThrow(runner::runAtStartup);
        assertTrue(runner.hasPendingWork());
    }

    @Test
    @DisplayName("Operational wipe DB failure is deferred when clear-db-on-start is set")
    void givenDbDownDuringWipe_whenRunAtStartup_thenPendingWorkRemains() {
        OperationalDataWiper wiper = mock(OperationalDataWiper.class);
        doThrow(new DataAccessResourceFailureException("db gone")).when(wiper).wipeAll();

        PlatformInitializationService platformInit = mock(PlatformInitializationService.class);
        when(platformInit.initialize()).thenReturn(PlatformInitializationService.InitializationResult.success("ok"));

        DataBootstrapRunner runner = newRunner(false, true, "none", platformInit, wiper);

        assertDoesNotThrow(runner::runAtStartup);
        assertTrue(runner.hasPendingWork());
        verify(wiper).wipeAll();
    }

    @Test
    @DisplayName("Missing-table DB error during platform init is deferred — boot continues")
    void givenMissingTableDuringPlatformInit_whenRunAtStartup_thenPendingWorkRemains() {
        PlatformInitializationService platformInit = mock(PlatformInitializationService.class);
        when(platformInit.initialize()).thenThrow(new InvalidDataAccessResourceUsageException(
                "relation admin does not exist",
                new org.hibernate.exception.SQLGrammarException(
                        "relation admin does not exist",
                        new java.sql.SQLException("ERROR: relation \"admin\" does not exist"))));

        DataBootstrapRunner runner = newRunner(true, false, "none", platformInit, mock(OperationalDataWiper.class));

        assertDoesNotThrow(runner::runAtStartup);
        assertTrue(runner.hasPendingWork());
    }

    private static DataBootstrapRunner newRunner(
            boolean initializePlatform,
            boolean clearDbOnStart,
            String dataset,
            PlatformInitializationService platformInit,
            OperationalDataWiper wiper) {
        DatabaseConnectivityProbe probe = mock(DatabaseConnectivityProbe.class);
        when(probe.isReachable()).thenReturn(true);
        @SuppressWarnings("unchecked")
        ObjectProvider<DatabaseConnectivityProbe> probeProvider = mock(ObjectProvider.class);
        when(probeProvider.getIfAvailable()).thenReturn(probe);

        return new DataBootstrapRunner(
                initializePlatform,
                clearDbOnStart,
                dataset,
                false,
                "",
                platformInit,
                mock(DevSeedDataInitializer.class),
                mock(InitialStateExecutor.class),
                wiper,
                probeProvider);
    }
}
