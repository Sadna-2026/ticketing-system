package com.ticketing.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ticketing.application.auth.ISessionTokenService;
import com.ticketing.domain.company.ICompanyRepository;
import com.ticketing.domain.member.IMemberRepository;
import com.ticketing.infrastructure.InMemoryCompanyRepository;
import com.ticketing.infrastructure.InMemoryEventPublisher;
import com.ticketing.infrastructure.InMemoryMemberRepository;

public class CompanyServiceTest {

    private ICompanyRepository companyRepository;
    private IMemberRepository memberRepository;
    private InMemoryEventPublisher eventPublisher;
    private ISessionTokenService sessionTokenServiceMock;
    private CompanyService companyService;

    @BeforeEach
    public void setUp() {
        companyRepository = new InMemoryCompanyRepository();
        memberRepository = new InMemoryMemberRepository();
        eventPublisher = new InMemoryEventPublisher();
        sessionTokenServiceMock = mock(ISessionTokenService.class);

        companyService = new CompanyService(companyRepository, eventPublisher, sessionTokenServiceMock);
    }

    @Test
    public void testOpenProductionCompanySuccess() {
        UUID memberId = UUID.randomUUID();
        String token = "valid-token";
        String companyName = "NewCompany";

        when(sessionTokenServiceMock.isValid(token)).thenReturn(true);
        when(sessionTokenServiceMock.extractMemberId(token)).thenReturn(memberId);

        String result = companyService.openProductionCompany(token, companyName, "Description");

        assertEquals(companyName, result);
        assertTrue(companyRepository.existsByName(companyName));
    }

    @Test
    public void testOpenProductionCompanyDuplicateName() {
        UUID memberId = UUID.randomUUID();
        String token = "valid-token";
        String companyName = "ExistingCompany";

        when(sessionTokenServiceMock.isValid(token)).thenReturn(true);
        when(sessionTokenServiceMock.extractMemberId(token)).thenReturn(memberId);

        companyService.openProductionCompany(token, companyName, "First");

        assertThrows(IllegalArgumentException.class, () -> {
            companyService.openProductionCompany(token, companyName, "Second");
        });
    }
}