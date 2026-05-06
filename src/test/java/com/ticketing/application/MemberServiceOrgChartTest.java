package com.ticketing.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ticketing.application.auth.ISessionTokenService;
import com.ticketing.domain.member.IMemberRepository;
import com.ticketing.domain.member.ManagerPermission;
import com.ticketing.domain.member.Member;
import com.ticketing.domain.member.StaffAppointment;
import com.ticketing.infrastructure.PasswordEncryptionUtils;

public class MemberServiceOrgChartTest {

    private IMemberRepository memberRepository;
    private PasswordEncryptionUtils passwordEncryptionUtils;
    private ISessionTokenService sessionTokenService;
    private MemberService memberService;

    private final String COMPANY_NAME = "TestComp";
    private final String AUTH_TOKEN = "valid-token";
    private UUID ownerId;

    @BeforeEach
    public void setUp() {
        memberRepository = mock(IMemberRepository.class);
        passwordEncryptionUtils = new PasswordEncryptionUtils();
        sessionTokenService = mock(ISessionTokenService.class);
        memberService = new MemberService(memberRepository, passwordEncryptionUtils, sessionTokenService);

        ownerId = UUID.randomUUID();
        when(sessionTokenService.isValid(AUTH_TOKEN)).thenReturn(true);
        when(sessionTokenService.extractMemberId(AUTH_TOKEN)).thenReturn(ownerId);
    }

    @Test
    public void testGetOrganizationChart_AccessDeniedForNonOwner() {
        UUID managerId = UUID.randomUUID();
        Member manager = new Member(managerId, "manager", "m@test.com", "pass");
        manager.addStaffAppointment(COMPANY_NAME, new StaffAppointment(COMPANY_NAME, ownerId, StaffAppointment.StaffRole.MANAGER, Collections.emptySet()));
        
        when(sessionTokenService.extractMemberId(AUTH_TOKEN)).thenReturn(managerId);
        when(memberRepository.findById(managerId)).thenReturn(Optional.of(manager));

        assertThrows(SecurityException.class, () -> {
            memberService.getOrganizationChart(AUTH_TOKEN, COMPANY_NAME);
        });
    }

    @Test
    public void testGetOrganizationChart_Success() {
        // Setup Owner (Root)
        Member owner = new Member(ownerId, "owner", "o@test.com", "pass");
        owner.addStaffAppointment(COMPANY_NAME, new StaffAppointment(COMPANY_NAME, null, StaffAppointment.StaffRole.OWNER, Collections.emptySet()));
        
        // Setup Manager (Subordinate of Owner)
        UUID managerId = UUID.randomUUID();
        Member manager = new Member(managerId, "manager", "m@test.com", "pass");
        manager.addStaffAppointment(COMPANY_NAME, new StaffAppointment(COMPANY_NAME, ownerId, StaffAppointment.StaffRole.MANAGER, Set.of(ManagerPermission.VIEW_REPORTS)));

        // Setup Junior (Subordinate of Manager)
        UUID juniorId = UUID.randomUUID();
        Member junior = new Member(juniorId, "junior", "j@test.com", "pass");
        junior.addStaffAppointment(COMPANY_NAME, new StaffAppointment(COMPANY_NAME, managerId, StaffAppointment.StaffRole.MANAGER, Collections.emptySet()));

        when(memberRepository.findById(ownerId)).thenReturn(Optional.of(owner));
        when(memberRepository.findByCompanyAppointment(COMPANY_NAME)).thenReturn(List.of(owner, manager, junior));

        List<OrgNodeDTO> chart = memberService.getOrganizationChart(AUTH_TOKEN, COMPANY_NAME);

        assertEquals(1, chart.size());
        OrgNodeDTO root = chart.get(0);
        assertEquals(ownerId, root.memberId());
        assertEquals(1, root.subordinates().size());

        OrgNodeDTO managerNode = root.subordinates().get(0);
        assertEquals(managerId, managerNode.memberId());
        assertTrue(managerNode.permissions().contains(ManagerPermission.VIEW_REPORTS));
        assertEquals(1, managerNode.subordinates().size());

        OrgNodeDTO juniorNode = managerNode.subordinates().get(0);
        assertEquals(juniorId, juniorNode.memberId());
        assertTrue(juniorNode.subordinates().isEmpty());
    }

    @Test
    public void testGetOrganizationChart_HandlesDisconnectedRoots() {
        // Case where founder is gone, but multiple top-level owners exist
        Member owner1 = new Member(ownerId, "owner1", "o1@test.com", "pass");
        owner1.addStaffAppointment(COMPANY_NAME, new StaffAppointment(COMPANY_NAME, UUID.randomUUID(), StaffAppointment.StaffRole.OWNER, Collections.emptySet()));

        UUID owner2Id = UUID.randomUUID();
        Member owner2 = new Member(owner2Id, "owner2", "o2@test.com", "pass");
        owner2.addStaffAppointment(COMPANY_NAME, new StaffAppointment(COMPANY_NAME, UUID.randomUUID(), StaffAppointment.StaffRole.OWNER, Collections.emptySet()));

        when(memberRepository.findById(ownerId)).thenReturn(Optional.of(owner1));
        when(memberRepository.findByCompanyAppointment(COMPANY_NAME)).thenReturn(List.of(owner1, owner2));

        List<OrgNodeDTO> chart = memberService.getOrganizationChart(AUTH_TOKEN, COMPANY_NAME);

        assertEquals(2, chart.size());
    }
}
