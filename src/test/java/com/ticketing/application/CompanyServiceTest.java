package com.ticketing.application;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ticketing.application.auth.ISessionTokenService;
import com.ticketing.application.listener.MemberCompanyOpenedEventHandler;
import com.ticketing.domain.company.Company;
import com.ticketing.domain.company.ICompanyRepository;
import com.ticketing.domain.member.IMemberRepository;
import com.ticketing.domain.member.IRoleAppointmentOfferRepository;
import com.ticketing.domain.member.Member;
import com.ticketing.domain.member.StaffAppointment;
import com.ticketing.infrastructure.InMemoryCompanyRepository;
import com.ticketing.infrastructure.InMemoryEventPublisher;
import com.ticketing.infrastructure.InMemoryMemberRepository;

public class CompanyServiceTest {

    private ICompanyRepository companyRepository;
    private IMemberRepository memberRepository;
    private InMemoryEventPublisher eventPublisher;
    private ISessionTokenService sessionTokenServiceMock;
    private IRoleAppointmentOfferRepository offerRepository;
    private INotificationService notificationService;
    private CompanyService companyService;

    @BeforeEach
    public void setUp() {
        companyRepository = new InMemoryCompanyRepository();
        memberRepository = new InMemoryMemberRepository();
        offerRepository = mock(IRoleAppointmentOfferRepository.class);
        eventPublisher = new InMemoryEventPublisher();
        sessionTokenServiceMock = mock(ISessionTokenService.class);
        notificationService = mock(INotificationService.class);

        // Wire up the event listener as requested by Tamar
        MemberCompanyOpenedEventHandler handler = new MemberCompanyOpenedEventHandler(memberRepository);
        eventPublisher.subscribe("CompanyOpened", handler);

        companyService = new CompanyService(companyRepository, eventPublisher, sessionTokenServiceMock, memberRepository, offerRepository);
    }

    @Test
    public void GivenValidToken_WhenOpenProductionCompany_ThenReturnCompanyNameAndSaveWithRole() {
        // Setup
        String validToken = "valid-token";
        String companyName = "Company A";
        String description = "Meow";
        UUID founderId = UUID.randomUUID();
        Member founder = new Member(founderId, "founder_user", "founder@example.com", "hashed_password");
        memberRepository.save(founder);

        when(sessionTokenServiceMock.isValid(validToken)).thenReturn(true);
        when(sessionTokenServiceMock.extractMemberId(validToken)).thenReturn(founderId);

        // Test
        String result = companyService.openProductionCompany(validToken, companyName, description);

        // Verify result
        assertEquals(companyName, result);
        
        // Verify state in CompanyRepository
        Optional<Company> savedCompany = companyRepository.findByName(companyName);
        assertTrue(savedCompany.isPresent());
        assertEquals(founderId, savedCompany.get().getFounderId());

        // Verify state in MemberRepository (updated via event listener)
        Member updatedMember = memberRepository.findById(founderId).orElseThrow();
        StaffAppointment appointment = updatedMember.getStaffAppointment(companyName);
        assertNotNull(appointment, "Member should have an appointment for the new company");
        assertEquals(StaffAppointment.StaffRole.OWNER, appointment.getRole());
    }

    @Test
    public void GivenDuplicateCompanyDetails_WhenOpenProductionCompany_ThenThrowExceptionAndDoNotSave() {
        // Setup
        String validToken = "valid-token";
        String duplicateName = "Existing Company Name";
        UUID founderId = UUID.randomUUID();
        
        // Pre-create a company with the same name
        companyRepository.save(new Company(duplicateName, "Existing", UUID.randomUUID()));

        when(sessionTokenServiceMock.isValid(validToken)).thenReturn(true);
        when(sessionTokenServiceMock.extractMemberId(validToken)).thenReturn(founderId);

        // Test & Verify
        assertThrows(
            IllegalArgumentException.class,
            () -> companyService.openProductionCompany(validToken, duplicateName, "Description")
        );

        // Verify that no new company was saved (count should still be 1)
        assertEquals(1, companyRepository.getAll().size());
    }

    @Test
    public void GivenInvalidCompanyDetails_WhenOpenProductionCompany_ThenThrowExceptionAndDoNotSave() {
        // Setup
        String validToken = "valid-token";
        String invalidName = ""; 
        UUID founderId = UUID.randomUUID();

        when(sessionTokenServiceMock.isValid(validToken)).thenReturn(true);
        when(sessionTokenServiceMock.extractMemberId(validToken)).thenReturn(founderId);

        // Test & Verify
        assertThrows(
            IllegalArgumentException.class, 
            () -> companyService.openProductionCompany(validToken, invalidName, "Description")
        );

        assertTrue(companyRepository.getAll().isEmpty());
    }

    @Test
    public void GivenGuestUser_WhenOpenProductionCompany_ThenThrowExceptionAndDoNotSave() {
        // Setup
        String guestToken = "guest-token";
        String companyName = "New Company";

        when(sessionTokenServiceMock.isValid(guestToken)).thenReturn(true);
        when(sessionTokenServiceMock.extractMemberId(guestToken)).thenReturn(null);

        // Test & Verify
        assertThrows(
            IllegalArgumentException.class,
            () -> companyService.openProductionCompany(guestToken, companyName, "Description")
        );

        assertTrue(companyRepository.getAll().isEmpty());
    }

    @Test
    public void GivenInvalidToken_WhenOpenProductionCompany_ThenThrowIllegalArgumentException() {
        // Setup
        String invalidToken = "expired-token";
        when(sessionTokenServiceMock.isValid(invalidToken)).thenReturn(false);

        // Test & Verify
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> companyService.openProductionCompany(invalidToken, "Test Co", "Desc")
        );

        assertEquals("Invalid or expired authentication token", exception.getMessage());
        assertTrue(companyRepository.getAll().isEmpty());
    }

    @Test
    public void GivenNullToken_WhenOpenProductionCompany_ThenThrowIllegalArgumentException() {
        // Test & Verify
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> companyService.openProductionCompany(null, "Test", "Desc")
        );

        assertEquals("Authentication token is required", exception.getMessage());
        assertTrue(companyRepository.getAll().isEmpty());
    }
}