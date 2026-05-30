package com.ticketing.application;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.ticketing.domain.event.EventCategory;
import com.ticketing.domain.event.EventSchedule;
import com.ticketing.domain.event.LockTimerDuration;
import com.ticketing.domain.event.LotteryWindow;
import com.ticketing.domain.event.SaleMethod;

class ApplicationRequestValidationTest {

    @Test
    void GivenValidCreateEventRequest_WhenConstructed_ThenDefaultsToRegularSaleAndCopiesCollections() {
        List<CreateEventRequest.ZoneSpec> zones = new ArrayList<>();
        zones.add(new CreateEventRequest.GAZoneSpec("General", BigDecimal.valueOf(80), 100));
        Map<String, String> sectionToZoneName = new HashMap<>();
        sectionToZoneName.put("GA", "General");

        CreateEventRequest request = new CreateEventRequest(
                "Acme",
                "Spring Concert",
                "desc",
                EventCategory.CONCERT,
                validSchedule(),
                validLockTimer(),
                zones,
                sectionToZoneName);

        zones.add(new CreateEventRequest.GAZoneSpec("Late", BigDecimal.TEN, 1));
        sectionToZoneName.put("Late", "Late");

        assertAll(
                () -> assertEquals(SaleMethod.REGULAR, request.saleMethod()),
                () -> assertEquals(1, request.zones().size()),
                () -> assertEquals(1, request.sectionToZoneName().size()),
                () -> assertThrows(UnsupportedOperationException.class,
                        () -> request.zones().add(new CreateEventRequest.GAZoneSpec("Blocked", BigDecimal.ONE, 1))),
                () -> assertThrows(UnsupportedOperationException.class,
                        () -> request.sectionToZoneName().put("Blocked", "Blocked"))
        );
    }

    @Test
    void GivenLotterySaleWithoutWindow_WhenConstructCreateEventRequest_ThenThrows() {
        assertThrows(IllegalArgumentException.class, () -> new CreateEventRequest(
                "Acme",
                "Lottery Show",
                "desc",
                EventCategory.CONCERT,
                validSchedule(),
                validLockTimer(),
                List.of(new CreateEventRequest.GAZoneSpec("General", BigDecimal.valueOf(80), 100)),
                Map.of("GA", "General"),
                SaleMethod.LOTTERY,
                null));
    }

    @Test
    void GivenLotterySaleWithWindow_WhenConstructCreateEventRequest_ThenAccepted() {
        Instant now = Instant.now();
        CreateEventRequest request = new CreateEventRequest(
                "Acme",
                "Lottery Show",
                "desc",
                EventCategory.CONCERT,
                validSchedule(),
                validLockTimer(),
                List.of(new CreateEventRequest.GAZoneSpec("General", BigDecimal.valueOf(80), 100)),
                Map.of("GA", "General"),
                SaleMethod.LOTTERY,
                new LotteryWindow(now, now.plusSeconds(3600)));

        assertEquals(SaleMethod.LOTTERY, request.saleMethod());
        assertNotNull(request.lotteryWindow());
    }

