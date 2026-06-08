package com.ticketing.application.listener;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ticketing.application.services.INotificationService;
import com.ticketing.domain.event.IEvent;
import com.ticketing.domain.event.IEventListener;
import com.ticketing.domain.exception.OptimisticLockException;
import com.ticketing.domain.member.IMemberRepository;
import com.ticketing.domain.member.ManagerPermission;
import com.ticketing.domain.member.Member;
import com.ticketing.domain.member.PendingRoleOffer;
import com.ticketing.domain.member.PermissionDeniedException;
import com.ticketing.domain.member.StaffAppointment;
import com.ticketing.domain.member.communication.RoleAppointmentOfferRequestedEvent;

public class RoleAppointmentOfferRequestedHandler implements IEventListener {

    private static final Logger log = LoggerFactory.getLogger(RoleAppointmentOfferRequestedHandler.class);
    private static final int MAX_SAVE_RETRIES = 100;

    private final IMemberRepository memberRepository;
    private final INotificationService notificationService;

    public RoleAppointmentOfferRequestedHandler(IMemberRepository memberRepository, INotificationService notificationService) {
        this.memberRepository = memberRepository;
        this.notificationService = notificationService;
    }

    @Override
    public void handle(IEvent event) {
        if (event instanceof RoleAppointmentOfferRequestedEvent) {
            RoleAppointmentOfferRequestedEvent reqEvent = (RoleAppointmentOfferRequestedEvent) event;

            addOfferWithOptimisticRetry(reqEvent);
        }
    }

    private void addOfferWithOptimisticRetry(RoleAppointmentOfferRequestedEvent reqEvent) {
        OptimisticLockException lastConflict = null;
        for (int attempt = 0; attempt < MAX_SAVE_RETRIES; attempt++) {
            Member appointer = memberRepository.findById(reqEvent.getAppointerId())
                .orElseThrow(() -> new IllegalArgumentException("Appointer not found"));
            
            // Authorization Check
            checkPermission(appointer, reqEvent.getCompanyName(), ManagerPermission.PERSONNEL_MGMT);
            
            Member target = memberRepository.findById(reqEvent.getTargetMemberId())
                .orElseThrow(() -> new IllegalArgumentException("Target member not found"));
            
            // Domain rule: cannot promote existing owner
            if (target.hasStaffAppointment(reqEvent.getCompanyName(), StaffAppointment.StaffRole.OWNER)) {
                throw new IllegalArgumentException("Target is already an owner of this company");
            }

            // Create and add the offer to the target member
            PendingRoleOffer offer = new PendingRoleOffer(
                reqEvent.getAppointerId(),
                reqEvent.getCompanyName(),
                reqEvent.getRole(),
                reqEvent.getPermissions(),
                LocalDateTime.now().plusDays(7)
            );
            target.addPendingOffer(offer);

            try {
                memberRepository.save(target);
                if (notificationService != null) {
                    notificationService.notify(target.getId().toString(), "You have a new role offer for company: " + reqEvent.getCompanyName() + " | Offer ID: " + offer.getOfferId());
                }
                log.info("Role offer created: offerId={}, target={}, company={}", offer.getOfferId(), target.getId(), reqEvent.getCompanyName());
                return;
            } catch (OptimisticLockException ex) {
                lastConflict = ex;
            }
        }
        throw lastConflict;
    }

    private void checkPermission(Member member, String companyName, ManagerPermission permission) {
        if (!member.hasStaffAppointment(companyName, StaffAppointment.StaffRole.OWNER)) {
            StaffAppointment appointment = member.getStaffAppointment(companyName);
            if (appointment == null || !appointment.hasPermission(permission)) {
                throw new PermissionDeniedException("Member does not have " + permission + " permission");
            }
        }
    }
}
