package com.ticketing.application.listener;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ticketing.domain.event.CompanyClosedEvent;
import com.ticketing.domain.event.IEvent;
import com.ticketing.domain.event.IEventListener;
import com.ticketing.domain.member.IMemberRepository;
import com.ticketing.domain.member.Member;

/**
 * Handles CompanyClosedEvent by revoking all staff appointments if the closure is permanent.
 */
public class MemberCompanyClosedEventHandler implements IEventListener {
    private static final Logger log = LoggerFactory.getLogger(MemberCompanyClosedEventHandler.class);
    
    private final IMemberRepository memberRepository;

    public MemberCompanyClosedEventHandler(IMemberRepository memberRepository) {
        if (memberRepository == null) {
            throw new IllegalArgumentException("memberRepository cannot be null");
        }
        this.memberRepository = memberRepository;
    }

    @Override
    public void handle(IEvent event) {
        if (!(event instanceof CompanyClosedEvent)) {
            return;
        }

        CompanyClosedEvent closedEvent = (CompanyClosedEvent) event;
        String companyName = closedEvent.getCompanyName();
        boolean permanent = closedEvent.isPermanent();

        if (!permanent) {
            log.info("Temporary closure for company: {}. No staff revocation required.", companyName);
            return;
        }

        log.info("Permanent closure detected for company: {}. Starting staff revocation.", companyName);
        
        try {
            List<Member> staff = memberRepository.findByCompanyAppointment(companyName);
            for (Member m : staff) {
                m.removeStaffAppointment(companyName);
                memberRepository.save(m);
            }
            log.info("Successfully revoked roles for {} staff members of company: {}", staff.size(), companyName);
        } catch (Exception e) {
            log.error("Error revoking staff for permanently closed company {}: {}", 
                companyName, e.getMessage(), e);
            throw new RuntimeException("Failed to handle CompanyClosedEvent staff revocation", e);
        }
    }
}
