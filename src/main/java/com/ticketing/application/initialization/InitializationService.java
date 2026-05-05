package com.ticketing.application.initialization;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ticketing.application.CompanyService;
import com.ticketing.application.INotificationService;
import com.ticketing.application.auth.ISessionTokenService;
import com.ticketing.application.listener.MemberCompanyOpenedEventHandler;
import com.ticketing.application.listener.MemberCompanyClosedEventHandler;
import com.ticketing.application.listener.RoleAppointmentOfferRequestedHandler;
import com.ticketing.domain.company.ICompanyRepository;
import com.ticketing.domain.event.IEventPublisher;
import com.ticketing.domain.member.IMemberRepository;

/**
 * Initializes and wires up application services with their dependencies.
 * Handles event publisher/listener registration to establish the event-driven communication.
 */
public class InitializationService {
    private static final Logger log = LoggerFactory.getLogger(InitializationService.class);

    private final ICompanyRepository companyRepository;
    private final IMemberRepository memberRepository;
    private final IEventPublisher eventPublisher;
    private final ISessionTokenService sessionTokenService;
    private final INotificationService notificationService;

    public InitializationService(
            ICompanyRepository companyRepository,
            IMemberRepository memberRepository,
            IEventPublisher eventPublisher,
            ISessionTokenService sessionTokenService,
            INotificationService notificationService
    ) {
        if (companyRepository == null || memberRepository == null || 
            eventPublisher == null || sessionTokenService == null || notificationService == null) {
            throw new IllegalArgumentException("All dependencies must be provided");
        }
        this.companyRepository = companyRepository;
        this.memberRepository = memberRepository;
        this.eventPublisher = eventPublisher;
        this.sessionTokenService = sessionTokenService;
        this.notificationService = notificationService;
    }

    /**
     * Initializes the CompanyService with all required dependencies.
     * @return initialized CompanyService
     */
    public CompanyService initializeCompanyService() {
        log.info("Initializing CompanyService");
        return new CompanyService(companyRepository, eventPublisher, sessionTokenService);
    }

    /**
     * Initializes and registers event listeners for the domain events.
     */
    public void initializeEventListeners() {
        log.info("Initializing event listeners");
        
        // Create and register the handler for CompanyOpenedEvent
        MemberCompanyOpenedEventHandler companyOpenedHandler = new MemberCompanyOpenedEventHandler(memberRepository);
        eventPublisher.subscribe("CompanyOpened", companyOpenedHandler);

        // Register the handler for RoleAppointmentOfferRequested
        RoleAppointmentOfferRequestedHandler offerHandler = new RoleAppointmentOfferRequestedHandler(memberRepository);
        eventPublisher.subscribe("RoleAppointmentOfferRequested", offerHandler);

        // Register the handler for CompanyClosedEvent
        MemberCompanyClosedEventHandler companyClosedHandler = new MemberCompanyClosedEventHandler(memberRepository);
        eventPublisher.subscribe("CompanyClosed", companyClosedHandler);
        
        log.info("Event listeners registered");
    }

    /**
     * Complete initialization: creates services and registers listeners.
     * @return initialized CompanyService (other services are initialized as side effect)
     */
    public CompanyService initialize() {
        initializeEventListeners();
        return initializeCompanyService();
    }
}
