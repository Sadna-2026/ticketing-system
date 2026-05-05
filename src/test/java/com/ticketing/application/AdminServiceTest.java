package com.ticketing.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.ticketing.application.auth.ISessionTokenService;
import com.ticketing.domain.company.Company;
import com.ticketing.domain.company.ICompanyRepository;
import com.ticketing.domain.member.IMemberRepository;
import com.ticketing.domain.member.Member;
import com.ticketing.domain.member.StaffAppointment;
import com.ticketing.infrastructure.InMemoryCompanyRepository;
import com.ticketing.infrastructure.InMemoryMemberRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;

public class AdminServiceTest {

    private IMemberRepository memberRepository;
    private ICompanyRepository companyRepository;
    private ISessionTokenService sessionTokenService;
    private AdminService adminService;

    @BeforeEach
    public void setUp() {
        memberRepository = new InMemoryMemberRepository();
        companyRepository = new InMemoryCompanyRepository();
        sessionTokenService = mock(ISessionTokenService.class);
        
        when(sessionTokenService.isValid(anyString())).thenReturn(true);

        adminService = new AdminService(memberRepository, companyRepository, sessionTokenService);
    }

    @Test
    public void testNonAdminDenied() {
        UUID targetId = UUID.randomUUID();
        String token = "user-token";

        // Mock token to not have SYSTEM_ADMIN
        when(sessionTokenService.extractPermissions(token)).thenReturn(Collections.emptySet());

        SecurityException ex = assertThrows(SecurityException.class, () -> {
            adminService.removeMember(token, targetId);
        });

        assertTrue(ex.getMessage().contains("System admin permission required"));
    }

    @Test
    public void testCrossCompanyRoleRevocationAndSessionTermination() {
        UUID adminId = UUID.randomUUID();
        String adminToken = "admin-token";
        when(sessionTokenService.extractPermissions(adminToken)).thenReturn(Set.of("SYSTEM_ADMIN"));

        UUID targetId = UUID.randomUUID();
        Member target = new Member(targetId, "target", "target@example.com", "pass");
        memberRepository.saveIfUsernameAndEmailAvailable(target);

        // Target has roles in company 1 and 2, but not as the sole owner
        Company company1 = new Company("Comp1", "desc", UUID.randomUUID());
        Company company2 = new Company("Comp2", "desc", UUID.randomUUID());
        companyRepository.save(company1);
        companyRepository.save(company2);

        // Setup another owner for both companies to avoid Sole Owner check failure
        UUID otherOwnerId = UUID.randomUUID();
        Member otherOwner = new Member(otherOwnerId, "other", "other@example.com", "pass");
        otherOwner.addStaffAppointment("Comp1", new StaffAppointment("Comp1", UUID.randomUUID(), StaffAppointment.StaffRole.OWNER, Collections.emptySet()));
        otherOwner.addStaffAppointment("Comp2", new StaffAppointment("Comp2", UUID.randomUUID(), StaffAppointment.StaffRole.OWNER, Collections.emptySet()));
        memberRepository.saveIfUsernameAndEmailAvailable(otherOwner);

        // Give target owner roles
        target.addStaffAppointment("Comp1", new StaffAppointment("Comp1", UUID.randomUUID(), StaffAppointment.StaffRole.OWNER, Collections.emptySet()));
        target.addStaffAppointment("Comp2", new StaffAppointment("Comp2", UUID.randomUUID(), StaffAppointment.StaffRole.MANAGER, Collections.emptySet()));

        // Setup an appointee appointed by the target to verify non-cascading behavior
        UUID appointeeId = UUID.randomUUID();
        Member appointee = new Member(appointeeId, "appointee", "appointee@example.com", "pass");
        appointee.addStaffAppointment("Comp1", new StaffAppointment("Comp1", targetId, StaffAppointment.StaffRole.MANAGER, Collections.emptySet()));
        memberRepository.saveIfUsernameAndEmailAvailable(appointee);

        // Act
        adminService.removeMember(adminToken, targetId);

        // Assert member is deleted (which revokes roles indirectly since Member is dropped)
        assertTrue(memberRepository.findById(targetId).isEmpty());
        // Assert sessions are terminated
        verify(sessionTokenService).revokeMemberSessions(targetId);
        // Assert appointee's role remains unchanged (non-cascading)
        assertTrue(memberRepository.findById(appointeeId).isPresent());
        assertTrue(memberRepository.findById(appointeeId).get().hasStaffAppointment("Comp1", StaffAppointment.StaffRole.MANAGER));
    }

    @Test
    public void testCompanyIntegrityBlock() {
        UUID adminId = UUID.randomUUID();
        String adminToken = "admin-token";
        when(sessionTokenService.extractPermissions(adminToken)).thenReturn(Set.of("SYSTEM_ADMIN"));

        UUID targetId = UUID.randomUUID();
        Member target = new Member(targetId, "target", "target@example.com", "pass");
        memberRepository.saveIfUsernameAndEmailAvailable(target);

        // Target is sole owner
        Company company = new Company("SoleComp", "desc", targetId);
        companyRepository.save(company);

        target.addStaffAppointment("SoleComp", new StaffAppointment("SoleComp", targetId, StaffAppointment.StaffRole.OWNER, Collections.emptySet()));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
            adminService.removeMember(adminToken, targetId);
        });

        assertTrue(ex.getMessage().contains("Cannot remove the only owner of active company"));

        // Verify member was not deleted and sessions not revoked
        assertTrue(memberRepository.findById(targetId).isPresent());
        verify(sessionTokenService, never()).revokeMemberSessions(targetId);
    }


}
