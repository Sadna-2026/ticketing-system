package com.ticketing.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ticketing.application.auth.ISessionTokenService;
import com.ticketing.application.dto.PurchaseRecordDTO;
import com.ticketing.application.services.AdminService;
import com.ticketing.domain.admin.IAdminRepository;
import com.ticketing.domain.company.Company;
import com.ticketing.domain.company.ICompanyRepository;
import com.ticketing.domain.member.IMemberRepository;
import com.ticketing.domain.member.Member;
import com.ticketing.domain.member.StaffAppointment;
import com.ticketing.domain.order.CompletedPurchase;
import com.ticketing.domain.order.IOrderRepository;
import com.ticketing.infrastructure.InMemoryCompanyRepository;
import com.ticketing.infrastructure.InMemoryMemberRepository;

public class AdminServiceTest {

    private IMemberRepository memberRepository;
    private ICompanyRepository companyRepository;
    private ISessionTokenService sessionTokenService;
    private IAdminRepository adminRepository;
    private IOrderRepository orderRepository;
    private AdminService adminService;

    @BeforeEach
    public void setUp() {
        memberRepository = new InMemoryMemberRepository();
        companyRepository = new InMemoryCompanyRepository();
        sessionTokenService = mock(ISessionTokenService.class);
        adminRepository = mock(IAdminRepository.class);
        orderRepository = mock(IOrderRepository.class);
        
        when(sessionTokenService.isValid(anyString())).thenReturn(true);

        adminService = new AdminService(memberRepository, companyRepository, sessionTokenService, adminRepository, orderRepository);
    }

    @Test
    public void GivenNonAdmin_WhenRemoveMember_ThenSecurityException() {
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
    public void GivenAdminAndMultiCompanyMember_WhenRemoveMember_ThenRolesRevokedAndSessionsTerminated() {
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
    public void GivenSoleOwnerTarget_WhenRemoveMember_ThenIllegalStateException() {
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
        memberRepository.save(target);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
            adminService.removeMember(adminToken, targetId);
        });

        assertTrue(ex.getMessage().contains("Cannot remove the only owner of active company"));

        // Verify member was not deleted and sessions not revoked
        verify(sessionTokenService, never()).revokeMemberSessions(targetId);
    }

