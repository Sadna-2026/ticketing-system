package com.ticketing.application.listener;

import java.util.Set;
import java.util.UUID;

import com.ticketing.application.services.INotificationService;
import com.ticketing.domain.company.Company;
import com.ticketing.domain.event.IEvent;
import com.ticketing.domain.event.IEventListener;
import com.ticketing.domain.exception.OptimisticLockException;
import com.ticketing.domain.member.IMemberRepository;
import com.ticketing.domain.member.Member;
import com.ticketing.domain.member.StaffAppointment;
import com.ticketing.domain.member.communication.RelinquishOwnershipEvent;

public class RelinquishOwnershipHandler implements IEventListener {
    private final IMemberRepository memberRepository;
    private final INotificationService notificationService;

    public RelinquishOwnershipHandler(IMemberRepository memberRepository, INotificationService notificationService) {
        this.memberRepository = memberRepository;
        this.notificationService = notificationService;
    }

    @Override
    public void handle(IEvent event) {
        if (!(event instanceof RelinquishOwnershipEvent)) {
            return;
        }

        RelinquishOwnershipEvent relinquishEvent = (RelinquishOwnershipEvent) event;

        UUID ownerId = relinquishEvent.getOwnerId();
        Company company = relinquishEvent.getCompany();
        String companyName = company.getName();

        Member owner = memberRepository.findById(ownerId)
            .orElseThrow(() -> new IllegalArgumentException("Owner member not found"));

        if (company.getFounderId().equals(ownerId)) {
            throw new IllegalArgumentException("Founder cannot relinquish ownership. Use company closure flow instead.");
        }

        StaffAppointment ownerAppointment = owner.getStaffAppointment(companyName);
        if (ownerAppointment == null) {
            throw new IllegalArgumentException("Member does not have a role in this company");
        }

        if (!ownerAppointment.isOwner()) {
            throw new IllegalArgumentException("Only owners can relinquish ownership. Managers must be revoked by their appointer.");
        }

        int ownerCount = countOwnersInCompany(companyName);
        if (ownerCount <= 1) {
            throw new IllegalArgumentException("Cannot relinquish ownership: you are the last owner. Add another owner first.");
        }

        UUID appointerId = ownerAppointment.getAppointedByMemberId();
        Member appointer = memberRepository.findById(appointerId)
            .orElseThrow(() -> new IllegalArgumentException("Appointer not found"));

        StaffAppointment appointerAppointment = appointer.getStaffAppointment(companyName);
        if (appointerAppointment == null) {
            throw new IllegalArgumentException("Appointer does not have a staff appointment in this company");
        }

        Set<UUID> childrenAppointedIds = ownerAppointment.getAppointedStaffMemberIds();

        if (childrenAppointedIds != null && !childrenAppointedIds.isEmpty()) {
            for (UUID childId : childrenAppointedIds) {
                Member appointedMember = memberRepository.findById(childId)
                    .orElseThrow(() -> new IllegalArgumentException("Appointed member not found"));

                appointedMember.getStaffAppointment(companyName).updateAppointedBy(appointerId);
                saveMember(appointedMember);

                notificationService.notify(childId.toString(), "Your previous owner has relinquished ownership. You now report to the higher-level appointer.");
            }

            appointerAppointment.addAppointedStaffMemberGroup(childrenAppointedIds);
        }

        appointerAppointment.removeAppointedStaffMember(ownerId);
        saveMember(appointer);

        owner.removeStaffAppointment(companyName);
        saveMember(owner);

        notificationService.notify(ownerId.toString(), "You have successfully relinquished ownership of the company.");
    }

    private void saveMember(Member member) {
        try {
            memberRepository.save(member);
        } catch (OptimisticLockException ex) {
            throw new IllegalStateException("Ownership relinquishment changed concurrently. Please retry.", ex);
        }
    }

    private int countOwnersInCompany(String companyName) {
        int count = 0;
        for (Member member : memberRepository.findByCompanyAppointment(companyName)) {
            StaffAppointment appointment = member.getStaffAppointment(companyName);
            if (appointment != null && appointment.isOwner()) {
                count++;
            }
        }
        return count;
    }
}
