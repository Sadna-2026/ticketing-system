package com.ticketing.application.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ticketing.application.INotificationService;
import com.ticketing.domain.company.Company;
import com.ticketing.domain.member.IMemberRepository;
import com.ticketing.domain.member.ManagerPermission;
import com.ticketing.domain.member.Member;
import com.ticketing.domain.member.StaffAppointment;
import com.ticketing.domain.member.communication.RelinquishOwnershipEvent;

public class RelinquishOwnershipHandlerTest {

    private IMemberRepository memberRepository;
    private INotificationService notificationService;
    private RelinquishOwnershipHandler handler;

    @BeforeEach
    public void setUp() {
        memberRepository = mock(IMemberRepository.class);
        notificationService = mock(INotificationService.class);
        handler = new RelinquishOwnershipHandler(memberRepository, notificationService);
    }

    /**
     * Fulfills Acceptance Test: "Owner relinquishes successfully when others remain"
     */
    @Test
    public void GivenMultipleOwners_WhenRelinquish_ThenOwnerRemovedAndSubordinatesReassigned() {
        UUID founderId = UUID.randomUUID();
        UUID relinquishingOwnerId = UUID.randomUUID();
        UUID subordinateId = UUID.randomUUID();
        UUID otherOwnerId = UUID.randomUUID();
        String companyName = "TestCo";
        Company company = new Company(companyName, "desc", founderId);

        Member relinquishingOwner = new Member(relinquishingOwnerId, "owner1", "owner1@test.com", "pass");
        StaffAppointment ownerAppointment = new StaffAppointment(companyName, founderId, StaffAppointment.StaffRole.OWNER, Collections.emptySet(), Set.of(subordinateId));
        relinquishingOwner.addStaffAppointment(companyName, ownerAppointment);

        Member subordinate = new Member(subordinateId, "subordinate", "sub@test.com", "pass");
        StaffAppointment subAppointment = new StaffAppointment(companyName, relinquishingOwnerId, StaffAppointment.StaffRole.MANAGER, Set.of(ManagerPermission.INVENTORY_MGMT));
        subordinate.addStaffAppointment(companyName, subAppointment);

        Member founder = new Member(founderId, "founder", "founder@test.com", "pass");
        StaffAppointment founderAppointment = new StaffAppointment(companyName, founderId, StaffAppointment.StaffRole.OWNER, Collections.emptySet(), Set.of(relinquishingOwnerId));
        founder.addStaffAppointment(companyName, founderAppointment);

        Member otherOwner = new Member(otherOwnerId, "owner2", "owner2@test.com", "pass");
        StaffAppointment otherOwnerAppointment = new StaffAppointment(companyName, founderId, StaffAppointment.StaffRole.OWNER, Collections.emptySet());
        otherOwner.addStaffAppointment(companyName, otherOwnerAppointment);

        when(memberRepository.findById(relinquishingOwnerId)).thenReturn(Optional.of(relinquishingOwner));
        when(memberRepository.findById(founderId)).thenReturn(Optional.of(founder));
        when(memberRepository.findById(subordinateId)).thenReturn(Optional.of(subordinate));
        when(memberRepository.findByCompanyAppointment(companyName)).thenReturn(List.of(relinquishingOwner, otherOwner, founder));

        RelinquishOwnershipEvent event = new RelinquishOwnershipEvent(company, relinquishingOwnerId);
        handler.handle(event);

        assertNull(relinquishingOwner.getStaffAppointment(companyName));
        assertEquals(founderId, subordinate.getStaffAppointment(companyName).getAppointedByMemberId());
        verify(memberRepository, times(3)).save(any(Member.class));
        verify(notificationService).notify(eq(subordinateId.toString()), anyString());
        verify(notificationService).notify(eq(relinquishingOwnerId.toString()), anyString());
    }

