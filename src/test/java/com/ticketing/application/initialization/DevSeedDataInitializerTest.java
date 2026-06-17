package com.ticketing.application.initialization;

import static com.ticketing.application.initialization.DevSeedDataInitializer.AND_POLICY_EVENT_ID;
import static com.ticketing.application.initialization.DevSeedDataInitializer.CONCERT_ID;
import static com.ticketing.application.initialization.DevSeedDataInitializer.COUPON_CHECKOUT_EVENT_ID;
import static com.ticketing.application.initialization.DevSeedDataInitializer.MIN_QTY_EVENT_ID;
import static com.ticketing.application.initialization.DevSeedDataInitializer.MIXED_LIMITED_EVENT_ID;
import static com.ticketing.application.initialization.DevSeedDataInitializer.SEEDED_ANALYTICS_ACTIVE_VISITORS;
import static com.ticketing.application.initialization.DevSeedDataInitializer.SEEDED_ANALYTICS_PURCHASES;
import static com.ticketing.application.initialization.DevSeedDataInitializer.SEEDED_ANALYTICS_REGISTRATIONS;
import static com.ticketing.application.initialization.DevSeedDataInitializer.SEEDED_ANALYTICS_RESERVATIONS;
import static com.ticketing.application.initialization.DevSeedDataInitializer.SEEDED_ANALYTICS_VISITOR_ENTERS;
import static com.ticketing.application.initialization.DevSeedDataInitializer.SEEDED_ANALYTICS_VISITOR_EXITS;
import static com.ticketing.application.initialization.DevSeedDataInitializer.SIMPLE_DISCOUNT_EVENT_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.ticketing.application.TestClock;
import com.ticketing.application.auth.ISessionTokenService;
import com.ticketing.application.services.AdminService;
import com.ticketing.application.services.SystemAnalyticsCollector;
import com.ticketing.application.services.SystemAnalyticsCollector.Snapshot;
import com.ticketing.infrastructure.InMemoryAdminRepository;
import com.ticketing.infrastructure.InMemoryCompanyRepository;
import com.ticketing.infrastructure.InMemoryEventRepository;
import com.ticketing.infrastructure.InMemoryMemberRepository;
import com.ticketing.infrastructure.InMemoryOrderRepository;
import com.ticketing.infrastructure.InMemoryQueueRepository;
import com.ticketing.infrastructure.PasswordEncryptionUtils;

class DevSeedDataInitializerTest {

    @Test
    void GivenEmptyRepositories_WhenSeedRuns_ThenKeyDemoEventsAreCreated() {
        InMemoryEventRepository eventRepository = new InMemoryEventRepository();
        DevSeedDataInitializer initializer = initializer(eventRepository, new InMemoryAdminRepository());

        initializer.runSeed();

        assertTrue(eventRepository.findById(CONCERT_ID).isPresent());
        assertTrue(eventRepository.findById(COUPON_CHECKOUT_EVENT_ID).isPresent());
        assertTrue(eventRepository.findById(MIXED_LIMITED_EVENT_ID).isPresent());
        assertTrue(eventRepository.findById(MIN_QTY_EVENT_ID).isPresent());
        assertTrue(eventRepository.findById(AND_POLICY_EVENT_ID).isPresent());
        assertTrue(eventRepository.findById(SIMPLE_DISCOUNT_EVENT_ID).isPresent());
    }

    @Test
    void GivenEmptyRepositories_WhenSeedRuns_ThenAnalyticsQaUsersAreCreated() {
        InMemoryAdminRepository adminRepository = new InMemoryAdminRepository();
        DevSeedDataInitializer initializer = initializer(new InMemoryEventRepository(), adminRepository);

        initializer.runSeed();

        assertTrue(adminRepository.existsByUsername("admin"));
        assertTrue(adminRepository.existsByUsername("admin2"));
    }

    @Test
    void GivenSeedAlreadyApplied_WhenSeedRunsAgain_ThenEventsAreNotDuplicated() {
        InMemoryEventRepository eventRepository = new InMemoryEventRepository();
        DevSeedDataInitializer initializer = initializer(eventRepository, new InMemoryAdminRepository());

        initializer.runSeed();
        int eventsAfterFirst = eventRepository.findAll().size();
        initializer.runSeed();

        assertEquals(eventsAfterFirst, eventRepository.findAll().size());
    }

    @Test
    void GivenCollector_WhenSeedRuns_ThenAnalyticsWarmupIsApplied() {
        TestClock clock = new TestClock(Instant.parse("2026-06-01T12:00:00Z"));
        SystemAnalyticsCollector collector = new SystemAnalyticsCollector(clock);
        DevSeedDataInitializer initializer = initializer(
                new InMemoryEventRepository(), new InMemoryAdminRepository(), collector);

        initializer.runSeed();

        Snapshot snapshot = collector.snapshot();
        assertEquals(SEEDED_ANALYTICS_ACTIVE_VISITORS, snapshot.activeVisitors());
        assertEquals(SEEDED_ANALYTICS_VISITOR_ENTERS, snapshot.historical().visitorEnter().count());
        assertEquals(SEEDED_ANALYTICS_VISITOR_EXITS, snapshot.historical().visitorExit().count());
        assertEquals(SEEDED_ANALYTICS_REGISTRATIONS, snapshot.historical().registration().count());
        assertEquals(SEEDED_ANALYTICS_RESERVATIONS, snapshot.historical().reservation().count());
        assertEquals(SEEDED_ANALYTICS_PURCHASES, snapshot.historical().purchase().count());
    }

    private static DevSeedDataInitializer initializer(
            InMemoryEventRepository eventRepository,
            InMemoryAdminRepository adminRepository) {
        return initializer(eventRepository, adminRepository, null);
    }

    private static DevSeedDataInitializer initializer(
            InMemoryEventRepository eventRepository,
            InMemoryAdminRepository adminRepository,
            SystemAnalyticsCollector analyticsCollector) {
        InMemoryMemberRepository memberRepository = new InMemoryMemberRepository();
        InMemoryCompanyRepository companyRepository = new InMemoryCompanyRepository();
        InMemoryOrderRepository orderRepository = new InMemoryOrderRepository();
        InMemoryQueueRepository queueRepository = new InMemoryQueueRepository();
        PasswordEncryptionUtils passwords = new PasswordEncryptionUtils();
        AdminService adminService = new AdminService(
                memberRepository, companyRepository, mock(ISessionTokenService.class),
                adminRepository, orderRepository);

        return new DevSeedDataInitializer(
                memberRepository,
                adminRepository,
                companyRepository,
                eventRepository,
                orderRepository,
                queueRepository,
                passwords,
                adminService,
                analyticsCollector);
    }
}
