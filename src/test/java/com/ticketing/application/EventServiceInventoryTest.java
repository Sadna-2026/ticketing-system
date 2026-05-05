package com.ticketing.application;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ticketing.application.auth.ISessionTokenService;
import com.ticketing.domain.company.Company;
import com.ticketing.domain.event.Event;
import com.ticketing.domain.event.EventCategory;
import com.ticketing.domain.event.EventSchedule;
import com.ticketing.domain.event.InventoryZone;
import com.ticketing.domain.event.LockTimerDuration;
import com.ticketing.domain.event.Seat;
import com.ticketing.domain.member.ManagerPermission;
import com.ticketing.domain.member.Member;
import com.ticketing.domain.member.StaffAppointment;
import com.ticketing.infrastructure.InMemoryCompanyRepository;
import com.ticketing.infrastructure.InMemoryEventRepository;
import com.ticketing.infrastructure.InMemoryMemberRepository;
import com.ticketing.infrastructure.Interface.IActiveOrderRepository;

public class EventServiceInventoryTest {

    private static final String COMPANY = "Acme Productions";
    private static final String TOKEN = "valid-token";

    private InMemoryEventRepository eventRepo;
    private InMemoryCompanyRepository companyRepo;
    private InMemoryMemberRepository memberRepo;
    private ISessionTokenService tokens;
    private EventService eventService;

    private UUID memberId;
    private Member member;
    private UUID eventId;
    private UUID gaZoneId;
    private UUID assignedZoneId;

    @BeforeEach
    public void setUp() {
        eventRepo = new InMemoryEventRepository();
        companyRepo = new InMemoryCompanyRepository();
        memberRepo = new InMemoryMemberRepository();
        tokens = mock(ISessionTokenService.class);
        eventService = new EventService(eventRepo, companyRepo, memberRepo,
                mock(IActiveOrderRepository.class), tokens);

        memberId = UUID.randomUUID();
        member = new Member(memberId, "owner", "owner@x.com", "pw");
        memberRepo.save(member);
        companyRepo.save(new Company(COMPANY, "desc", memberId));

        // seed an event with a GA zone (capacity 100) and an assigned zone (4 seats)
        eventId = UUID.randomUUID();
        Instant start = Instant.now().plus(30, ChronoUnit.DAYS);
        Event e = new Event(eventId, COMPANY, "Concert", "desc", EventCategory.CONCERT,
                new EventSchedule(start, start.plus(2, ChronoUnit.HOURS), start.minus(1, ChronoUnit.HOURS)),
                new LockTimerDuration(Duration.ofMinutes(15)));

        gaZoneId = UUID.randomUUID();
        e.addZone(InventoryZone.createGA(gaZoneId, "Floor", new BigDecimal("50.00"), 100));

        assignedZoneId = UUID.randomUUID();
        InventoryZone vip = InventoryZone.createAssigned(assignedZoneId, "VIP", new BigDecimal("150.00"));
        vip.addSeat(new Seat(UUID.randomUUID(), "A", "1"));
        vip.addSeat(new Seat(UUID.randomUUID(), "A", "2"));
        vip.addSeat(new Seat(UUID.randomUUID(), "A", "3"));
        vip.addSeat(new Seat(UUID.randomUUID(), "A", "4"));
        e.addZone(vip);

        eventRepo.save(e);

        when(tokens.isValid(TOKEN)).thenReturn(true);
        when(tokens.extractMemberId(TOKEN)).thenReturn(memberId);
    }

    // 1. Add ticket to inventory

    @Test
    public void GivenOwner_WhenAddSeatsToAssignedZone_ThenSeatsAdded() {
        appoint(StaffAppointment.StaffRole.OWNER, Set.of());

        eventService.addSeatsToZone(TOKEN, eventId, assignedZoneId, List.of(
                new CreateEventRequest.SeatSpec("B", "1"),
                new CreateEventRequest.SeatSpec("B", "2")));

        InventoryZone zone = eventRepo.findById(eventId).orElseThrow().findZone(assignedZoneId);
        assertEquals(6, zone.getSeats().size());
    }

