package com.ticketing.application.listener;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ticketing.domain.company.Company;
import com.ticketing.domain.company.ICompanyRepository;
import com.ticketing.domain.member.IMemberRepository;
import com.ticketing.domain.member.ManagerPermission;
import com.ticketing.domain.member.Member;
import com.ticketing.domain.member.StaffAppointment;
import com.ticketing.domain.member.communication.ManagerPermissionsChangedEvent;

public class ManagerPermissionsChangedHandlerTest {

    private IMemberRepository memberRepository;
    private ICompanyRepository companyRepository;
    private ManagerPermissionsChangedHandler handler;
    private final String COMPANY_NAME = "TestCompany";

    @BeforeEach
    public void setUp() {
        memberRepository = mock(IMemberRepository.class);
        companyRepository = mock(ICompanyRepository.class);
        handler = new ManagerPermissionsChangedHandler(memberRepository, companyRepository);
    }

    @Test
    public void testHandle_FounderChangesPermissions_Success() {
        UUID founderId = UUID.randomUUID();
        Member founder = new Member(founderId, "founder", "f@test.com", "pass");
        founder.addStaffAppointment(COMPANY_NAME, new StaffAppointment(COMPANY_NAME, null, StaffAppointment.StaffRole.OWNER, Collections.emptySet()));

        UUID managerId = UUID.randomUUID();
        Member manager = new Member(managerId, "manager", "m@test.com", "pass");
        manager.addStaffAppointment(COMPANY_NAME, new StaffAppointment(COMPANY_NAME, founderId, StaffAppointment.StaffRole.MANAGER, Collections.emptySet()));

        Company company = new Company(COMPANY_NAME, "desc", founderId);

        when(memberRepository.findById(founderId)).thenReturn(Optional.of(founder));
        when(memberRepository.findById(managerId)).thenReturn(Optional.of(manager));
        when(companyRepository.findByName(COMPANY_NAME)).thenReturn(Optional.of(company));

        Set<ManagerPermission> newPerms = Set.of(ManagerPermission.VIEW_REPORTS);
        ManagerPermissionsChangedEvent event = new ManagerPermissionsChangedEvent(founderId, managerId, COMPANY_NAME, newPerms);

        handler.handle(event);

        assertTrue(manager.getStaffAppointment(COMPANY_NAME).getPermissions().contains(ManagerPermission.VIEW_REPORTS));
        verify(memberRepository).save(manager);
    }

    @Test
    public void testHandle_DirectAppointerChangesPermissions_Success() {
        UUID founderId = UUID.randomUUID();
        UUID appointerId = UUID.randomUUID();
        Member appointer = new Member(appointerId, "appointer", "a@test.com", "pass");
        appointer.addStaffAppointment(COMPANY_NAME, new StaffAppointment(COMPANY_NAME, founderId, StaffAppointment.StaffRole.MANAGER, Collections.emptySet()));

        UUID managerId = UUID.randomUUID();
        Member manager = new Member(managerId, "manager", "m@test.com", "pass");
        manager.addStaffAppointment(COMPANY_NAME, new StaffAppointment(COMPANY_NAME, appointerId, StaffAppointment.StaffRole.MANAGER, Collections.emptySet()));

        Company company = new Company(COMPANY_NAME, "desc", founderId);

        when(memberRepository.findById(appointerId)).thenReturn(Optional.of(appointer));
        when(memberRepository.findById(managerId)).thenReturn(Optional.of(manager));
        when(companyRepository.findByName(COMPANY_NAME)).thenReturn(Optional.of(company));

        Set<ManagerPermission> newPerms = Set.of(ManagerPermission.VIEW_REPORTS);
        ManagerPermissionsChangedEvent event = new ManagerPermissionsChangedEvent(appointerId, managerId, COMPANY_NAME, newPerms);

        handler.handle(event);

        assertTrue(manager.getStaffAppointment(COMPANY_NAME).getPermissions().contains(ManagerPermission.VIEW_REPORTS));
        verify(memberRepository).save(manager);
    }

