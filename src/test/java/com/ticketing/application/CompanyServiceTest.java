package com.ticketing.application;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ticketing.application.auth.ISessionTokenService;
import com.ticketing.domain.company.Company;
import com.ticketing.domain.company.ICompanyRepository;
import com.ticketing.domain.member.IMemberRepository;
import com.ticketing.domain.member.Member;

public class CompanyServiceTest {

    // Mocking the interfaces
    ICompanyRepository companyRepositoryMock = mock(ICompanyRepository.class);
    IMemberRepository memberRepositoryMock = mock(IMemberRepository.class);
    ISessionTokenService sessionTokenServiceMock = mock(ISessionTokenService.class);

    // Dependency Injection
    CompanyService companyService = new CompanyService(companyRepositoryMock, memberRepositoryMock, sessionTokenServiceMock);

    // SuccessfulCompanyOpening test case
    @Test
    public void GivenValidToken_WhenOpenProductionCompany_ThenReturnCompanyNameAndSave() {
        // Setup mock behavior
        String validToken = "valid-token";
        String companyName = "Company A";
        String description = "Meow";
        UUID founderId = UUID.randomUUID();
        Member mockFounder = new Member(founderId, "founder_user", "founder@example.com", "hashed_password");

        when(sessionTokenServiceMock.isValid(validToken)).thenReturn(true);
        when(sessionTokenServiceMock.extractMemberId(validToken)).thenReturn(founderId);
        when(memberRepositoryMock.findById(founderId)).thenReturn(Optional.of(mockFounder));

        // Test company creation processing
        String result = companyService.openProductionCompany(validToken, companyName, description);

        // Verify the result
        assertEquals(companyName, result);
        
        // Verify the black-box side effects (entities were saved)
        verify(companyRepositoryMock, times(1)).save(any(Company.class));
        verify(memberRepositoryMock, times(1)).save(mockFounder);
    }

    // DuplicateCompanyOpening denied
    @Test
    public void GivenDuplicateCompanyDetails_WhenOpenProductionCompany_ThenThrowExceptionAndDoNotSave() {
        // Setup mock behavior
        String validToken = "valid-token";
        String duplicateName = "Existing Company Name";
        UUID founderId = UUID.randomUUID();

        when(sessionTokenServiceMock.isValid(validToken)).thenReturn(true);
        when(sessionTokenServiceMock.extractMemberId(validToken)).thenReturn(founderId);
        
        // Assuming your repository has a way to check for existing companies
        when(companyRepositoryMock.existsByName(duplicateName)).thenReturn(true);

        // Test company creation processing
        Exception exception = assertThrows(
            IllegalArgumentException.class,
            () -> companyService.openProductionCompany(validToken, duplicateName, "Description")
        );

        // Verify the side effects (ensure system denied and didn't save)
        verify(companyRepositoryMock, never()).save(any(Company.class));
    }

    // InvalidCompanyDetails denied
    @Test
    public void GivenInvalidCompanyDetails_WhenOpenProductionCompany_ThenThrowExceptionAndDoNotSave() {
        // Setup mock behavior
        String validToken = "valid-token";
        String invalidName = ""; // An empty string represents invalid details
        UUID founderId = UUID.randomUUID();

        when(sessionTokenServiceMock.isValid(validToken)).thenReturn(true);
        when(sessionTokenServiceMock.extractMemberId(validToken)).thenReturn(founderId);

        // Test company creation processing
        Exception exception = assertThrows(
            IllegalArgumentException.class, 
            () -> companyService.openProductionCompany(validToken, invalidName, "Description")
        );

        // Verify the side effects (ensure system denied and didn't save)
        verify(companyRepositoryMock, never()).save(any(Company.class));
    }

    // GuestAttemptsCompanyOpening denied (auth check)
    @Test
    public void GivenGuestUser_WhenOpenProductionCompany_ThenThrowExceptionAndDoNotSave() {
        // Setup mock behavior for a guest (using a null token to represent no logged-in member)
        String guestToken = "guest-token";
        String companyName = "New Company";

        when(sessionTokenServiceMock.isValid(guestToken)).thenReturn(true);
        when(sessionTokenServiceMock.extractMemberId(guestToken)).thenReturn(null); // No member ID for guest

        // Test company creation processing
        Exception exception = assertThrows(
            IllegalArgumentException.class,
            () -> companyService.openProductionCompany(guestToken, companyName, "Description")
        );

        // Verify the side effects (ensure system denied and completely blocked the request)
        verifyNoInteractions(companyRepositoryMock);
        verifyNoInteractions(memberRepositoryMock);
    }

    @Test
    public void GivenInvalidToken_WhenOpenProductionCompany_ThenThrowIllegalArgumentException() {
        // Setup mock behavior
        String invalidToken = "expired-token";
        when(sessionTokenServiceMock.isValid(invalidToken)).thenReturn(false);

        // Test processing and verify exception
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> companyService.openProductionCompany(invalidToken, "Test Co", "Desc")
        );

        // Verify the result
        assertEquals("Invalid or expired authentication token", exception.getMessage());
        
        // Verify black-box behavior (repo wasn't touched)
        verify(companyRepositoryMock, never()).save(any());
        verify(memberRepositoryMock, never()).findById(any());
    }

    @Test
    public void GivenNullToken_WhenOpenProductionCompany_ThenThrowIllegalArgumentException() {
        // Test processing and verify exception
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> companyService.openProductionCompany(null, "Test", "Desc")
        );

        // Verify the result
        assertEquals("Authentication token is required", exception.getMessage());
        
        // Verify that no interactions with dependencies occurred
        verifyNoInteractions(sessionTokenServiceMock, companyRepositoryMock, memberRepositoryMock);
    }
}