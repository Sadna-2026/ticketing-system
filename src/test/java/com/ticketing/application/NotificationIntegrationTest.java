package com.ticketing.application;

import java.time.LocalDate;
import java.util.Collections;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ticketing.application.listener.RevokePersonnelHandler;
import com.ticketing.application.services.INotificationService;
import com.ticketing.domain.company.Company;
import com.ticketing.domain.member.Member;
import com.ticketing.domain.member.StaffAppointment;
import com.ticketing.domain.member.communication.RevokePersonnelEvent;
import com.ticketing.infrastructure.InMemoryMemberRepository;

/**
 * Application-level test verifying that handlers trigger notifications
 * via INotificationService with the correct memberId and message.
 */
class NotificationIntegrationTest {

    private INotificationService notificationService;
    private InMemoryMemberRepository memberRepo;
    private RevokePersonnelHandler revokeHandler;

    @BeforeEach
    void setUp() {
        notificationService = mock(INotificationService.class);
        memberRepo = new InMemoryMemberRepository();
        revokeHandler = new RevokePersonnelHandler(memberRepo, notificationService);
    }

    @Test
    void GivenRevokeEvent_WhenHandled_ThenTargetMemberNotified() {
        UUID founderId = UUID.randomUUID();
        UUID revokerId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        String companyName = "TestCo";

        Company company = new Company(companyName, "desc", founderId);

        // Revoker is a manager who appointed the target
        Member revoker = new Member(revokerId, "revoker", "revoker@test.com", "pw",
                "050-1111111", LocalDate.of(1990, 1, 1));
        StaffAppointment revokerAppointment = new StaffAppointment(companyName, founderId,
                StaffAppointment.StaffRole.MANAGER, Collections.emptySet());
        revoker.addStaffAppointment(companyName, revokerAppointment);

        // Target was appointed by the revoker
        Member target = new Member(targetId, "target", "target@test.com", "pw",
                "050-2222222", LocalDate.of(1990, 1, 1));
        StaffAppointment targetAppointment = new StaffAppointment(companyName, revokerId,
                StaffAppointment.StaffRole.MANAGER, Collections.emptySet());
        target.addStaffAppointment(companyName, targetAppointment);
        revokerAppointment.addAppointedStaffMember(targetId);

        memberRepo.save(revoker);
        memberRepo.save(target);

        RevokePersonnelEvent event = new RevokePersonnelEvent(company, revokerId, targetId);
        revokeHandler.handle(event);

        // Verify the TARGET was notified (not the revoker)
        verify(notificationService).notify(
                eq(targetId.toString()),
                eq("You have been revoked from the company."));
        // Verify the revoker was NOT notified
        verify(notificationService, never()).notify(eq(revokerId.toString()), anyString());
    }
}