    @Test
    public void testHandle_SiblingOwnerCannotModify_ThrowsSecurityException() {
        UUID founderId = UUID.randomUUID();
        UUID owner2Id = UUID.randomUUID();
        
        Member founder = new Member(founderId, "founder", "f@test.com", "pass");
        founder.addStaffAppointment(COMPANY_NAME, new StaffAppointment(COMPANY_NAME, null, StaffAppointment.StaffRole.OWNER, Collections.emptySet()));

        Member owner2 = new Member(owner2Id, "owner2", "o2@test.com", "pass");
        owner2.addStaffAppointment(COMPANY_NAME, new StaffAppointment(COMPANY_NAME, founderId, StaffAppointment.StaffRole.OWNER, Collections.emptySet()));

        UUID managerId = UUID.randomUUID();
        Member manager = new Member(managerId, "manager", "m@test.com", "pass");
        manager.addStaffAppointment(COMPANY_NAME, new StaffAppointment(COMPANY_NAME, founderId, StaffAppointment.StaffRole.MANAGER, Collections.emptySet()));

        Company company = new Company(COMPANY_NAME, "desc", founderId);

        when(memberRepository.findById(owner2Id)).thenReturn(Optional.of(owner2));
        when(memberRepository.findById(managerId)).thenReturn(Optional.of(manager));
        when(companyRepository.findByName(COMPANY_NAME)).thenReturn(Optional.of(company));

        ManagerPermissionsChangedEvent event = new ManagerPermissionsChangedEvent(owner2Id, managerId, COMPANY_NAME, Set.of(ManagerPermission.VIEW_REPORTS));

        assertThrows(SecurityException.class, () -> handler.handle(event));
        verify(memberRepository, never()).save(any());
    }

    @Test
    public void testHandle_Idempotency_NoSave() {
        UUID founderId = UUID.randomUUID();
        Member founder = new Member(founderId, "founder", "f@test.com", "pass");
        founder.addStaffAppointment(COMPANY_NAME, new StaffAppointment(COMPANY_NAME, null, StaffAppointment.StaffRole.OWNER, Collections.emptySet()));

        UUID managerId = UUID.randomUUID();
        Member manager = new Member(managerId, "manager", "m@test.com", "pass");
        Set<ManagerPermission> existingPerms = Set.of(ManagerPermission.VIEW_REPORTS);
        manager.addStaffAppointment(COMPANY_NAME, new StaffAppointment(COMPANY_NAME, founderId, StaffAppointment.StaffRole.MANAGER, existingPerms));

        Company company = new Company(COMPANY_NAME, "desc", founderId);

        when(memberRepository.findById(founderId)).thenReturn(Optional.of(founder));
        when(memberRepository.findById(managerId)).thenReturn(Optional.of(manager));
        when(companyRepository.findByName(COMPANY_NAME)).thenReturn(Optional.of(company));

        ManagerPermissionsChangedEvent event = new ManagerPermissionsChangedEvent(founderId, managerId, COMPANY_NAME, existingPerms);

        handler.handle(event);

        verify(memberRepository, never()).save(any());
    }

    @Test
    public void testHandle_TargetNotManager_ThrowsIllegalArgumentException() {
        UUID founderId = UUID.randomUUID();
        Member founder = new Member(founderId, "founder", "f@test.com", "pass");
        founder.addStaffAppointment(COMPANY_NAME, new StaffAppointment(COMPANY_NAME, null, StaffAppointment.StaffRole.OWNER, Collections.emptySet()));

        UUID owner2Id = UUID.randomUUID();
        Member owner2 = new Member(owner2Id, "owner2", "o2@test.com", "pass");
        owner2.addStaffAppointment(COMPANY_NAME, new StaffAppointment(COMPANY_NAME, founderId, StaffAppointment.StaffRole.OWNER, Collections.emptySet()));

        Company company = new Company(COMPANY_NAME, "desc", founderId);

        when(memberRepository.findById(founderId)).thenReturn(Optional.of(founder));
        when(memberRepository.findById(owner2Id)).thenReturn(Optional.of(owner2));
        when(companyRepository.findByName(COMPANY_NAME)).thenReturn(Optional.of(company));

        ManagerPermissionsChangedEvent event = new ManagerPermissionsChangedEvent(founderId, owner2Id, COMPANY_NAME, Set.of(ManagerPermission.VIEW_REPORTS));

        assertThrows(IllegalArgumentException.class, () -> handler.handle(event));
    }
}
