package com.ticketing.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.UUID;
import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ticketing.application.auth.ISessionTokenService;
import com.ticketing.domain.company.Company;
import com.ticketing.domain.company.CompanyStatus;
import com.ticketing.domain.company.ICompanyRepository;
import com.ticketing.domain.member.IMemberRepository;
import com.ticketing.domain.member.Member;
import com.ticketing.domain.member.StaffAppointment;
import com.ticketing.infrastructure.InMemoryCompanyRepository;
import com.ticketing.infrastructure.TestEventPublisher;
import com.ticketing.infrastructure.InMemoryMemberRepository;

import com.ticketing.application.initialization.InitializationService;
import com.ticketing.application.INotificationService;
import com.ticketing.domain.event.IEventRepository;
import com.ticketing.domain.order.ICompletedPurchaseRepository;
import com.ticketing.domain.gateway.IPaymentGateway;
import com.ticketing.infrastructure.InMemoryEventRepository;
import com.ticketing.infrastructure.InMemoryCompletedPurchaseRepository;
import com.ticketing.infrastructure.gateway.StubPaymentGateway;

public class CompanyServiceCloseTest {

    private ICompanyRepository companyRepository;
    private IMemberRepository memberRepository;
    private TestEventPublisher eventPublisher;
    private ISessionTokenService sessionTokenServiceMock;
    private INotificationService notificationServiceMock;
    private CompanyService companyService;

    @BeforeEach
    public void setUp() {
        companyRepository = new InMemoryCompanyRepository();
        memberRepository = new InMemoryMemberRepository();
        IEventRepository eventRepository = new InMemoryEventRepository();
        ICompletedPurchaseRepository purchaseRepository = new InMemoryCompletedPurchaseRepository();
        IPaymentGateway paymentGateway = new StubPaymentGateway();
        eventPublisher = new TestEventPublisher();
        sessionTokenServiceMock = mock(ISessionTokenService.class);
        notificationServiceMock = mock(INotificationService.class);

        InitializationService initService = new InitializationService(
            companyRepository, memberRepository, eventRepository, 
            purchaseRepository, paymentGateway, eventPublisher, 
            sessionTokenServiceMock, notificationServiceMock
        );
        companyService = initService.initialize();
    }

    @Test
    public void testTemporaryClosureSuccess() {
        UUID ownerId = UUID.randomUUID();
        String token = "owner-token";
        String companyName = "TempCloseCorp";

        // Setup company and owner
        Company company = new Company(companyName, "Desc", ownerId);
        companyRepository.save(company);
        
        Member owner = new Member(ownerId, "owner", "o@test.com", "pass");
        owner.addStaffAppointment(companyName, new StaffAppointment(companyName, ownerId, StaffAppointment.StaffRole.OWNER, Collections.emptySet()));
        memberRepository.save(owner);
        
        when(sessionTokenServiceMock.isValid(token)).thenReturn(true);
        when(sessionTokenServiceMock.extractMemberId(token)).thenReturn(ownerId);

        companyService.closeProductionCompany(token, companyName, false);

        Company closedCompany = companyRepository.findByName(companyName).orElseThrow();
        assertEquals(CompanyStatus.SUSPENDED, closedCompany.getStatus());
        assertTrue(eventPublisher.getPublishedEvents().stream()
            .anyMatch(e -> e.getEventType().equals("CompanyClosed")));
    }

    @Test
    public void testPermanentClosureSuccess() {
        UUID ownerId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        String token = "owner-token";
        String companyName = "PermCloseCorp";

        // Setup company
        Company company = new Company(companyName, "Desc", ownerId);
        companyRepository.save(company);
        
        // Setup owner and staff members
        Member owner = new Member(ownerId, "owner", "o@test.com", "pass");
        owner.addStaffAppointment(companyName, new StaffAppointment(companyName, ownerId, StaffAppointment.StaffRole.OWNER, Collections.emptySet()));
        memberRepository.save(owner);

        Member staff = new Member(staffId, "staff", "s@test.com", "pass");
        staff.addStaffAppointment(companyName, new StaffAppointment(companyName, staffId, StaffAppointment.StaffRole.MANAGER, Collections.emptySet()));
        memberRepository.save(staff);

        when(sessionTokenServiceMock.isValid(token)).thenReturn(true);
        when(sessionTokenServiceMock.extractMemberId(token)).thenReturn(ownerId);

        companyService.closeProductionCompany(token, companyName, true);

        // Verify company status
        Company closedCompany = companyRepository.findByName(companyName).orElseThrow();
        assertEquals(CompanyStatus.CLOSED, closedCompany.getStatus());

        // Verify staff revocation
        Member staffAfter = memberRepository.findById(staffId).orElseThrow();
        assertNull(staffAfter.getStaffAppointment(companyName));
        
        Member ownerAfter = memberRepository.findById(ownerId).orElseThrow();
        assertNull(ownerAfter.getStaffAppointment(companyName));

        assertTrue(eventPublisher.getPublishedEvents().stream()
            .anyMatch(e -> e.getEventType().equals("CompanyClosed")));
    }

    @Test
    public void testClosureUnauthorized() {
        UUID ownerId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        String token = "other-token";
        String companyName = "UnauthorizedCorp";

        Company company = new Company(companyName, "Desc", ownerId);
        companyRepository.save(company);
        
        Member owner = new Member(ownerId, "owner", "o@test.com", "pass");
        owner.addStaffAppointment(companyName, new StaffAppointment(companyName, ownerId, StaffAppointment.StaffRole.OWNER, Collections.emptySet()));
        memberRepository.save(owner);
        
        Member other = new Member(otherId, "other", "other@test.com", "pass");
        memberRepository.save(other);

        when(sessionTokenServiceMock.isValid(token)).thenReturn(true);
        when(sessionTokenServiceMock.extractMemberId(token)).thenReturn(otherId);

        assertThrows(SecurityException.class, () -> {
            companyService.closeProductionCompany(token, companyName, false);
        });
    }
}
