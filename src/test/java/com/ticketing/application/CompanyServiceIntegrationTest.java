package com.ticketing.application;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ticketing.application.CompanyService;
import com.ticketing.application.INotificationService;
import com.ticketing.application.auth.ISessionTokenService;
import com.ticketing.application.initialization.InitializationService;
import com.ticketing.application.listener.MemberCompanyOpenedEventHandler;
import com.ticketing.domain.company.ICompanyRepository;
import com.ticketing.domain.event.CompanyOpenedEvent;
import com.ticketing.domain.event.IEvent;
import com.ticketing.domain.event.IEventListener;
import com.ticketing.domain.event.IEventPublisher;
import com.ticketing.domain.member.IMemberRepository;
import com.ticketing.domain.member.IRoleAppointmentOfferRepository;
import com.ticketing.domain.member.Member;
import com.ticketing.domain.member.StaffAppointment;
import com.ticketing.infrastructure.InMemoryCompanyRepository;
import com.ticketing.infrastructure.InMemoryEventPublisher;
import com.ticketing.infrastructure.InMemoryMemberRepository;

import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/**
 * Integration tests for CompanyService with real (non-mocked) repositories
 * Tests event publishing and member update handling through event listeners
 */
public class CompanyServiceIntegrationTest {

    private ICompanyRepository companyRepository;
    private IMemberRepository memberRepository;
    private IEventPublisher eventPublisher;
    private ISessionTokenService sessionTokenService;
    private INotificationService notificationService;
    private CompanyService companyService;
    private InitializationService initializationService;

    // Test listener to verify events are published
    private TestEventListener testEventListener;

    @BeforeEach
    public void setUp() {
        companyRepository = new InMemoryCompanyRepository();
        memberRepository = new com.ticketing.infrastructure.InMemoryMemberRepository();
        eventPublisher = new InMemoryEventPublisher();
        
        // Mock the session token service using Mockito
        sessionTokenService = mock(ISessionTokenService.class);
        when(sessionTokenService.isValid(anyString())).thenAnswer(invocation -> {
            String token = invocation.getArgument(0);
            return !token.isEmpty() && token.startsWith("valid-");
        });
        when(sessionTokenService.extractMemberId(anyString())).thenAnswer(invocation -> {
            String token = invocation.getArgument(0);
            if (token.startsWith("valid-")) {
                try {
                    return UUID.fromString(token.substring(6));
                } catch (Exception e) {
                    return null;
                }
            }
            return null;
        });

        notificationService = mock(INotificationService.class);

        // Setup initialization
        IRoleAppointmentOfferRepository offerRepository = mock(IRoleAppointmentOfferRepository.class);
        initializationService = new InitializationService(
            companyRepository,
            memberRepository,
            offerRepository,
            eventPublisher,
            sessionTokenService,
            notificationService
        );

        // Initialize with event listeners
        companyService = initializationService.initialize();

        // Create test event listener
        testEventListener = new TestEventListener();
        eventPublisher.subscribe("CompanyOpened", testEventListener);
    }

    // ===== Happy Path Tests =====

    @Test
    public void testSuccessfulCompanyOpening() {
        UUID founderId = UUID.randomUUID();
        String token = "valid-" + founderId.toString();
        String companyName = "TechCorp";
        String description = "A tech company";

        // Create founder member
        Member founder = new Member(founderId, "founder", "founder@example.com", "hashedPassword");
        memberRepository.saveIfUsernameAndEmailAvailable(founder);

        // Open company
        String result = companyService.openProductionCompany(token, companyName, description);

        // Verify company was saved
        assertEquals(companyName, result);
        assertTrue(companyRepository.existsByName(companyName));

        // Verify event was published
        assertTrue(testEventListener.eventReceived);
        assertEquals(companyName, testEventListener.lastCompanyOpenedEvent.getCompanyName());
        assertEquals(founderId, testEventListener.lastCompanyOpenedEvent.getFounderId());
    }

    @Test
    public void testCompanyOpeningPublishesEvent() {
        UUID founderId = UUID.randomUUID();
        String token = "valid-" + founderId.toString();
        String companyName = "InnovateCorp";
        String description = "Innovation hub";

        Member founder = new Member(founderId, "innovator", "innovator@example.com", "hashedPassword");
        memberRepository.saveIfUsernameAndEmailAvailable(founder);

        // Verify no event yet
        assertFalse(testEventListener.eventReceived);

        // Open company
        companyService.openProductionCompany(token, companyName, description);

        // Verify event was published
        assertTrue(testEventListener.eventReceived);
    }

    @Test
    public void testEventHandlerAssignsFounderRole() {
        UUID founderId = UUID.randomUUID();
        String token = "valid-" + founderId.toString();
        String companyName = "FinServ";
        String description = "Financial services";

        // Create founder
        Member founder = new Member(founderId, "financeuser", "finance@example.com", "hashedPassword");
        memberRepository.saveIfUsernameAndEmailAvailable(founder);

        // Open company
        companyService.openProductionCompany(token, companyName, description);

        // Verify founder has owner role
        Optional<Member> updatedFounder = memberRepository.findById(founderId);
        assertTrue(updatedFounder.isPresent());
        
        Member founderAfter = updatedFounder.get();
        assertTrue(founderAfter.hasStaffAppointment(companyName, StaffAppointment.StaffRole.OWNER));
    }

    // ===== Error Cases =====

    @Test
    public void testDuplicateCompanyNameThrows() {
        UUID founderId = UUID.randomUUID();
        String token = "valid-" + founderId.toString();
        String companyName = "UniqueCompany";

        Member founder = new Member(founderId, "user1", "user1@example.com", "hashedPassword");
        memberRepository.saveIfUsernameAndEmailAvailable(founder);

        // Create first company
        companyService.openProductionCompany(token, companyName, "First");

        // Attempt duplicate
        assertThrows(IllegalArgumentException.class, () -> {
            companyService.openProductionCompany(token, companyName, "Second");
        });
    }

    @Test
    public void testInvalidTokenThrows() {
        UUID founderId = UUID.randomUUID();
        String invalidToken = "invalid-token";

        assertThrows(IllegalArgumentException.class, () -> {
            companyService.openProductionCompany(invalidToken, "CompanyName", "Description");
        });
    }

    @Test
    public void testEmptyTokenThrows() {
        assertThrows(IllegalArgumentException.class, () -> {
            companyService.openProductionCompany("", "CompanyName", "Description");
        });
    }

    @Test
    public void testNullTokenThrows() {
        assertThrows(IllegalArgumentException.class, () -> {
            companyService.openProductionCompany(null, "CompanyName", "Description");
        });
    }

    // ===== Test Event Listener =====

    private static class TestEventListener implements IEventListener {
        private boolean eventReceived = false;
        private CompanyOpenedEvent lastCompanyOpenedEvent;

        @Override
        public void handle(IEvent event) {
            if (event instanceof CompanyOpenedEvent) {
                eventReceived = true;
                lastCompanyOpenedEvent = (CompanyOpenedEvent) event;
            }
        }
    }
}
