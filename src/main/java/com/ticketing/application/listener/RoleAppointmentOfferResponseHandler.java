package com.ticketing.application.listener;

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

    public RoleAppointmentOfferResponseHandler(IMemberRepository memberRepository, ICompanyRepository companyRepository) {
        this.memberRepository = memberRepository;
        this.companyRepository = companyRepository;
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

        // Check if the company exists and is active
        if (!companyRepository.existsByName(offer.getCompanyName())) {
            throw new IllegalArgumentException("Company does not exist");
        }

        com.ticketing.domain.company.Company company = companyRepository.findByName(offer.getCompanyName())
            .orElseThrow(() -> new IllegalArgumentException("Company not found"));

        if (!company.isActive()) {
            throw new IllegalArgumentException("Cannot accept role appointment for inactive company");
        }

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

            target.addStaffAppointment(offer.getCompanyName(), appointment);
        }

        target.removePendingOffer(responseEvent.getOfferId());
        memberRepository.save(target);
    }
}
