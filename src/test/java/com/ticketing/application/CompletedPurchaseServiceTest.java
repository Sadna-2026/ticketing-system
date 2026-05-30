package com.ticketing.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ticketing.application.auth.ISessionTokenService;
import com.ticketing.application.dto.SalesReportDTO;
import com.ticketing.application.services.CompletedPurchaseService;
import com.ticketing.domain.company.Company;
import com.ticketing.domain.member.ManagerPermission;
import com.ticketing.domain.member.Member;
import com.ticketing.domain.member.StaffAppointment;
import com.ticketing.domain.order.CompletedPurchase;
import com.ticketing.infrastructure.InMemoryCompanyRepository;
import com.ticketing.infrastructure.InMemoryMemberRepository;
import com.ticketing.infrastructure.InMemoryOrderRepository;

/**
 * Unit tests for CompletedPurchaseService.
 * Covers hierarchical sales report generation with proper authorization and scope.
 */
public class CompletedPurchaseServiceTest {

    private static final String COMPANY = "Acme Productions";
    private static final String OWNER_TOKEN = "owner-token";
    private static final String MANAGER_TOKEN = "manager-token";
    private static final String GUEST_TOKEN = "guest-token";
    private static final String OUTSIDER_TOKEN = "outsider-token";

    private InMemoryOrderRepository orderRepo;
    private InMemoryCompanyRepository companyRepo;
    private InMemoryMemberRepository memberRepo;
    private ISessionTokenService tokens;
    private CompletedPurchaseService service;

    private UUID ownerId;
    private UUID managerId;
    private UUID subordinate1Id;
    private UUID subordinate2Id;
    private Member owner;
    private Member manager;
    private Member subordinate1;
    private Member subordinate2;

    @BeforeEach
    public void setUp() {
        orderRepo = new InMemoryOrderRepository();
        companyRepo = new InMemoryCompanyRepository();
        memberRepo = new InMemoryMemberRepository();
        tokens = mock(ISessionTokenService.class);
        service = new CompletedPurchaseService(orderRepo, companyRepo, memberRepo, tokens);

        // Setup owner
        ownerId = UUID.randomUUID();
        owner = new Member(ownerId, "owner", "owner@x.com", "pw");
        owner.addStaffAppointment(COMPANY,
                new StaffAppointment(COMPANY, ownerId, StaffAppointment.StaffRole.OWNER, Set.of()));
        memberRepo.save(owner);

        // Setup manager (appointed by owner)
        managerId = UUID.randomUUID();
        manager = new Member(managerId, "manager", "manager@x.com", "pw");
        Set<UUID> managerSubordinates = new HashSet<>();
        manager.addStaffAppointment(COMPANY,
                new StaffAppointment(COMPANY, ownerId, StaffAppointment.StaffRole.MANAGER,
                        Set.of(ManagerPermission.VIEW_REPORTS), managerSubordinates));
        memberRepo.save(manager);

        // Add manager as subordinate to owner's appointment
        StaffAppointment ownerAppt = owner.getStaffAppointment(COMPANY);
        ownerAppt.addAppointedStaffMember(managerId);
        memberRepo.save(owner);

        // Setup subordinates under manager
        subordinate1Id = UUID.randomUUID();
        subordinate1 = new Member(subordinate1Id, "subordinate1", "sub1@x.com", "pw");
        subordinate1.addStaffAppointment(COMPANY,
                new StaffAppointment(COMPANY, managerId, StaffAppointment.StaffRole.MANAGER,
                        Set.of(), new HashSet<>()));
        memberRepo.save(subordinate1);

        subordinate2Id = UUID.randomUUID();
        subordinate2 = new Member(subordinate2Id, "subordinate2", "sub2@x.com", "pw");
        subordinate2.addStaffAppointment(COMPANY,
                new StaffAppointment(COMPANY, managerId, StaffAppointment.StaffRole.MANAGER,
                        Set.of(), new HashSet<>()));
        memberRepo.save(subordinate2);

        // Add subordinates to manager's subordinate list
        StaffAppointment managerAppt = manager.getStaffAppointment(COMPANY);
        managerAppt.addAppointedStaffMember(subordinate1Id);
        managerAppt.addAppointedStaffMember(subordinate2Id);
        memberRepo.save(manager);

        // Setup company
        companyRepo.save(new Company(COMPANY, "desc", ownerId));

        // Setup tokens
        when(tokens.isValid(OWNER_TOKEN)).thenReturn(true);
        when(tokens.extractMemberId(OWNER_TOKEN)).thenReturn(ownerId);

        when(tokens.isValid(MANAGER_TOKEN)).thenReturn(true);
        when(tokens.extractMemberId(MANAGER_TOKEN)).thenReturn(managerId);

        when(tokens.isValid(GUEST_TOKEN)).thenReturn(true);
        when(tokens.extractMemberId(GUEST_TOKEN)).thenReturn(null); // guest has no memberId

        when(tokens.isValid(OUTSIDER_TOKEN)).thenReturn(true);
        when(tokens.extractMemberId(OUTSIDER_TOKEN)).thenReturn(UUID.randomUUID());
    }

