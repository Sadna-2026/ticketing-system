package com.ticketing.application;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;
import java.util.UUID;
import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ticketing.application.CompanyService;
import com.ticketing.application.INotificationService;
import com.ticketing.application.auth.ISessionTokenService;
import com.ticketing.application.initialization.InitializationService;
import com.ticketing.domain.company.ICompanyRepository;
import com.ticketing.domain.event.CompanyOpenedEvent;
import com.ticketing.domain.event.IEvent;
import com.ticketing.domain.event.IEventListener;
import com.ticketing.domain.event.IEventPublisher;
import com.ticketing.domain.member.IMemberRepository;
import com.ticketing.domain.member.Member;
import com.ticketing.domain.member.StaffAppointment;
import com.ticketing.domain.member.ManagerPermission;
import com.ticketing.domain.member.PendingRoleOffer;
import com.ticketing.domain.member.communication.RoleAppointmentOfferRequestedEvent;
import com.ticketing.infrastructure.InMemoryCompanyRepository;
import com.ticketing.infrastructure.InMemoryEventPublisher;
import com.ticketing.infrastructure.InMemoryMemberRepository;
import com.ticketing.domain.event.IEventRepository;
import com.ticketing.domain.order.ICompletedPurchaseRepository;
import com.ticketing.domain.gateway.IPaymentGateway;
import com.ticketing.infrastructure.InMemoryEventRepository;
import com.ticketing.infrastructure.InMemoryCompletedPurchaseRepository;
import com.ticketing.infrastructure.gateway.StubPaymentGateway;

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
        memberRepository = new InMemoryMemberRepository();
        eventPublisher = new InMemoryEventPublisher();
        
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
        IEventRepository eventRepository = new InMemoryEventRepository();
        ICompletedPurchaseRepository purchaseRepository = new InMemoryCompletedPurchaseRepository();
        IPaymentGateway paymentGateway = new StubPaymentGateway();

        initializationService = new InitializationService(
            companyRepository,
            memberRepository,
            eventRepository,
            purchaseRepository,
            paymentGateway,
            eventPublisher,
            sessionTokenService,
            notificationService
        );

        // Initialize with event listeners
        companyService = initializationService.initialize();

        // Create test event listener
        testEventListener = new TestEventListener();
        eventPublisher.subscribe("CompanyOpened", testEventListener);
        eventPublisher.subscribe("RoleAppointmentOfferRequested", testEventListener);
    }

    @Test
    public void testSuccessfulCompanyOpening() {
        UUID founderId = UUID.randomUUID();
        String token = "valid-" + founderId.toString();
        String companyName = "TechCorp";
        
        Member founder = new Member(founderId, "founder", "founder@example.com", "hashedPassword");
        memberRepository.saveIfUsernameAndEmailAvailable(founder);

        String result = companyService.openProductionCompany(token, companyName, "A tech company");

        assertEquals(companyName, result);
        assertTrue(companyRepository.existsByName(companyName));
        
        Optional<Member> updatedFounder = memberRepository.findById(founderId);
        assertTrue(updatedFounder.isPresent() && updatedFounder.get().hasStaffAppointment(companyName, StaffAppointment.StaffRole.OWNER));
    }

    @Test
    public void testSuccessfulRoleOfferIntegration() {
        UUID ownerId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        String companyName = "OfferCorp";
        String token = "valid-" + ownerId.toString();

        // 1. Setup Company and Owner
        Member owner = new Member(ownerId, "owner", "owner@test.com", "pass");
        memberRepository.saveIfUsernameAndEmailAvailable(owner);
        companyService.openProductionCompany(token, companyName, "Desc");

        // 2. Setup Target
        Member target = new Member(targetId, "target", "target@test.com", "pass");
        memberRepository.saveIfUsernameAndEmailAvailable(target);

        // 3. Offer role
        companyService.offerRoleAppointment(token, companyName, targetId, StaffAppointment.StaffRole.MANAGER, Collections.emptySet());

        // 4. Verify target has pending offer (via listener)
        Optional<Member> updatedTarget = memberRepository.findById(targetId);
        assertTrue(updatedTarget.isPresent());
        Member targetAfter = updatedTarget.get();
        assertEquals(1, targetAfter.getPendingOffers().size());
        PendingRoleOffer offer = targetAfter.getPendingOffers().get(0);
        assertEquals(companyName, offer.getCompanyName());
        assertEquals(StaffAppointment.StaffRole.MANAGER, offer.getRole());
    }

    @Test
    public void testUnauthorizedRoleOfferIntegration() {
        UUID nonOwnerId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        String companyName = "SecCorp";
        String token = "valid-" + nonOwnerId.toString();

        // 1. Setup Company (owner is someone else)
        companyRepository.save(new com.ticketing.domain.company.Company(companyName, "Desc", UUID.randomUUID()));

        // 2. Setup Non-Owner Appointer
        Member nonOwner = new Member(nonOwnerId, "nonowner", "no@test.com", "pass");
        memberRepository.saveIfUsernameAndEmailAvailable(nonOwner);

        // 3. Setup Target
        Member target = new Member(targetId, "target", "target@test.com", "pass");
        memberRepository.saveIfUsernameAndEmailAvailable(target);

        // 4. Attempt offer - since InMemoryEventPublisher swallows exceptions in listeners
        // to prevent one listener from breaking others, we don't expect an exception here.
        // Instead, we verify that the side effect (adding the offer) DID NOT happen.
        companyService.offerRoleAppointment(token, companyName, targetId, StaffAppointment.StaffRole.MANAGER, Collections.emptySet());

        assertEquals(0, memberRepository.findById(targetId).get().getPendingOffers().size(), 
            "Offer should not have been added due to unauthorized appointer");
    }

    private static class TestEventListener implements IEventListener {
        private CompanyOpenedEvent lastCompanyOpenedEvent;

        @Override
        public void handle(IEvent event) {
            if (event instanceof CompanyOpenedEvent) {
                lastCompanyOpenedEvent = (CompanyOpenedEvent) event;
            }
        }
    }
}
