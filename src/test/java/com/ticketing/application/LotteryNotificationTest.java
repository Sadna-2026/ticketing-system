package com.ticketing.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ticketing.application.auth.ISessionTokenService;
import com.ticketing.application.services.EventService;
import com.ticketing.domain.company.Company;
import com.ticketing.domain.event.AlwaysAllowPolicy;
import com.ticketing.domain.event.Event;
import com.ticketing.domain.event.EventCategory;
import com.ticketing.domain.event.EventSchedule;
import com.ticketing.domain.event.InventoryZone;
import com.ticketing.domain.event.LockTimerDuration;
import com.ticketing.domain.event.LotteryWindow;
import com.ticketing.domain.event.NoDiscountPolicy;
import com.ticketing.domain.event.SaleMethod;
import com.ticketing.domain.event.VenueMap;
import com.ticketing.domain.lottery.LotteryEntry;
import com.ticketing.domain.member.Member;
import com.ticketing.domain.member.StaffAppointment;
import com.ticketing.domain.order.ActiveOrder;
import com.ticketing.infrastructure.InMemoryCompanyRepository;
import com.ticketing.infrastructure.InMemoryEventRepository;
import com.ticketing.infrastructure.InMemoryLotteryRepository;
import com.ticketing.infrastructure.InMemoryMemberRepository;
import com.ticketing.infrastructure.InMemoryOrderRepository;
import com.ticketing.infrastructure.notification.InMemoryPendingNotificationRepository;
import com.ticketing.infrastructure.notification.WebSocketNotificationService;

/**
 * FIX-V2-21: a lottery draw with one winning slot and two connected registrants
 * delivers a real-time result notification to each — the correct winner vs
 * non-winner message — through the real WebSocket notification path.
 */
@DisplayName("Lottery result notifications")
public class LotteryNotificationTest {

    private static final String ORGANIZER_TOKEN = "organizer-token";
    private static final String COMPANY_NAME = "Lottery Corp";
    private static final Instant NOW = Instant.parse("2026-07-01T12:00:00Z");

    private InMemoryEventRepository eventRepository;
    private InMemoryCompanyRepository companyRepository;
    private InMemoryMemberRepository memberRepository;
    private InMemoryOrderRepository orderRepository;
    private InMemoryLotteryRepository lotteryRepository;
    private InMemoryPendingNotificationRepository pendingRepository;
    private WebSocketNotificationService notificationService;
    private ISessionTokenService sessionTokenService;
    private EventService eventService;

    private UUID eventId;
    private UUID zoneId;
    private UUID memberAId;
    private UUID memberBId;

    @BeforeEach
    void setUp() {
        eventRepository = new InMemoryEventRepository();
        companyRepository = new InMemoryCompanyRepository();
        memberRepository = new InMemoryMemberRepository();
        orderRepository = new InMemoryOrderRepository();
        lotteryRepository = new InMemoryLotteryRepository();
        pendingRepository = new InMemoryPendingNotificationRepository();
        // Single-thread executor → deterministic, ordered delivery in tests.
        notificationService = new WebSocketNotificationService(pendingRepository,
                Executors.newSingleThreadExecutor());

        UUID organizerId = UUID.randomUUID();
        sessionTokenService = mock(ISessionTokenService.class);
        when(sessionTokenService.isValid(ORGANIZER_TOKEN)).thenReturn(true);
        when(sessionTokenService.extractMemberId(ORGANIZER_TOKEN)).thenReturn(organizerId);

        ISystemClock fixedClock = () -> NOW;
        eventService = new EventService(eventRepository, companyRepository, memberRepository,
                orderRepository, sessionTokenService, lotteryRepository, fixedClock, null,
                notificationService);

        companyRepository.save(new Company(COMPANY_NAME, "desc", organizerId));
        Member organizer = new Member(organizerId, "organizer", "org@lotterytest.com", "pw");
        organizer.addStaffAppointment(COMPANY_NAME,
                new StaffAppointment(COMPANY_NAME, organizerId, StaffAppointment.StaffRole.OWNER,
                        java.util.Set.of()));
        memberRepository.save(organizer);

        eventId = UUID.randomUUID();
        zoneId = UUID.randomUUID();
        // Registration window closed (2 days ago) so drawLottery is permitted
        Event event = new Event(eventId, COMPANY_NAME, "Big Lottery Show", "desc",
                EventCategory.CONCERT, defaultSchedule(),
                new LockTimerDuration(Duration.ofMinutes(15)),
                new AlwaysAllowPolicy(), new NoDiscountPolicy(),
                SaleMethod.LOTTERY,
                new LotteryWindow(NOW.minus(3, ChronoUnit.DAYS), NOW.minus(1, ChronoUnit.DAYS)));
        // Capacity of 1 so a draw across the two registrants produces exactly one winner.
        event.addZone(InventoryZone.createGA(zoneId, "Floor", new BigDecimal("50.00"), 1));
        event.setVenueMap(new VenueMap(Map.of("Section A", zoneId)));
        event.publish();
        eventRepository.save(event);

        memberAId = registerForLottery("alice");
        memberBId = registerForLottery("bob");
    }

    @AfterEach
    void tearDown() {
        notificationService.shutdown();
    }

