package com.ticketing.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.ticketing.application.auth.ISessionTokenService;
import com.ticketing.domain.company.Company;
import com.ticketing.domain.company.ICompanyRepository;
import com.ticketing.domain.event.IEventPublisher;
import com.ticketing.domain.member.IMemberRepository;
import com.ticketing.domain.member.IRoleAppointmentOfferRepository;
import com.ticketing.domain.member.Member;
import com.ticketing.domain.member.RoleAppointmentOffer;
import com.ticketing.domain.member.StaffAppointment;
import com.ticketing.domain.member.communication.RoleAppointmentOfferedEvent;
import com.ticketing.domain.member.StaffAppointment.StaffRole;
import com.ticketing.domain.member.ManagerPermission;

public class CompanyServiceOfferTest {

    private ICompanyRepository companyRepository;
    private IMemberRepository memberRepository;
    private IRoleAppointmentOfferRepository offerRepository;
    private IEventPublisher eventPublisher;
    private ISessionTokenService sessionTokenService;
    private CompanyService companyService;

    private String token = "valid-token";
    private UUID appointerId = UUID.randomUUID();
    private String companyName = "TestCompany";
    private UUID targetId = UUID.randomUUID();

    @BeforeEach
    public void setUp() {
        companyRepository = mock(ICompanyRepository.class);
        memberRepository = mock(IMemberRepository.class);
        offerRepository = mock(IRoleAppointmentOfferRepository.class);
        eventPublisher = mock(IEventPublisher.class);
        sessionTokenService = mock(ISessionTokenService.class);

        companyService = new CompanyService(companyRepository, eventPublisher, sessionTokenService, memberRepository, offerRepository);

        when(sessionTokenService.isValid(token)).thenReturn(true);
        when(sessionTokenService.extractMemberId(token)).thenReturn(appointerId);
    }

    @Test
    public void testOfferRoleAppointmentSuccess() {
        Member appointer = createOwner(companyName, appointerId);
        Member target = new Member(targetId, "target", "target@test.com", "pass");
        Company company = new Company(companyName, "desc", appointerId);

        when(memberRepository.findById(appointerId)).thenReturn(Optional.of(appointer));
        when(memberRepository.findById(targetId)).thenReturn(Optional.of(target));
        when(companyRepository.findByName(companyName)).thenReturn(Optional.of(company));

        companyService.offerRoleAppointment(token, companyName, targetId, StaffAppointment.StaffRole.MANAGER, Collections.emptySet());

        // Verify save
        verify(offerRepository).save(any(RoleAppointmentOffer.class));

        // Verify event published
        ArgumentCaptor<RoleAppointmentOfferedEvent> eventCaptor = ArgumentCaptor.forClass(RoleAppointmentOfferedEvent.class);
        verify(eventPublisher).publish(eventCaptor.capture());
        
        RoleAppointmentOfferedEvent event = eventCaptor.getValue();
        assertEquals(targetId, event.getTargetMemberId());
        assertEquals(companyName, event.getCompanyName());
    }

    private Member createOwner(String companyId, UUID memberId) {
        Member member = new Member(memberId, "owner", "owner@test.com", "pass");
        member.addStaffAppointment(companyId, new StaffAppointment(companyId, memberId, StaffAppointment.StaffRole.OWNER, Collections.emptySet()));
        return member;
    }
}
