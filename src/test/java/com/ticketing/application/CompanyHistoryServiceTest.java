package com.ticketing.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ticketing.application.auth.ISessionTokenService;
import com.ticketing.domain.company.Company;
import com.ticketing.domain.member.ManagerPermission;
import com.ticketing.domain.member.Member;
import com.ticketing.domain.member.StaffAppointment;
import com.ticketing.domain.order.CompletedPurchase;
import com.ticketing.infrastructure.InMemoryCompanyRepository;
import com.ticketing.infrastructure.InMemoryCompletedPurchaseRepository;
import com.ticketing.infrastructure.InMemoryMemberRepository;

public class CompanyHistoryServiceTest {

    private static final String COMPANY = "Acme Productions";
    private static final String OTHER_COMPANY = "Other Co";
    private static final String OWNER_TOKEN = "owner-token";
    private static final String OUTSIDER_TOKEN = "outsider-token";

    private InMemoryCompanyRepository companyRepo;
    private InMemoryMemberRepository memberRepo;
    private InMemoryCompletedPurchaseRepository completedPurchaseRepo;
    private ISessionTokenService tokens;
    private CompanyHistoryService service;

    private UUID ownerId;
    private Member owner;

    @BeforeEach
    public void setUp() {
        companyRepo = new InMemoryCompanyRepository();
        memberRepo = new InMemoryMemberRepository();
        completedPurchaseRepo = new InMemoryCompletedPurchaseRepository();
        tokens = mock(ISessionTokenService.class);
        service = new CompanyHistoryService(companyRepo, memberRepo, completedPurchaseRepo, tokens);

        ownerId = UUID.randomUUID();
        owner = new Member(ownerId, "owner", "owner@x.com", "pw");
        owner.addStaffAppointment(COMPANY,
                new StaffAppointment(COMPANY, ownerId, StaffAppointment.StaffRole.OWNER, Set.of()));
        memberRepo.save(owner);

        companyRepo.save(new Company(COMPANY, "desc", ownerId));

        when(tokens.isValid(OWNER_TOKEN)).thenReturn(true);
        when(tokens.extractMemberId(OWNER_TOKEN)).thenReturn(ownerId);
        when(tokens.isValid(OUTSIDER_TOKEN)).thenReturn(true);
        when(tokens.extractMemberId(OUTSIDER_TOKEN)).thenReturn(UUID.randomUUID());
    }

    @Test
    public void GivenOwner_WhenGetPurchaseHistory_ThenReturnsAllCompanyPurchases() {
        completedPurchaseRepo.save(purchase("Concert A", new BigDecimal("50.00")));
        completedPurchaseRepo.save(purchase("Concert B", new BigDecimal("75.00")));

        List<PurchaseRecordDTO> history = service.getPurchaseHistory(OWNER_TOKEN, COMPANY);

        assertEquals(2, history.size());
    }

    @Test
    public void GivenManagerWithViewReports_WhenGetPurchaseHistory_ThenReturnsHistory() {
        UUID managerId = UUID.randomUUID();
        Member manager = new Member(managerId, "mgr", "m@x.com", "pw");
        manager.addStaffAppointment(COMPANY,
                new StaffAppointment(COMPANY, managerId, StaffAppointment.StaffRole.MANAGER,
                        Set.of(ManagerPermission.VIEW_REPORTS)));
        memberRepo.save(manager);
        when(tokens.isValid("mgr-token")).thenReturn(true);
        when(tokens.extractMemberId("mgr-token")).thenReturn(managerId);
        completedPurchaseRepo.save(purchase("Concert", new BigDecimal("20.00")));

        List<PurchaseRecordDTO> history = service.getPurchaseHistory("mgr-token", COMPANY);

        assertEquals(1, history.size());
    }