    @Test
    @DisplayName("LotteryRealTimeNotification — connected winner and non-winner each receive a live result")
    void GivenTwoConnectedRegistrants_WhenDrawWithOneWinningSlot_ThenWinnerAndNonWinnerReceiveRealTimeResults()
            throws Exception {
        List<String> inboxA = Collections.synchronizedList(new ArrayList<>());
        List<String> inboxB = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch delivered = new CountDownLatch(2);

        // Both registrants are connected (a real-time listener is registered for each).
        notificationService.registerListener(memberAId.toString(), msg -> { inboxA.add(msg); delivered.countDown(); });
        notificationService.registerListener(memberBId.toString(), msg -> { inboxB.add(msg); delivered.countDown(); });

        // When: the draw runs automatically (capacity 1 → a single winning slot).
        List<ActiveOrder> winners = eventService.drawLotteryAutomatically(eventId);

        // Then: both connected users receive a real-time message.
        assertTrue(delivered.await(2, TimeUnit.SECONDS),
                "both connected registrants should receive a real-time notification");
        assertEquals(1, winners.size());

        UUID winnerId = winners.get(0).getMemberId();
        List<String> winnerInbox = winnerId.equals(memberAId) ? inboxA : inboxB;
        List<String> loserInbox = winnerId.equals(memberAId) ? inboxB : inboxA;

        // Winner vs non-winner messages are correct.
        assertEquals(1, winnerInbox.size());
        assertEquals(1, loserInbox.size());
        assertTrue(winnerInbox.get(0).toLowerCase().contains("won"),
                "winner should be told they won, was: " + winnerInbox.get(0));
        assertTrue(loserInbox.get(0).toLowerCase().contains("not selected"),
                "non-winner should be told they were not selected, was: " + loserInbox.get(0));

        // And: nothing was queued for later — both users were connected.
        assertTrue(pendingRepository.getPendingNotifications(memberAId.toString()).isEmpty());
        assertTrue(pendingRepository.getPendingNotifications(memberBId.toString()).isEmpty());
    }

    @Test
    @DisplayName("LotteryAllRegistrantsNotified — every winner AND every loser receives a result notification")
    void GivenManyRegistrants_WhenDrawWithMultipleWinnersAndLosers_ThenEveryRegistrantGetsAResultNotification()
            throws Exception {
        // A fresh lottery event with capacity 2 (→ 2 winners) and 5 registrants (→ 3 losers),
        // so the draw produces both winners and losers per the advisor's requirement.
        UUID multiEventId = createLotteryEventWithCapacity(2);
        int registrantCount = 5;

        Map<UUID, List<String>> inboxes = new ConcurrentHashMap<>();
        List<UUID> registrants = new ArrayList<>();
        CountDownLatch delivered = new CountDownLatch(registrantCount);

        // Every registrant is connected, so each result lands as a live notification.
        for (int i = 0; i < registrantCount; i++) {
            UUID memberId = registerForLottery(multiEventId, "registrant-" + i);
            registrants.add(memberId);
            List<String> inbox = Collections.synchronizedList(new ArrayList<>());
            inboxes.put(memberId, inbox);
            notificationService.registerListener(memberId.toString(),
                    msg -> { inbox.add(msg); delivered.countDown(); });
        }

        // When: the draw runs automatically.
        List<ActiveOrder> winners = eventService.drawLotteryAutomatically(multiEventId);

        // Then: all five registrants — winners and losers alike — received a result notification.
        assertTrue(delivered.await(2, TimeUnit.SECONDS),
                "every registrant (winner or loser) should receive a result notification");
        assertEquals(2, winners.size(), "capacity 2 → exactly 2 winners");

        Set<UUID> winnerIds = winners.stream().map(ActiveOrder::getMemberId).collect(Collectors.toSet());
        int winnersNotified = 0;
        int losersNotified = 0;
        for (UUID memberId : registrants) {
            List<String> inbox = inboxes.get(memberId);
            assertEquals(1, inbox.size(),
                    "each registrant gets exactly one result notification: " + memberId);
            String message = inbox.get(0).toLowerCase();
            if (winnerIds.contains(memberId)) {
                assertTrue(message.contains("won"), "winner should be told they won, was: " + inbox.get(0));
                winnersNotified++;
            } else {
                assertTrue(message.contains("not selected"),
                        "loser should be told they were not selected, was: " + inbox.get(0));
                losersNotified++;
            }
        }
        assertEquals(2, winnersNotified, "both winners were notified");
        assertEquals(3, losersNotified, "all three losers were notified");
    }

    private UUID registerForLottery(String username) {
        return registerForLottery(eventId, username);
    }

    private UUID registerForLottery(UUID lotteryEventId, String username) {
        UUID memberId = UUID.randomUUID();
        memberRepository.save(new Member(memberId, username, username + "@example.com", "encryptedPw"));
        lotteryRepository.save(new LotteryEntry(UUID.randomUUID(), lotteryEventId, memberId, NOW));
        return memberId;
    }

    /** Builds, publishes and saves a fresh lottery event whose draw yields up to {@code capacity} winners. */
    private UUID createLotteryEventWithCapacity(int capacity) {
        UUID newEventId = UUID.randomUUID();
        UUID newZoneId = UUID.randomUUID();
        Event event = new Event(newEventId, COMPANY_NAME, "Multi-Winner Lottery", "desc",
                EventCategory.CONCERT, defaultSchedule(),
                new LockTimerDuration(Duration.ofMinutes(15)),
                new AlwaysAllowPolicy(), new NoDiscountPolicy(),
                SaleMethod.LOTTERY,
                new LotteryWindow(NOW.minus(3, ChronoUnit.DAYS), NOW.minus(1, ChronoUnit.DAYS), capacity, 48));
        event.addZone(InventoryZone.createGA(newZoneId, "Floor", new BigDecimal("50.00"), capacity));
        event.setVenueMap(new VenueMap(Map.of("Section A", newZoneId)));
        event.publish();
        eventRepository.save(event);
        return newEventId;
    }

    private static EventSchedule defaultSchedule() {
        Instant start = NOW.plus(30, ChronoUnit.DAYS);
        return new EventSchedule(start, start.plus(3, ChronoUnit.HOURS), start.minus(1, ChronoUnit.HOURS));
    }
}