    @Test
    public void GivenOwner_WhenGetHierarchicalSalesReport_ThenIncludesAllCompanyPurchases() {
        // Add purchases from various members
        addPurchase(ownerId, "Concert A", new BigDecimal("100.00"));
        addPurchase(managerId, "Concert B", new BigDecimal("75.00"));
        addPurchase(subordinate1Id, "Concert C", new BigDecimal("50.00"));
        addPurchase(subordinate2Id, "Concert D", new BigDecimal("25.00"));

        SalesReportDTO report = service.getHierarchicalSalesReport(OWNER_TOKEN, COMPANY);

        assertEquals(COMPANY, report.companyName());
        assertEquals(ownerId, report.requestedByMemberId());
        assertEquals(4, report.totalPurchases());
        assertEquals(new BigDecimal("250.00"), report.totalRevenue());
    }

    @Test
    public void GivenManagerWithViewReports_WhenGetHierarchicalSalesReport_ThenIncludesSubtreePurchases() {
        // Add purchases
        addPurchase(ownerId, "Concert Owner", new BigDecimal("100.00")); // should NOT be included
        addPurchase(managerId, "Concert Manager", new BigDecimal("75.00"));
        addPurchase(subordinate1Id, "Concert Sub1", new BigDecimal("50.00"));
        addPurchase(subordinate2Id, "Concert Sub2", new BigDecimal("25.00"));

        SalesReportDTO report = service.getHierarchicalSalesReport(MANAGER_TOKEN, COMPANY);

        assertEquals(COMPANY, report.companyName());
        assertEquals(managerId, report.requestedByMemberId());
        assertEquals(3, report.totalPurchases()); // manager + 2 subordinates
        assertEquals(new BigDecimal("150.00"), report.totalRevenue()); // 75 + 50 + 25
    }

    @Test
    public void GivenManagerWithoutViewReports_WhenGetHierarchicalSalesReport_ThenThrowSecurityException() {
        // Create a manager without VIEW_REPORTS permission
        UUID noReportsManagerId = UUID.randomUUID();
        Member noReportsManager = new Member(noReportsManagerId, "noreports", "nr@x.com", "pw");
        noReportsManager.addStaffAppointment(COMPANY,
                new StaffAppointment(COMPANY, ownerId, StaffAppointment.StaffRole.MANAGER,
                        Set.of(ManagerPermission.INVENTORY_MGMT)));
        memberRepo.save(noReportsManager);

        when(tokens.isValid("noreports-token")).thenReturn(true);
        when(tokens.extractMemberId("noreports-token")).thenReturn(noReportsManagerId);

        assertThrows(SecurityException.class,
                () -> service.getHierarchicalSalesReport("noreports-token", COMPANY));
    }

    @Test
    public void GivenGuestToken_WhenGetHierarchicalSalesReport_ThenThrowSecurityException() {
        assertThrows(SecurityException.class,
                () -> service.getHierarchicalSalesReport(GUEST_TOKEN, COMPANY));
    }

    @Test
    public void GivenOutsider_WhenGetHierarchicalSalesReport_ThenThrowSecurityException() {
        // outsider has a member account but no appointment for this company
        UUID outsiderId = UUID.randomUUID();
        memberRepo.save(new Member(outsiderId, "outsider", "o@x.com", "pw"));
        when(tokens.extractMemberId(OUTSIDER_TOKEN)).thenReturn(outsiderId);

        assertThrows(SecurityException.class,
                () -> service.getHierarchicalSalesReport(OUTSIDER_TOKEN, COMPANY));
    }

    @Test
    public void GivenInvalidToken_WhenGetHierarchicalSalesReport_ThenThrowIllegalArgumentException() {
        when(tokens.isValid("invalid-token")).thenReturn(false);

        assertThrows(IllegalArgumentException.class,
                () -> service.getHierarchicalSalesReport("invalid-token", COMPANY));
    }

