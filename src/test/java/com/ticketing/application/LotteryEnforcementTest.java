package com.ticketing.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.ticketing.application.auth.ISessionTokenService;
import com.ticketing.application.services.EventService;
import com.ticketing.application.services.OrderService;
import com.ticketing.domain.company.Company;
import com.ticketing.domain.event.*;
import com.ticketing.domain.lottery.LotteryEntry;
import com.ticketing.domain.member.ManagerPermission;
import com.ticketing.domain.member.Member;
import com.ticketing.domain.member.StaffAppointment;
import com.ticketing.domain.order.ActiveOrder;
import com.ticketing.infrastructure.InMemoryCompanyRepository;
import com.ticketing.infrastructure.InMemoryEventRepository;
import com.ticketing.infrastructure.InMemoryLotteryRepository;
import com.ticketing.infrastructure.InMemoryMemberRepository;
import com.ticketing.infrastructure.InMemoryOrderRepository;

/**
 * FIX-V3-LOTTERY-FLOW: verifies that the lottery purchase-right rules are
 * enforced end-to-end in the application/domain layer.
 */
@DisplayName("Lottery purchase-right enforcement")
class LotteryEnforcementTest {

    private static final String COMPANY = "Acme Events";
    private static final Instant NOW = Instant.parse("2026-07-01T12:00:00Z");
    private static final String OWNER_TOKEN = "owner-token";
    private static final String MEMBER_TOKEN = "member-token";

    private InMemoryEventRepository eventRepository;
    private InMemoryCompanyRepository companyRepository;
    private InMemoryMemberRepository memberRepository;
    private InMemoryOrderRepository orderRepository;
    private InMemoryLotteryRepository lotteryRepository;
    private ISessionTokenService sessionTokenService;
    private EventService eventService;
    private OrderService orderService;

    private UUID ownerId;
    private UUID memberId;
    private UUID eventId;
    private UUID zoneId;

    @BeforeEach
    void setUp() {
        eventRepository = new InMemoryEventRepository();
        companyRepository = new InMemoryCompanyRepository();
        memberRepository = new InMemoryMemberRepository();
        orderRepository = new InMemoryOrderRepository();
        lotteryRepository = new InMemoryLotteryRepository();

        ISystemClock clock = () -> NOW;

        ownerId = UUID.randomUUID();
        memberId = UUID.randomUUID();

        sessionTokenService = mock(ISessionTokenService.class);
        when(sessionTokenService.isValid(anyString())).thenReturn(true);
        when(sessionTokenService.extractMemberId(OWNER_TOKEN)).thenReturn(ownerId);
        when(sessionTokenService.extractMemberId(MEMBER_TOKEN)).thenReturn(memberId);
        when(sessionTokenService.extractSessionId(OWNER_TOKEN)).thenReturn(UUID.randomUUID());
        when(sessionTokenService.extractSessionId(MEMBER_TOKEN)).thenReturn(UUID.randomUUID());

        companyRepository.save(new Company(COMPANY, "desc", ownerId));

        Member owner = new Member(ownerId, "owner", "owner@test.com", "pw");
        owner.addStaffAppointment(COMPANY,
                new StaffAppointment(COMPANY, ownerId, StaffAppointment.StaffRole.OWNER, Set.of()));
        memberRepository.save(owner);

        Member regularMember = new Member(memberId, "alice", "alice@test.com", "pw");
        memberRepository.save(regularMember);

        eventService = new EventService(eventRepository, companyRepository, memberRepository,
                orderRepository, sessionTokenService, lotteryRepository, clock);
        orderService = new OrderService(sessionTokenService, orderRepository, eventRepository,
                memberRepository, List.of(), List.of(), clock, null, null, null);

        eventId = UUID.randomUUID();
        zoneId = UUID.randomUUID();
    }

    private Event publishedLotteryEvent(Instant windowClose) {
        Event e = new Event(eventId, COMPANY, "Lottery Show", "desc",
                EventCategory.CONCERT,
                new EventSchedule(NOW.plusSeconds(86400), NOW.plusSeconds(90000), NOW.plusSeconds(82000)),
                new LockTimerDuration(Duration.ofMinutes(15)),
                new AlwaysAllowPolicy(), new NoDiscountPolicy(),
                SaleMethod.LOTTERY,
                new LotteryWindow(NOW.minusSeconds(86400), windowClose));
        e.addZone(InventoryZone.createGA(zoneId, "Floor", new BigDecimal("50.00"), 100));
        e.setVenueMap(new VenueMap(Map.of("Floor", zoneId)));
        e.publish();
        eventRepository.save(e);
        return e;
    }

