package com.ticketing.application;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ticketing.application.auth.ISessionTokenService;
import com.ticketing.domain.company.Company;
import com.ticketing.domain.company.CompanyStatus;
import com.ticketing.domain.event.Event;
import com.ticketing.domain.event.EventCategory;
import com.ticketing.domain.event.EventSchedule;
import com.ticketing.domain.event.EventStatus;
import com.ticketing.domain.event.LockTimerDuration;
import com.ticketing.domain.gateway.IPaymentGateway;
import com.ticketing.domain.gateway.RefundResult;
import com.ticketing.domain.member.ManagerPermission;
import com.ticketing.domain.member.Member;
import com.ticketing.domain.member.StaffAppointment;
import com.ticketing.domain.order.CompletedPurchase;
import com.ticketing.infrastructure.InMemoryCompanyRepository;
import com.ticketing.infrastructure.InMemoryCompletedPurchaseRepository;
import com.ticketing.infrastructure.InMemoryEventRepository;
import com.ticketing.infrastructure.InMemoryMemberRepository;

public class CompanyLifecycleServiceTest {

    private static final String COMPANY = "Acme Productions";
    private static final String FOUNDER_TOKEN = "founder-token";
    private static final String ADMIN_TOKEN = "admin-token";
    private static final String OUTSIDER_TOKEN = "outsider-token";

    private InMemoryCompanyRepository companyRepo;
    private InMemoryEventRepository eventRepo;
    private InMemoryMemberRepository memberRepo;
    private InMemoryCompletedPurchaseRepository purchaseRepo;
    private IPaymentGateway paymentGateway;
    private ISessionTokenService tokens;
    private CompanyLifecycleService service;

    private UUID founderId;
    private Member founder;
    private Company company;

    @BeforeEach
    public void setUp() {
        companyRepo = new InMemoryCompanyRepository();
        eventRepo = new InMemoryEventRepository();
        memberRepo = new InMemoryMemberRepository();
        purchaseRepo = new InMemoryCompletedPurchaseRepository();
        paymentGateway = mock(IPaymentGateway.class);
        tokens = mock(ISessionTokenService.class);
        service = new CompanyLifecycleService(
                companyRepo, eventRepo, memberRepo, purchaseRepo, paymentGateway, tokens);

        founderId = UUID.randomUUID();
        founder = new Member(founderId, "founder", "founder@x.com", "pw");
        StaffAppointment ownerAppt = new StaffAppointment(
                COMPANY, founderId, StaffAppointment.StaffRole.OWNER, Set.of());
        founder.addStaffAppointment(COMPANY, ownerAppt);
        memberRepo.save(founder);

        company = new Company(COMPANY, "desc", founderId);
        companyRepo.save(company);

        when(tokens.isValid(anyString())).thenReturn(true);
        when(tokens.extractMemberId(FOUNDER_TOKEN)).thenReturn(founderId);
        when(tokens.extractPermissions(FOUNDER_TOKEN)).thenReturn(Set.of());
        when(tokens.extractMemberId(ADMIN_TOKEN)).thenReturn(UUID.randomUUID());
        when(tokens.extractPermissions(ADMIN_TOKEN)).thenReturn(Set.of("SYSTEM_ADMIN"));
        when(tokens.extractMemberId(OUTSIDER_TOKEN)).thenReturn(UUID.randomUUID());
        when(tokens.extractPermissions(OUTSIDER_TOKEN)).thenReturn(Set.of());
    }

    // --- 1. SuccessfulTemporarySuspension ---

    @Test
    public void GivenFounder_WhenSuspendCompany_ThenStatusBecomesSuspended() {
        service.suspendCompany(FOUNDER_TOKEN, COMPANY);

        Company saved = companyRepo.findByName(COMPANY).orElseThrow();
        assertEquals(CompanyStatus.SUSPENDED, saved.getStatus());
    }

    @Test
    public void GivenSuspendedCompany_WhenReopen_ThenStatusBecomesActive() {
        service.suspendCompany(FOUNDER_TOKEN, COMPANY);

        service.reopenCompany(FOUNDER_TOKEN, COMPANY);

        assertEquals(CompanyStatus.ACTIVE, companyRepo.findByName(COMPANY).orElseThrow().getStatus());
    }

    // --- 2. SuccessfulPermanentClosure (Founder) ---