    @Test
    public void GivenOwner_WhenIncreaseGACapacity_ThenCapacityGrows() {
        appoint(StaffAppointment.StaffRole.OWNER, Set.of());

        eventService.increaseGACapacity(TOKEN, eventId, gaZoneId, 50);

        InventoryZone zone = eventRepo.findById(eventId).orElseThrow().findZone(gaZoneId);
        assertEquals(150, zone.getMaxCapacity());
        assertEquals(150, zone.getAvailableCount());
    }

    // 2. Remove ticket — only if not reserved/sold

    @Test
    public void GivenAvailableSeat_WhenRemoveSeats_ThenSeatRemoved() {
        appoint(StaffAppointment.StaffRole.OWNER, Set.of());
        InventoryZone zone = eventRepo.findById(eventId).orElseThrow().findZone(assignedZoneId);
        UUID toRemove = zone.getSeats().get(0).getId();

        eventService.removeSeats(TOKEN, eventId, assignedZoneId, List.of(toRemove));

        InventoryZone after = eventRepo.findById(eventId).orElseThrow().findZone(assignedZoneId);
        assertEquals(3, after.getSeats().size());
    }

    @Test
    public void GivenLockedSeat_WhenRemoveSeats_ThenThrowIllegalStateException() {
        appoint(StaffAppointment.StaffRole.OWNER, Set.of());
        InventoryZone zone = eventRepo.findById(eventId).orElseThrow().findZone(assignedZoneId);
        UUID lockedSeatId = zone.getSeats().get(0).getId();
        zone.lockSeat(lockedSeatId); // simulate active reservation
        eventRepo.save(eventRepo.findById(eventId).orElseThrow());

        assertThrows(IllegalStateException.class,
                () -> eventService.removeSeats(TOKEN, eventId, assignedZoneId, List.of(lockedSeatId)));
    }

    @Test
    public void GivenInsufficientFreeGA_WhenDecreaseCapacity_ThenThrowIllegalStateException() {
        appoint(StaffAppointment.StaffRole.OWNER, Set.of());
        // lock 90 of the 100 — only 10 are free
        InventoryZone zone = eventRepo.findById(eventId).orElseThrow().findZone(gaZoneId);
        zone.lockGA(90);
        eventRepo.save(eventRepo.findById(eventId).orElseThrow());

        assertThrows(IllegalStateException.class,
                () -> eventService.decreaseGACapacity(TOKEN, eventId, gaZoneId, 50));
    }

    // 3. Modify ticket price — only if not sold (or locked, V1 strict)

    @Test
    public void GivenZeroSales_WhenSetZonePrice_ThenPriceUpdated() {
        appoint(StaffAppointment.StaffRole.OWNER, Set.of());

        eventService.setZonePrice(TOKEN, eventId, gaZoneId, new BigDecimal("75.00"));

        BigDecimal price = eventRepo.findById(eventId).orElseThrow()
                .findZone(gaZoneId).getPricePerTicket();
        assertEquals(0, price.compareTo(new BigDecimal("75.00")));
    }

    @Test
    public void GivenLockedTickets_WhenSetZonePrice_ThenThrowIllegalStateException() {
        appoint(StaffAppointment.StaffRole.OWNER, Set.of());
        InventoryZone zone = eventRepo.findById(eventId).orElseThrow().findZone(gaZoneId);
        zone.lockGA(5);
        eventRepo.save(eventRepo.findById(eventId).orElseThrow());

        assertThrows(IllegalStateException.class,
                () -> eventService.setZonePrice(TOKEN, eventId, gaZoneId, new BigDecimal("80.00")));
    }

