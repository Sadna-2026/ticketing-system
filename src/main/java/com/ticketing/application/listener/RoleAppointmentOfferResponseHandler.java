package com.ticketing.application.listener;

import com.ticketing.application.INotificationService;
import com.ticketing.domain.company.ICompanyRepository;
import com.ticketing.domain.event.IEvent;
import com.ticketing.domain.event.IEventListener;
import com.ticketing.domain.member.IMemberRepository;
import com.ticketing.domain.member.Member;
import com.ticketing.domain.member.PendingRoleOffer;
import com.ticketing.domain.member.StaffAppointment;
import com.ticketing.domain.member.communication.RoleAppointmentOfferResponseEvent;

public class RoleAppointmentOfferResponseHandler implements IEventListener {
    private final IMemberRepository memberRepository;
    private final ICompanyRepository companyRepository;
    private final INotificationService notificationService;

    public RoleAppointmentOfferResponseHandler(
            IMemberRepository memberRepository,
            ICompanyRepository companyRepository,
            INotificationService notificationService
    ) {
        this.memberRepository = memberRepository;
        this.companyRepository = companyRepository;
        this.notificationService = notificationService;
    }

    @Override
    public void handle(IEvent event) {
        if (!(event instanceof RoleAppointmentOfferResponseEvent)) {
            return;
        }

        RoleAppointmentOfferResponseEvent responseEvent = (RoleAppointmentOfferResponseEvent) event;

        Member target = memberRepository.findById(responseEvent.getTargetMemberId())
            .orElseThrow(() -> new IllegalArgumentException("Target member not found"));

        PendingRoleOffer offer = target.findPendingOffer(responseEvent.getOfferId())
            .orElseThrow(() -> new IllegalArgumentException("Pending offer not found"));

        if (offer.isExpired()) {
            throw new IllegalArgumentException("Offer has expired");
        }

        Member appointer = memberRepository.findById(offer.getOfferedByMemberId())
            .orElseThrow(() -> new IllegalArgumentException("Appointer not found"));

        // Check if the company exists
        if (!companyRepository.existsByName(offer.getCompanyName())) {
            throw new IllegalArgumentException("Company does not exist");
        }

        com.ticketing.domain.company.Company company = companyRepository.findByName(offer.getCompanyName())
            .orElseThrow(() -> new IllegalArgumentException("Company not found"));

        // Check if the company is active
        if (!company.isActive()) {
            throw new IllegalArgumentException("Cannot accept role appointment for inactive company");
        }

        StaffAppointment existingAppointment = target.getStaffAppointment(offer.getCompanyName());

        if (responseEvent.isAccepted()) {
            if (target.hasStaffAppointment(offer.getCompanyName(), StaffAppointment.StaffRole.OWNER)) {
                throw new IllegalArgumentException("Target is already an owner of this company");
            }

            if (offer.getRole() == StaffAppointment.StaffRole.OWNER && !offer.getPermissions().isEmpty()) {
                throw new IllegalArgumentException("Owner role cannot have specific permissions");
            }

            // Create the staff appointment based on the offer details
            // Can promote (even a manager) to owner
            StaffAppointment appointment = new StaffAppointment(
                offer.getCompanyName(),
                offer.getOfferedByMemberId(), // The appointer is the one who made the offer
                offer.getRole(),
                offer.getPermissions() // Use the permissions from the offer, which should be empty for non-manager roles
            );

            // Add the staff appointment to the member
            target.addStaffAppointment(offer.getCompanyName(), appointment);

            // update the appointer's record to save the new appointee
            StaffAppointment appointerAppointment = appointer.getStaffAppointment(offer.getCompanyName());
            if (appointerAppointment != null) {
                appointerAppointment.addAppointedStaffMember(target.getId());
                memberRepository.save(appointer);
            }
        }

        // Pending offer was taken care of (accepted or rejected), so remove it from the member's pending offers
        target.removePendingOffer(responseEvent.getOfferId());
        memberRepository.save(target);

        String responseText;
        // Determine the notification message based on acceptance/rejection and role changes
        // Special case for manager promotion to owner
        if (responseEvent.isAccepted()) { // accepted the offer
            if (existingAppointment != null && existingAppointment.getRole() == StaffAppointment.StaffRole.MANAGER && offer.getRole() == StaffAppointment.StaffRole.OWNER) {
                responseText = "The manager was promoted to owner for company '" + offer.getCompanyName() + "'.";
            } else {
                String roleLabel = offer.getRole() == StaffAppointment.StaffRole.MANAGER ? "manager" : "owner";
                responseText = "The member has accepted the " + roleLabel + " role offer for company '" + offer.getCompanyName() + "'.";
            }
        } else { // rejected the offer
            if (existingAppointment != null && existingAppointment.getRole() == StaffAppointment.StaffRole.MANAGER && offer.getRole() == StaffAppointment.StaffRole.OWNER) {
                responseText = "The manager rejected the promotion to owner for company '" + offer.getCompanyName() + "'.";
            } else {
                String roleLabel = offer.getRole() == StaffAppointment.StaffRole.MANAGER ? "manager" : "owner";
                responseText = "The member has declined the " + roleLabel + " role offer for company '" + offer.getCompanyName() + "'.";
            }
        }

        // Notify the appointer about the response to their offer
        notificationService.notify(offer.getOfferedByMemberId().toString(), responseText);
    }
}