    /**
     * Fulfills Acceptance Test: "Last owner cannot relinquish"
     */
    @Test
    public void GivenOnlyOneOwner_WhenRelinquish_ThenThrowsIllegalArgumentException() {
        UUID founderId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        String companyName = "TestCo";
        Company company = new Company(companyName, "desc", founderId);

        Member owner = new Member(ownerId, "owner", "owner@test.com", "pass");
        StaffAppointment ownerAppointment = new StaffAppointment(companyName, founderId, StaffAppointment.StaffRole.OWNER, Collections.emptySet());
        owner.addStaffAppointment(companyName, ownerAppointment);

        when(memberRepository.findById(ownerId)).thenReturn(Optional.of(owner));
        when(memberRepository.findByCompanyAppointment(companyName)).thenReturn(List.of(owner));

        RelinquishOwnershipEvent event = new RelinquishOwnershipEvent(company, ownerId);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            handler.handle(event);
        });
        assertEquals("Cannot relinquish ownership: you are the last owner. Add another owner first.", exception.getMessage());
    }

    /**
     * Fulfills Acceptance Test: "Founder relinquishment rejected -> directed to closure"
     */
    @Test
    public void GivenFounder_WhenRelinquish_ThenThrowsIllegalArgumentException() {
        UUID founderId = UUID.randomUUID();
        String companyName = "TestCo";
        Company company = new Company(companyName, "desc", founderId);

        Member founder = new Member(founderId, "founder", "founder@test.com", "pass");
        StaffAppointment founderAppointment = new StaffAppointment(companyName, founderId, StaffAppointment.StaffRole.OWNER, Collections.emptySet());
        founder.addStaffAppointment(companyName, founderAppointment);

        when(memberRepository.findById(founderId)).thenReturn(Optional.of(founder));

        RelinquishOwnershipEvent event = new RelinquishOwnershipEvent(company, founderId);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            handler.handle(event);
        });
        assertEquals("Founder cannot relinquish ownership. Use company closure flow instead.", exception.getMessage());
    }

    /**
     * Fulfills Acceptance Test: "Manager cannot relinquish (must be revoked)"
     */
    @Test
    public void GivenManager_WhenRelinquish_ThenThrowsIllegalArgumentException() {
        UUID founderId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();
        String companyName = "TestCo";
        Company company = new Company(companyName, "desc", founderId);

        Member manager = new Member(managerId, "manager", "manager@test.com", "pass");
        StaffAppointment managerAppointment = new StaffAppointment(companyName, founderId, StaffAppointment.StaffRole.MANAGER, Set.of(ManagerPermission.INVENTORY_MGMT));
        manager.addStaffAppointment(companyName, managerAppointment);

        Member otherOwner = new Member(UUID.randomUUID(), "owner", "owner@test.com", "pass");

        when(memberRepository.findById(managerId)).thenReturn(Optional.of(manager));
        when(memberRepository.findByCompanyAppointment(companyName)).thenReturn(List.of(manager, otherOwner));

        RelinquishOwnershipEvent event = new RelinquishOwnershipEvent(company, managerId);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            handler.handle(event);
        });
        assertEquals("Only owners can relinquish ownership. Managers must be revoked by their appointer.", exception.getMessage());
    }

    /**
     * Fulfills Acceptance Test: "Cross-company roles unaffected"
     */
    @Test
    public void GivenOwnerInMultipleCompanies_WhenRelinquishOne_ThenOtherCompanyRoleRemains() {
        UUID founderId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        String companyName1 = "TestCo1";
        String companyName2 = "TestCo2";
        Company company1 = new Company(companyName1, "desc", founderId);

        Member owner = new Member(ownerId, "owner", "owner@test.com", "pass");
        StaffAppointment appointment1 = new StaffAppointment(companyName1, founderId, StaffAppointment.StaffRole.OWNER, Collections.emptySet());
        StaffAppointment appointment2 = new StaffAppointment(companyName2, UUID.randomUUID(), StaffAppointment.StaffRole.OWNER, Collections.emptySet());
        owner.addStaffAppointment(companyName1, appointment1);
        owner.addStaffAppointment(companyName2, appointment2);

        Member otherOwner = new Member(UUID.randomUUID(), "other", "other@test.com", "pass");
        StaffAppointment otherOwnerAppointment = new StaffAppointment(companyName1, founderId, StaffAppointment.StaffRole.OWNER, Collections.emptySet());
        otherOwner.addStaffAppointment(companyName1, otherOwnerAppointment);

        Member founder = new Member(founderId, "founder", "founder@test.com", "pass");
        StaffAppointment founderAppointment = new StaffAppointment(companyName1, founderId, StaffAppointment.StaffRole.OWNER, Collections.emptySet(), Set.of(ownerId, otherOwner.getId()));
        founder.addStaffAppointment(companyName1, founderAppointment);

        when(memberRepository.findById(ownerId)).thenReturn(Optional.of(owner));
        when(memberRepository.findById(founderId)).thenReturn(Optional.of(founder));
        when(memberRepository.findByCompanyAppointment(companyName1)).thenReturn(List.of(owner, otherOwner, founder));

        RelinquishOwnershipEvent event = new RelinquishOwnershipEvent(company1, ownerId);
        handler.handle(event);

        assertNull(owner.getStaffAppointment(companyName1));
        assertNotNull(owner.getStaffAppointment(companyName2));
    }
}
