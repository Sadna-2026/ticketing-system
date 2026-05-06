package com.ticketing.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ticketing.application.auth.ISessionTokenService;
import com.ticketing.application.initialization.InitializationService;
import com.ticketing.domain.company.Company;
import com.ticketing.domain.company.ICompanyRepository;
import com.ticketing.domain.member.IMemberRepository;
import com.ticketing.domain.member.ManagerPermission;
import com.ticketing.domain.member.Member;
import com.ticketing.domain.member.PendingRoleOffer;
import com.ticketing.domain.member.StaffAppointment;
import com.ticketing.infrastructure.InMemoryCompanyRepository;
import com.ticketing.infrastructure.InMemoryEventPublisher;
import com.ticketing.infrastructure.InMemoryMemberRepository;

public class CompanyServiceTest {

    private ICompanyRepository companyRepository;
    private IMemberRepository memberRepository;
    private InMemoryEventPublisher eventPublisher;
    private ISessionTokenService sessionTokenServiceMock;
    private INotificationService notificationService;
    private InitializationService initializationService;
    private CompanyService companyService;

    @BeforeEach
    public void setUp() {
        companyRepository = new InMemoryCompanyRepository();
        memberRepository = new InMemoryMemberRepository();
        eventPublisher = new InMemoryEventPublisher();
        sessionTokenServiceMock = mock(ISessionTokenService.class);
        notificationService = mock(INotificationService.class);

        initializationService = new InitializationService(
            companyRepository,
            memberRepository,
            eventPublisher,
            sessionTokenServiceMock,
            notificationService
        );

        companyService = initializationService.initialize();
    }

    @Test
    public void GivenValidToken_WhenOpenProductionCompany_ThenCompanyCreated() {
        UUID memberId = UUID.randomUUID();
        String token = "valid-token";
        String companyName = "NewCompany";

        when(sessionTokenServiceMock.isValid(token)).thenReturn(true);
        when(sessionTokenServiceMock.extractMemberId(token)).thenReturn(memberId);

        String result = companyService.openProductionCompany(token, companyName, "Description");

        assertEquals(companyName, result);
        assertTrue(companyRepository.existsByName(companyName));
    }

    @Test
    public void GivenDuplicateCompanyName_WhenOpenProductionCompany_ThenThrowsIllegalArgumentException() {
        UUID memberId = UUID.randomUUID();
        String token = "valid-token";
        String companyName = "ExistingCompany";

        when(sessionTokenServiceMock.isValid(token)).thenReturn(true);
        when(sessionTokenServiceMock.extractMemberId(token)).thenReturn(memberId);

        companyService.openProductionCompany(token, companyName, "First");

        assertThrows(IllegalArgumentException.class, () -> {
            companyService.openProductionCompany(token, companyName, "Second");
        });
    }

    @Test
    public void GivenAcceptedResponse_WhenRespondToRoleAppointment_ThenNotifiesAppointee() {
        UUID appointerId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        String companyName = "TestCo";
        String appointerToken = "valid-" + appointerId.toString();
        String targetToken = "valid-" + targetId.toString();

        when(sessionTokenServiceMock.isValid(appointerToken)).thenReturn(true);
        when(sessionTokenServiceMock.extractMemberId(appointerToken)).thenReturn(appointerId);
        when(sessionTokenServiceMock.isValid(targetToken)).thenReturn(true);
        when(sessionTokenServiceMock.extractMemberId(targetToken)).thenReturn(targetId);

        Member appointer = new Member(appointerId, "appointer", "appointer@test.com", "pass");
        memberRepository.saveIfUsernameAndEmailAvailable(appointer);
        companyService.openProductionCompany(appointerToken, companyName, "Desc");

        Member target = new Member(targetId, "target", "target@test.com", "pass");
        memberRepository.saveIfUsernameAndEmailAvailable(target);

        companyService.offerRoleAppointment(appointerToken, companyName, targetId, StaffAppointment.StaffRole.MANAGER, Collections.singleton(ManagerPermission.PERSONNEL_MGMT));

        PendingRoleOffer offer = memberRepository.findById(targetId).get().getPendingOffers().get(0);
        assertFalse(offer.isExpired());
        companyService.respondToRoleAppointment(targetToken, offer.getOfferId(), true);

        assertNotNull(memberRepository.findById(targetId).get().getStaffAppointment(companyName));
        verify(notificationService).notify(eq(appointerId.toString()), contains("accepted the manager role offer"));
    }

