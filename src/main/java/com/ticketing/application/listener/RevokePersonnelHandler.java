package com.ticketing.application.listener;

import java.util.UUID;

import com.ticketing.application.INotificationService;
import com.ticketing.domain.company.Company;
import com.ticketing.domain.event.IEvent;
import com.ticketing.domain.event.IEventListener;
import com.ticketing.domain.member.IMemberRepository;
import com.ticketing.domain.member.Member;
import com.ticketing.domain.member.StaffAppointment;
import com.ticketing.domain.member.communication.RevokePersonnelEvent;

public class RevokePersonnelHandler implements IEventListener {
    private final IMemberRepository memberRepository;
    private final INotificationService notificationService;

    public RevokePersonnelHandler(IMemberRepository memberRepository, INotificationService notificationService) {
        this.memberRepository = memberRepository;
        this.notificationService = notificationService;
    }

    @Override
    public void handle(IEvent event) {
        if (!(event instanceof RevokePersonnelEvent)) {
            return;
        }

        RevokePersonnelEvent revokePersonnelEvent = (RevokePersonnelEvent) event;

        UUID targetMemberId = revokePersonnelEvent.getTargetMemberId();
        UUID revokerId = revokePersonnelEvent.getRevokerId();
        Company company = revokePersonnelEvent.getCompany();
        String companyName = company.getName();

        Member targetMember = memberRepository.findById(targetMemberId)
            .orElseThrow(() -> new IllegalArgumentException("Target member not found"));

        Member revokerMember = memberRepository.findById(revokerId)
            .orElseThrow(() -> new IllegalArgumentException("Revoker member not found"));

        if (company.getFounderId().equals(targetMemberId)) {
            throw new IllegalArgumentException("Cannot revoke the founder of the company.");
        }

        StaffAppointment targetAppointment = targetMember.getStaffAppointment(companyName);
        if (targetAppointment == null) {
            throw new IllegalArgumentException("Target member does not have a role in this company.");
        }
        if (targetAppointment.isRevoked()) {
            throw new IllegalArgumentException("Target member is already revoked from this company.");
        }

        UUID appointerId = targetAppointment.getAppointedByMemberId();
        if (!appointerId.equals(revokerId)) {
            throw new IllegalArgumentException(
                    "Revoker does not have permission to revoke this member. Only the appointer can revoke their appointees.");
        }

        StaffAppointment appointerAppointment = revokerMember.getStaffAppointment(companyName);
        if (appointerAppointment == null) {
            throw new IllegalArgumentException("Revoker does not have a staff appointment in this company");
        }

        appointerAppointment.removeAppointedStaffMember(targetMemberId);
        targetAppointment.revoke();

        memberRepository.save(revokerMember);
        memberRepository.save(targetMember);

        notificationService.notify(
                targetMemberId.toString(),
                "You have been revoked from the company.");
    }
}