    @Test
    void GivenInvalidCreateEventRequestParts_WhenConstructed_ThenValidationRejectsThem() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new CreateEventRequest.GAZoneSpec(" ", BigDecimal.ONE, 1)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new CreateEventRequest.GAZoneSpec("GA", BigDecimal.valueOf(-1), 1)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new CreateEventRequest.GAZoneSpec("GA", BigDecimal.ONE, 0)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new CreateEventRequest.SeatSpec(" ", "1")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new CreateEventRequest.SeatSpec("A", " ")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new CreateEventRequest.AssignedZoneSpec("Assigned", BigDecimal.ONE, List.of())),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new CreateEventRequest(" ", "Event", "desc", EventCategory.CONCERT,
                                validSchedule(), validLockTimer(),
                                List.of(new CreateEventRequest.GAZoneSpec("GA", BigDecimal.ONE, 1)),
                                Map.of("GA", "GA"))),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new CreateEventRequest("Acme", " ", "desc", EventCategory.CONCERT,
                                validSchedule(), validLockTimer(),
                                List.of(new CreateEventRequest.GAZoneSpec("GA", BigDecimal.ONE, 1)),
                                Map.of("GA", "GA"))),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new CreateEventRequest("Acme", "Event", "desc", EventCategory.CONCERT,
                                null, validLockTimer(),
                                List.of(new CreateEventRequest.GAZoneSpec("GA", BigDecimal.ONE, 1)),
                                Map.of("GA", "GA"))),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new CreateEventRequest("Acme", "Event", "desc", EventCategory.CONCERT,
                                validSchedule(), null,
                                List.of(new CreateEventRequest.GAZoneSpec("GA", BigDecimal.ONE, 1)),
                                Map.of("GA", "GA"))),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new CreateEventRequest("Acme", "Event", "desc", EventCategory.CONCERT,
                                validSchedule(), validLockTimer(), List.of(), Map.of("GA", "GA"))),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new CreateEventRequest("Acme", "Event", "desc", EventCategory.CONCERT,
                                validSchedule(), validLockTimer(),
                                List.of(new CreateEventRequest.GAZoneSpec("GA", BigDecimal.ONE, 1)),
                                Map.of()))
        );
    }

    @Test
    void GivenSelectionRequest_WhenConstructed_ThenNullListsBecomeEmptyAndDuplicatesAreRejected() {
        UUID eventId = UUID.randomUUID();
        SelectionRequest empty = new SelectionRequest(eventId, null, null);
        assertTrue(empty.isEmpty());

        UUID zoneId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();
        SelectionRequest selected = new SelectionRequest(
                eventId,
                List.of(new SelectionRequest.SeatPick(zoneId, seatId)),
                List.of(new SelectionRequest.GAPick(zoneId, 2)));

        assertFalse(selected.isEmpty());
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new SelectionRequest(null, null, null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new SelectionRequest.SeatPick(null, seatId)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new SelectionRequest.SeatPick(zoneId, null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new SelectionRequest.GAPick(null, 1)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new SelectionRequest.GAPick(zoneId, 0)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new SelectionRequest(eventId,
                                List.of(new SelectionRequest.SeatPick(zoneId, seatId),
                                        new SelectionRequest.SeatPick(zoneId, seatId)),
                                List.of()))
        );
    }

    @Test
    void GivenEditAndSearchRequests_WhenConstructed_ThenValidationAndHelpersWork() {
        UUID eventId = UUID.randomUUID();
        EditEventRequest noChanges = new EditEventRequest(eventId, null, null, null, null);
        EditEventRequest withChange = new EditEventRequest(eventId, "New name", null, null, null);

        assertAll(
                () -> assertFalse(noChanges.hasAnyChange()),
                () -> assertTrue(withChange.hasAnyChange()),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new EditEventRequest(null, null, null, null, null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new EditEventRequest(eventId, " ", null, null, null)),
                () -> assertNotNull(SearchEventsRequest.empty()),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new SearchEventsRequest(null, null, null, null,
                                BigDecimal.TEN, BigDecimal.ONE, null, null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new SearchEventsRequest(null, null, null, null,
                                null, null, Instant.parse("2026-01-02T00:00:00Z"),
                                Instant.parse("2026-01-01T00:00:00Z")))
        );
    }

    @Test
    void GivenMarketInitializationResponseFactories_WhenCalled_ThenResponsesReflectSuccessState() {
        MarketInitializationResponse success = MarketInitializationResponse.success("ok");
        MarketInitializationResponse failure = MarketInitializationResponse.failure("bad");

        assertAll(
                () -> assertTrue(success.success()),
                () -> assertEquals("ok", success.message()),
                () -> assertFalse(failure.success()),
                () -> assertEquals("bad", failure.message())
        );
    }

    @Test
    void GivenTestClock_WhenAdvancedOrSet_ThenNowReflectsConfiguredInstant() {
        Instant base = Instant.parse("2026-01-01T00:00:00Z");
        TestClock clock = new TestClock(base);

        clock.advance(Duration.ofMinutes(5));
        assertEquals(base.plus(Duration.ofMinutes(5)), clock.now());

        Instant replacement = Instant.parse("2026-01-02T00:00:00Z");
        clock.setTime(replacement);
        assertEquals(replacement, clock.now());
    }

    @Test
    void GivenStringDateRequestConstructors_WhenUsed_ThenDatesAreParsed() {
        assertEquals(LocalDate.of(2000, 1, 2),
                new com.ticketing.domain.member.request.RegisterRequest(
                        "user", "user@example.com", "pass", "050", "2000-01-02")
                        .dateOfBirth());

        assertEquals(LocalDate.of(2001, 3, 4),
                new com.ticketing.domain.member.request.UpdateMemberDetailsRequest(
                        "user", "user@example.com", "050", "2001-03-04")
                        .dateOfBirth());
    }

    private static EventSchedule validSchedule() {
        Instant start = Instant.now().plus(Duration.ofDays(10));
        return new EventSchedule(start, start.plus(Duration.ofHours(2)), start.minus(Duration.ofHours(1)));
    }

    private static LockTimerDuration validLockTimer() {
        return new LockTimerDuration(Duration.ofMinutes(15));
    }
}
