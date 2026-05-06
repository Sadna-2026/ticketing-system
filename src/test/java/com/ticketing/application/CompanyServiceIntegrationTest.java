package com.ticketing.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ticketing.application.auth.ISessionTokenService;
import com.ticketing.application.initialization.InitializationService;
import com.ticketing.domain.company.ICompanyRepository;
import com.ticketing.domain.event.CompanyOpenedEvent;
import com.ticketing.domain.event.IEvent;
import com.ticketing.domain.event.IEventListener;
import com.ticketing.domain.event.IEventPublisher;
import com.ticketing.domain.member.IMemberRepository;
import com.ticketing.domain.member.ManagerPermission;
import com.ticketing.domain.member.Member;
import com.ticketing.domain.member.PendingRoleOffer;
import com.ticketing.domain.member.PermissionDeniedException;
import com.ticketing.domain.member.StaffAppointment;
import com.ticketing.infrastructure.InMemoryCompanyRepository;
import com.ticketing.infrastructure.InMemoryEventPublisher;
import com.ticketing.infrastructure.InMemoryMemberRepository;

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
        initializationService = new InitializationService(
            companyRepository,
            memberRepository,
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
        eventPublisher.subscribe("RoleAppointmentOfferResponse", testEventListener);
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

        // 4. Attempt offer - with the updated InMemoryEventPublisher, exceptions now propagate.
        // We expect a PermissionDeniedException when an unauthorized user attempts to offer a role.
        assertThrows(PermissionDeniedException.class, () -> 
            companyService.offerRoleAppointment(token, companyName, targetId, StaffAppointment.StaffRole.MANAGER, Collections.emptySet())
        );

        assertEquals(0, memberRepository.findById(targetId).get().getPendingOffers().size(), 
            "Offer should not have been added due to unauthorized appointer");
    }

    // SuccessfulRoleAcceptance
    @Test
    public void GivenAcceptedResponse_WhenRespondToRoleAppointment_ThenNotifiesAppointee() {
        UUID appointerId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        String companyName = "TestCo";
        String appointerToken = "valid-" + appointerId.toString();
        String targetToken = "valid-" + targetId.toString();

        // Setup Company and Appointer
        Member appointer = new Member(appointerId, "appointer", "appointer@test.com", "pass");
        memberRepository.saveIfUsernameAndEmailAvailable(appointer);
        companyService.openProductionCompany(appointerToken, companyName, "Desc");

        // Setup Target
        Member target = new Member(targetId, "target", "target@test.com", "pass");
        memberRepository.saveIfUsernameAndEmailAvailable(target);

        // Offer role
        companyService.offerRoleAppointment(appointerToken, companyName, targetId, StaffAppointment.StaffRole.MANAGER, new java.util.HashSet<>(Collections.singletonList(ManagerPermission.PERSONNEL_MGMT)));

        // Get the offer ID
        PendingRoleOffer offer = memberRepository.findById(targetId).get().getPendingOffers().get(0);

        // Respond to offer
        companyService.respondToRoleAppointment(targetToken, offer.getOfferId(), true);

        // Verify appointment and notification
        Optional<Member> updatedTarget = memberRepository.findById(targetId);
        assertTrue(updatedTarget.isPresent());
        assertNotNull(updatedTarget.get().getStaffAppointment(companyName));
        verify(notificationService).notify(eq(appointerId.toString()), contains("accepted the manager role offer"));
    }

    // Role rejection
    @Test
    public void GivenRejectedResponse_WhenRespondToRoleAppointment_ThenNotifiesAppointeeWithoutAppointment() {
        UUID appointerId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        String companyName = "TestCo";
        String appointerToken = "valid-" + appointerId.toString();
        String targetToken = "valid-" + targetId.toString();

        // Setup Company and Appointer
        Member appointer = new Member(appointerId, "appointer", "appointer@test.com", "pass");
        memberRepository.saveIfUsernameAndEmailAvailable(appointer);
        companyService.openProductionCompany(appointerToken, companyName, "Desc");

        // Setup Target
        Member target = new Member(targetId, "target", "target@test.com", "pass");
        memberRepository.saveIfUsernameAndEmailAvailable(target);

        // Offer role
        companyService.offerRoleAppointment(appointerToken, companyName, targetId, StaffAppointment.StaffRole.MANAGER, new java.util.HashSet<>(Collections.singletonList(ManagerPermission.PERSONNEL_MGMT)));

        // Get the offer ID
        PendingRoleOffer offer = memberRepository.findById(targetId).get().getPendingOffers().get(0);

        // Respond to offer
        companyService.respondToRoleAppointment(targetToken, offer.getOfferId(), false);

        // Verify no appointment and notification
        Optional<Member> updatedTarget = memberRepository.findById(targetId);
        assertTrue(updatedTarget.isPresent());
        assertNull(updatedTarget.get().getStaffAppointment(companyName));
        verify(notificationService).notify(eq(appointerId.toString()), contains("declined the manager role offer"));
    }

    // Manager promotion acceptance
    @Test
    public void GivenManagerPromotionAccepted_WhenRespondToRoleAppointment_ThenNotifiesAppointee() {
        UUID appointerId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        String companyName = "TestCo";
        String appointerToken = "valid-" + appointerId.toString();
        String targetToken = "valid-" + targetId.toString();

        // Setup Company and Appointer
        Member appointer = new Member(appointerId, "appointer", "appointer@test.com", "pass");
        memberRepository.saveIfUsernameAndEmailAvailable(appointer);
        companyService.openProductionCompany(appointerToken, companyName, "Desc");

        // Setup Target as Manager
        Member target = new Member(targetId, "target", "target@test.com", "pass");
        StaffAppointment managerAppointment = new StaffAppointment(companyName, appointerId, StaffAppointment.StaffRole.MANAGER, Collections.emptySet());
        target.addStaffAppointment(companyName, managerAppointment);
        memberRepository.saveIfUsernameAndEmailAvailable(target);

        // Offer owner role
        companyService.offerRoleAppointment(appointerToken, companyName, targetId, StaffAppointment.StaffRole.OWNER, Collections.emptySet());

        // Get the offer ID
        PendingRoleOffer offer = memberRepository.findById(targetId).get().getPendingOffers().get(0);

        // Respond to offer
        companyService.respondToRoleAppointment(targetToken, offer.getOfferId(), true);

        // Verify promotion and notification
        Optional<Member> updatedTarget = memberRepository.findById(targetId);
        assertTrue(updatedTarget.isPresent());
        assertEquals(StaffAppointment.StaffRole.OWNER, updatedTarget.get().getStaffAppointment(companyName).getRole());
        verify(notificationService).notify(eq(appointerId.toString()), contains("promoted to owner"));
    }

    // Manager promotion rejection
    @Test
    public void GivenManagerPromotionRejected_WhenRespondToRoleAppointment_ThenNotifiesAppointee() {
        UUID appointerId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        String companyName = "TestCo";
        String appointerToken = "valid-" + appointerId.toString();
        String targetToken = "valid-" + targetId.toString();

        // Setup Company and Appointer
        Member appointer = new Member(appointerId, "appointer", "appointer@test.com", "pass");
        memberRepository.saveIfUsernameAndEmailAvailable(appointer);
        companyService.openProductionCompany(appointerToken, companyName, "Desc");

        // Setup Target as Manager
        Member target = new Member(targetId, "target", "target@test.com", "pass");
        StaffAppointment managerAppointment = new StaffAppointment(companyName, appointerId, StaffAppointment.StaffRole.MANAGER, Collections.emptySet());
        target.addStaffAppointment(companyName, managerAppointment);
        memberRepository.saveIfUsernameAndEmailAvailable(target);

        // Offer owner role
        companyService.offerRoleAppointment(appointerToken, companyName, targetId, StaffAppointment.StaffRole.OWNER, Collections.emptySet());

        // Get the offer ID
        PendingRoleOffer offer = memberRepository.findById(targetId).get().getPendingOffers().get(0);

        // Respond to offer
        companyService.respondToRoleAppointment(targetToken, offer.getOfferId(), false);

        // Verify still manager and notification
        Optional<Member> updatedTarget = memberRepository.findById(targetId);
        assertTrue(updatedTarget.isPresent());
        assertEquals(StaffAppointment.StaffRole.MANAGER, updatedTarget.get().getStaffAppointment(companyName).getRole());
        verify(notificationService).notify(eq(appointerId.toString()), contains("rejected the promotion to owner"));
    }

    // TODO: implement method to close company and test that role acceptance is blocked for closed companies with appropriate notification
    /*@Test
    public void GivenInactiveCompany_WhenRespondToRoleAppointment_ThenThrows() {
        UUID appointerId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        String companyName = "TestCo";
        String appointerToken = "valid-" + appointerId.toString();
        String targetToken = "valid-" + targetId.toString();

        // Setup Company and Appointer
        Member appointer = new Member(appointerId, "appointer", "appointer@test.com", "pass");
        memberRepository.saveIfUsernameAndEmailAvailable(appointer);
        companyService.openProductionCompany(appointerToken, companyName, "Desc");

        // Setup Target as Member
        Member target = new Member(targetId, "target", "target@test.com", "pass");
        memberRepository.saveIfUsernameAndEmailAvailable(target);

        // Offer owner role
        companyService.offerRoleAppointment(appointerToken, companyName, targetId, StaffAppointment.StaffRole.OWNER, Collections.emptySet());

        // Get the offer ID
        PendingRoleOffer offer = memberRepository.findById(targetId).get().getPendingOffers().get(0);

        // Close company
        companyService.closeCompany(appointerToken, companyName);

        // Respond to offer
        assertThrows(CompanyClosedException.class, () -> companyService.respondToRoleAppointment(targetToken, offer.getOfferId(), true));
    } */

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
