package com.ticketing.application.listener;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ticketing.application.INotificationService;
import com.ticketing.domain.company.Company;
import com.ticketing.domain.member.IMemberRepository;
import com.ticketing.domain.member.Member;
import com.ticketing.domain.member.StaffAppointment;
import com.ticketing.domain.member.communication.RevokePersonnelEvent;

public class RevokePersonnelHandlerTest {

    private IMemberRepository memberRepository;
    private INotificationService notificationService;
    private RevokePersonnelHandler handler;

    @BeforeEach
    public void setUp() {
        memberRepository = mock(IMemberRepository.class);
        notificationService = mock(INotificationService.class);
        handler = new RevokePersonnelHandler(memberRepository, notificationService);
    }

    /**
     * Fulfills Acceptance Test: "TargetIsFounder" / "Founder removal rejected"
     * V0 Requirement: Founder cannot be revoked. The system strictly denies the request.
     */
    @Test
    public void GivenTargetIsFounder_WhenHandle_ThenThrowsIllegalArgumentException() {
        UUID founderId = UUID.randomUUID();
        UUID revokerId = UUID.randomUUID(); // Someone else trying to revoke founder
        String companyName = "TestCo";
        Company company = new Company(companyName, "desc", founderId);

        Member founder = new Member(founderId, "founder", "founder@test.com", "pass");
        Member revoker = new Member(revokerId, "revoker", "revoker@test.com", "pass");

        when(memberRepository.findById(founderId)).thenReturn(Optional.of(founder));
        when(memberRepository.findById(revokerId)).thenReturn(Optional.of(revoker));

        RevokePersonnelEvent event = new RevokePersonnelEvent(company, revokerId, founderId);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            handler.handle(event);
        });
        assertEquals("Cannot revoke the founder of the company.", exception.getMessage());
    }

    /**
     * Fulfills Acceptance Test: "Unauthorized Appointer" / "FounderOverrideAttempt"
     * V0 Requirement: Only the strict direct appointer can revoke the target's appointment.
     */
    @Test
    public void GivenUnauthorizedRevoker_WhenHandle_ThenThrowsIllegalArgumentException() {
        UUID founderId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        UUID unauthorizedRevokerId = UUID.randomUUID(); // Not the person who appointed target
        String companyName = "TestCo";
        Company company = new Company(companyName, "desc", founderId);

        Member target = new Member(targetId, "target", "target@test.com", "pass");
        // Target was appointed by the founder, NOT the unauthorized revoker
        StaffAppointment targetAppointment = new StaffAppointment(companyName, founderId, StaffAppointment.StaffRole.MANAGER, Collections.emptySet());
        target.addStaffAppointment(companyName, targetAppointment);

        Member unauthorizedRevoker = new Member(unauthorizedRevokerId, "revoker", "revoker@test.com", "pass");

        when(memberRepository.findById(targetId)).thenReturn(Optional.of(target));
        when(memberRepository.findById(unauthorizedRevokerId)).thenReturn(Optional.of(unauthorizedRevoker));

        // Attempt to revoke the target by someone who is not their appointer (founder in this case), should throw exception
        RevokePersonnelEvent event = new RevokePersonnelEvent(company, unauthorizedRevokerId, targetId);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            handler.handle(event);
        });
        assertTrue(exception.getMessage().contains("Only the appointer can revoke their appointees"));
    }

    /**
     * Fulfills Acceptance Test: "Hierarchy remains valid after non-cascading removal" 
     * V0 Requirement: Members that X previously appointed remain in their roles.
     * Architectural Implementation: Target is removed, and their subordinates roll up to the Revoker.
     */
    @Test
    public void GivenValidRevocationWithSubordinates_WhenHandle_ThenRemovesTargetAndReassignsSubordinates() {
        UUID founderId = UUID.randomUUID();
        UUID targetManagerId = UUID.randomUUID();
        UUID subordinateId = UUID.randomUUID();
        String companyName = "TestCo";
        Company company = new Company(companyName, "desc", founderId);

        // Setup the Revoker (Founder)
        Member founder = new Member(founderId, "founder", "founder@test.com", "pass");
        StaffAppointment founderAppointment = new StaffAppointment(companyName, founderId, StaffAppointment.StaffRole.OWNER, Collections.emptySet());
        founderAppointment.addAppointedStaffMember(targetManagerId);
        founder.addStaffAppointment(companyName, founderAppointment);

        // Setup the Target (Manager)
        Member targetManager = new Member(targetManagerId, "manager", "manager@test.com", "pass");
        StaffAppointment targetAppointment = new StaffAppointment(companyName, founderId, StaffAppointment.StaffRole.MANAGER, Collections.emptySet());
        targetAppointment.addAppointedStaffMember(subordinateId); // Target has a subordinate
        targetManager.addStaffAppointment(companyName, targetAppointment);

        // Setup the Subordinate
        Member subordinate = new Member(subordinateId, "sub", "sub@test.com", "pass");
        StaffAppointment subAppointment = new StaffAppointment(companyName, targetManagerId, StaffAppointment.StaffRole.MANAGER, Collections.emptySet());
        subordinate.addStaffAppointment(companyName, subAppointment);

        when(memberRepository.findById(founderId)).thenReturn(Optional.of(founder));
        when(memberRepository.findById(targetManagerId)).thenReturn(Optional.of(targetManager));
        when(memberRepository.findById(subordinateId)).thenReturn(Optional.of(subordinate));

        RevokePersonnelEvent event = new RevokePersonnelEvent(company, founderId, targetManagerId);

        // Act
        handler.handle(event);

        // Assert 1: Target is removed from Founder's list, but Subordinate is added to Founder's list (Reparenting)
        assertFalse(founder.getStaffAppointment(companyName).getAppointedStaffMemberIds().contains(targetManagerId));
        assertTrue(founder.getStaffAppointment(companyName).getAppointedStaffMemberIds().contains(subordinateId));

        // Assert 2: Subordinate's appointer is updated to the Founder
        assertEquals(founderId, subordinate.getStaffAppointment(companyName).getAppointedByMemberId());

        // Assert 3: Saves were called correctly
        verify(memberRepository).save(founder);
        verify(memberRepository).save(targetManager);
        verify(memberRepository).save(subordinate);
        
        // Assert 4: Notifications were sent
        verify(notificationService).notify(eq(targetManagerId.toString()), anyString());
        verify(notificationService).notify(eq(subordinateId.toString()), anyString());
    }

    /**
     * Fulfills Acceptance Test: "Existing subordinate/sibling appointments remain intact"
     * V0 Requirement: Revoking a target does not affect siblings appointed by the same revoker.
     */
    @Test
    public void GivenSiblingAppointments_WhenHandle_ThenSiblingRemainsIntact() {
        UUID founderId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        UUID siblingId = UUID.randomUUID();
        String companyName = "TestCo";
        Company company = new Company(companyName, "desc", founderId);

        // Setup Founder
        Member founder = new Member(founderId, "founder", "founder@test.com", "pass");
        StaffAppointment founderAppt = new StaffAppointment(companyName, founderId, StaffAppointment.StaffRole.OWNER, Collections.emptySet());
        founderAppt.addAppointedStaffMember(targetId);
        founderAppt.addAppointedStaffMember(siblingId); // Founder manages both Target and Sibling
        founder.addStaffAppointment(companyName, founderAppt);

        // Setup Target
        Member target = new Member(targetId, "target", "target@test.com", "pass");
        target.addStaffAppointment(companyName, new StaffAppointment(companyName, founderId, StaffAppointment.StaffRole.MANAGER, Collections.emptySet()));

        // Setup Sibling
        Member sibling = new Member(siblingId, "sibling", "sibling@test.com", "pass");
        sibling.addStaffAppointment(companyName, new StaffAppointment(companyName, founderId, StaffAppointment.StaffRole.MANAGER, Collections.emptySet()));

        when(memberRepository.findById(founderId)).thenReturn(Optional.of(founder));
        when(memberRepository.findById(targetId)).thenReturn(Optional.of(target));
        when(memberRepository.findById(siblingId)).thenReturn(Optional.of(sibling));

        // Act: Revoke the target
        handler.handle(new RevokePersonnelEvent(company, founderId, targetId));

        // Assert: Target is removed from Founder's list, but Sibling remains completely intact
        assertFalse(founder.getStaffAppointment(companyName).getAppointedStaffMemberIds().contains(targetId));
        assertTrue(founder.getStaffAppointment(companyName).getAppointedStaffMemberIds().contains(siblingId));
        assertNotNull(sibling.getStaffAppointment(companyName)); // Sibling still has their role
    }

    /**
     * Fulfills Acceptance Test: "Cross-company roles unaffected"
     * V0 Requirement: Revocation removes X's role in THAT company only.
     */
    @Test
    public void GivenCrossCompanyRoles_WhenHandle_ThenOtherCompanyRoleUnaffected() {
        UUID founderId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        String targetCompany = "TestCo";
        String otherCompany = "OtherCo";
        Company company = new Company(targetCompany, "desc", founderId);

        // Setup Founder
        Member founder = new Member(founderId, "founder", "founder@test.com", "pass");
        StaffAppointment founderAppt = new StaffAppointment(targetCompany, founderId, StaffAppointment.StaffRole.OWNER, Collections.emptySet());
        founderAppt.addAppointedStaffMember(targetId);
        founder.addStaffAppointment(targetCompany, founderAppt);

        // Setup Target with roles in TWO companies
        Member target = new Member(targetId, "target", "target@test.com", "pass");
        target.addStaffAppointment(targetCompany, new StaffAppointment(targetCompany, founderId, StaffAppointment.StaffRole.MANAGER, Collections.emptySet()));
        target.addStaffAppointment(otherCompany, new StaffAppointment(otherCompany, UUID.randomUUID(), StaffAppointment.StaffRole.MANAGER, Collections.emptySet()));

        when(memberRepository.findById(founderId)).thenReturn(Optional.of(founder));
        when(memberRepository.findById(targetId)).thenReturn(Optional.of(target));

        // Act: Revoke from TestCo
        handler.handle(new RevokePersonnelEvent(company, founderId, targetId));

        // Assert: TestCo role is gone, but OtherCo role is perfectly intact
        assertNull(target.getStaffAppointment(targetCompany));
        assertNotNull(target.getStaffAppointment(otherCompany));
    }
}