package com.ticketing.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ticketing.application.auth.SessionTokenData;
import com.ticketing.application.auth.SessionTokenService;
import com.ticketing.application.services.EventService;
import com.ticketing.application.services.INotificationService;
import com.ticketing.application.services.OrderService;
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
import com.ticketing.domain.order.ActiveOrder;
import com.ticketing.domain.order.OrderStatus;
import com.ticketing.domain.services.OrderTimeDomainService;
import com.ticketing.infrastructure.InMemoryCompanyRepository;
import com.ticketing.infrastructure.InMemoryEventRepository;
import com.ticketing.infrastructure.InMemoryLotteryRepository;
import com.ticketing.infrastructure.InMemoryMemberRepository;
import com.ticketing.infrastructure.InMemoryOrderRepository;
import com.ticketing.infrastructure.InMemorySessionTokenRepository;

/**
 * Requirement §II.3.6 lottery draw — edge cases beyond the basic win/lose path (issue #540).
 *
 * <p>Covers the draw outcomes that the happy-path tests don't: fewer registrants than tickets
 * (everyone wins), zero registrants (no-op, no error), and a winner who never claims within the
 * purchase window (their reserved tickets are released back). The "more registrants than tickets"
 * and "winners + losers both notified" cases are covered in {@code LotteryNotificationTest}; the
 * "duplicate registration prevented" case in {@code LotteryRegistrationTest}.
 */
@DisplayName("Lottery draw edge cases (#540)")
class LotteryDrawEdgeCasesTest {

    private static final String COMPANY = "Lottery Corp";
    private static final Instant NOW = Instant.parse("2026-07-01T12:00:00Z");

    private InMemoryEventRepository eventRepository;
    private InMemoryCompanyRepository companyRepository;
    private InMemoryMemberRepository memberRepository;
    private InMemoryOrderRepository orderRepository;
    private InMemoryLotteryRepository lotteryRepository;
    private INotificationService notificationService;
    private SessionTokenService sessionTokenService;
    private MutableClock clock;

    private EventService eventService;
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        eventRepository = new InMemoryEventRepository();
        companyRepository = new InMemoryCompanyRepository();
        memberRepository = new InMemoryMemberRepository();
        orderRepository = new InMemoryOrderRepository();
        lotteryRepository = new InMemoryLotteryRepository();
        notificationService = mock(INotificationService.class);
        clock = new MutableClock(NOW);

        String secret = Base64.getEncoder().encodeToString(
                "01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8));
        sessionTokenService = new SessionTokenService(secret, 120, new InMemorySessionTokenRepository());

        eventService = new EventService(eventRepository, companyRepository, memberRepository,
                orderRepository, sessionTokenService, lotteryRepository, clock, null, notificationService);

        OrderTimeDomainService orderTime = new OrderTimeDomainService(orderRepository, eventRepository, clock);
        orderService = new OrderService(sessionTokenService, orderRepository, eventRepository, memberRepository,
                List.of(), List.of(), clock, null, orderTime, notificationService);

        companyRepository.save(new Company(COMPANY, "desc", UUID.randomUUID()));
    }

    @Test
    @DisplayName("Fewer registrants than tickets — everyone wins, nobody is told they lost")
    void GivenFewerRegistrantsThanTickets_WhenDraw_ThenEveryoneWinsAndNoLoserNotification() {
        UUID eventId = createLotteryEvent(/* maxWinners */ 5, /* zoneCapacity */ 5, /* purchaseWindowHours */ 48);
        List<UUID> registrants = registerMembers(eventId, 3);

        List<ActiveOrder> winners = eventService.drawLotteryAutomatically(eventId);

        // Every registrant wins — the draw is bounded by the number of entries, not the capacity.
        assertEquals(3, winners.size(), "all three registrants win when tickets outnumber them");
        Set<UUID> winnerIds = winners.stream().map(ActiveOrder::getMemberId).collect(Collectors.toSet());
        assertTrue(winnerIds.containsAll(registrants), "exactly the registrants won");
        assertTrue(winners.stream().allMatch(ActiveOrder::isLotteryWin));

        // Each registrant is told they won; nobody receives a "not selected" message.
        for (UUID memberId : registrants) {
            verify(notificationService).notify(eq(memberId.toString()), contains("won the lottery"));
        }
        verify(notificationService, never()).notify(anyString(), contains("not selected"));
    }

    @Test
    @DisplayName("Zero registrants — draw completes with no winners, no notifications and no error")
    void GivenNoRegistrants_WhenDraw_ThenCompletesQuietly() {
        UUID eventId = createLotteryEvent(5, 5, 48);

        List<ActiveOrder> winners = eventService.drawLotteryAutomatically(eventId);

        assertTrue(winners.isEmpty(), "no entries means no winners");
        assertTrue(orderRepository.findActiveByEventId(eventId).isEmpty(), "no lottery-win orders created");
        verify(notificationService, never()).notify(anyString(), anyString());
    }