    @Test
    public void GivenSoldTickets_WhenSetZonePrice_ThenThrowIllegalStateException() {
        appoint(StaffAppointment.StaffRole.OWNER, Set.of());
        InventoryZone zone = eventRepo.findById(eventId).orElseThrow().findZone(gaZoneId);
        zone.lockGA(3);
        zone.sellGA(3);
        eventRepo.save(eventRepo.findById(eventId).orElseThrow());

        assertThrows(IllegalStateException.class,
                () -> eventService.setZonePrice(TOKEN, eventId, gaZoneId, new BigDecimal("90.00")));
    }

    // 4. Permission check

    @Test
    public void GivenManagerWithInventoryMgmt_WhenAddSeats_ThenSucceed() {
        appoint(StaffAppointment.StaffRole.MANAGER, Set.of(ManagerPermission.INVENTORY_MGMT));

        eventService.addSeatsToZone(TOKEN, eventId, assignedZoneId,
                List.of(new CreateEventRequest.SeatSpec("Z", "1")));

        assertEquals(5, eventRepo.findById(eventId).orElseThrow()
                .findZone(assignedZoneId).getSeats().size());
    }

    @Test
    public void GivenManagerWithMapDefinition_WhenIncreaseCapacity_ThenSucceed() {
        appoint(StaffAppointment.StaffRole.MANAGER, Set.of(ManagerPermission.MAP_DEFINITION));

        eventService.increaseGACapacity(TOKEN, eventId, gaZoneId, 10);

        assertEquals(110, eventRepo.findById(eventId).orElseThrow()
                .findZone(gaZoneId).getMaxCapacity());
    }

    @Test
    public void GivenManagerWithoutEitherPermission_WhenSetPrice_ThenThrowSecurityException() {
        appoint(StaffAppointment.StaffRole.MANAGER, Set.of(ManagerPermission.EVENT_LIFECYCLE));

        assertThrows(SecurityException.class,
                () -> eventService.setZonePrice(TOKEN, eventId, gaZoneId, new BigDecimal("99.00")));
    }

    @Test
    public void GivenGuestToken_WhenAddSeats_ThenThrowSecurityException() {
        when(tokens.extractMemberId(TOKEN)).thenReturn(null);

        assertThrows(SecurityException.class,
                () -> eventService.addSeatsToZone(TOKEN, eventId, assignedZoneId,
                        List.of(new CreateEventRequest.SeatSpec("X", "1"))));
    }

    // 5. Active locked-order conflict — covered by GivenLockedSeat_WhenRemoveSeats above

    // 6. Concurrent edit safety — race test per V1 §6.a

