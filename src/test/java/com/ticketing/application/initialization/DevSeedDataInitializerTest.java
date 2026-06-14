package com.ticketing.application.initialization;

import static com.ticketing.application.initialization.DevSeedDataInitializer.AND_POLICY_EVENT_ID;
import static com.ticketing.application.initialization.DevSeedDataInitializer.CONCERT_ID;
import static com.ticketing.application.initialization.DevSeedDataInitializer.COUPON_CHECKOUT_EVENT_ID;
import static com.ticketing.application.initialization.DevSeedDataInitializer.MIN_QTY_EVENT_ID;
import static com.ticketing.application.initialization.DevSeedDataInitializer.MIXED_LIMITED_EVENT_ID;
import static com.ticketing.application.initialization.DevSeedDataInitializer.SIMPLE_DISCOUNT_EVENT_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import com.ticketing.application.auth.ISessionTokenService;
import com.ticketing.application.services.AdminService;
import com.ticketing.application.services.PlatformInitializationService;
import com.ticketing.infrastructure.InMemoryAdminRepository;
import com.ticketing.infrastructure.InMemoryCompanyRepository;
import com.ticketing.infrastructure.InMemoryEventRepository;
import com.ticketing.infrastructure.InMemoryMemberRepository;
import com.ticketing.infrastructure.InMemoryOrderRepository;
import com.ticketing.infrastructure.PasswordEncryptionUtils;

class DevSeedDataInitializerTest {

    @Test
    void GivenEmptyRepositories_WhenSeedRuns_ThenKeyDemoEventsAreCreated() throws Exception {
        InMemoryEventRepository eventRepository = new InMemoryEventRepository();
        DevSeedDataInitializer initializer = initializer(true, eventRepository);

        initializer.run(new DefaultApplicationArguments(new String[0]));

        assertTrue(eventRepository.findById(CONCERT_ID).isPresent());
        assertTrue(eventRepository.findById(COUPON_CHECKOUT_EVENT_ID).isPresent());
        assertTrue(eventRepository.findById(MIXED_LIMITED_EVENT_ID).isPresent());
        assertTrue(eventRepository.findById(MIN_QTY_EVENT_ID).isPresent());
        assertTrue(eventRepository.findById(AND_POLICY_EVENT_ID).isPresent());
        assertTrue(eventRepository.findById(SIMPLE_DISCOUNT_EVENT_ID).isPresent());
    }

    @Test
    void GivenSeedAlreadyApplied_WhenSeedRunsAgain_ThenEventsAreNotDuplicated() throws Exception {
        InMemoryEventRepository eventRepository = new InMemoryEventRepository();
        DevSeedDataInitializer initializer = initializer(true, eventRepository);
        DefaultApplicationArguments args = new DefaultApplicationArguments(new String[0]);

        initializer.run(args);
        int eventsAfterFirst = eventRepository.findAll().size();
        initializer.run(args);

        assertEquals(eventsAfterFirst, eventRepository.findAll().size());
    }

    @Test
    void GivenSeedDisabled_WhenInitializerRuns_ThenNoEventsAreCreated() throws Exception {
        InMemoryEventRepository eventRepository = new InMemoryEventRepository();
        DevSeedDataInitializer initializer = initializer(false, eventRepository);

        initializer.run(new DefaultApplicationArguments(new String[0]));

        assertTrue(eventRepository.findAll().isEmpty());
    }

    @Test
    void GivenPlatformInitializationFails_WhenSeedRuns_ThenNoDataIsSeeded() throws Exception {
        InMemoryEventRepository eventRepository = new InMemoryEventRepository();
        InMemoryMemberRepository memberRepository = new InMemoryMemberRepository();
        InMemoryAdminRepository adminRepository = new InMemoryAdminRepository();
        InMemoryCompanyRepository companyRepository = new InMemoryCompanyRepository();
        InMemoryOrderRepository orderRepository = new InMemoryOrderRepository();
        PasswordEncryptionUtils passwords = new PasswordEncryptionUtils();
        AdminService adminService = new AdminService(
                memberRepository, companyRepository, mock(ISessionTokenService.class),
                adminRepository, orderRepository);

        PlatformInitializationService platformInit = mock(PlatformInitializationService.class);
        when(platformInit.initialize()).thenReturn(
                PlatformInitializationService.InitializationResult.failure("Unable to connect to clearing service"));

        // initializePlatform = true AND seedEnabled = true, but initialization fails.
        DevSeedDataInitializer initializer = new DevSeedDataInitializer(
                true, true, platformInit,
                memberRepository, adminRepository, companyRepository, eventRepository,
                orderRepository, passwords, adminService);

        initializer.run(new DefaultApplicationArguments(new String[0]));

        verify(platformInit).initialize();
        assertTrue(eventRepository.findAll().isEmpty(), "no events should be seeded after a failed platform init");
    }

    private static DevSeedDataInitializer initializer(boolean seedEnabled, InMemoryEventRepository eventRepository) {
        InMemoryMemberRepository memberRepository = new InMemoryMemberRepository();
        InMemoryAdminRepository adminRepository = new InMemoryAdminRepository();
        InMemoryCompanyRepository companyRepository = new InMemoryCompanyRepository();
        InMemoryOrderRepository orderRepository = new InMemoryOrderRepository();
        PasswordEncryptionUtils passwords = new PasswordEncryptionUtils();
        AdminService adminService = new AdminService(
                memberRepository, companyRepository, mock(com.ticketing.application.auth.ISessionTokenService.class),
                adminRepository, orderRepository);

        return new DevSeedDataInitializer(
                false,
                seedEnabled,
                mock(PlatformInitializationService.class),
                memberRepository,
                adminRepository,
                companyRepository,
                eventRepository,
                orderRepository,
                passwords,
                adminService);
    }
}