    @Test
    @DisplayName("Winner does not claim within the purchase window — reserved tickets are released back")
    void GivenWinnerSelectsButDoesNotCheckout_WhenPurchaseWindowExpires_ThenTicketsReleased() {
        // Lock timer is long (100h) so only the 48h purchase window governs expiry here.
        UUID eventId = createLotteryEvent(/* maxWinners */ 1, /* zoneCapacity */ 100,
                /* purchaseWindowHours */ 48, /* lockTimer */ Duration.ofHours(100));
        UUID zoneId = eventRepository.findById(eventId).orElseThrow().getZones().get(0).getId();

        UUID winnerId = registerMembers(eventId, 1).get(0);
        List<ActiveOrder> winners = eventService.drawLotteryAutomatically(eventId);
        assertEquals(1, winners.size());
        assertEquals(winnerId, winners.get(0).getMemberId());

        // The winner selects a ticket within the window (reserving inventory) but never checks out.
        String winnerToken = sessionTokenService.generateMemberToken(new SessionTokenData(
                UUID.randomUUID(), winnerId, Set.of(), "winner", "winner@example.com", "MEMBER"));
        orderService.addGATicketsToOrder(winnerToken, eventId, zoneId, 1);
        assertEquals(99, eventRepository.findById(eventId).orElseThrow()
                .findZone(zoneId).getAvailableCount(), "one ticket is reserved by the winner");

        // The purchase window elapses (still within the 100h lock timer).
        clock.advance(Duration.ofHours(49));

        // Acting on the expired win order releases the reserved ticket and reports the expiry.
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> orderService.checkout(winnerToken, null));
        assertTrue(ex.getMessage().toLowerCase().contains("expired"),
                "winner is told their purchase window expired, was: " + ex.getMessage());

        Event after = eventRepository.findById(eventId).orElseThrow();
        assertEquals(100, after.findZone(zoneId).getAvailableCount(), "the unclaimed ticket is released back");
        assertEquals(0, after.findZone(zoneId).getLockedCount());
        assertEquals(OrderStatus.EXPIRED,
                orderRepository.findById(winners.get(0).getId()).orElseThrow().getStatus());
    }

    // ── helpers ─────────────────────────────────────────────────────

    private UUID createLotteryEvent(int maxWinners, int zoneCapacity, int purchaseWindowHours) {
        return createLotteryEvent(maxWinners, zoneCapacity, purchaseWindowHours, Duration.ofMinutes(15));
    }

    private UUID createLotteryEvent(int maxWinners, int zoneCapacity, int purchaseWindowHours, Duration lockTimer) {
        UUID eventId = UUID.randomUUID();
        UUID zoneId = UUID.randomUUID();
        // Registration window already closed (1 day ago) so the automatic draw is permitted.
        Event event = new Event(eventId, COMPANY, "Edge Lottery", "desc",
                EventCategory.CONCERT, schedule(), new LockTimerDuration(lockTimer),
                new AlwaysAllowPolicy(), new NoDiscountPolicy(), SaleMethod.LOTTERY,
                new LotteryWindow(NOW.minus(3, ChronoUnit.DAYS), NOW.minus(1, ChronoUnit.DAYS),
                        maxWinners, purchaseWindowHours));
        event.addZone(InventoryZone.createGA(zoneId, "Floor", new BigDecimal("50.00"), zoneCapacity));
        event.setVenueMap(new VenueMap(Map.of("Floor", zoneId)));
        event.publish();
        eventRepository.save(event);
        return eventId;
    }

    private List<UUID> registerMembers(UUID eventId, int count) {
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            UUID memberId = UUID.randomUUID();
            memberRepository.save(new Member(memberId, "member-" + i, "member-" + i + "@example.com", "pw"));
            lotteryRepository.save(new LotteryEntry(UUID.randomUUID(), eventId, memberId, NOW));
            ids.add(memberId);
        }
        return ids;
    }

    private static EventSchedule schedule() {
        Instant start = NOW.plus(30, ChronoUnit.DAYS);
        return new EventSchedule(start, start.plus(3, ChronoUnit.HOURS), start.minus(1, ChronoUnit.HOURS));
    }

    /** Minimal advanceable clock so the purchase-window test can let time pass. */
    private static final class MutableClock implements ISystemClock {
        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        @Override
        public Instant now() {
            return now;
        }

        void advance(Duration d) {
            this.now = this.now.plus(d);
        }
    }
}