    @Test
    public void GivenFounder_WhenPermanentClose_ThenEventsCancelledPurchasesRefundedAppointmentsKept() {
        // seed two events for this company
        Event e1 = seedEvent(UUID.randomUUID());
        Event e2 = seedEvent(UUID.randomUUID());

        // seed two completed purchases tied to those events
        purchaseRepo.save(new CompletedPurchase(UUID.randomUUID(), e1.getId(), "Concert 1",
                COMPANY, UUID.randomUUID(), "txn-1", new BigDecimal("50.00"), java.time.Instant.now()));
        purchaseRepo.save(new CompletedPurchase(UUID.randomUUID(), e2.getId(), "Concert 2",
                COMPANY, UUID.randomUUID(), "txn-2", new BigDecimal("75.00"), java.time.Instant.now()));

        when(paymentGateway.refund(anyString(), anyDouble()))
                .thenReturn(RefundResult.successful("ref-x"));

        service.permanentCloseByFounder(FOUNDER_TOKEN, COMPANY);

        assertEquals(CompanyStatus.CLOSED, companyRepo.findByName(COMPANY).orElseThrow().getStatus());
        assertEquals(EventStatus.CANCELLED, eventRepo.findById(e1.getId()).orElseThrow().getStatus());
        assertEquals(EventStatus.CANCELLED, eventRepo.findById(e2.getId()).orElseThrow().getStatus());
        verify(paymentGateway, times(2)).refund(anyString(), anyDouble());

        // Founder's appointment must be preserved per the V0 spec
        Member f = memberRepo.findById(founderId).orElseThrow();
        assertNotNull(f.getStaffAppointment(COMPANY));
    }

    // --- 3. AdminForcedClosureSuccess - all roles revoked ---

    @Test
    public void GivenAdmin_WhenPermanentClose_ThenAllAppointmentsRevoked() {
        Event e1 = seedEvent(UUID.randomUUID());

        // seed a second staff member (manager) appointed to the same company
        UUID managerId = UUID.randomUUID();
        Member manager = new Member(managerId, "managerUser", "manager@x.com", "pw");
        manager.addStaffAppointment(COMPANY, new StaffAppointment(
                COMPANY, founderId, StaffAppointment.StaffRole.MANAGER,
                Set.of(ManagerPermission.EVENT_LIFECYCLE)));
        memberRepo.save(manager);

        when(paymentGateway.refund(anyString(), anyDouble()))
                .thenReturn(RefundResult.successful("ref"));

        service.permanentCloseByAdmin(ADMIN_TOKEN, COMPANY);

        assertEquals(CompanyStatus.CLOSED, companyRepo.findByName(COMPANY).orElseThrow().getStatus());
        assertEquals(EventStatus.CANCELLED, eventRepo.findById(e1.getId()).orElseThrow().getStatus());

        // BOTH founder and manager appointments must be gone
        assertNull(memberRepo.findById(founderId).orElseThrow().getStaffAppointment(COMPANY),
                "Admin close revokes founder appointment");
        assertNull(memberRepo.findById(managerId).orElseThrow().getStaffAppointment(COMPANY),
                "Admin close revokes manager appointment");
    }

    // --- 4. PaymentServiceUnavailableFallback - PENDING_CLOSURE state ---

    @Test
    public void GivenPaymentGatewayFails_WhenPermanentClose_ThenStatusBecomesPendingClosure() {
        Event e1 = seedEvent(UUID.randomUUID());
        purchaseRepo.save(new CompletedPurchase(UUID.randomUUID(), e1.getId(), "Concert",
                COMPANY, UUID.randomUUID(), "txn-fail", new BigDecimal("99.00"), java.time.Instant.now()));

        when(paymentGateway.refund(anyString(), anyDouble()))
                .thenReturn(RefundResult.failed("gateway down"));

        service.permanentCloseByFounder(FOUNDER_TOKEN, COMPANY);

        Company c = companyRepo.findByName(COMPANY).orElseThrow();
        assertEquals(CompanyStatus.PENDING_CLOSURE, c.getStatus());
        // events still get cancelled even if refunds fail
        assertEquals(EventStatus.CANCELLED, eventRepo.findById(e1.getId()).orElseThrow().getStatus());
    }

