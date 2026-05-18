package com.ticketing.application.listener;

import com.ticketing.domain.company.Company;
import com.ticketing.domain.company.ICompanyRepository;
import com.ticketing.domain.event.IEvent;
import com.ticketing.domain.event.IEventListener;
import com.ticketing.domain.member.IMemberRepository;
import com.ticketing.domain.member.Member;
import com.ticketing.domain.member.StaffAppointment;
import com.ticketing.domain.member.communication.ManagerPermissionsChangedEvent;

/**
 * Handles the ManagerPermissionsChangedEvent.
 * Enforces authorization and updates the member aggregate.
 */
public class ManagerPermissionsChangedHandler implements IEventListener {

    private final IMemberRepository memberRepository;
    private final ICompanyRepository companyRepository;

    public ManagerPermissionsChangedHandler(IMemberRepository memberRepository, ICompanyRepository companyRepository) {
        this.memberRepository = memberRepository;
        this.companyRepository = companyRepository;
    }

    @Override
    public void handle(IEvent event) {
        if (!(event instanceof ManagerPermissionsChangedEvent)) {
            return;
        }

        ManagerPermissionsChangedEvent changeEvent = (ManagerPermissionsChangedEvent) event;

        Member caller = memberRepository.findById(changeEvent.getCallerId())
                .orElseThrow(() -> new IllegalArgumentException("Caller not found: " + changeEvent.getCallerId()));

        Company company = companyRepository.findByName(changeEvent.getCompanyName())
                .orElseThrow(() -> new IllegalArgumentException("Company not found: " + changeEvent.getCompanyName()));

        StaffAppointment callerAppt = caller.getStaffAppointment(changeEvent.getCompanyName());
        if (callerAppt == null) {
            throw new SecurityException("Caller is not a staff member of company: " + changeEvent.getCompanyName());
        }

        Member targetMember = memberRepository.findById(changeEvent.getTargetMemberId())
                .orElseThrow(() -> new IllegalArgumentException("Target member not found: " + changeEvent.getTargetMemberId()));

        StaffAppointment targetAppt = targetMember.getStaffAppointment(changeEvent.getCompanyName());
        if (targetAppt == null) {
            throw new IllegalArgumentException("Target is not a staff member of company: " + changeEvent.getCompanyName());
        }

        if (!targetAppt.isManager()) {
            throw new IllegalArgumentException("Only manager permissions can be modified.");
        }

        // Authorization check: Only founder or direct appointer
        boolean isFounder = changeEvent.getCallerId().equals(company.getFounderId());
        boolean isDirectAppointer = changeEvent.getCallerId().equals(targetAppt.getAppointedByMemberId());

        if (!isFounder && !isDirectAppointer) {
            throw new SecurityException("Only the founder or the direct appointer can modify manager permissions.");
        }

        // Idempotency check
        if (targetAppt.getPermissions().equals(changeEvent.getNewPermissions())) {
            return;
        }

        targetAppt.updateManagerPermissions(changeEvent.getNewPermissions());
        memberRepository.save(targetMember);
    }
}