    @Test
    public void GivenConcurrentSeatAdds_WhenRunInParallel_ThenAllAddsApplied() throws Exception {
        appoint(StaffAppointment.StaffRole.OWNER, Set.of());

        final int threadCount = 8;
        final int seatsPerThread = 5;
        final ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(threadCount);
        final AtomicInteger failures = new AtomicInteger();

        for (int t = 0; t < threadCount; t++) {
            final int tid = t;
            pool.submit(() -> {
                try {
                    start.await(); // all threads block until released
                    List<CreateEventRequest.SeatSpec> batch = new java.util.ArrayList<>();
                    for (int i = 0; i < seatsPerThread; i++) {
                        batch.add(new CreateEventRequest.SeatSpec("T" + tid, String.valueOf(i)));
                    }
                    eventService.addSeatsToZone(TOKEN, eventId, assignedZoneId, batch);
                } catch (Exception ex) {
                    failures.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown(); // unleash
        assertTrue(done.await(10, TimeUnit.SECONDS), "concurrent adds did not finish in time");
        pool.shutdown();

        assertEquals(0, failures.get(), "no concurrent add should fail");
        // started with 4 seats; added 8 * 5 = 40 → expect 44
        InventoryZone zone = eventRepo.findById(eventId).orElseThrow().findZone(assignedZoneId);
        assertEquals(4 + threadCount * seatsPerThread, zone.getSeats().size());
    }

    @Test
    public void GivenConcurrentSameSeatRemoval_WhenRunInParallel_ThenExactlyOneSucceeds() throws Exception {
        appoint(StaffAppointment.StaffRole.OWNER, Set.of());
        UUID seatId = eventRepo.findById(eventId).orElseThrow()
                .findZone(assignedZoneId).getSeats().get(0).getId();

        final int threadCount = 6;
        final ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(threadCount);
        final AtomicInteger successes = new AtomicInteger();
        final AtomicInteger expectedFailures = new AtomicInteger();

        for (int t = 0; t < threadCount; t++) {
            pool.submit(() -> {
                try {
                    start.await();
                    eventService.removeSeats(TOKEN, eventId, assignedZoneId, List.of(seatId));
                    successes.incrementAndGet();
                } catch (IllegalArgumentException | IllegalStateException ex) {
                    expectedFailures.incrementAndGet(); // seat-not-found after first removal
                } catch (Exception ignore) {
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertTrue(done.await(10, TimeUnit.SECONDS));
        pool.shutdown();

        assertEquals(1, successes.get(), "exactly one removal should succeed");
        assertEquals(threadCount - 1, expectedFailures.get(),
                "all other threads should see the seat already gone");
        // and the seat is actually gone
        assertEquals(3, eventRepo.findById(eventId).orElseThrow()
                .findZone(assignedZoneId).getSeats().size());
    }

    @Test
    public void GivenCancelledEvent_WhenAddSeats_ThenThrowIllegalStateException() {
        appoint(StaffAppointment.StaffRole.OWNER, Set.of());
        Event e = eventRepo.findById(eventId).orElseThrow();
        e.cancel();
        eventRepo.save(e);

        assertThrows(IllegalStateException.class,
                () -> eventService.addSeatsToZone(TOKEN, eventId, assignedZoneId,
                        List.of(new CreateEventRequest.SeatSpec("X", "1"))));
    }

    @Test
    public void GivenTwoDifferentEvents_WhenEditedInParallel_ThenBothComplete() throws Exception {
        appoint(StaffAppointment.StaffRole.OWNER, Set.of());

        // seed a second event under the same company
        UUID otherEventId = UUID.randomUUID();
        UUID otherZoneId = UUID.randomUUID();
        Instant start = Instant.now().plus(45, ChronoUnit.DAYS);
        Event other = new Event(otherEventId, COMPANY, "Concert 2", "desc", EventCategory.CONCERT,
                new EventSchedule(start, start.plus(2, ChronoUnit.HOURS), start.minus(1, ChronoUnit.HOURS)),
                new LockTimerDuration(Duration.ofMinutes(15)));
        InventoryZone vip2 = InventoryZone.createAssigned(otherZoneId, "VIP", new BigDecimal("100.00"));
        other.addZone(vip2);
        eventRepo.save(other);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start_ = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);

        pool.submit(() -> {
            try { start_.await();
                eventService.addSeatsToZone(TOKEN, eventId, assignedZoneId,
                        List.of(new CreateEventRequest.SeatSpec("E1", "1")));
            } catch (Exception ignore) {} finally { done.countDown(); }
        });
        pool.submit(() -> {
            try { start_.await();
                eventService.addSeatsToZone(TOKEN, otherEventId, otherZoneId,
                        List.of(new CreateEventRequest.SeatSpec("E2", "1")));
            } catch (Exception ignore) {} finally { done.countDown(); }
        });

        start_.countDown();
        assertTrue(done.await(5, TimeUnit.SECONDS));
        pool.shutdown();

        assertEquals(5, eventRepo.findById(eventId).orElseThrow()
                .findZone(assignedZoneId).getSeats().size());
        assertEquals(1, eventRepo.findById(otherEventId).orElseThrow()
                .findZone(otherZoneId).getSeats().size());
    }

    // helpers

    private void appoint(StaffAppointment.StaffRole role, Set<ManagerPermission> perms) {
        member.addStaffAppointment(COMPANY,
                new StaffAppointment(COMPANY, memberId, role, perms));
        memberRepo.save(member);
    }
}