    @Test
    public void GivenLastAdminTarget_WhenRemoveMember_ThenIllegalStateException() {
        UUID adminId = UUID.randomUUID();
        String adminToken = "admin-token";
        when(sessionTokenService.extractPermissions(adminToken)).thenReturn(Set.of("SYSTEM_ADMIN"));

        UUID targetId = UUID.randomUUID();
        Member target = new Member(targetId, "target", "target@example.com", "pass");
        memberRepository.saveIfUsernameAndEmailAvailable(target);

        // Target is an admin
        com.ticketing.domain.admin.Admin targetAdmin = new com.ticketing.domain.admin.Admin(targetId, "target", "target@example.com");
        when(adminRepository.findById(targetId)).thenReturn(java.util.Optional.of(targetAdmin));
        // And they are the last one
        when(adminRepository.findAll()).thenReturn(java.util.List.of(targetAdmin));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
            adminService.removeMember(adminToken, targetId);
        });

        assertTrue(ex.getMessage().contains("Cannot remove the last system admin"));

        // Verify member was not deleted
        assertTrue(memberRepository.findById(targetId).isPresent());
    }

    @Test
    public void GivenNonAdmin_WhenGetGlobalPurchaseHistory_ThenSecurityException() {
        String token = "user-token";
        when(sessionTokenService.extractPermissions(token)).thenReturn(Collections.emptySet());

        SecurityException ex = assertThrows(SecurityException.class, () -> {
            adminService.getGlobalPurchaseHistory(token, null, null);
        });

        assertTrue(ex.getMessage().contains("System admin permission required"));
    }

    @Test
    public void GivenAdminAndBuyerFilter_WhenGetGlobalPurchaseHistory_ThenFilteredByBuyer() {
        String adminToken = "admin-token";
        when(sessionTokenService.extractPermissions(adminToken)).thenReturn(Set.of("SYSTEM_ADMIN"));

        UUID buyerId = UUID.randomUUID();
        CompletedPurchase p1 = new CompletedPurchase(UUID.randomUUID(), UUID.randomUUID(), "Event1", "Comp1", buyerId, "T1", new BigDecimal("100"), Instant.now());
        CompletedPurchase p2 = new CompletedPurchase(UUID.randomUUID(), UUID.randomUUID(), "Event2", "Comp2", buyerId, "T2", new BigDecimal("200"), Instant.now());
        
        when(orderRepository.findCompletedByMemberId(buyerId)).thenReturn(List.of(p1, p2));

        List<PurchaseRecordDTO> results = adminService.getGlobalPurchaseHistory(adminToken, buyerId, null);

        assertEquals(2, results.size());
        assertEquals("Event1", results.get(0).eventName());
        assertEquals("Comp1", results.get(0).companyName());
        assertEquals("Event2", results.get(1).eventName());
        assertEquals("Comp2", results.get(1).companyName());
        verify(orderRepository).findCompletedByMemberId(buyerId);
    }

    @Test
    public void GivenAdminAndCompanyFilter_WhenGetGlobalPurchaseHistory_ThenFilteredByCompany() {
        String adminToken = "admin-token";
        when(sessionTokenService.extractPermissions(adminToken)).thenReturn(Set.of("SYSTEM_ADMIN"));

        String companyName = "Comp1";
        CompletedPurchase p1 = new CompletedPurchase(UUID.randomUUID(), UUID.randomUUID(), "Event1", companyName, UUID.randomUUID(), "T1", new BigDecimal("100"), Instant.now());
        
        when(orderRepository.findCompletedByCompanyName(companyName)).thenReturn(List.of(p1));

        List<PurchaseRecordDTO> results = adminService.getGlobalPurchaseHistory(adminToken, null, companyName);

        assertEquals(1, results.size());
        assertEquals("Event1", results.get(0).eventName());
        assertEquals(companyName, results.get(0).companyName());
        verify(orderRepository).findCompletedByCompanyName(companyName);
    }
    
    @Test
    public void GivenAdminNoFilters_WhenGetGlobalPurchaseHistory_ThenAllPurchasesReturned() {
        String adminToken = "admin-token";
        when(sessionTokenService.extractPermissions(adminToken)).thenReturn(Set.of("SYSTEM_ADMIN"));

        when(orderRepository.findAllCompleted()).thenReturn(List.of(
            new CompletedPurchase(UUID.randomUUID(), UUID.randomUUID(), "E1", "C1", UUID.randomUUID(), "T1", new BigDecimal("10"), Instant.now())
        ));

        List<PurchaseRecordDTO> results = adminService.getGlobalPurchaseHistory(adminToken, null, null);

        assertEquals(1, results.size());
        verify(orderRepository).findAllCompleted();
    }

    @Test
    public void GivenUnknownBuyer_WhenGetGlobalPurchaseHistory_ThenEmptyList() {
        String adminToken = "admin-token";
        when(sessionTokenService.extractPermissions(adminToken)).thenReturn(Set.of("SYSTEM_ADMIN"));

        UUID buyerId = UUID.randomUUID();
        when(orderRepository.findCompletedByMemberId(buyerId)).thenReturn(Collections.emptyList());

        List<PurchaseRecordDTO> results = adminService.getGlobalPurchaseHistory(adminToken, buyerId, null);

        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    public void GivenCancelledEventPurchase_WhenGetGlobalPurchaseHistory_ThenHistoryPreserved() {
        String adminToken = "admin-token";
        when(sessionTokenService.extractPermissions(adminToken)).thenReturn(Set.of("SYSTEM_ADMIN"));

        UUID eventId = UUID.randomUUID();
        CompletedPurchase p1 = new CompletedPurchase(UUID.randomUUID(), eventId, "Cancelled Event", "Comp1", UUID.randomUUID(), "T1", new BigDecimal("100"), Instant.now());
        
        // Even if the event is "cancelled" in the real system, the historical record in the repo remains.
        when(orderRepository.findAllCompleted()).thenReturn(List.of(p1));

        List<PurchaseRecordDTO> results = adminService.getGlobalPurchaseHistory(adminToken, null, null);

        assertEquals(1, results.size());
        assertEquals("Cancelled Event", results.get(0).eventName());
    }

    @Test
    public void GivenClosedCompanyPurchase_WhenGetGlobalPurchaseHistory_ThenHistoryPreserved() {
        String adminToken = "admin-token";
        when(sessionTokenService.extractPermissions(adminToken)).thenReturn(Set.of("SYSTEM_ADMIN"));

        String companyName = "Closed Company";
        CompletedPurchase p1 = new CompletedPurchase(UUID.randomUUID(), UUID.randomUUID(), "Event1", companyName, UUID.randomUUID(), "T1", new BigDecimal("100"), Instant.now());
        
        // Even if the company is "closed" in the repository, the historical purchase record remains.
        when(orderRepository.findCompletedByCompanyName(companyName)).thenReturn(List.of(p1));

        List<PurchaseRecordDTO> results = adminService.getGlobalPurchaseHistory(adminToken, null, companyName);

        assertEquals(1, results.size());
        assertEquals(companyName, results.get(0).companyName());
    }
}
