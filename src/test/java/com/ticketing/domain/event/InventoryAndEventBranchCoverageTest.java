package com.ticketing.domain.event;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class InventoryAndEventBranchCoverageTest {

    @Test
    void GivenInvalidSchedulesLockTimersAndLotteryWindows_WhenConstructed_ThenValidationRejectsThem() {
        Instant start = Instant.parse("2026-07-01T20:00:00Z");
        Instant end = start.plus(Duration.ofHours(2));

        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> new EventSchedule(null, end, start)),
                () -> assertThrows(IllegalArgumentException.class, () -> new EventSchedule(start, null, start)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new EventSchedule(start, start.minusSeconds(1), start)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new EventSchedule(start, end, start.plusSeconds(1))),
                () -> assertThrows(IllegalArgumentException.class, () -> new LockTimerDuration(null)),
                () -> assertThrows(IllegalArgumentException.class, () -> new LockTimerDuration(Duration.ZERO)),
                () -> assertThrows(IllegalArgumentException.class, () -> new LockTimerDuration(Duration.ofSeconds(-1))),
                () -> assertEquals(15, new LockTimerDuration(Duration.ofMinutes(15)).toMinutes()),
                () -> assertThrows(IllegalArgumentException.class, () -> new LotteryWindow(null, end)),
                () -> assertThrows(IllegalArgumentException.class, () -> new LotteryWindow(start, null)),
                () -> assertThrows(IllegalArgumentException.class, () -> new LotteryWindow(start, start)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new LotteryWindow(start, start.minusSeconds(1))),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new LotteryWindow(start, end).isOpen(null)),
                () -> assertTrue(new LotteryWindow(start, end).isOpen(start)),
                () -> assertFalse(new LotteryWindow(start, end).isOpen(end))
        );
    }

    @Test
    void GivenInvalidInventoryZoneInputs_WhenConstructed_ThenValidationRejectsThem() {
        UUID id = UUID.randomUUID();

        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> InventoryZone.createGA(null, "GA", BigDecimal.TEN, 10)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> InventoryZone.createGA(id, null, BigDecimal.TEN, 10)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> InventoryZone.createGA(id, " ", BigDecimal.TEN, 10)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> InventoryZone.createGA(id, "GA", null, 10)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> InventoryZone.createGA(id, "GA", BigDecimal.valueOf(-1), 10)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> InventoryZone.createGA(id, "GA", BigDecimal.TEN, 0)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> InventoryZone.createAssigned(id, "Assigned", BigDecimal.valueOf(-1)))
        );
    }

    @Test
    void GivenGAZone_WhenLockReleaseSellAndResize_ThenStateAndFailureBranchesAreCovered() {
        InventoryZone zone = InventoryZone.createGA(UUID.randomUUID(), "General", BigDecimal.valueOf(50), 5);

        assertAll(
                () -> assertTrue(zone.isGA()),
                () -> assertFalse(zone.isAssigned()),
                () -> assertEquals(5, zone.getAvailableCount()),
                () -> assertEquals(0, zone.getLockedCount()),
                () -> assertEquals(0, zone.getSoldCount()),
                () -> assertEquals(5, zone.getTotalCapacity()),
                () -> assertThrows(IllegalStateException.class,
                        () -> zone.addSeat(new Seat(UUID.randomUUID(), "A", "1"))),
                () -> assertThrows(IllegalStateException.class, () -> zone.removeSeat(UUID.randomUUID())),
                () -> assertThrows(IllegalStateException.class, () -> zone.lockSeat(UUID.randomUUID())),
                () -> assertThrows(IllegalStateException.class, () -> zone.releaseSeat(UUID.randomUUID())),
                () -> assertThrows(IllegalStateException.class, () -> zone.sellSeat(UUID.randomUUID()))
        );

        zone.increaseCapacity(2);
        assertEquals(7, zone.getAvailableCount());
        assertThrows(IllegalArgumentException.class, () -> zone.increaseCapacity(0));

        zone.lockGA(3);
        assertEquals(4, zone.getAvailableCount());
        assertEquals(3, zone.getLockedCount());
        assertThrows(IllegalArgumentException.class, () -> zone.lockGA(0));
        assertThrows(IllegalStateException.class, () -> zone.lockGA(99));
        assertThrows(IllegalStateException.class, () -> zone.setPricePerTicket(BigDecimal.ONE));

        zone.releaseGA(1);
        assertEquals(5, zone.getAvailableCount());
        assertEquals(2, zone.getLockedCount());
        assertThrows(IllegalArgumentException.class, () -> zone.releaseGA(0));
        assertThrows(IllegalStateException.class, () -> zone.releaseGA(99));

        zone.sellGA(2);
        assertEquals(0, zone.getLockedCount());
        assertEquals(2, zone.getSoldCount());
        assertThrows(IllegalArgumentException.class, () -> zone.sellGA(0));
        assertThrows(IllegalStateException.class, () -> zone.sellGA(1));
        assertThrows(IllegalStateException.class, () -> zone.setPricePerTicket(BigDecimal.ONE));

        zone.decreaseCapacity(1);
        assertEquals(6, zone.getMaxCapacity());
        assertThrows(IllegalArgumentException.class, () -> zone.decreaseCapacity(0));
        assertThrows(IllegalStateException.class, () -> zone.decreaseCapacity(99));
    }

    @Test
    void GivenAssignedZone_WhenSeatLifecycleRuns_ThenCountsAndFailureBranchesAreCovered() {
        InventoryZone zone = InventoryZone.createAssigned(UUID.randomUUID(), "Assigned", BigDecimal.valueOf(100));
        Seat seatA = new Seat(UUID.randomUUID(), "A", "1");
        Seat seatB = new Seat(UUID.randomUUID(), "A", "2");

        assertAll(
                () -> assertFalse(zone.isGA()),
                () -> assertTrue(zone.isAssigned()),
                () -> assertThrows(IllegalStateException.class, () -> zone.increaseCapacity(1)),
                () -> assertThrows(IllegalStateException.class, () -> zone.decreaseCapacity(1)),
                () -> assertThrows(IllegalStateException.class, () -> zone.lockGA(1)),
                () -> assertThrows(IllegalStateException.class, () -> zone.releaseGA(1)),
                () -> assertThrows(IllegalStateException.class, () -> zone.sellGA(1)),
                () -> assertThrows(IllegalArgumentException.class, () -> zone.addSeat(null))
        );

        zone.addSeat(seatA);
        zone.addSeat(seatB);
        assertEquals(2, zone.getAvailableCount());
        assertEquals(2, zone.getTotalCapacity());
        assertThrows(IllegalArgumentException.class, () -> zone.findSeat(UUID.randomUUID()));

        Seat locked = zone.lockSeat(seatA.getId());
        assertEquals(seatA, locked);
        assertEquals(1, zone.getLockedCount());
        assertThrows(IllegalStateException.class, () -> zone.removeSeat(seatA.getId()));
        assertThrows(IllegalStateException.class, () -> zone.setPricePerTicket(BigDecimal.ONE));

        zone.releaseSeat(seatA.getId());
        assertEquals(2, zone.getAvailableCount());

        zone.lockSeat(seatA.getId());
        zone.sellSeat(seatA.getId());
        assertEquals(1, zone.getSoldCount());
        assertEquals(1, zone.getAvailableCount());
        assertThrows(IllegalStateException.class, () -> zone.releaseSeat(seatB.getId()));

        zone.removeSeat(seatB.getId());
        assertEquals(1, zone.getTotalCapacity());
    }

    @Test
    void GivenSeat_WhenConstructingAndTransitioning_ThenStateBranchesAreCovered() {
        UUID id = UUID.randomUUID();
        Seat seat = new Seat(id, "A", "1");
        Seat sameId = new Seat(id, "B", "2");
        Seat other = new Seat(UUID.randomUUID(), "A", "1");

        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> new Seat(null, "A", "1")),
                () -> assertThrows(IllegalArgumentException.class, () -> new Seat(UUID.randomUUID(), null, "1")),
                () -> assertThrows(IllegalArgumentException.class, () -> new Seat(UUID.randomUUID(), " ", "1")),
                () -> assertThrows(IllegalArgumentException.class, () -> new Seat(UUID.randomUUID(), "A", null)),
                () -> assertThrows(IllegalArgumentException.class, () -> new Seat(UUID.randomUUID(), "A", " ")),
                () -> assertTrue(seat.isAvailable()),
                () -> assertFalse(seat.isLocked()),
                () -> assertFalse(seat.isSold()),
                () -> assertEquals(seat, seat),
                () -> assertEquals(seat, sameId),
                () -> assertNotEquals(seat, other),
                () -> assertNotEquals(seat, null),
                () -> assertNotEquals(seat, "seat")
        );

        assertThrows(IllegalStateException.class, seat::release);
        assertThrows(IllegalStateException.class, seat::sell);
        seat.lock();
        assertTrue(seat.isLocked());
        assertThrows(IllegalStateException.class, seat::lock);
        seat.sell();
        assertTrue(seat.isSold());
        assertThrows(IllegalStateException.class, seat::sell);
        seat.release();
        assertTrue(seat.isAvailable());
    }

    @Test
    void GivenVenueMap_WhenConstructed_ThenValidationLookupAndImmutabilityBranchesAreCovered() {
        UUID zoneId = UUID.randomUUID();
        VenueMap map = new VenueMap(Map.of("Main", zoneId));

        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> new VenueMap(null)),
                () -> assertThrows(IllegalArgumentException.class, () -> new VenueMap(Map.of())),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new VenueMap(mapWithNullSection(zoneId))),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new VenueMap(Map.of(" ", zoneId))),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new VenueMap(mapWithNullZone())),
                () -> assertEquals(zoneId, map.zoneIdFor("Main")),
                () -> assertNull(map.zoneIdFor(null)),
                () -> assertNull(map.zoneIdFor("Missing")),
                () -> assertTrue(map.mappedZoneIds().contains(zoneId)),
                () -> assertThrows(UnsupportedOperationException.class,
                        () -> map.getSectionToZone().put("Other", UUID.randomUUID()))
        );
    }

    @Test
    void GivenEvent_WhenConstructingInvalidInputs_ThenValidationRejectsThem() {
        UUID id = UUID.randomUUID();
        EventSchedule schedule = schedule();
        LockTimerDuration lockTimer = lockTimer();
        LotteryWindow window = lotteryWindow();

        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new Event(null, "Acme", "Show", "desc", EventCategory.CONCERT, schedule, lockTimer)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new Event(id, null, "Show", "desc", EventCategory.CONCERT, schedule, lockTimer)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new Event(id, " ", "Show", "desc", EventCategory.CONCERT, schedule, lockTimer)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new Event(id, "Acme", null, "desc", EventCategory.CONCERT, schedule, lockTimer)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new Event(id, "Acme", " ", "desc", EventCategory.CONCERT, schedule, lockTimer)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new Event(id, "Acme", "Show", "desc", EventCategory.CONCERT, null, lockTimer)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new Event(id, "Acme", "Show", "desc", EventCategory.CONCERT, schedule, null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new Event(id, "Acme", "Show", "desc", EventCategory.CONCERT, schedule, lockTimer,
                                null, new NoDiscountPolicy())),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new Event(id, "Acme", "Show", "desc", EventCategory.CONCERT, schedule, lockTimer,
                                new AlwaysAllowPolicy(), null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new Event(id, "Acme", "Show", "desc", EventCategory.CONCERT, schedule, lockTimer,
                                new AlwaysAllowPolicy(), new NoDiscountPolicy(), null, null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new Event(id, "Acme", "Show", "desc", EventCategory.CONCERT, schedule, lockTimer,
                                new AlwaysAllowPolicy(), new NoDiscountPolicy(), SaleMethod.LOTTERY, null)),
                () -> assertTrue(new Event(id, "Acme", "Show", "desc", EventCategory.CONCERT, schedule, lockTimer,
                        new AlwaysAllowPolicy(), new NoDiscountPolicy(), SaleMethod.LOTTERY, window).isLottery())
        );
    }

    @Test
    void GivenDraftEvent_WhenEditingPublishingAndMapping_ThenLifecycleBranchesAreCovered() {
        Event event = event();
        InventoryZone ga = InventoryZone.createGA(UUID.randomUUID(), "GA", BigDecimal.TEN, 10);
        InventoryZone assigned = InventoryZone.createAssigned(UUID.randomUUID(), "Assigned", BigDecimal.valueOf(20));
        assigned.addSeat(new Seat(UUID.randomUUID(), "A", "1"));

        assertAll(
                () -> assertFalse(event.isPublished()),
                () -> assertFalse(event.isCancelled()),
                () -> assertFalse(event.hasAvailableTickets()),
                () -> assertEquals(0, event.getTotalAvailableTickets()),
                () -> assertThrows(IllegalStateException.class, event::publish),
                () -> assertThrows(IllegalStateException.class, event::markSoldOut),
                () -> assertThrows(IllegalArgumentException.class, () -> event.addZone(null)),
                () -> assertThrows(IllegalArgumentException.class, () -> event.setVenueMap(null))
        );

        event.setName("Updated");
        event.setDescription("new desc");
        event.setArtist("artist");
        event.setRegion("south");
        event.setSchedule(schedule());
        event.setPurchasePolicy(new AlwaysAllowPolicy());
        event.setDiscountPolicy(new NoDiscountPolicy());
        event.incrementVersion();
        event.addZone(ga);
        event.addZone(assigned);

        assertAll(
                () -> assertEquals("Updated", event.getName()),
                () -> assertEquals("artist", event.getArtist()),
                () -> assertEquals("south", event.getRegion()),
                () -> assertEquals(1, event.getVersion()),
                () -> assertTrue(event.hasAvailableTickets()),
                () -> assertEquals(11, event.getTotalAvailableTickets()),
                () -> assertEquals(ga, event.findZone(ga.getId())),
                () -> assertThrows(IllegalArgumentException.class, () -> event.findZone(UUID.randomUUID())),
                () -> assertThrows(UnsupportedOperationException.class, () -> event.getZones().add(ga)),
                () -> assertThrows(IllegalArgumentException.class, () -> event.setName(" ")),
                () -> assertThrows(IllegalArgumentException.class, () -> event.setSchedule(null)),
                () -> assertThrows(IllegalArgumentException.class, () -> event.setPurchasePolicy(null)),
                () -> assertThrows(IllegalArgumentException.class, () -> event.setDiscountPolicy(null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> event.setVenueMap(new VenueMap(Map.of("Unknown", UUID.randomUUID())))),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> event.setVenueMap(new VenueMap(Map.of("OnlyGA", ga.getId()))))
        );

        event.setVenueMap(new VenueMap(Map.of("GA", ga.getId(), "Assigned", assigned.getId())));
        assertEquals(ga.getId(), event.getVenueMap().zoneIdFor("GA"));

        event.publish();
        assertTrue(event.isPublished());
        assertThrows(IllegalStateException.class, () -> event.addZone(ga));
        assertThrows(IllegalStateException.class, () -> event.setVenueMap(new VenueMap(Map.of("GA", ga.getId()))));
        assertThrows(IllegalStateException.class, event::publish);
        event.markSoldOut();
        assertEquals(EventStatus.SOLD_OUT, event.getStatus());
    }

    @Test
    void GivenCancelledEvent_WhenEditingAgain_ThenCancelledBranchesAreCovered() {
        Event event = event();
        event.addZone(InventoryZone.createGA(UUID.randomUUID(), "GA", BigDecimal.TEN, 1));
        event.cancel();

        assertAll(
                () -> assertTrue(event.isCancelled()),
                () -> assertThrows(IllegalStateException.class, event::cancel),
                () -> assertThrows(IllegalStateException.class, () -> event.setName("new")),
                () -> assertThrows(IllegalStateException.class, () -> event.setDescription("new")),
                () -> assertThrows(IllegalStateException.class, () -> event.setArtist("new")),
                () -> assertThrows(IllegalStateException.class, () -> event.setRegion("new")),
                () -> assertThrows(IllegalStateException.class, () -> event.setSchedule(schedule())),
                () -> assertThrows(IllegalStateException.class, () -> event.setPurchasePolicy(new AlwaysAllowPolicy())),
                () -> assertThrows(IllegalStateException.class, () -> event.setDiscountPolicy(new NoDiscountPolicy())),
                () -> assertThrows(IllegalStateException.class, event::publish)
        );
    }

    @Test
    void GivenLotteryEvent_WhenCheckingRegistration_ThenRegularAndWindowBranchesAreCovered() {
        Instant now = Instant.parse("2026-06-01T10:00:00Z");
        LotteryWindow window = new LotteryWindow(now.minusSeconds(10), now.plusSeconds(10));
        Event regular = event();
        Event lottery = new Event(UUID.randomUUID(), "Acme", "Lottery", "desc", EventCategory.CONCERT,
                schedule(), lockTimer(), new AlwaysAllowPolicy(), new NoDiscountPolicy(), SaleMethod.LOTTERY, window);

        assertFalse(regular.isLotteryRegistrationOpen(now));
        assertTrue(lottery.isLotteryRegistrationOpen(now));
        assertFalse(lottery.isLotteryRegistrationOpen(now.plusSeconds(20)));
    }

    private static Event event() {
        return new Event(UUID.randomUUID(), "Acme", "Show", "desc", EventCategory.CONCERT, schedule(), lockTimer());
    }

    private static EventSchedule schedule() {
        Instant start = Instant.parse("2026-07-01T20:00:00Z");
        return new EventSchedule(start, start.plus(Duration.ofHours(2)), start.minus(Duration.ofHours(1)));
    }

    private static LockTimerDuration lockTimer() {
        return new LockTimerDuration(Duration.ofMinutes(15));
    }

    private static LotteryWindow lotteryWindow() {
        Instant start = Instant.parse("2026-06-01T00:00:00Z");
        return new LotteryWindow(start, start.plus(Duration.ofDays(1)));
    }

    private static Map<String, UUID> mapWithNullSection(UUID zoneId) {
        java.util.HashMap<String, UUID> map = new java.util.HashMap<>();
        map.put(null, zoneId);
        return map;
    }

    private static Map<String, UUID> mapWithNullZone() {
        java.util.HashMap<String, UUID> map = new java.util.HashMap<>();
        map.put("Main", null);
        return map;
    }
}