    @Test
    public void GivenPendingClosureWithSuccessfulRetry_WhenRetry_ThenStatusBecomesClosed() {
        seedEvent(UUID.randomUUID());
        purchaseRepo.save(new CompletedPurchase(UUID.randomUUID(), UUID.randomUUID(), "Retry Concert",
                COMPANY, UUID.randomUUID(), "txn-retry", new BigDecimal("12.00"), java.time.Instant.now()));
        when(paymentGateway.refund(anyString(), anyDouble()))
                .thenReturn(RefundResult.failed("first try fails"));
        service.permanentCloseByFounder(FOUNDER_TOKEN, COMPANY);
        assertEquals(CompanyStatus.PENDING_CLOSURE,
                companyRepo.findByName(COMPANY).orElseThrow().getStatus());

        // gateway recovers
        when(paymentGateway.refund(anyString(), anyDouble()))
                .thenReturn(RefundResult.successful("ref-ok"));

        service.retryPendingRefunds(COMPANY);

        assertEquals(CompanyStatus.CLOSED,
                companyRepo.findByName(COMPANY).orElseThrow().getStatus());
        verify(paymentGateway, atLeastOnce()).refund(anyString(), anyDouble());
    }

    @Test
    public void GivenPendingClosureAfterServiceRestart_WhenRetry_ThenRehydratesFromRepoAndCloses() {
        // 1) seed an event + a completed purchase so there's data to refund
        Event e = seedEvent(UUID.randomUUID());
        purchaseRepo.save(new CompletedPurchase(UUID.randomUUID(), e.getId(), "Rehydrate Concert",
                COMPANY, UUID.randomUUID(), "txn-rehydrate", new BigDecimal("42.00"), java.time.Instant.now()));

        // 2) drive into PENDING_CLOSURE via a failing gateway
        when(paymentGateway.refund(anyString(), anyDouble()))
                .thenReturn(RefundResult.failed("down"));
        service.permanentCloseByFounder(FOUNDER_TOKEN, COMPANY);
        assertEquals(CompanyStatus.PENDING_CLOSURE,
                companyRepo.findByName(COMPANY).orElseThrow().getStatus());

        // 3) simulate a service restart: build a fresh service whose in-memory queue is empty
        CompanyLifecycleService freshService = new CompanyLifecycleService(
                companyRepo, eventRepo, memberRepo, purchaseRepo, paymentGateway, tokens);

        // 4) gateway recovers; retry must rehydrate the queue from completedPurchaseRepo
        when(paymentGateway.refund(anyString(), anyDouble()))
                .thenReturn(RefundResult.successful("ref-ok"));

        freshService.retryPendingRefunds(COMPANY);

        assertEquals(CompanyStatus.CLOSED,
                companyRepo.findByName(COMPANY).orElseThrow().getStatus());
    }

    // --- negatives ---

    @Test
    public void GivenNonFounder_WhenSuspendCompany_ThenThrowSecurityException() {
        assertThrows(SecurityException.class,
                () -> service.suspendCompany(OUTSIDER_TOKEN, COMPANY));
    }

    @Test
    public void GivenNonAdmin_WhenAdminClose_ThenThrowSecurityException() {
        // founder-token is a member but does NOT have SYSTEM_ADMIN
        assertThrows(SecurityException.class,
                () -> service.permanentCloseByAdmin(FOUNDER_TOKEN, COMPANY));
    }

    @Test
    public void GivenAlreadySuspendedCompany_WhenSuspend_ThenThrowIllegalStateException() {
        service.suspendCompany(FOUNDER_TOKEN, COMPANY);

        assertThrows(IllegalStateException.class,
                () -> service.suspendCompany(FOUNDER_TOKEN, COMPANY));
    }

    @Test
    public void GivenActiveCompany_WhenReopen_ThenThrowIllegalStateException() {
        assertThrows(IllegalStateException.class,
                () -> service.reopenCompany(FOUNDER_TOKEN, COMPANY));
    }

    @Test
    public void GivenInvalidToken_WhenSuspend_ThenThrowIllegalArgumentException() {
        when(tokens.isValid("bad")).thenReturn(false);

        assertThrows(IllegalArgumentException.class,
                () -> service.suspendCompany("bad", COMPANY));
    }

    @Test
    public void GivenUnknownCompany_WhenSuspend_ThenThrowIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> service.suspendCompany(FOUNDER_TOKEN, "Nonexistent Co"));
    }

    // --- helpers ---

    private Event seedEvent(UUID id) {
        Instant start = Instant.now().plus(30, ChronoUnit.DAYS);
        EventSchedule schedule = new EventSchedule(
                start, start.plus(2, ChronoUnit.HOURS), start.minus(1, ChronoUnit.HOURS));
        Event e = new Event(id, COMPANY, "Concert " + id, null, EventCategory.CONCERT,
                schedule, new LockTimerDuration(Duration.ofMinutes(15)));
        eventRepo.save(e);
        return e;
    }
}
