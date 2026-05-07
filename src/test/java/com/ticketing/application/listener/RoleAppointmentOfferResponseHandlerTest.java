package com.ticketing.application.listener;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ticketing.application.INotificationService;
import com.ticketing.domain.company.Company;
import com.ticketing.domain.event.IEvent;
import com.ticketing.domain.member.IMemberRepository;
import com.ticketing.domain.member.Member;
import com.ticketing.domain.member.PendingRoleOffer;
import com.ticketing.domain.member.StaffAppointment;
import com.ticketing.domain.member.communication.RoleAppointmentOfferResponseEvent;
import com.ticketing.domain.company.ICompanyRepository;

public class RoleAppointmentOfferResponseHandlerTest {

    private IMemberRepository memberRepository;
    private ICompanyRepository companyRepository;
    private INotificationService notificationService;
    private RoleAppointmentOfferResponseHandler handler;

    @BeforeEach
    public void setUp() {
        memberRepository = mock(IMemberRepository.class);
        companyRepository = mock(ICompanyRepository.class);
        notificationService = mock(INotificationService.class);
        handler = new RoleAppointmentOfferResponseHandler(memberRepository, companyRepository, notificationService);
    }

    @Test
    public void GivenAcceptedResponse_WhenHandle_ThenNotifiesAppointee() {
        UUID targetId = UUID.randomUUID();
        String companyName = "TestCo";
        PendingRoleOffer offer = new PendingRoleOffer(UUID.randomUUID(), companyName, StaffAppointment.StaffRole.MANAGER, java.util.Collections.emptySet(), java.time.LocalDateTime.now().plusDays(1));
        Member target = new Member(targetId, "target", "target@test.com", "password");
        target.addPendingOffer(offer);

        Member appointer = new Member(offer.getOfferedByMemberId(), "appointer", "appointer@test.com", "password");
        appointer.addStaffAppointment(companyName, new StaffAppointment(companyName, appointer.getId(), StaffAppointment.StaffRole.OWNER, java.util.Collections.emptySet()));
        when(memberRepository.findById(appointer.getId())).thenReturn(Optional.of(appointer));
        when(memberRepository.findById(targetId)).thenReturn(Optional.of(target));
        when(companyRepository.existsByName(companyName)).thenReturn(true);
        when(companyRepository.findByName(companyName)).thenReturn(Optional.of(new Company(companyName, "desc", UUID.randomUUID())));

        RoleAppointmentOfferResponseEvent event = new RoleAppointmentOfferResponseEvent(offer.getOfferId(), targetId, true);
        handler.handle(event);

        assertTrue(target.getPendingOffers().isEmpty());
        assertNotNull(target.getStaffAppointment(companyName));
        verify(memberRepository).save(target);
        verify(notificationService).notify(eq(offer.getOfferedByMemberId().toString()), contains("accepted the manager role offer"));
    }

    @Test
    public void GivenAcceptedResponse_WhenHandle_ThenAppointerAppointmentTracksTargetMember() {
        UUID targetId = UUID.randomUUID();
        UUID appointerId = UUID.randomUUID();
        String companyName = "TestCo";
        PendingRoleOffer offer = new PendingRoleOffer(appointerId, companyName, StaffAppointment.StaffRole.MANAGER, java.util.Collections.emptySet(), java.time.LocalDateTime.now().plusDays(1));
        Member target = new Member(targetId, "target", "target@test.com", "password");
        target.addPendingOffer(offer);

        Member appointer = new Member(appointerId, "appointer", "appointer@test.com", "password");
        appointer.addStaffAppointment(companyName, new StaffAppointment(companyName, appointerId, StaffAppointment.StaffRole.OWNER, java.util.Collections.emptySet()));

        when(memberRepository.findById(targetId)).thenReturn(Optional.of(target));
        when(memberRepository.findById(appointerId)).thenReturn(Optional.of(appointer));
        when(companyRepository.existsByName(companyName)).thenReturn(true);
        when(companyRepository.findByName(companyName)).thenReturn(Optional.of(new Company(companyName, "desc", UUID.randomUUID())));

        RoleAppointmentOfferResponseEvent event = new RoleAppointmentOfferResponseEvent(offer.getOfferId(), targetId, true);
        handler.handle(event);

        assertTrue(target.getPendingOffers().isEmpty());
        assertNotNull(target.getStaffAppointment(companyName));
        assertTrue(appointer.getStaffAppointment(companyName).getAppointedStaffMemberIds().contains(targetId));
        verify(memberRepository).save(target);
        verify(memberRepository).save(appointer);
        verify(notificationService).notify(eq(appointerId.toString()), contains("accepted the manager role offer"));
    }