    @Test
    public void GivenManagerWithoutViewReports_WhenGetPurchaseHistory_ThenThrowSecurityException() {
        UUID managerId = UUID.randomUUID();
        Member manager = new Member(managerId, "mgr", "m@x.com", "pw");
        manager.addStaffAppointment(COMPANY,
                new StaffAppointment(COMPANY, managerId, StaffAppointment.StaffRole.MANAGER,
                        Set.of(ManagerPermission.INVENTORY_MGMT)));
        memberRepo.save(manager);
        when(tokens.isValid("mgr-token")).thenReturn(true);
        when(tokens.extractMemberId("mgr-token")).thenReturn(managerId);

        assertThrows(SecurityException.class,
                () -> service.getPurchaseHistory("mgr-token", COMPANY));
    }

    @Test
    public void GivenOutsider_WhenGetPurchaseHistory_ThenThrowSecurityException() {
        // outsider has a member account but no appointment for this company
        UUID outsiderId = UUID.randomUUID();
        memberRepo.save(new Member(outsiderId, "outsider", "o@x.com", "pw"));
        when(tokens.extractMemberId(OUTSIDER_TOKEN)).thenReturn(outsiderId);

        assertThrows(SecurityException.class,
                () -> service.getPurchaseHistory(OUTSIDER_TOKEN, COMPANY));
    }

    @Test
    public void GivenGuestToken_WhenGetPurchaseHistory_ThenThrowSecurityException() {
        when(tokens.extractMemberId(OWNER_TOKEN)).thenReturn(null);

        assertThrows(SecurityException.class,
                () -> service.getPurchaseHistory(OWNER_TOKEN, COMPANY));
    }

    @Test
    public void GivenUnknownCompany_WhenGetPurchaseHistory_ThenThrowIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> service.getPurchaseHistory(OWNER_TOKEN, "Nonexistent Co"));
    }

    @Test
    public void GivenCompanyWithNoPurchases_WhenGetPurchaseHistory_ThenReturnsEmptyList() {
        List<PurchaseRecordDTO> history = service.getPurchaseHistory(OWNER_TOKEN, COMPANY);

        assertTrue(history.isEmpty());
    }

    @Test
    public void GivenPurchasesAcrossCompanies_WhenGetPurchaseHistory_ThenReturnsOnlyOwn() {
        // seed another company with its own purchase
        companyRepo.save(new Company(OTHER_COMPANY, "x", UUID.randomUUID()));
        completedPurchaseRepo.save(new CompletedPurchase(UUID.randomUUID(), UUID.randomUUID(),
                "Other Concert", OTHER_COMPANY, UUID.randomUUID(),
                "txn-other", new BigDecimal("30.00"), Instant.now()));
        // and one purchase under our company
        completedPurchaseRepo.save(purchase("Mine", new BigDecimal("99.00")));

        List<PurchaseRecordDTO> history = service.getPurchaseHistory(OWNER_TOKEN, COMPANY);

        assertEquals(1, history.size());
        assertEquals("Mine", history.get(0).eventName());
    }

    @Test
    public void GivenSnapshotPurchase_WhenSourceFieldsCannotMutate_ThenDtoStaysFrozen() {
        // The CompletedPurchase record is immutable by design — no mutator exists for
        // eventName, amount, etc. This test pins that invariant: even after time passes,
        // the DTO returned at query time matches the originally captured snapshot.
        Instant when = Instant.parse("2026-01-15T10:00:00Z");
        CompletedPurchase original = new CompletedPurchase(UUID.randomUUID(), UUID.randomUUID(),
                "Original Event Name", COMPANY, UUID.randomUUID(),
                "txn-snap", new BigDecimal("123.45"), when);
        completedPurchaseRepo.save(original);

        PurchaseRecordDTO dto = service.getPurchaseHistory(OWNER_TOKEN, COMPANY).get(0);

        assertEquals("Original Event Name", dto.eventName());
        assertEquals(when, dto.purchasedAt());
        assertEquals(0, dto.amount().compareTo(new BigDecimal("123.45")));
    }

    private CompletedPurchase purchase(String eventName, BigDecimal amount) {
        return new CompletedPurchase(UUID.randomUUID(), UUID.randomUUID(), eventName, COMPANY,
                UUID.randomUUID(), "txn-" + UUID.randomUUID().toString().substring(0, 6),
                amount, Instant.now());
    }
}
