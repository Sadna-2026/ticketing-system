package com.ticketing.application.listener;

import java.util.Set;
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

        // Get the target member and throw if member doesn't exist
        Member targetMember = memberRepository.findById(targetMemberId)
            .orElseThrow(() -> new IllegalArgumentException("Target member not found"));
        
        // Get the revoker member and throw if member doesn't exist (optional, depending on your domain rules)
        Member revokerMember = memberRepository.findById(revokerId)
            .orElseThrow(() -> new IllegalArgumentException("Revoker member not found"));
        
        // Check if the target is founder (not allowed to revoke the founder)
        if (company.getFounderId().equals(targetMemberId)) {
            throw new IllegalArgumentException("Cannot revoke the founder of the company.");
        }

        // Get target's role in the company and throw if they don't have a role (not part of the company)
        StaffAppointment targetAppointment = targetMember.getStaffAppointment(company.getName());
        if (targetAppointment == null) {
            throw new IllegalArgumentException("Target member does not have a role in this company.");
        }

        // Get the target's appointer (the member who appointed them)
        UUID fatherAppointerId = targetAppointment.getAppointedByMemberId();
        if (!fatherAppointerId.equals(revokerId)) {
             throw new IllegalArgumentException("Revoker does not have permission to revoke this member. Only the appointer can revoke their appointees.");
        }
        // Now revoker is the appointer of the target, so they have permission to revoke them.

        // Get target's appointed staff members (the members they appointed)
        Set<UUID> childrenAppointedIds = targetAppointment.getAppointedStaffMemberIds();
        
        StaffAppointment fatherAppointment = revokerMember.getStaffAppointment(company.getName());
        if (fatherAppointment == null) { // sanity check
            throw new IllegalArgumentException("Father appointer does not have a staff appointment in this company");
        }
        
        if (childrenAppointedIds != null && !childrenAppointedIds.isEmpty()) {
            // Reassign the appointer of the children to the revoked member's appointer (member's "father")
            for (UUID childId : childrenAppointedIds) {
                Member appointedMember = memberRepository.findById(childId)
                    .orElseThrow(() -> new IllegalArgumentException("Appointed member not found"));
                
                // Update the appointedByMemberId to the father appointer
                appointedMember.getStaffAppointment(company.getName()).updateAppointedBy(fatherAppointerId);
                memberRepository.save(appointedMember);
                
                // Notify the child members that their appointer has been revoked and they have been reassigned to the father appointer
                notificationService.notify(childId.toString(), "Your previous manager has been removed. You now report directly to the higher-level appointer.");
            }

            // assign all the children of the revoked member to the father appointer
            fatherAppointment.addAppointedStaffMemberGroup(childrenAppointedIds); // Reassign children to the father appointer
        }

        // Remove the revoked member from their father's appointedStaffMemberIds
        fatherAppointment.removeAppointedStaffMember(targetMemberId);
        
        // Save the updated father appointer (with the revoked member removed and children reassigned)
        memberRepository.save(revokerMember);

        // Finally, remove the staff appointment of the revoked member (removing them from the company)
        targetMember.removeStaffAppointment(company.getName());
        
        // Keep the changes in the revoked member
        memberRepository.save(targetMember);

        // Notify the target member that they have been revoked from the company
        notificationService.notify(targetMemberId.toString(), "You have been revoked from the company.");
    }
}
