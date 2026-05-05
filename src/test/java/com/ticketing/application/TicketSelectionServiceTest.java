package com.ticketing.application;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ticketing.domain.event.Event;
import com.ticketing.domain.event.EventCategory;
import com.ticketing.domain.event.EventSchedule;
import com.ticketing.domain.event.InventoryZone;
import com.ticketing.domain.event.LockTimerDuration;
import com.ticketing.domain.event.Seat;
import com.ticketing.infrastructure.InMemoryEventRepository;

public class TicketSelectionServiceTest {

    private static final String COMPANY = "Acme Productions";

    private InMemoryEventRepository eventRepo;
    private TicketSelectionService service;

    private UUID eventId;
    private UUID gaZoneId;
    private UUID assignedZoneId;
    private UUID seatA1;
    private UUID seatA2;
    private UUID seatA3;

    @BeforeEach
    public void setUp() {
        eventRepo = new InMemoryEventRepository();
        service = new TicketSelectionService(eventRepo);

        eventId = UUID.randomUUID();
        Instant start = Instant.now().plus(30, ChronoUnit.DAYS);
        Event e = new Event(eventId, COMPANY, "Concert", "desc", EventCategory.CONCERT,
                new EventSchedule(start, start.plus(2, ChronoUnit.HOURS), start.minus(1, ChronoUnit.HOURS)),
                new LockTimerDuration(Duration.ofMinutes(15)));

        gaZoneId = UUID.randomUUID();
        e.addZone(InventoryZone.createGA(gaZoneId, "Floor", new BigDecimal("50.00"), 100));

        assignedZoneId = UUID.randomUUID();
        InventoryZone vip = InventoryZone.createAssigned(assignedZoneId, "VIP", new BigDecimal("150.00"));
        seatA1 = UUID.randomUUID();
        seatA2 = UUID.randomUUID();
        seatA3 = UUID.randomUUID();
        vip.addSeat(new Seat(seatA1, "A", "1"));
        vip.addSeat(new Seat(seatA2, "A", "2"));
        vip.addSeat(new Seat(seatA3, "A", "3"));
        e.addZone(vip);

        e.publish();
        eventRepo.save(e);
    }

    @Test
    public void GivenValidSeats_WhenValidateSelection_ThenAccepted() {
        SelectionRequest req = new SelectionRequest(eventId,
                List.of(new SelectionRequest.SeatPick(assignedZoneId, seatA1),
                        new SelectionRequest.SeatPick(assignedZoneId, seatA2)),
                List.of());

        assertDoesNotThrow(() -> service.validateSelection(req));
    }

    @Test
    public void GivenValidGAQuantity_WhenValidateSelection_ThenAccepted() {
        SelectionRequest req = new SelectionRequest(eventId,
                List.of(),
                List.of(new SelectionRequest.GAPick(gaZoneId, 5)));

        assertDoesNotThrow(() -> service.validateSelection(req));
    }

    @Test
    public void GivenMixedSelection_WhenValidateSelection_ThenAccepted() {
        SelectionRequest req = new SelectionRequest(eventId,
                List.of(new SelectionRequest.SeatPick(assignedZoneId, seatA1)),
                List.of(new SelectionRequest.GAPick(gaZoneId, 2)));

        assertDoesNotThrow(() -> service.validateSelection(req));
    }

    @Test
    public void GivenNonexistentSeat_WhenValidateSelection_ThenThrowIllegalArgumentException() {
        SelectionRequest req = new SelectionRequest(eventId,
                List.of(new SelectionRequest.SeatPick(assignedZoneId, UUID.randomUUID())),
                List.of());

        assertThrows(IllegalArgumentException.class, () -> service.validateSelection(req));
    }