    @Test
    public void GivenRejectedResponse_WhenRespondToRoleAppointment_ThenNotifiesAppointeeWithoutAppointment() {
        UUID appointerId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        String companyName = "TestCo";
        String appointerToken = "valid-" + appointerId.toString();
        String targetToken = "valid-" + targetId.toString();

        when(sessionTokenServiceMock.isValid(appointerToken)).thenReturn(true);
        when(sessionTokenServiceMock.extractMemberId(appointerToken)).thenReturn(appointerId);
        when(sessionTokenServiceMock.isValid(targetToken)).thenReturn(true);
        when(sessionTokenServiceMock.extractMemberId(targetToken)).thenReturn(targetId);

        Member appointer = new Member(appointerId, "appointer", "appointer@test.com", "pass");
        memberRepository.saveIfUsernameAndEmailAvailable(appointer);
        companyService.openProductionCompany(appointerToken, companyName, "Desc");

        Member target = new Member(targetId, "target", "target@test.com", "pass");
        memberRepository.saveIfUsernameAndEmailAvailable(target);

        companyService.offerRoleAppointment(appointerToken, companyName, targetId, StaffAppointment.StaffRole.MANAGER, Collections.singleton(ManagerPermission.PERSONNEL_MGMT));

        PendingRoleOffer offer = memberRepository.findById(targetId).get().getPendingOffers().get(0);
        assertFalse(offer.isExpired());
        companyService.respondToRoleAppointment(targetToken, offer.getOfferId(), false);

        assertNull(memberRepository.findById(targetId).get().getStaffAppointment(companyName));
        verify(notificationService).notify(eq(appointerId.toString()), contains("declined the manager role offer"));
    }

    @Test
    public void GivenManagerPromotionAccepted_WhenRespondToRoleAppointment_ThenNotifiesAppointee() {
        UUID appointerId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        String companyName = "TestCo";
        String appointerToken = "valid-" + appointerId.toString();
        String targetToken = "valid-" + targetId.toString();

        when(sessionTokenServiceMock.isValid(appointerToken)).thenReturn(true);
        when(sessionTokenServiceMock.extractMemberId(appointerToken)).thenReturn(appointerId);
        when(sessionTokenServiceMock.isValid(targetToken)).thenReturn(true);
        when(sessionTokenServiceMock.extractMemberId(targetToken)).thenReturn(targetId);

        Member appointer = new Member(appointerId, "appointer", "appointer@test.com", "pass");
        memberRepository.saveIfUsernameAndEmailAvailable(appointer);
        companyService.openProductionCompany(appointerToken, companyName, "Desc");

        Member target = new Member(targetId, "target", "target@test.com", "pass");
        StaffAppointment managerAppointment = new StaffAppointment(companyName, appointerId, StaffAppointment.StaffRole.MANAGER, Collections.emptySet());
        target.addStaffAppointment(companyName, managerAppointment);
        memberRepository.saveIfUsernameAndEmailAvailable(target);

        companyService.offerRoleAppointment(appointerToken, companyName, targetId, StaffAppointment.StaffRole.OWNER, Collections.emptySet());

        PendingRoleOffer offer = memberRepository.findById(targetId).get().getPendingOffers().get(0);
        assertFalse(offer.isExpired());
        companyService.respondToRoleAppointment(targetToken, offer.getOfferId(), true);

        assertEquals(StaffAppointment.StaffRole.OWNER, memberRepository.findById(targetId).get().getStaffAppointment(companyName).getRole());
        verify(notificationService).notify(eq(appointerId.toString()), contains("promoted to owner"));
    }

