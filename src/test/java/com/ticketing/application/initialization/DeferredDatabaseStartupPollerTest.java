package com.ticketing.application.initialization;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import com.ticketing.application.scheduling.LotteryDrawScheduler;
import com.ticketing.infrastructure.persistence.DatabaseConnectivityProbe;

@DisplayName("DeferredDatabaseStartupPoller")
class DeferredDatabaseStartupPollerTest {

    private DatabaseConnectivityProbe connectivityProbe;
    private DataBootstrapRunner bootstrapRunner;
    private LotteryDrawScheduler lotteryDrawScheduler;
    private ObjectProvider<LotteryDrawScheduler> lotteryDrawSchedulerProvider;
    private DeferredDatabaseStartupPoller poller;

    @BeforeEach
    void setUp() {
        connectivityProbe = mock(DatabaseConnectivityProbe.class);
        bootstrapRunner = mock(DataBootstrapRunner.class);
        lotteryDrawScheduler = mock(LotteryDrawScheduler.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<LotteryDrawScheduler> provider = mock(ObjectProvider.class);
        lotteryDrawSchedulerProvider = provider;
        when(provider.getObject()).thenReturn(lotteryDrawScheduler);
        poller = new DeferredDatabaseStartupPoller(connectivityProbe, bootstrapRunner, lotteryDrawSchedulerProvider);
    }

    @Test
    @DisplayName("Unreachable database skips bootstrap retry and lottery rearm")
    void givenDbUnreachable_whenPoll_thenNoRecoveryWork() {
        when(connectivityProbe.isReachable()).thenReturn(false);

        poller.pollForDatabaseRecovery();

        verify(bootstrapRunner, never()).retryWhenDatabaseAvailable();
        verify(lotteryDrawSchedulerProvider, never()).getObject();
    }

    @Test
    @DisplayName("Reachable database with pending bootstrap completes deferred startup")
    void givenDbReachableWithPendingWork_whenPoll_thenRetriesBootstrapAndLotteryRearm() {
        when(connectivityProbe.isReachable()).thenReturn(true);
        when(bootstrapRunner.hasPendingWork()).thenReturn(true);

        poller.pollForDatabaseRecovery();

        verify(bootstrapRunner).retryWhenDatabaseAvailable();
        verify(lotteryDrawScheduler).tryReschedulePendingStartup();
    }

    @Test
    @DisplayName("Reachable database without pending bootstrap still rearms lottery scheduler")
    void givenDbReachableWithoutPendingWork_whenPoll_thenOnlyLotteryRearm() {
        when(connectivityProbe.isReachable()).thenReturn(true);
        when(bootstrapRunner.hasPendingWork()).thenReturn(false);

        poller.pollForDatabaseRecovery();

        verify(bootstrapRunner, never()).retryWhenDatabaseAvailable();
        verify(lotteryDrawScheduler).tryReschedulePendingStartup();
    }

    @Test
    @DisplayName("Database recovery transition is logged on unreachable-to-reachable poll")
    void givenDbRecovers_whenPollTwice_thenCompletesDeferredWork() {
        when(connectivityProbe.isReachable()).thenReturn(false, true);
        when(bootstrapRunner.hasPendingWork()).thenReturn(false);

        poller.pollForDatabaseRecovery();
        poller.pollForDatabaseRecovery();

        verify(lotteryDrawScheduler).tryReschedulePendingStartup();
    }
}
