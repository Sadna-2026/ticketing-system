package com.ticketing.domain.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

/**
 * V3-4 (#262): the Event aggregate + its inventory (zones, seats), venue map, venue
 * layout, schedule, and lottery window persist and round-trip via H2 (JPA).
 *
 * Uses @DataJpaTest (embedded H2) with ddl-auto=create-drop so the schema is built for
 * the test even though the app config sets ddl-auto=none. @DataJpaTest is transactional,
 * so the LAZY seat collection can be traversed inside the test method.
 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@DisplayName("Event JPA mapping")
class EventJpaMappingTest {

    @Autowired
    private TestEntityManager em;

    @Test
    void GivenPublishedLotteryEventWithFullInventory_WhenPersistedAndReloaded_ThenAggregateRoundTrips() {
        // --- Given: a fully populated, published LOTTERY event -------------------
        UUID eventId = UUID.randomUUID();
        UUID gaZoneId = UUID.randomUUID();
        UUID assignedZoneId = UUID.randomUUID();
        UUID seatAvailableId = UUID.randomUUID();
        UUID seatSoldId = UUID.randomUUID();

        Instant start = Instant.parse("2026-09-01T20:00:00Z");
        Instant end = Instant.parse("2026-09-01T23:00:00Z");
        Instant doorsOpen = Instant.parse("2026-09-01T19:00:00Z");
        EventSchedule schedule = new EventSchedule(start, end, doorsOpen);

        Instant lotteryOpen = Instant.parse("2026-08-01T00:00:00Z");
        Instant lotteryClose = Instant.parse("2026-08-15T00:00:00Z");
        LotteryWindow lotteryWindow = new LotteryWindow(lotteryOpen, lotteryClose);

        LockTimerDuration lockTimer = new LockTimerDuration(Duration.ofMinutes(10));

        Event event = new Event(
                eventId, "Acme Productions", "Summer Fest", "An outdoor festival",
                EventCategory.FESTIVAL, schedule, lockTimer,
                new AlwaysAllowPolicy(), new NoDiscountPolicy(),
                SaleMethod.LOTTERY, lotteryWindow);
        event.setArtist("The Headliners");
        event.setRegion("North");

        // GA zone
        InventoryZone gaZone = InventoryZone.createGA(gaZoneId, "Lawn", new BigDecimal("49.50"), 500);
        event.addZone(gaZone);

        // Assigned zone with two seats in DIFFERENT statuses: one AVAILABLE, one locked->sold
        InventoryZone assignedZone =
                InventoryZone.createAssigned(assignedZoneId, "Front Row", new BigDecimal("199.00"));
        Seat available = new Seat(seatAvailableId, "A", "1");
        Seat sold = new Seat(seatSoldId, "A", "2");
        assignedZone.addSeat(available);
        assignedZone.addSeat(sold);
        sold.lock();
        sold.sell();
        event.addZone(assignedZone);

        // Venue map: every zone referenced exactly once (Event enforces the bijection)
        VenueMap venueMap = new VenueMap(Map.of(
                "Lawn Section", gaZoneId,
                "Front Section", assignedZoneId));
        event.setVenueMap(venueMap);

        // Venue layout: a SEAT cell, a GA cell, and a STAGE cell on a 2x3 grid
        VenueLayout layout = new VenueLayout(2, 3, List.of(
                LayoutCell.seat(0, 0, assignedZoneId, seatAvailableId),
                LayoutCell.ga(0, 1, gaZoneId, "GA Area"),
                LayoutCell.stage(1, 0, "Main Stage")));
        event.setVenueLayout(layout);

        event.publish();

        // --- When: persisted, flushed, cleared, and reloaded ---------------------
        em.persistAndFlush(event);
        em.clear();
        Event reloaded = em.find(Event.class, eventId);

        // --- Then: scalars + enums survive --------------------------------------
        assertThat(reloaded).isNotNull();
        assertThat(reloaded.getId()).isEqualTo(eventId);
        assertThat(reloaded.getCompanyName()).isEqualTo("Acme Productions");
        assertThat(reloaded.getName()).isEqualTo("Summer Fest");
        assertThat(reloaded.getDescription()).isEqualTo("An outdoor festival");
        assertThat(reloaded.getArtist()).isEqualTo("The Headliners");
        assertThat(reloaded.getRegion()).isEqualTo("North");
        assertThat(reloaded.getCategory()).isEqualTo(EventCategory.FESTIVAL);
        assertThat(reloaded.getStatus()).isEqualTo(EventStatus.PUBLISHED);
        assertThat(reloaded.getSaleMethod()).isEqualTo(SaleMethod.LOTTERY);
        assertThat(reloaded.isLottery()).isTrue();

        // Embedded schedule
        assertThat(reloaded.getSchedule().getStartTime()).isEqualTo(start);
        assertThat(reloaded.getSchedule().getEndTime()).isEqualTo(end);
        assertThat(reloaded.getSchedule().getDoorsOpenTime()).isEqualTo(doorsOpen);

        // Embedded lock timer
        assertThat(reloaded.getLockTimerDuration().getDuration()).isEqualTo(Duration.ofMinutes(10));

        // Embedded (nullable) lottery window — instants did not clash with the schedule's
        assertThat(reloaded.getLotteryWindow()).isNotNull();
        assertThat(reloaded.getLotteryWindow().registrationOpen()).isEqualTo(lotteryOpen);
        assertThat(reloaded.getLotteryWindow().registrationClose()).isEqualTo(lotteryClose);

        // Policies (V3-6 / #264) round-trip via FK + cascade.
        assertThat(reloaded.getPurchasePolicy()).isInstanceOf(AlwaysAllowPolicy.class);
        assertThat(reloaded.getDiscountPolicy()).isInstanceOf(NoDiscountPolicy.class);

        // --- Then: zones survive -------------------------------------------------
        assertThat(reloaded.getZones()).hasSize(2);
        InventoryZone reloadedGa = reloaded.findZone(gaZoneId);
        assertThat(reloadedGa.isGA()).isTrue();
        assertThat(reloadedGa.getName()).isEqualTo("Lawn");
        assertThat(reloadedGa.getPricePerTicket()).isEqualByComparingTo("49.50");
        assertThat(reloadedGa.getMaxCapacity()).isEqualTo(500);
        assertThat(reloadedGa.getAvailableCount()).isEqualTo(500);

        InventoryZone reloadedAssigned = reloaded.findZone(assignedZoneId);
        assertThat(reloadedAssigned.isAssigned()).isTrue();
        assertThat(reloadedAssigned.getPricePerTicket()).isEqualByComparingTo("199.00");

        // --- Then: seats (LAZY) survive WITH their statuses ----------------------
        // Traversed inside the test transaction, so the LAZY collection initialises.
        assertThat(reloadedAssigned.getSeats()).hasSize(2);
        Seat reloadedAvailable = reloadedAssigned.findSeat(seatAvailableId);
        Seat reloadedSold = reloadedAssigned.findSeat(seatSoldId);
        assertThat(reloadedAvailable.getRow()).isEqualTo("A");
        assertThat(reloadedAvailable.getSeatNumber()).isEqualTo("1");
        assertThat(reloadedAvailable.getStatus()).isEqualTo(SeatStatus.AVAILABLE);
        assertThat(reloadedAvailable.isAvailable()).isTrue();
        assertThat(reloadedSold.getStatus()).isEqualTo(SeatStatus.SOLD);
        assertThat(reloadedSold.isSold()).isTrue();
        // The CAS logic is intact on the reloaded seat: an AVAILABLE seat can still lock.
        reloadedAvailable.lock();
        assertThat(reloadedAvailable.getStatus()).isEqualTo(SeatStatus.LOCKED);

        // --- Then: venue map survives -------------------------------------------
        VenueMap reloadedMap = reloaded.getVenueMap();
        assertThat(reloadedMap).isNotNull();
        assertThat(reloadedMap.zoneIdFor("Lawn Section")).isEqualTo(gaZoneId);
        assertThat(reloadedMap.zoneIdFor("Front Section")).isEqualTo(assignedZoneId);

        // --- Then: venue layout + cells survive ---------------------------------
        VenueLayout reloadedLayout = reloaded.getVenueLayout();
        assertThat(reloadedLayout).isNotNull();
        assertThat(reloadedLayout.getRows()).isEqualTo(2);
        assertThat(reloadedLayout.getCols()).isEqualTo(3);
        assertThat(reloadedLayout.getCells()).hasSize(3);
        assertThat(reloadedLayout.cellsOfType(LayoutCellType.SEAT)).hasSize(1);
        assertThat(reloadedLayout.cellsOfType(LayoutCellType.GENERAL_ADMISSION)).hasSize(1);
        assertThat(reloadedLayout.cellsOfType(LayoutCellType.STAGE)).hasSize(1);
        LayoutCell seatCell = reloadedLayout.cellAt(0, 0).orElseThrow();
        assertThat(seatCell.getType()).isEqualTo(LayoutCellType.SEAT);
        assertThat(seatCell.getZoneId()).isEqualTo(assignedZoneId);
        assertThat(seatCell.getSeatId()).isEqualTo(seatAvailableId);
        LayoutCell stageCell = reloadedLayout.cellAt(1, 0).orElseThrow();
        assertThat(stageCell.getType()).isEqualTo(LayoutCellType.STAGE);
        assertThat(stageCell.getLabel()).isEqualTo("Main Stage");
    }

    @Test
    void GivenEventWithCustomPolicies_WhenPersistedAndReloaded_ThenPoliciesRoundTrip() {
        UUID eventId = UUID.randomUUID();
        UUID gaZoneId = UUID.randomUUID();
        EventSchedule schedule = new EventSchedule(
                Instant.parse("2026-11-01T18:00:00Z"),
                Instant.parse("2026-11-01T21:00:00Z"),
                null);

        Event event = new Event(
                eventId, "Acme Productions", "Policy Show", "desc",
                EventCategory.CONCERT, schedule, new LockTimerDuration(Duration.ofMinutes(5)),
                new AgeRestrictionPolicy(18), new SimpleDiscount(new BigDecimal("15")));
        event.addZone(InventoryZone.createGA(gaZoneId, "Floor", new BigDecimal("40.00"), 50));
        event.publish();

        em.persistAndFlush(event);
        em.clear();
        Event reloaded = em.find(Event.class, eventId);

        assertThat(reloaded.getPurchasePolicy()).isInstanceOf(AgeRestrictionPolicy.class);
        assertThat(((AgeRestrictionPolicy) reloaded.getPurchasePolicy()).getMinimumAge()).isEqualTo(18);
        assertThat(reloaded.getDiscountPolicy()).isInstanceOf(SimpleDiscount.class);
        assertThat(((SimpleDiscount) reloaded.getDiscountPolicy()).getPercentOff()).isEqualByComparingTo("15");
    }

    @Test
    void GivenRegularEventWithoutLottery_WhenPersistedAndReloaded_ThenLotteryWindowIsNull() {
        UUID eventId = UUID.randomUUID();
        UUID gaZoneId = UUID.randomUUID();
        EventSchedule schedule = new EventSchedule(
                Instant.parse("2026-10-01T18:00:00Z"),
                Instant.parse("2026-10-01T21:00:00Z"),
                null);

        Event event = new Event(
                eventId, "Acme Productions", "Open Sale Show", "desc",
                EventCategory.CONCERT, schedule, new LockTimerDuration(Duration.ofMinutes(5)));
        event.addZone(InventoryZone.createGA(gaZoneId, "Standing", new BigDecimal("30.00"), 100));
        event.publish();

        em.persistAndFlush(event);
        em.clear();
        Event reloaded = em.find(Event.class, eventId);

        assertThat(reloaded.getSaleMethod()).isEqualTo(SaleMethod.REGULAR);
        assertThat(reloaded.isLottery()).isFalse();
        // Nullable embedded: all-null components reload as a null embeddable.
        assertThat(reloaded.getLotteryWindow()).isNull();
        // doorsOpenTime was null and survives as null inside the embedded schedule.
        assertThat(reloaded.getSchedule().getDoorsOpenTime()).isNull();
        assertThat(reloaded.getZones()).hasSize(1);
    }
}