    @Test
    public void GivenSoldSeat_WhenValidateSelection_ThenThrowIllegalStateException() {
        InventoryZone vip = eventRepo.findById(eventId).orElseThrow().findZone(assignedZoneId);
        vip.lockSeat(seatA1);
        vip.sellSeat(seatA1);
        eventRepo.save(eventRepo.findById(eventId).orElseThrow());

        SelectionRequest req = new SelectionRequest(eventId,
                List.of(new SelectionRequest.SeatPick(assignedZoneId, seatA1)),
                List.of());

        assertThrows(IllegalStateException.class, () -> service.validateSelection(req));
    }

    @Test
    public void GivenLockedSeat_WhenValidateSelection_ThenThrowIllegalStateException() {
        InventoryZone vip = eventRepo.findById(eventId).orElseThrow().findZone(assignedZoneId);
        vip.lockSeat(seatA2);
        eventRepo.save(eventRepo.findById(eventId).orElseThrow());

        SelectionRequest req = new SelectionRequest(eventId,
                List.of(new SelectionRequest.SeatPick(assignedZoneId, seatA2)),
                List.of());

        assertThrows(IllegalStateException.class, () -> service.validateSelection(req));
    }

    @Test
    public void GivenZoneNotInEvent_WhenValidateSelection_ThenThrowIllegalArgumentException() {
        // a random zone id that doesn't belong to any zone of the event
        SelectionRequest req = new SelectionRequest(eventId,
                List.of(),
                List.of(new SelectionRequest.GAPick(UUID.randomUUID(), 1)));

        assertThrows(IllegalArgumentException.class, () -> service.validateSelection(req));
    }

    @Test
    public void GivenSeatPickOnGAZone_WhenValidateSelection_ThenThrowIllegalArgumentException() {
        SelectionRequest req = new SelectionRequest(eventId,
                List.of(new SelectionRequest.SeatPick(gaZoneId, UUID.randomUUID())),
                List.of());

        assertThrows(IllegalArgumentException.class, () -> service.validateSelection(req));
    }

    @Test
    public void GivenGAPickOnAssignedZone_WhenValidateSelection_ThenThrowIllegalArgumentException() {
        SelectionRequest req = new SelectionRequest(eventId,
                List.of(),
                List.of(new SelectionRequest.GAPick(assignedZoneId, 1)));

        assertThrows(IllegalArgumentException.class, () -> service.validateSelection(req));
    }

    @Test
    public void GivenGAQuantityExceedsAvailable_WhenValidateSelection_ThenThrowIllegalStateException() {
        SelectionRequest req = new SelectionRequest(eventId,
                List.of(),
                List.of(new SelectionRequest.GAPick(gaZoneId, 999)));

        assertThrows(IllegalStateException.class, () -> service.validateSelection(req));
    }

    @Test
    public void GivenUnknownEvent_WhenValidateSelection_ThenThrowIllegalArgumentException() {
        SelectionRequest req = new SelectionRequest(UUID.randomUUID(),
                List.of(),
                List.of(new SelectionRequest.GAPick(gaZoneId, 1)));

        assertThrows(IllegalArgumentException.class, () -> service.validateSelection(req));
    }

    @Test
    public void GivenCancelledEvent_WhenValidateSelection_ThenThrowIllegalStateException() {
        Event e = eventRepo.findById(eventId).orElseThrow();
        e.cancel();
        eventRepo.save(e);

        SelectionRequest req = new SelectionRequest(eventId,
                List.of(),
                List.of(new SelectionRequest.GAPick(gaZoneId, 1)));

        assertThrows(IllegalStateException.class, () -> service.validateSelection(req));
    }

    @Test
    public void GivenEmptySelection_WhenValidateSelection_ThenThrowIllegalArgumentException() {
        SelectionRequest req = new SelectionRequest(eventId, List.of(), List.of());

        assertThrows(IllegalArgumentException.class, () -> service.validateSelection(req));
    }

    @Test
    public void GivenNullRequest_WhenValidateSelection_ThenThrowIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> service.validateSelection(null));
    }
}
