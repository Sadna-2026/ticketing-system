package com.ticketing.application.listener;

import com.ticketing.application.INotificationService;
import com.ticketing.domain.event.IEvent;
import com.ticketing.domain.event.IEventListener;
import com.ticketing.domain.member.communication.RoleAppointmentOfferedEvent;

public class RoleAppointmentOfferedEventHandler implements IEventListener {

    private final INotificationService notificationService;

    public RoleAppointmentOfferedEventHandler(INotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Override
    public void handle(IEvent event) {
        if (event instanceof RoleAppointmentOfferedEvent) {
            RoleAppointmentOfferedEvent offerEvent = (RoleAppointmentOfferedEvent) event;
            String message = "You have received a new role appointment offer for company: " + offerEvent.getCompanyName();
            notificationService.notify(offerEvent.getTargetMemberId().toString(), message);
        }
    }
}
