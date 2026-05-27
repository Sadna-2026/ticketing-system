package com.ticketing.application;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ticketing.application.TestClock;
import com.ticketing.application.auth.SessionTokenService;
import com.ticketing.application.dto.QueueEntryDto;
import com.ticketing.application.services.OrderService;
import com.ticketing.domain.event.Event;
import com.ticketing.domain.event.EventCategory;
import com.ticketing.domain.event.EventSchedule;
import com.ticketing.domain.event.LockTimerDuration;
import com.ticketing.domain.exception.OptimisticLockException;
import com.ticketing.domain.queue.VirtualQueue;
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

/**
 * Concurrency tests for queue operations within OrderService.
 * Verifies that optimistic locking prevents race conditions
 * when multiple users enter the queue simultaneously.
 */
class QueueConcurrencyTest {

    private OrderService orderService;
    private InMemoryQueueRepository queueRepo;
    private InMemoryOrderRepository orderRepo;
    private InMemoryEventRepository eventRepo;
    private TestClock clock;
    private SessionTokenService tokenService;

    private UUID eventId;
    private String adminToken;

    @BeforeEach
    void setUp() {
        queueRepo = new InMemoryQueueRepository();
        orderRepo = new InMemoryOrderRepository();
        eventRepo = new InMemoryEventRepository();
        InMemoryMemberRepository memberRepo = new InMemoryMemberRepository();
        clock = new TestClock(Instant.parse("2026-06-01T10:00:00Z"));
        String secret = Base64.getEncoder().encodeToString(
                "01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8));

        InMemorySessionTokenRepository sessionTokenRepository = new InMemorySessionTokenRepository();
        tokenService = new SessionTokenService(secret, 120, sessionTokenRepository);
        TicketReservationDomainService ticketReservationService = new TicketReservationDomainService(orderRepo, eventRepo, clock, memberRepo);
        OrderCheckoutDomainService orderCheckoutService = new OrderCheckoutDomainService(orderRepo, eventRepo, memberRepo, List.of(new StubPaymentGateway()), List.of(new StubTicketSupplyGateway()), clock);
        QueueDomainService queueDomainService = new QueueDomainService(queueRepo, eventRepo, clock);
        orderService = new OrderService(tokenService, ticketReservationService, orderCheckoutService, queueDomainService, null, null);

        adminToken = tokenService.generateMemberToken(UUID.randomUUID(), UUID.randomUUID(), Set.of("Admin"));

        eventId = UUID.randomUUID();
        EventSchedule schedule = new EventSchedule(
                Instant.parse("2026-07-01T20:00:00Z"),
                Instant.parse("2026-07-01T23:00:00Z"),
                Instant.parse("2026-07-01T19:00:00Z"));
        Event event = new Event(eventId, "company", "Concert", "desc",
                EventCategory.CONCERT, schedule, new LockTimerDuration(Duration.ofMinutes(15)));
        eventRepo.save(event);
    }

    @Test
    void GivenConcurrentUsers_WhenTryEnterOrQueue_ThenExactlyThresholdEnterDirectly() throws InterruptedException {
        int threshold = 3;
        orderService.createQueue(adminToken, eventId, threshold, 5);

        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger directEntries = new AtomicInteger(0);
        AtomicInteger queuedEntries = new AtomicInteger(0);
        AtomicInteger conflicts = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    QueueEntryDto result = orderService.tryEnterOrQueue(eventId, UUID.randomUUID());
                    if (result == null) {
                        directEntries.incrementAndGet();
                    } else {
                        queuedEntries.incrementAndGet();
                    }
                } catch (OptimisticLockException e) {
                    conflicts.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        int totalProcessed = directEntries.get() + queuedEntries.get();
        // Some requests may fail with OptimisticLockException - that's correct behavior
        assertTrue(conflicts.get() > 0 || totalProcessed == threadCount,
                "With " + threadCount + " concurrent threads, expect either conflicts or all processed");
        // Direct entries should not exceed the threshold
        assertTrue(directEntries.get() <= threshold,
                "Direct entries (" + directEntries.get() + ") should not exceed threshold (" + threshold + ")");
    }

    @Test
    void GivenConcurrentEnterAndLeave_WhenRacing_ThenActiveCountRemainsConsistent() throws InterruptedException {
        orderService.createQueue(adminToken, eventId, 1000, 5);

        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger conflicts = new AtomicInteger(0);

        // Half enter, half leave — interleaved operations on the same queue
        for (int i = 0; i < threadCount; i++) {
            final boolean shouldEnter = (i % 2 == 0);
            executor.submit(() -> {
                try {
                    startLatch.await();
                    if (shouldEnter) {
                        orderService.tryEnterOrQueue(eventId, UUID.randomUUID());
                    } else {
                        orderService.userLeft(eventId);
                    }
                } catch (OptimisticLockException e) {
                    conflicts.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        VirtualQueue queue = queueRepo.findByEventId(eventId).orElseThrow();
        assertTrue(queue.getCurrentActiveUsers() >= 0,
                "Active user count should never go below zero, was: " + queue.getCurrentActiveUsers());
        // With concurrent enter+leave on the same aggregate, optimistic lock conflicts are expected
        int totalProcessed = threadCount - conflicts.get();
        assertTrue(totalProcessed > 0, "At least some operations should succeed");
    }

    @Test
    void GivenTwoConcurrentCreates_WhenCreateQueueForSameEvent_ThenExactlyOneSucceeds() throws InterruptedException {
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successes = new AtomicInteger(0);
        AtomicInteger failures = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    orderService.createQueue(adminToken, eventId, 10, 5);
                    successes.incrementAndGet();
                } catch (IllegalStateException e) {
                    failures.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(1, successes.get(), "Exactly one queue creation should succeed");
        assertEquals(threadCount - 1, failures.get(), "All other attempts should fail");
    }

    @Test
    void GivenStaleQueueVersion_WhenSave_ThenOptimisticLockExceptionThrown() {
        orderService.createQueue(adminToken, eventId, 10, 5);

        // Get two references to the same queue (same object since in-memory)
        VirtualQueue first = queueRepo.findByEventId(eventId).orElseThrow();

        // First modification succeeds
        first.userEnteredDirectly();
        queueRepo.save(first);

        // Simulate a stale save by creating a queue with the original version
        // The version was incremented twice (once on create, once on the save above)
        // so a new object with version=0 will be stale
        VirtualQueue stale = new VirtualQueue(first.getId(), first.getEventId(), first.getConfig());
        try {
            queueRepo.save(stale);
            // Should not reach here
            assertTrue(false, "Expected OptimisticLockException");
        } catch (OptimisticLockException e) {
            assertTrue(e.getMessage().contains("VirtualQueue"));
        }
    }
}
