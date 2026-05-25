package com.ticketing.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.ticketing.application.auth.ISessionTokenService;
import com.ticketing.application.auth.SessionTokenService;
import com.ticketing.application.dto.QueueEntryDto;
import com.ticketing.application.dto.VirtualQueueDto;
import com.ticketing.application.services.QueueManagementService;
import com.ticketing.domain.admin.Admin;
import com.ticketing.domain.admin.IAdminRepository;
import com.ticketing.domain.event.Event;
import com.ticketing.domain.event.EventCategory;
import com.ticketing.domain.event.EventDiscountPolicy;
import com.ticketing.domain.event.EventPurchasePolicy;
import com.ticketing.domain.event.EventSchedule;
import com.ticketing.domain.event.LockTimerDuration;
import com.ticketing.domain.member.ContactInfo;
import com.ticketing.domain.member.Member;
import com.ticketing.infrastructure.InMemoryAdminRepository;
import com.ticketing.infrastructure.InMemoryEventRepository;
import com.ticketing.infrastructure.InMemoryMemberRepository;
import com.ticketing.infrastructure.InMemoryQueueRepository;
import com.ticketing.infrastructure.InMemorySessionTokenRepository;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for QueueService: queue creation, entering/queuing, batch admission,
 * config updates, flushing, and monitoring.
 */
class QueueServiceTest {

    private QueueManagementService queueService;
    private InMemoryQueueRepository queueRepo;
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
        eventRepo = new InMemoryEventRepository();
        memberRepo = new InMemoryMemberRepository();
        adminRepo = new InMemoryAdminRepository();
        clock = new TestClock(Instant.parse("2026-06-01T10:00:00Z"));
        String secret = Base64.getEncoder().encodeToString(
                "01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8)
        );

        sessionTokenRepository = new InMemorySessionTokenRepository();
        tokenService = new SessionTokenService(secret, 120, sessionTokenRepository);
        queueService = new QueueManagementService(queueRepo, eventRepo, memberRepo, tokenService, clock);

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
        //EventPurchasePolicy pp = EventPurchasePolicy.createDefault(UUID.randomUUID());
        //EventDiscountPolicy dp = new EventDiscountPolicy(UUID.randomUUID());
        Event event = new Event(eventId, "company", "myConcert", "Concert",
                EventCategory.CONCERT, schedule, new LockTimerDuration(Duration.ofMinutes(15)));
        eventRepo.save(event);
    }

    @Test
    void GivenAdmin_WhenCreateQueue_ThenQueueCreated() {
        UUID queueId = queueService.createQueue(adminToken, eventId, 10, 5);
        assertNotNull(queueId);
    }

    @Test
    void GivenExistingQueue_WhenCreateDuplicate_ThenThrows() {
        queueService.createQueue(adminToken, eventId, 10, 5);
        assertThrows(IllegalStateException.class,
                () -> queueService.createQueue(adminToken, eventId, 10, 5));
    }

    @Test
    void GivenNoQueue_WhenTryEnter_ThenReturnsNull() {
        QueueEntryDto result = queueService.tryEnterOrQueue(eventId, UUID.randomUUID());
        assertNull(result);
    }

    @Test
    void GivenQueueBelowThreshold_WhenTryEnter_ThenEntersDirectly() {
        queueService.createQueue(adminToken, eventId, 10, 5);
        QueueEntryDto result = queueService.tryEnterOrQueue(eventId, UUID.randomUUID());
        assertNull(result); // Below threshold, enters directly
    }

    @Test
    void GivenQueueAtThreshold_WhenTryEnter_ThenQueued() {
        queueService.createQueue(adminToken, eventId, 2, 1);

        // Fill to threshold
        queueService.tryEnterOrQueue(eventId, UUID.randomUUID());
        queueService.tryEnterOrQueue(eventId, UUID.randomUUID());

        // Third user should be queued
        QueueEntryDto result = queueService.tryEnterOrQueue(eventId, UUID.randomUUID());
        assertNotNull(result);
        assertEquals("WAITING", result.getStatus());
    }

    @Test
    void GivenQueuedUsers_WhenAdmitBatch_ThenUsersAdmitted() {
        queueService.createQueue(adminToken, eventId, 1, 2);

        // Fill to threshold, then queue 3 users
        queueService.tryEnterOrQueue(eventId, UUID.randomUUID()); // direct
        queueService.tryEnterOrQueue(eventId, UUID.randomUUID()); // queued
        queueService.tryEnterOrQueue(eventId, UUID.randomUUID()); // queued
        queueService.tryEnterOrQueue(eventId, UUID.randomUUID()); // queued

        List<QueueEntryDto> admitted = queueService.admitNextBatch(adminToken, eventId);
        assertEquals(2, admitted.size()); // flowRate = 2
    }

    @Test
    void GivenQueue_WhenUserLeft_ThenActiveCountDecreases() {
        queueService.createQueue(adminToken, eventId, 10, 5);
        queueService.tryEnterOrQueue(eventId, UUID.randomUUID()); // enters directly
        queueService.userLeft(eventId);

        VirtualQueueDto queue = queueService.getQueueForEvent(eventId);
        assertEquals(0, queue.getCurrentActiveUsers());
    }

    @Test
    void GivenQueue_WhenUpdateConfig_ThenConfigUpdated() {
        queueService.createQueue(adminToken, eventId, 10, 5);
        queueService.updateConfig(adminToken, eventId, 20, 10);

        VirtualQueueDto queue = queueService.getQueueForEvent(eventId);
        assertEquals(20, queue.getThreshold());
        assertEquals(10, queue.getFlowRate());
    }

    @Test
    void GivenQueuedUsers_WhenFlush_ThenQueueCleared() {
        queueService.createQueue(adminToken, eventId, 1, 1);
        queueService.tryEnterOrQueue(eventId, UUID.randomUUID()); // direct
        queueService.tryEnterOrQueue(eventId, UUID.randomUUID()); // queued
        queueService.tryEnterOrQueue(eventId, UUID.randomUUID()); // queued

        queueService.flushQueue(adminToken, eventId);

        VirtualQueueDto queue = queueService.getQueueForEvent(eventId);
        assertEquals(0, queue.getWaitingCount());
    }

    @Test
    void GivenActiveQueues_WhenGetAll_ThenReturnsActive() {
        queueService.createQueue(adminToken, eventId, 10, 5);
        List<VirtualQueueDto> queues = queueService.getAllActiveQueues(adminToken);
        assertEquals(1, queues.size());
    }

    // @Test
    // void GivenNonAdmin_WhenCreateQueue_ThenThrows() {
    //     UUID nonAdminId = UUID.randomUUID();
    //     Member nonAdmin = new Member(nonAdminId, new ContactInfo("user@test.com", "U", "S", null, null),
    //             "hash", clock.now());
    //     memberRepo.save(nonAdmin);
    //     tokenService.setMemberId(nonAdminId);

    //     assertThrows(IllegalStateException.class,
    //             () -> queueService.createQueue("t-" + nonAdminId, eventId, 10, 5));
    // }
}

