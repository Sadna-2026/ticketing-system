package com.ticketing.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.ticketing.application.auth.ISessionTokenService;
import com.ticketing.application.auth.SessionTokenService;
import com.ticketing.application.dto.QueueEntryDto;
import com.ticketing.application.dto.VirtualQueueDto;
import com.ticketing.application.services.OrderService;
import com.ticketing.domain.admin.Admin;
import com.ticketing.domain.admin.IAdminRepository;
import com.ticketing.domain.event.Event;
import com.ticketing.domain.event.EventCategory;
import com.ticketing.domain.event.EventSchedule;
import com.ticketing.domain.event.LockTimerDuration;
import com.ticketing.infrastructure.InMemoryAdminRepository;
import com.ticketing.domain.order.OrderCheckoutDomainService;
import com.ticketing.domain.order.TicketReservationDomainService;
import com.ticketing.domain.services.QueueDomainService;
import com.ticketing.infrastructure.InMemoryEventRepository;
import com.ticketing.infrastructure.InMemoryMemberRepository;
import com.ticketing.infrastructure.InMemoryOrderRepository;
import com.ticketing.infrastructure.InMemoryQueueRepository;
import com.ticketing.infrastructure.InMemorySessionTokenRepository;
import com.ticketing.infrastructure.gateway.StubPaymentGateway;
import com.ticketing.infrastructure.gateway.StubTicketSupplyGateway;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for queue management operations within OrderService:
 * queue creation, entering/queuing, batch admission,
 * config updates, flushing, and monitoring.
 */
class QueueServiceTest {

    private OrderService orderService;
    private InMemoryQueueRepository queueRepo;
    private InMemoryOrderRepository orderRepo;
    private InMemoryEventRepository eventRepo;
    private InMemoryMemberRepository memberRepo;
    private IAdminRepository adminRepo;
    private TestClock clock;
    private SessionTokenService tokenService;
    private InMemorySessionTokenRepository sessionTokenRepository;

    private UUID adminId;
    private UUID eventId;
    private String adminToken;

    @BeforeEach
    void setUp() {
        queueRepo = new InMemoryQueueRepository();
        orderRepo = new InMemoryOrderRepository();
        eventRepo = new InMemoryEventRepository();
        memberRepo = new InMemoryMemberRepository();
        adminRepo = new InMemoryAdminRepository();
        clock = new TestClock(Instant.parse("2026-06-01T10:00:00Z"));
        String secret = Base64.getEncoder().encodeToString(
                "01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8)
        );

        sessionTokenRepository = new InMemorySessionTokenRepository();
        tokenService = new SessionTokenService(secret, 120, sessionTokenRepository);
        TicketReservationDomainService ticketReservationService = new TicketReservationDomainService(orderRepo, eventRepo, clock);
        OrderCheckoutDomainService orderCheckoutService = new OrderCheckoutDomainService(orderRepo, eventRepo, memberRepo, List.of(new StubPaymentGateway()), List.of(new StubTicketSupplyGateway()), clock);
        QueueDomainService queueDomainService = new QueueDomainService(queueRepo, eventRepo, clock);
        orderService = new OrderService(tokenService, ticketReservationService, orderCheckoutService, queueDomainService, null, null);

        // Admin
        adminId = UUID.randomUUID();
        Admin admin = new Admin(adminId, "admin", "admin@mail.com");
        adminRepo.save(admin);
        adminToken = tokenService.generateMemberToken(UUID.randomUUID(), adminId, Set.of("Admin"));

        // Event
        eventId = UUID.randomUUID();
        EventSchedule schedule = new EventSchedule(
                Instant.parse("2026-07-01T20:00:00Z"),
                Instant.parse("2026-07-01T23:00:00Z"),
                Instant.parse("2026-07-01T19:00:00Z"));
        Event event = new Event(eventId, "company", "myConcert", "Concert",
                EventCategory.CONCERT, schedule, new LockTimerDuration(Duration.ofMinutes(15)));
        eventRepo.save(event);
    }

