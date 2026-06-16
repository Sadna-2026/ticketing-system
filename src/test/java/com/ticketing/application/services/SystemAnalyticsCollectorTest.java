package com.ticketing.application.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ticketing.application.TestClock;
import com.ticketing.application.services.SystemAnalyticsCollector.Snapshot;

@DisplayName("SystemAnalyticsCollector")
class SystemAnalyticsCollectorTest {

    private TestClock clock;
    private SystemAnalyticsCollector collector;

    @BeforeEach
    void setUp() {
        clock = new TestClock(Instant.parse("2026-06-01T12:00:00Z"));
        collector = new SystemAnalyticsCollector(clock);
    }

    @Test
    void GivenEventsRecorded_WhenSnapshotTaken_ThenLiveAndHistoricalRatesAreAvailable() {
        collector.recordVisitorEnter();
        collector.recordVisitorEnter();
        collector.recordVisitorExit();
        collector.recordRegistration();
        collector.recordReservation(2);
        collector.recordPurchase(1);

        Snapshot snapshot = collector.snapshot();

        assertEquals(1, snapshot.activeVisitors());
        assertEquals(SystemAnalyticsCollector.LIVE_WINDOW, snapshot.liveWindow());
        assertTrue(snapshot.historical().visitorEnter().count() >= 2);
        assertEquals(2, snapshot.historical().reservation().count());
        assertEquals(1, snapshot.historical().purchase().count());
        assertTrue(snapshot.live().visitorEnter().perMinute() > 0);
    }

    @Test
    void GivenOldEvents_WhenSnapshotTakenAfterLiveWindowPasses_ThenLiveCountsDropButHistoricalRemains() {
        collector.recordVisitorEnter();
        clock.advance(Duration.ofMinutes(6));

        Snapshot snapshot = collector.snapshot();

        assertEquals(0, snapshot.live().visitorEnter().count());
        assertEquals(1, snapshot.historical().visitorEnter().count());
    }
}
