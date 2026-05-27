package com.ticketing.application.initialization;

import org.springframework.stereotype.Component;

import com.ticketing.application.listener.ManagerPermissionsChangedHandler;
import com.ticketing.application.listener.MemberCompanyOpenedEventHandler;
import com.ticketing.application.listener.RelinquishOwnershipHandler;
import com.ticketing.application.listener.RevokePersonnelHandler;
import com.ticketing.application.listener.RoleAppointmentOfferRequestedHandler;
import com.ticketing.application.listener.RoleAppointmentOfferResponseHandler;
import com.ticketing.application.services.INotificationService;
import com.ticketing.domain.company.ICompanyRepository;
import com.ticketing.domain.event.IEventPublisher;
import com.ticketing.domain.member.IMemberRepository;

import jakarta.annotation.PostConstruct;

@Component
public class EventListenerRegistrar {

    private final ICompanyRepository companyRepository;
    private final IMemberRepository memberRepository;
    private final IEventPublisher eventPublisher;
    private final INotificationService notificationService;

    public EventListenerRegistrar(
            ICompanyRepository companyRepository,
            IMemberRepository memberRepository,
            IEventPublisher eventPublisher,
            INotificationService notificationService
    ) {
        this.companyRepository = companyRepository;
        this.memberRepository = memberRepository;
        this.eventPublisher = eventPublisher;
        this.notificationService = notificationService;
    }

    @PostConstruct
    public void registerListeners() {
        eventPublisher.subscribe("CompanyOpened", new MemberCompanyOpenedEventHandler(memberRepository));
        eventPublisher.subscribe("RoleAppointmentOfferRequested", new RoleAppointmentOfferRequestedHandler(memberRepository, notificationService));
        eventPublisher.subscribe("RoleAppointmentOfferResponse",
                new RoleAppointmentOfferResponseHandler(memberRepository, companyRepository, notificationService));
        eventPublisher.subscribe("ManagerPermissionsChanged",
                new ManagerPermissionsChangedHandler(memberRepository, companyRepository));
        eventPublisher.subscribe("RevokePersonnel", new RevokePersonnelHandler(memberRepository, notificationService));
        eventPublisher.subscribe("RelinquishOwnership", new RelinquishOwnershipHandler(memberRepository, notificationService));
    }
}