    @Test
    void GivenAdmin_WhenCreateQueue_ThenQueueCreated() {
        UUID queueId = orderService.createQueue(adminToken, eventId, 10, 5);
        assertNotNull(queueId);
    }

    @Test
    void GivenExistingQueue_WhenCreateDuplicate_ThenThrows() {
        orderService.createQueue(adminToken, eventId, 10, 5);
        assertThrows(IllegalStateException.class,
                () -> orderService.createQueue(adminToken, eventId, 10, 5));
    }

    @Test
    void GivenNoQueue_WhenTryEnter_ThenReturnsNull() {
        QueueEntryDto result = orderService.tryEnterOrQueue(eventId, UUID.randomUUID());
        assertNull(result);
    }

    @Test
    void GivenQueueBelowThreshold_WhenTryEnter_ThenEntersDirectly() {
        orderService.createQueue(adminToken, eventId, 10, 5);
        QueueEntryDto result = orderService.tryEnterOrQueue(eventId, UUID.randomUUID());
        assertNull(result); // Below threshold, enters directly
    }

    @Test
    void GivenQueueAtThreshold_WhenTryEnter_ThenQueued() {
        orderService.createQueue(adminToken, eventId, 2, 1);

        // Fill to threshold
        orderService.tryEnterOrQueue(eventId, UUID.randomUUID());
        orderService.tryEnterOrQueue(eventId, UUID.randomUUID());

        // Third user should be queued
        QueueEntryDto result = orderService.tryEnterOrQueue(eventId, UUID.randomUUID());
        assertNotNull(result);
        assertEquals("WAITING", result.getStatus());
    }

    @Test
    void GivenQueuedUsers_WhenAdmitBatch_ThenUsersAdmitted() {
        orderService.createQueue(adminToken, eventId, 1, 2);

        // Fill to threshold, then queue 3 users
        orderService.tryEnterOrQueue(eventId, UUID.randomUUID()); // direct
        orderService.tryEnterOrQueue(eventId, UUID.randomUUID()); // queued
        orderService.tryEnterOrQueue(eventId, UUID.randomUUID()); // queued
        orderService.tryEnterOrQueue(eventId, UUID.randomUUID()); // queued

        List<QueueEntryDto> admitted = orderService.admitNextBatch(adminToken, eventId);
        assertEquals(2, admitted.size()); // flowRate = 2
    }

    @Test
    void GivenQueue_WhenUserLeft_ThenActiveCountDecreases() {
        orderService.createQueue(adminToken, eventId, 10, 5);
        orderService.tryEnterOrQueue(eventId, UUID.randomUUID()); // enters directly
        orderService.userLeft(eventId);

        VirtualQueueDto queue = orderService.getQueueForEvent(eventId);
        assertEquals(0, queue.getCurrentActiveUsers());
    }

    @Test
    void GivenQueue_WhenUpdateConfig_ThenConfigUpdated() {
        orderService.createQueue(adminToken, eventId, 10, 5);
        orderService.updateQueueConfig(adminToken, eventId, 20, 10);

        VirtualQueueDto queue = orderService.getQueueForEvent(eventId);
        assertEquals(20, queue.getThreshold());
        assertEquals(10, queue.getFlowRate());
    }

    @Test
    void GivenQueuedUsers_WhenFlush_ThenQueueCleared() {
        orderService.createQueue(adminToken, eventId, 1, 1);
        orderService.tryEnterOrQueue(eventId, UUID.randomUUID()); // direct
        orderService.tryEnterOrQueue(eventId, UUID.randomUUID()); // queued
        orderService.tryEnterOrQueue(eventId, UUID.randomUUID()); // queued

        orderService.flushQueue(adminToken, eventId);

        VirtualQueueDto queue = orderService.getQueueForEvent(eventId);
        assertEquals(0, queue.getWaitingCount());
    }

    @Test
    void GivenActiveQueues_WhenGetAll_ThenReturnsActive() {
        orderService.createQueue(adminToken, eventId, 10, 5);
        List<VirtualQueueDto> queues = orderService.getAllActiveQueues(adminToken);
        assertEquals(1, queues.size());
    }
}