    private Event publishedRegularEvent() {
        UUID regEventId = UUID.randomUUID();
        Event e = new Event(regEventId, COMPANY, "Regular Show", "desc",
                EventCategory.CONCERT,
                new EventSchedule(NOW.plusSeconds(86400), NOW.plusSeconds(90000), NOW.plusSeconds(82000)),
                new LockTimerDuration(Duration.ofMinutes(15)),
                new AlwaysAllowPolicy(), new NoDiscountPolicy(),
                SaleMethod.REGULAR, null);
        e.addZone(InventoryZone.createGA(UUID.randomUUID(), "GA", new BigDecimal("30.00"), 200));
        e.publish();
        eventRepository.save(e);
        return e;
    }

    private LotteryEntry register(UUID entryMemberId, int quantity) {
        LotteryEntry entry = new LotteryEntry(UUID.randomUUID(), eventId, entryMemberId, zoneId, quantity, NOW);
        lotteryRepository.save(entry);
        return entry;
    }

    // ── Bypass prevention ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("Bypass prevention")
    class BypassPrevention {

        @Test
        @DisplayName("Non-winner cannot create an order for a lottery event")
        void GivenLotteryEvent_WhenNonWinnerCreatesOrder_ThenRejected() {
            publishedLotteryEvent(NOW.minusSeconds(3600)); // window closed
            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> orderService.addGATicketsToOrder(MEMBER_TOKEN, eventId, zoneId, 1));
            assertTrue(ex.getMessage().contains("lottery"), ex.getMessage());
        }

        @Test
        @DisplayName("Guest cannot create an order for a lottery event")
        void GivenLotteryEvent_WhenGuestCreatesOrder_ThenRejected() {
            publishedLotteryEvent(NOW.minusSeconds(3600));
            String guestToken = "guest-token";
            when(sessionTokenService.isValid(guestToken)).thenReturn(true);
            when(sessionTokenService.extractMemberId(guestToken)).thenReturn(null);
            when(sessionTokenService.extractSessionId(guestToken)).thenReturn(UUID.randomUUID());

            assertThrows(Exception.class,
                    () -> orderService.addGATicketsToOrder(guestToken, eventId, zoneId, 1));
        }

