package com.ticketing.application;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ticketing.application.auth.SessionTokenService;
import com.ticketing.application.services.AdminService;
import com.ticketing.application.services.OrderService;
import com.ticketing.domain.admin.Admin;
import com.ticketing.domain.event.Event;
import com.ticketing.domain.event.EventCategory;
import com.ticketing.domain.event.EventSchedule;
import com.ticketing.domain.event.InventoryZone;
import com.ticketing.domain.event.LockTimerDuration;
import com.ticketing.domain.member.Member;
import com.ticketing.domain.member.Suspension;
import com.ticketing.domain.order.OrderStatus;
import com.ticketing.domain.services.OrderTimeDomainService;
import com.ticketing.infrastructure.InMemoryAdminRepository;
import com.ticketing.infrastructure.InMemoryCompanyRepository;
import com.ticketing.infrastructure.InMemoryEventRepository;
import com.ticketing.infrastructure.InMemoryMemberRepository;
import com.ticketing.infrastructure.InMemoryOrderRepository;
import com.ticketing.infrastructure.InMemorySessionTokenRepository;
import com.ticketing.infrastructure.gateway.StubPaymentGateway;
import com.ticketing.infrastructure.gateway.StubTicketSupplyGateway;

@DisplayName("Suspension end-to-end acceptance test")
class SuspensionLifecycleAcceptanceTest {

    private AdminService adminService;
    private OrderService orderService;
    private SessionTokenService tokenService;

    private InMemoryMemberRepository memberRepo;
    private InMemoryEventRepository eventRepo;
    private InMemoryOrderRepository orderRepo;

    private UUID adminId;
    private String adminToken;

    private UUID targetUserId;
    private String targetUserToken;

    private UUID eventId;
    private UUID gaZoneId;

    private TestClock clock;

    @BeforeEach
    void setUp() {
        memberRepo = new InMemoryMemberRepository();
        eventRepo = new InMemoryEventRepository();
        orderRepo = new InMemoryOrderRepository();
        InMemoryCompanyRepository companyRepo = new InMemoryCompanyRepository();
        InMemoryAdminRepository adminRepo = new InMemoryAdminRepository();
        InMemorySessionTokenRepository tokenRepo = new InMemorySessionTokenRepository();

        String secret = Base64.getEncoder().encodeToString(
                "01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8));
        tokenService = new SessionTokenService(secret, 120, tokenRepo);

        clock = new TestClock(Instant.now());
        OrderTimeDomainService orderTimeDomainService = new OrderTimeDomainService(orderRepo, eventRepo, clock);

        orderService = new OrderService(tokenService, orderRepo, eventRepo, memberRepo,
                List.of(new StubPaymentGateway()), List.of(new StubTicketSupplyGateway()), clock, null,
                orderTimeDomainService, null);
        adminService = new AdminService(memberRepo, companyRepo, tokenService, adminRepo, orderRepo,
                null, null, orderTimeDomainService);

        // Create Admin
        adminId = UUID.randomUUID();
        Admin admin = new Admin(adminId, "sysadmin", "admin@ticketing.com", "encPw");
        adminRepo.save(admin);
        adminToken = tokenService.generateMemberToken(UUID.randomUUID(), adminId, Set.of("SYSTEM_ADMIN"));

        // Create target user
        targetUserId = UUID.randomUUID();
        Member targetUser = new Member(targetUserId, "user1", "user1@ticketing.com", "encPw");
        memberRepo.save(targetUser);
        targetUserToken = tokenService.generateMemberToken(UUID.randomUUID(), targetUserId, Set.of("MEMBER"));

        // Create event
        eventId = UUID.randomUUID();
        EventSchedule schedule = new EventSchedule(
                Instant.parse("2026-07-01T20:00:00Z"),
                Instant.parse("2026-07-01T23:00:00Z"),
                Instant.parse("2026-07-01T19:00:00Z"));
        Event event = new Event(eventId, "company", "Concert", "desc",
                EventCategory.CONCERT, schedule, new LockTimerDuration(Duration.ofMinutes(15)));

        gaZoneId = UUID.randomUUID();
        event.addZone(InventoryZone.createGA(gaZoneId, "Floor", new BigDecimal("50.00"), 100));
        event.publish();
        eventRepo.save(event);
    }

    @Test
    @DisplayName("suspend -> reserve fails -> cancel suspension -> reserve succeeds")
    void GivenMember_WhenSuspendedAndUnsuspended_ThenReservationFailsThenSucceeds() {
        // Admin suspends the user
        Suspension suspension = adminService.suspendUser(adminToken, targetUserId, Duration.ofDays(7),
                "Rule violation");
        assertNotNull(suspension);

        clock.advance(Duration.ofSeconds(1));

        // The user tries to reserve a ticket and fails
        IllegalStateException ex1 = assertThrows(IllegalStateException.class,
                () -> orderService.createOrder(targetUserToken, eventId));
        assertTrue(ex1.getMessage().contains("suspended"));

        // View-only actions are allowed throughout suspension
        Member savedUser = memberRepo.findById(targetUserId).orElseThrow();
        assertNotNull(savedUser.getUsername());
        assertDoesNotThrow(() -> savedUser.getSuspensions());

        // Admin unsuspends the user
        adminService.cancelSuspension(adminToken, targetUserId, suspension.getSuspensionId());

        // The user tries to reserve a ticket and succeeds
        UUID orderId = assertDoesNotThrow(() -> orderService.createOrder(targetUserToken, eventId));
        assertNotNull(orderId);

        UUID itemId = assertDoesNotThrow(() -> orderService.addGATicketsToOrder(targetUserToken, eventId, gaZoneId, 1));
        assertNotNull(itemId);
    }

    @Test
    @DisplayName("suspend clears active order and releases reserved tickets")
    void GivenMemberWithActiveCart_WhenSuspended_ThenOrderClearedAndInventoryReleased() {
        UUID orderId = orderService.createOrder(targetUserToken, eventId);
        orderService.addGATicketsToOrder(targetUserToken, eventId, gaZoneId, 2);

        Event before = eventRepo.findById(eventId).orElseThrow();
        int availableAfterReserve = before.findZone(gaZoneId).getAvailableCount();

        adminService.suspendUser(adminToken, targetUserId, Duration.ofDays(7), "Rule violation");

        assertNull(orderService.getActiveOrder(targetUserToken));
        assertEquals(OrderStatus.CANCELLED, orderRepo.findById(orderId).orElseThrow().getStatus());

        Event after = eventRepo.findById(eventId).orElseThrow();
        assertEquals(availableAfterReserve + 2, after.findZone(gaZoneId).getAvailableCount());
    }
}