    @Test
    public void GivenRejectedResponse_WhenHandle_ThenNotifiesAppointeeWithoutAppointment() {
        UUID targetId = UUID.randomUUID();
        String companyName = "TestCo";
        PendingRoleOffer offer = new PendingRoleOffer(UUID.randomUUID(), companyName, StaffAppointment.StaffRole.MANAGER, java.util.Collections.emptySet(), java.time.LocalDateTime.now().plusDays(1));
        Member target = new Member(targetId, "target", "target@test.com", "password");
        target.addPendingOffer(offer);

        Member appointer = new Member(offer.getOfferedByMemberId(), "appointer", "appointer@test.com", "password");
        appointer.addStaffAppointment(companyName, new StaffAppointment(companyName, appointer.getId(), StaffAppointment.StaffRole.OWNER, java.util.Collections.emptySet()));
        when(memberRepository.findById(appointer.getId())).thenReturn(Optional.of(appointer));
        when(memberRepository.findById(targetId)).thenReturn(Optional.of(target));
        when(companyRepository.existsByName(companyName)).thenReturn(true);
        when(companyRepository.findByName(companyName)).thenReturn(Optional.of(new Company(companyName, "desc", UUID.randomUUID())));

        RoleAppointmentOfferResponseEvent event = new RoleAppointmentOfferResponseEvent(offer.getOfferId(), targetId, false);
        handler.handle(event);

        assertTrue(target.getPendingOffers().isEmpty());
        assertNull(target.getStaffAppointment(companyName));
        verify(memberRepository).save(target);
        verify(notificationService).notify(eq(offer.getOfferedByMemberId().toString()), contains("declined the manager role offer"));
    }

    @Test
    public void GivenManagerPromotionAccepted_WhenHandle_ThenNotifiesAppointee() {
        UUID targetId = UUID.randomUUID();
        String companyName = "TestCo";
        UUID appointerId = UUID.randomUUID();
        PendingRoleOffer offer = new PendingRoleOffer(appointerId, companyName, StaffAppointment.StaffRole.OWNER, java.util.Collections.emptySet(), java.time.LocalDateTime.now().plusDays(1));
        Member target = new Member(targetId, "target", "target@test.com", "password");
        
        // Target is already a manager
        StaffAppointment existingAppointment = new StaffAppointment(companyName, appointerId, StaffAppointment.StaffRole.MANAGER, java.util.Collections.emptySet());
        target.addStaffAppointment(companyName, existingAppointment);
        target.addPendingOffer(offer);

        Member appointer = new Member(appointerId, "appointer", "appointer@test.com", "password");
        appointer.addStaffAppointment(companyName, new StaffAppointment(companyName, appointerId, StaffAppointment.StaffRole.OWNER, java.util.Collections.emptySet()));
        when(memberRepository.findById(appointerId)).thenReturn(Optional.of(appointer));
        // Mock the repositories to return the target member and company
        when(memberRepository.findById(targetId)).thenReturn(Optional.of(target));
        when(companyRepository.existsByName(companyName)).thenReturn(true);
        when(companyRepository.findByName(companyName)).thenReturn(Optional.of(new Company(companyName, "desc", UUID.randomUUID())));

        RoleAppointmentOfferResponseEvent event = new RoleAppointmentOfferResponseEvent(offer.getOfferId(), targetId, true);
        handler.handle(event);

        assertTrue(target.getPendingOffers().isEmpty());
        assertNotNull(target.getStaffAppointment(companyName));
        assertEquals(StaffAppointment.StaffRole.OWNER, target.getStaffAppointment(companyName).getRole());
        verify(memberRepository).save(target);
        verify(notificationService).notify(eq(appointerId.toString()), contains("promoted to owner"));
    }