    @Test
    public void GivenManagerPromotionRejected_WhenRespondToRoleAppointment_ThenNotifiesAppointee() {
        UUID appointerId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        String companyName = "TestCo";
        String appointerToken = "valid-" + appointerId.toString();
        String targetToken = "valid-" + targetId.toString();

        when(sessionTokenServiceMock.isValid(appointerToken)).thenReturn(true);
        when(sessionTokenServiceMock.extractMemberId(appointerToken)).thenReturn(appointerId);
        when(sessionTokenServiceMock.isValid(targetToken)).thenReturn(true);
        when(sessionTokenServiceMock.extractMemberId(targetToken)).thenReturn(targetId);

        Member appointer = new Member(appointerId, "appointer", "appointer@test.com", "pass");
        memberRepository.saveIfUsernameAndEmailAvailable(appointer);
        companyService.openProductionCompany(appointerToken, companyName, "Desc");

        Member target = new Member(targetId, "target", "target@test.com", "pass");
        StaffAppointment managerAppointment = new StaffAppointment(companyName, appointerId, StaffAppointment.StaffRole.MANAGER, Collections.emptySet());
        target.addStaffAppointment(companyName, managerAppointment);
        memberRepository.saveIfUsernameAndEmailAvailable(target);

        companyService.offerRoleAppointment(appointerToken, companyName, targetId, StaffAppointment.StaffRole.OWNER, Collections.emptySet());

        PendingRoleOffer offer = memberRepository.findById(targetId).get().getPendingOffers().get(0);
        assertFalse(offer.isExpired());
        companyService.respondToRoleAppointment(targetToken, offer.getOfferId(), false);

        assertEquals(StaffAppointment.StaffRole.MANAGER, memberRepository.findById(targetId).get().getStaffAppointment(companyName).getRole());
        verify(notificationService).notify(eq(appointerId.toString()), contains("rejected the promotion to owner"));
    }

    /**
     * Fulfills Acceptance Test: "Valid Revocation Request Initiated" (happy path)
     * Tests the core success path of the service layer before delegating to the Handler.
     */
    @Test
    public void GivenValidRequest_WhenRevokePersonnel_ThenPublishesEventSuccessfully() {
        UUID revokerId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        String companyName = "TestCo";
        String validToken = "valid-" + revokerId.toString();

        // Create and save the Members
        Member revoker = new Member(revokerId, "revoker", "revoker@test.com", "pass");
        Member target = new Member(targetId, "target", "target@test.com", "pass");
        memberRepository.save(revoker);
        memberRepository.save(target);

        // Mock token validation for a logged-in user
        when(sessionTokenServiceMock.isValid(validToken)).thenReturn(true);
        when(sessionTokenServiceMock.extractMemberId(validToken)).thenReturn(revokerId);

        // Setup an ACTIVE company and save it to the repository
        Company activeCompany = new Company(companyName, "desc", revokerId);
        companyRepository.save(activeCompany);

        // Act & Assert: The method should complete without throwing any exceptions
        assertDoesNotThrow(() -> {
            companyService.revokePersonnel(validToken, companyName, targetId);
        });
    }

    /**
     * Fulfills Acceptance Test: "Inactive company blocks role changes"
     * V0 Requirement: Cannot revoke personnel from a suspended or closed company.
     */
    @Test
    public void GivenInactiveCompany_WhenRevokePersonnel_ThenThrowsIllegalArgumentException() {
        UUID revokerId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        String companyName = "TestCo";
        String validToken = "valid-" + revokerId.toString();

        // Create and save the Members to the real in-memory repository
        Member revoker = new Member(revokerId, "revoker", "revoker@test.com", "pass");
        Member target = new Member(targetId, "target", "target@test.com", "pass");
        memberRepository.save(revoker);
        memberRepository.save(target);

        // Mock token validation
        when(sessionTokenServiceMock.isValid(validToken)).thenReturn(true);
        when(sessionTokenServiceMock.extractMemberId(validToken)).thenReturn(revokerId);

        // Setup an INACTIVE company and save it to the real in-memory repository
        Company inactiveCompany = new Company(companyName, "desc", UUID.randomUUID());
        inactiveCompany.suspend(); // Makes isActive() return false
        companyRepository.save(inactiveCompany);

        // Act & Assert: Service should block the request
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            companyService.revokePersonnel(validToken, companyName, targetId);
        });

        assertEquals("Cannot revoke personnel from a suspended or closed company", exception.getMessage());
    }
    
}