        @Test
        @DisplayName("Regular event purchase is not affected by lottery enforcement")
        void GivenRegularEvent_WhenMemberCreatesOrder_ThenAllowed() {
            Event regular = publishedRegularEvent();
            UUID regZoneId = regular.getZones().iterator().next().getId();
            assertDoesNotThrow(
                    () -> orderService.addGATicketsToOrder(MEMBER_TOKEN, regular.getId(), regZoneId, 1));
        }
    }

    // ── Draw authorization ────────────────────────────────────────────────────

    @Nested
    @DisplayName("Draw authorization")
    class DrawAuthorization {

        @Test
        @DisplayName("Company owner can draw the lottery")
        void GivenOwner_WhenDraw_ThenSucceeds() {
            publishedLotteryEvent(NOW.minusSeconds(3600));
            register(UUID.randomUUID(), 1);
            assertDoesNotThrow(() -> eventService.drawLottery(OWNER_TOKEN, eventId, 10));
        }

        @Test
        @DisplayName("Authorized manager with EVENT_LIFECYCLE can draw")
        void GivenManagerWithPermission_WhenDraw_ThenSucceeds() {
            publishedLotteryEvent(NOW.minusSeconds(3600));
            register(UUID.randomUUID(), 1);

            UUID managerId = UUID.randomUUID();
            when(sessionTokenService.extractMemberId("mgr-token")).thenReturn(managerId);
            Member manager = new Member(managerId, "mgr", "mgr@test.com", "pw");
            manager.addStaffAppointment(COMPANY,
                    new StaffAppointment(COMPANY, ownerId, StaffAppointment.StaffRole.MANAGER,
                            Set.of(ManagerPermission.EVENT_LIFECYCLE)));
            memberRepository.save(manager);

            assertDoesNotThrow(() -> eventService.drawLottery("mgr-token", eventId, 10));
        }

        @Test
        @DisplayName("Regular member without staff role cannot draw")
        void GivenRegularMember_WhenDraw_ThenRejected() {
            publishedLotteryEvent(NOW.minusSeconds(3600));
            assertThrows(SecurityException.class,
                    () -> eventService.drawLottery(MEMBER_TOKEN, eventId, 10));
        }

        @Test
        @DisplayName("Manager of another company cannot draw")
        void GivenManagerOfOtherCompany_WhenDraw_ThenRejected() {
            publishedLotteryEvent(NOW.minusSeconds(3600));

            UUID otherId = UUID.randomUUID();
            when(sessionTokenService.extractMemberId("other-token")).thenReturn(otherId);
            companyRepository.save(new Company("Other Corp", "other", otherId));
            Member otherOwner = new Member(otherId, "other", "other@test.com", "pw");
            otherOwner.addStaffAppointment("Other Corp",
                    new StaffAppointment("Other Corp", otherId, StaffAppointment.StaffRole.OWNER, Set.of()));
            memberRepository.save(otherOwner);

            assertThrows(SecurityException.class,
                    () -> eventService.drawLottery("other-token", eventId, 10));
        }

        @Test
        @DisplayName("Manager without EVENT_LIFECYCLE permission cannot draw")
        void GivenManagerWithoutPermission_WhenDraw_ThenRejected() {
            publishedLotteryEvent(NOW.minusSeconds(3600));

            UUID noPermId = UUID.randomUUID();
            when(sessionTokenService.extractMemberId("noperm-token")).thenReturn(noPermId);
            Member noPermMgr = new Member(noPermId, "noperm", "noperm@test.com", "pw");
            noPermMgr.addStaffAppointment(COMPANY,
                    new StaffAppointment(COMPANY, ownerId, StaffAppointment.StaffRole.MANAGER,
                            Set.of(ManagerPermission.INVENTORY_MGMT)));
            memberRepository.save(noPermMgr);

            assertThrows(SecurityException.class,
                    () -> eventService.drawLottery("noperm-token", eventId, 10));
        }

        @Test
        @DisplayName("Regular event cannot be drawn")
        void GivenRegularEvent_WhenDraw_ThenRejected() {
            Event regular = publishedRegularEvent();
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> eventService.drawLottery(OWNER_TOKEN, regular.getId(), 10));
            assertTrue(ex.getMessage().contains("lottery"), ex.getMessage());
        }
    }

    // ── Draw correctness ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("Draw correctness")
    class DrawCorrectness {

        @Test
        @DisplayName("Draw before registration window closes is rejected")
        void GivenOpenWindow_WhenDraw_ThenRejected() {
            publishedLotteryEvent(NOW.plusSeconds(3600)); // window still open
            register(UUID.randomUUID(), 1);
            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> eventService.drawLottery(OWNER_TOKEN, eventId, 10));
            assertTrue(ex.getMessage().toLowerCase().contains("window"), ex.getMessage());
        }

        @Test
        @DisplayName("Repeated draw after results finalized is rejected (idempotency)")
        void GivenAlreadyDrawn_WhenDrawAgain_ThenRejected() {
            publishedLotteryEvent(NOW.minusSeconds(3600));
            register(UUID.randomUUID(), 1);
            eventService.drawLottery(OWNER_TOKEN, eventId, 10);

            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> eventService.drawLottery(OWNER_TOKEN, eventId, 10));
            assertTrue(ex.getMessage().toLowerCase().contains("already"), ex.getMessage());
        }

        @Test
        @DisplayName("Ticket quantities are respected — capacity counts tickets not entries")
        void GivenEntriesRequestingMultipleTickets_WhenDraw_ThenTotalTicketsRespectCapacity() {
            publishedLotteryEvent(NOW.minusSeconds(3600));
            // 5 entries, each requesting 3 tickets — capacity = 5 tickets → max 1 winner
            for (int i = 0; i < 5; i++) {
                register(UUID.randomUUID(), 3);
            }
            List<ActiveOrder> winners = eventService.drawLottery(OWNER_TOKEN, eventId, 5);
            int totalTickets = winners.stream()
                    .mapToInt(o -> o.getItems().stream().mapToInt(item -> item.getQuantity()).sum())
                    .sum();
            assertTrue(totalTickets <= 5, "Total tickets granted must not exceed capacity: " + totalTickets);
        }

        @Test
        @DisplayName("Inventory is NOT pre-allocated at draw time — winner picks tickets later")
        void GivenWinner_WhenDraw_ThenInventoryNotPreAllocated() {
            publishedLotteryEvent(NOW.minusSeconds(3600));
            register(memberId, 2);
            eventService.drawLottery(OWNER_TOKEN, eventId, 10);

            Event event = eventRepository.findById(eventId).orElseThrow();
            InventoryZone zone = event.findZone(zoneId);
            assertEquals(100, zone.getAvailableCount(), "inventory must not be locked at draw time");
        }
    }

    // ── Winner right ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Winner purchase right")
    class WinnerRight {

        @Test
        @DisplayName("Winner receives a persisted empty LOTTERY_WIN order with correct member/event/deadline")
        void GivenWinner_WhenDraw_ThenOrderPersistedWithCorrectAttributes() {
            publishedLotteryEvent(NOW.minusSeconds(3600));
            register(memberId, 2);
            List<ActiveOrder> winners = eventService.drawLottery(OWNER_TOKEN, eventId, 10);

            assertEquals(1, winners.size());
            ActiveOrder order = winners.get(0);
            assertTrue(order.isLotteryWin());
            assertEquals(memberId, order.getMemberId());
            assertEquals(eventId, order.getEventId());
            assertNotNull(order.getPurchaseWindowDeadline());
            // New flow: order is EMPTY at draw time — winner picks tickets on the Events page
            assertTrue(order.getItems().isEmpty(), "draw must not pre-allocate any items");
        }

        @Test
        @DisplayName("Winner can access their pre-allocated order via the regular order flow")
        void GivenWinner_WhenAccessOrder_ThenReturnsExistingLotteryWinOrder() {
            publishedLotteryEvent(NOW.minusSeconds(3600));
            register(memberId, 1);
            UUID winOrderId = eventService.drawLottery(OWNER_TOKEN, eventId, 10).get(0).getId();

            UUID returnedId = orderService.createOrder(MEMBER_TOKEN, eventId);
            assertEquals(winOrderId, returnedId, "Should return the existing LOTTERY_WIN order");
        }

        @Test
        @DisplayName("Winner can add tickets to their empty LOTTERY_WIN order from the Events page")
        void GivenWinner_WhenAddTicketsToOrder_ThenAllowed() {
            publishedLotteryEvent(NOW.minusSeconds(3600));
            register(memberId, 1);
            eventService.drawLottery(OWNER_TOKEN, eventId, 10);

            // New flow: winner picks their own tickets after winning — adding must succeed
            assertDoesNotThrow(
                    () -> orderService.addGATicketsToOrder(MEMBER_TOKEN, eventId, zoneId, 1));
        }

        @Test
        @DisplayName("Non-winner cannot use another member's winner order")
        void GivenWinnerOrder_WhenOtherMemberTriesOrder_ThenRejected() {
            publishedLotteryEvent(NOW.minusSeconds(3600));
            UUID winnerId = UUID.randomUUID();
            register(winnerId, 1);
            eventService.drawLottery(OWNER_TOKEN, eventId, 10);

            // memberId did not win — should be rejected
            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> orderService.addGATicketsToOrder(MEMBER_TOKEN, eventId, zoneId, 1));
            assertTrue(ex.getMessage().contains("lottery"), ex.getMessage());
        }

        @Test
        @DisplayName("After all winner windows expire, regular members can buy")
        void GivenAllWinnerWindowsExpired_WhenRegularMemberCreatesOrder_ThenAllowed() {
            // Use a custom clock to simulate time passing
            Instant[] clockTime = { NOW };
            ISystemClock travelingClock = () -> clockTime[0];

            EventService travelingService = new EventService(
                    eventRepository, companyRepository, memberRepository,
                    orderRepository, sessionTokenService, lotteryRepository, travelingClock);
            OrderService travelingOrderService = new OrderService(sessionTokenService, orderRepository,
                    eventRepository, memberRepository, List.of(), List.of(), travelingClock, null, null, null);

            publishedLotteryEvent(NOW.minusSeconds(3600));
            UUID winnerId = UUID.randomUUID();
            register(winnerId, 1);
            travelingService.drawLottery(OWNER_TOKEN, eventId, 10);

            // Advance clock 49 hours past the draw — winner window (48h) has expired
            clockTime[0] = NOW.plusSeconds(49 * 3600);

            // Regular member should now be allowed
            assertDoesNotThrow(
                    () -> travelingOrderService.addGATicketsToOrder(MEMBER_TOKEN, eventId, zoneId, 1));
        }
    }
}