    @Test
    public void GivenManagerPromotionRejected_WhenHandle_ThenNotifiesAppointee() {
        UUID targetId = UUID.randomUUID();
        String companyName = "TestCo";
        UUID appointerId = UUID.randomUUID();
        PendingRoleOffer offer = new PendingRoleOffer(appointerId, companyName, StaffAppointment.StaffRole.OWNER, java.util.Collections.emptySet(), java.time.LocalDateTime.now().plusDays(1));
        Member target = new Member(targetId, "target", "target@test.com", "password");
        // Target is already a manager
        StaffAppointment existingAppointment = new StaffAppointment(companyName, appointerId, StaffAppointment.StaffRole.MANAGER, java.util.Collections.emptySet());
        target.addStaffAppointment(companyName, existingAppointment);
        target.addPendingOffer(offer);

        Member appointer = new Member(appointerId, "appointer", "appointer@test.com", "password");
        appointer.addStaffAppointment(companyName, new StaffAppointment(companyName, appointerId, StaffAppointment.StaffRole.OWNER, java.util.Collections.emptySet()));
        when(memberRepository.findById(appointerId)).thenReturn(Optional.of(appointer));
        when(memberRepository.findById(targetId)).thenReturn(Optional.of(target));
        when(companyRepository.existsByName(companyName)).thenReturn(true);
        when(companyRepository.findByName(companyName)).thenReturn(Optional.of(new Company(companyName, "desc", UUID.randomUUID())));

        RoleAppointmentOfferResponseEvent event = new RoleAppointmentOfferResponseEvent(offer.getOfferId(), targetId, false);
        handler.handle(event);

        assertTrue(target.getPendingOffers().isEmpty());
        assertNotNull(target.getStaffAppointment(companyName));
        assertEquals(StaffAppointment.StaffRole.MANAGER, target.getStaffAppointment(companyName).getRole()); // Still manager
        verify(memberRepository).save(target);
        verify(notificationService).notify(eq(appointerId.toString()), contains("rejected the promotion to owner"));
    }

    @Test
    public void GivenExpiredOffer_WhenHandle_ThenThrowsIllegalArgumentException() {
        UUID targetId = UUID.randomUUID();
        String companyName = "TestCo";
        
        // Create the offer with a valid FUTURE date so the constructor doesn't complain
        java.time.LocalDateTime validDueDate = java.time.LocalDateTime.now().plusDays(1);
        PendingRoleOffer offer = new PendingRoleOffer(
            UUID.randomUUID(), 
            companyName, 
            StaffAppointment.StaffRole.MANAGER, 
            java.util.Collections.emptySet(), 
            validDueDate
        );
        
        Member target = new Member(targetId, "target", "target@test.com", "password");
        target.addPendingOffer(offer);

        when(memberRepository.findById(targetId)).thenReturn(Optional.of(target));

        RoleAppointmentOfferResponseEvent event = new RoleAppointmentOfferResponseEvent(offer.getOfferId(), targetId, true);

        // Fast-forward time to simulate the user waiting too long to respond
        try (org.mockito.MockedStatic<java.time.LocalDateTime> mockedTime = mockStatic(java.time.LocalDateTime.class)) {
            
            // Fast-forward to 1 day AFTER the due date
            mockedTime.when(java.time.LocalDateTime::now).thenReturn(validDueDate.plusDays(1));

            // Assert the handler catches the expired offer and throws the exception
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
                handler.handle(event);
            });
            
            // Optional: Verify the exact message if you want to be thorough!
            assertEquals("Offer has expired", exception.getMessage());
        }
    }


    @Test
    public void GivenInactiveCompany_WhenHandle_ThenThrowsIllegalArgumentException() {
        UUID targetId = UUID.randomUUID();
        UUID appointerId = UUID.randomUUID();
        String companyName = "TestCo";

        // Create a valid offer
        java.time.LocalDateTime validDueDate = java.time.LocalDateTime.now().plusDays(1);
        PendingRoleOffer offer = new PendingRoleOffer(
            appointerId, 
            companyName, 
            StaffAppointment.StaffRole.MANAGER, 
            java.util.Collections.emptySet(), 
            validDueDate
        );

        Member target = new Member(targetId, "target", "target@test.com", "password");
        target.addPendingOffer(offer);

        // Create the Appointer
        Member appointer = new Member(appointerId, "appointer", "appointer@test.com", "password");

        when(memberRepository.findById(targetId)).thenReturn(Optional.of(target));
        when(memberRepository.findById(appointerId)).thenReturn(Optional.of(appointer)); // Mock the appointer lookup!
        when(companyRepository.existsByName(companyName)).thenReturn(true);

        // Create an INACTIVE company
        Company inactiveCompany = new Company(companyName, "desc", UUID.randomUUID());
        inactiveCompany.suspend(); 
        
        when(companyRepository.findByName(companyName)).thenReturn(Optional.of(inactiveCompany));

        RoleAppointmentOfferResponseEvent event = new RoleAppointmentOfferResponseEvent(offer.getOfferId(), targetId, true);

        // Assert the handler catches the inactive company 
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            handler.handle(event);
        });

        // Check the exception message to ensure it's the expected reason for failure
        assertEquals("Cannot accept role appointment for inactive company", exception.getMessage());
    }
}