    @Test
    public void GivenNullToken_WhenGetHierarchicalSalesReport_ThenThrowIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> service.getHierarchicalSalesReport(null, COMPANY));
    }

    @Test
    public void GivenUnknownCompany_WhenGetHierarchicalSalesReport_ThenThrowIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> service.getHierarchicalSalesReport(OWNER_TOKEN, "Unknown Company"));
    }

    @Test
    public void GivenCompanyWithNoPurchases_WhenGetHierarchicalSalesReport_ThenReturnsEmptyReport() {
        SalesReportDTO report = service.getHierarchicalSalesReport(OWNER_TOKEN, COMPANY);

        assertEquals(COMPANY, report.companyName());
        assertEquals(ownerId, report.requestedByMemberId());
        assertEquals(0, report.totalPurchases());
        assertEquals(BigDecimal.ZERO, report.totalRevenue());
        assertTrue(report.purchases().isEmpty());
    }

    @Test
    public void GivenPurchasesOutsideSubtree_WhenGetHierarchicalSalesReport_ThenExcludesThem() {
        // Create another company with another owner
        UUID otherOwnerId = UUID.randomUUID();
        Member otherOwner = new Member(otherOwnerId, "otherowner", "other@x.com", "pw");
        otherOwner.addStaffAppointment("Other Company",
                new StaffAppointment("Other Company", otherOwnerId, StaffAppointment.StaffRole.OWNER, Set.of()));
        memberRepo.save(otherOwner);
        companyRepo.save(new Company("Other Company", "other", otherOwnerId));

        // Add purchase under other company
        orderRepo.save(new CompletedPurchase(UUID.randomUUID(), UUID.randomUUID(),
                "Other Concert", "Other Company", otherOwnerId,
                "txn-other", new BigDecimal("100.00"), Instant.now()));

        // Add purchase under our company
        addPurchase(ownerId, "Our Concert", new BigDecimal("50.00"));

        SalesReportDTO report = service.getHierarchicalSalesReport(OWNER_TOKEN, COMPANY);

        assertEquals(1, report.totalPurchases());
        assertEquals(new BigDecimal("50.00"), report.totalRevenue());
    }

    @Test
    public void GivenMultiLevelHierarchy_WhenGetHierarchicalSalesReport_ThenCollectsAllSubtree() {
        // Create another subordinate level (subordinate appointed by subordinate1)
        UUID subordinate1_1Id = UUID.randomUUID();
        Member subordinate1_1 = new Member(subordinate1_1Id, "sub1_1", "s11@x.com", "pw");
        subordinate1_1.addStaffAppointment(COMPANY,
                new StaffAppointment(COMPANY, subordinate1Id, StaffAppointment.StaffRole.MANAGER,
                        Set.of(), new HashSet<>()));
        memberRepo.save(subordinate1_1);

        // Add subordinate1_1 to subordinate1's subordinate list
        StaffAppointment sub1Appt = subordinate1.getStaffAppointment(COMPANY);
        sub1Appt.addAppointedStaffMember(subordinate1_1Id);
        memberRepo.save(subordinate1);

        // Add purchases at various levels
        addPurchase(ownerId, "Level 0", new BigDecimal("100.00"));
        addPurchase(managerId, "Level 1", new BigDecimal("50.00"));
        addPurchase(subordinate1Id, "Level 2", new BigDecimal("25.00"));
        addPurchase(subordinate1_1Id, "Level 3", new BigDecimal("10.00"));

        SalesReportDTO report = service.getHierarchicalSalesReport(OWNER_TOKEN, COMPANY);

        assertEquals(4, report.totalPurchases());
        assertEquals(new BigDecimal("185.00"), report.totalRevenue());
    }

    @Test
    public void GivenManagerRequestsReport_WhenSubordinatesHaveMoreLevels_ThenIncludesEntireSubtree() {
        // Create another subordinate level
        UUID subordinate1_1Id = UUID.randomUUID();
        Member subordinate1_1 = new Member(subordinate1_1Id, "sub1_1", "s11@x.com", "pw");
        subordinate1_1.addStaffAppointment(COMPANY,
                new StaffAppointment(COMPANY, subordinate1Id, StaffAppointment.StaffRole.MANAGER,
                        Set.of(), new HashSet<>()));
        memberRepo.save(subordinate1_1);

        StaffAppointment sub1Appt = subordinate1.getStaffAppointment(COMPANY);
        sub1Appt.addAppointedStaffMember(subordinate1_1Id);
        memberRepo.save(subordinate1);

        // Add purchases at all levels under manager
        addPurchase(managerId, "Manager Level", new BigDecimal("100.00"));
        addPurchase(subordinate1Id, "Sub1 Level", new BigDecimal("50.00"));
        addPurchase(subordinate1_1Id, "Sub1_1 Level", new BigDecimal("25.00"));
        addPurchase(subordinate2Id, "Sub2 Level", new BigDecimal("10.00"));

        SalesReportDTO report = service.getHierarchicalSalesReport(MANAGER_TOKEN, COMPANY);

        assertEquals(4, report.totalPurchases());
        assertEquals(new BigDecimal("185.00"), report.totalRevenue());
        assertEquals(managerId, report.requestedByMemberId());
    }

    private void addPurchase(UUID memberId, String eventName, BigDecimal amount) {
        orderRepo.save(new CompletedPurchase(
                UUID.randomUUID(),
                UUID.randomUUID(),
                eventName,
                COMPANY,
                memberId,
                "txn-" + UUID.randomUUID(),
                amount,
                Instant.now()
        ));
    }
}
