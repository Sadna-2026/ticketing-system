package com.ticketing.application.listener;

import java.util.Collections;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ticketing.domain.event.CompanyOpenedEvent;
import com.ticketing.domain.event.IEvent;
import com.ticketing.domain.event.IEventListener;
import com.ticketing.domain.member.IMemberRepository;
import com.ticketing.domain.member.Member;
import com.ticketing.domain.member.StaffAppointment;

/**
 * Handles CompanyOpenedEvent by assigning the founder the Owner role.
 * Subscribes to CompanyOpenedEvent and updates the founder's StaffAppointment.
 */
public class MemberCompanyOpenedEventHandler implements IEventListener {
    private static final Logger log = LoggerFactory.getLogger(MemberCompanyOpenedEventHandler.class);
    
    private final IMemberRepository memberRepository;

    public MemberCompanyOpenedEventHandler(IMemberRepository memberRepository) {
        if (memberRepository == null) {
            throw new IllegalArgumentException("memberRepository cannot be null");
        }
        this.memberRepository = memberRepository;
    }

    @Override
    public void handle(IEvent event) {
        if (!(event instanceof CompanyOpenedEvent)) {
            return;
        }

        CompanyOpenedEvent companyOpenedEvent = (CompanyOpenedEvent) event;
        String companyName = companyOpenedEvent.getCompanyName();
        java.util.UUID founderId = companyOpenedEvent.getFounderId();

        try {
            Member founder = memberRepository.findById(founderId)
                .orElseThrow(() -> new IllegalArgumentException("Member not found: " + founderId));

            // Assign Owner appointment to the member
            StaffAppointment ownerAppointment = new StaffAppointment(
                companyName,
                founderId,
                StaffAppointment.StaffRole.OWNER,
                Collections.emptySet()
            );
            founder.addStaffAppointment(companyName, ownerAppointment);
            memberRepository.save(founder);

            log.info("Member assigned owner role: memberId={}, companyName={}", founderId, companyName);
        } catch (Exception e) {
            log.error("Error handling CompanyOpenedEvent for company={}, founder={}: {}", 
                companyName, founderId, e.getMessage(), e);
            throw new RuntimeException("Failed to handle CompanyOpenedEvent", e);
        }
    }
}
