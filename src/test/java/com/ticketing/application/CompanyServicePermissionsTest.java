package com.ticketing.application;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ticketing.application.auth.ISessionTokenService;
import com.ticketing.domain.company.ICompanyRepository;
import com.ticketing.domain.event.IEventPublisher;
import com.ticketing.domain.member.ManagerPermission;
import com.ticketing.domain.member.communication.ManagerPermissionsChangedEvent;

public class CompanyServicePermissionsTest {

    private ICompanyRepository companyRepository;
    private IEventPublisher eventPublisher;
    private ISessionTokenService sessionTokenService;
    private CompanyService companyService;

    private final String COMPANY_NAME = "TestCompany";
    private final String VALID_TOKEN = "valid-token";
    private final UUID CALLER_ID = UUID.randomUUID();
    private final UUID TARGET_ID = UUID.randomUUID();

    @BeforeEach
    public void setUp() {
        companyRepository = mock(ICompanyRepository.class);
        eventPublisher = mock(IEventPublisher.class);
        sessionTokenService = mock(ISessionTokenService.class);
        companyService = new CompanyService(companyRepository, eventPublisher, sessionTokenService);
    }

    @Test
    public void testChangeManagerPermissions_Success() {
        when(sessionTokenService.isValid(VALID_TOKEN)).thenReturn(true);
        when(sessionTokenService.extractMemberId(VALID_TOKEN)).thenReturn(CALLER_ID);
        when(companyRepository.existsByName(COMPANY_NAME)).thenReturn(true);

        Set<ManagerPermission> newPerms = Set.of(ManagerPermission.VIEW_REPORTS);
        
        companyService.changeManagerPermissions(VALID_TOKEN, COMPANY_NAME, TARGET_ID, newPerms);

        verify(eventPublisher).publish(any(ManagerPermissionsChangedEvent.class));
    }

    @Test
    public void testChangeManagerPermissions_InvalidToken_ThrowsIllegalArgumentException() {
        when(sessionTokenService.isValid("invalid")).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> 
            companyService.changeManagerPermissions("invalid", COMPANY_NAME, TARGET_ID, Collections.emptySet())
        );
        
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    public void testChangeManagerPermissions_CompanyNotFound_ThrowsIllegalArgumentException() {
        when(sessionTokenService.isValid(VALID_TOKEN)).thenReturn(true);
        when(sessionTokenService.extractMemberId(VALID_TOKEN)).thenReturn(CALLER_ID);
        when(companyRepository.existsByName(COMPANY_NAME)).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> 
            companyService.changeManagerPermissions(VALID_TOKEN, COMPANY_NAME, TARGET_ID, Collections.emptySet())
        );
        
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    public void testChangeManagerPermissions_GuestCaller_ThrowsIllegalArgumentException() {
        when(sessionTokenService.isValid(VALID_TOKEN)).thenReturn(true);
        when(sessionTokenService.extractMemberId(VALID_TOKEN)).thenReturn(null); // Guest

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> 
            companyService.changeManagerPermissions(VALID_TOKEN, COMPANY_NAME, TARGET_ID, Collections.emptySet())
        );
        assertTrue(ex.getMessage().contains("guest"));
        
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    public void testChangeManagerPermissions_NullTarget_ThrowsIllegalArgumentException() {
        when(sessionTokenService.isValid(VALID_TOKEN)).thenReturn(true);
        when(sessionTokenService.extractMemberId(VALID_TOKEN)).thenReturn(CALLER_ID);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> 
            companyService.changeManagerPermissions(VALID_TOKEN, COMPANY_NAME, null, Collections.emptySet())
        );
        assertTrue(ex.getMessage().contains("Target member ID is required"));
        
        verify(eventPublisher, never()).publish(any());
    }
}
