package com.ticketing.application.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ticketing.domain.member.IMemberRepository;
import com.ticketing.domain.member.Member;
import com.ticketing.domain.member.PermissionDeniedException;
import com.ticketing.domain.member.StaffAppointment;
import com.ticketing.domain.member.communication.RoleAppointmentOfferRequestedEvent;

public class RoleAppointmentOfferRequestedHandlerTest {

    private IMemberRepository memberRepository;
    private RoleAppointmentOfferRequestedHandler handler;

    @BeforeEach
    public void setUp() {
        memberRepository = mock(IMemberRepository.class);
        handler = new RoleAppointmentOfferRequestedHandler(memberRepository);
    }

    @Test
    public void GivenAuthorizedAppointer_WhenHandleOfferRequest_ThenPendingOfferCreated() {
        UUID appointerId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        String companyName = "TestCo";

        Member appointer = createOwner(appointerId, companyName);
        Member target = new Member(targetId, "target", "t@t.com", "p");

        when(memberRepository.findById(appointerId)).thenReturn(java.util.Optional.of(appointer));
        when(memberRepository.findById(targetId)).thenReturn(java.util.Optional.of(target));

        RoleAppointmentOfferRequestedEvent event = new RoleAppointmentOfferRequestedEvent(
            appointerId, targetId, companyName, StaffAppointment.StaffRole.MANAGER, Collections.emptySet()
        );

        handler.handle(event);

        assertEquals(1, target.getPendingOffers().size());
        assertEquals(companyName, target.getPendingOffers().get(0).getCompanyName());
        verify(memberRepository).save(target);
    }

    @Test
    public void GivenUnauthorizedAppointer_WhenHandleOfferRequest_ThenPermissionDeniedException() {
        UUID appointerId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        String companyName = "TestCo";

        Member appointer = new Member(appointerId, "no-perm", "n@p.com", "p");
        Member target = new Member(targetId, "target", "t@t.com", "p");

        when(memberRepository.findById(appointerId)).thenReturn(java.util.Optional.of(appointer));

        RoleAppointmentOfferRequestedEvent event = new RoleAppointmentOfferRequestedEvent(
            appointerId, targetId, companyName, StaffAppointment.StaffRole.MANAGER, Collections.emptySet()
        );

        assertThrows(PermissionDeniedException.class, () -> handler.handle(event));
        verify(memberRepository, never()).save(any());
    }

    @Test
    public void GivenTargetAlreadyOwner_WhenHandleOfferRequest_ThenIllegalArgumentException() {
        UUID appointerId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        String companyName = "TestCo";

        Member appointer = createOwner(appointerId, companyName);
        Member target = createOwner(targetId, companyName);

        when(memberRepository.findById(appointerId)).thenReturn(java.util.Optional.of(appointer));
        when(memberRepository.findById(targetId)).thenReturn(java.util.Optional.of(target));

        RoleAppointmentOfferRequestedEvent event = new RoleAppointmentOfferRequestedEvent(
            appointerId, targetId, companyName, StaffAppointment.StaffRole.OWNER, Collections.emptySet()
        );

        assertThrows(IllegalArgumentException.class, () -> handler.handle(event));
    }

    private Member createOwner(UUID id, String companyName) {
        Member m = new Member(id, "owner", "o@o.com", "p");
        m.addStaffAppointment(companyName, new StaffAppointment(companyName, UUID.randomUUID(), StaffAppointment.StaffRole.OWNER, Collections.emptySet()));
        return m;
    }
}
