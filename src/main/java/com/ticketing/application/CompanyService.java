package com.ticketing.application;

import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ticketing.application.auth.ISessionTokenService;
import com.ticketing.domain.company.ICompanyRepository;
import com.ticketing.domain.event.CompanyOpenedEvent;
import com.ticketing.domain.event.IEventPublisher;
import com.ticketing.domain.member.ManagerPermission;
import com.ticketing.domain.member.StaffAppointment;
import com.ticketing.domain.member.communication.ManagerPermissionsChangedEvent;
import com.ticketing.domain.member.communication.RoleAppointmentOfferRequestedEvent;
import com.ticketing.domain.member.communication.RoleAppointmentOfferResponseEvent;

public class CompanyService {
    private static final Logger log = LoggerFactory.getLogger(CompanyService.class);

    private final ICompanyRepository companyRepository;
    private final IEventPublisher eventPublisher;
    private final ISessionTokenService sessionTokenService;
    private final Object lock = new Object();

    public CompanyService(
            ICompanyRepository companyRepository, 
            IEventPublisher eventPublisher, 
            ISessionTokenService sessionTokenService
    ) {
        this.companyRepository = companyRepository;
        this.eventPublisher = eventPublisher;
        this.sessionTokenService = sessionTokenService;
    }

    /**
     * Creates a new production company. The creating member becomes the Founder
     * and initial Owner (via a Founder StaffAppointment in the Member aggregate).
     * Publishes a CompanyOpenedEvent for listeners (e.g., MemberService) to handle.
     *
     * @param token token of the member creating the company
     * @param name the company name
     * @param description optional company description
     * @return the new company's name (unique identifier)
     */
    public String openProductionCompany(String token, String name, String description) {
        UUID founderId = validateToken(token);
        
        synchronized (lock) {
            log.info("Creating company: founderId={}, name={}", founderId, name);
            
            if (founderId == null) {
                throw new IllegalArgumentException("A guest user cannot create a production company. Please log in.");
            }

            if (companyRepository.existsByName(name)) {
                throw new IllegalArgumentException("A production company with this name already exists.");
            }

            com.ticketing.domain.company.Company company = new com.ticketing.domain.company.Company(name, description, founderId);
            companyRepository.save(company);

            // Publish event for listeners to handle member updates
            CompanyOpenedEvent event = new CompanyOpenedEvent(company.getName(), founderId);
            eventPublisher.publish(event);

            log.info("Company created: companyName={}, founderId={}", company.getName(), founderId);
            return company.getName();
        }
    }

    public void offerRoleAppointment(
            String token, 
            String companyName, 
            UUID targetMemberId, 
            StaffAppointment.StaffRole role, 
            Set<ManagerPermission> permissions
    ) {
        UUID appointerId = validateToken(token);
        
        if (!companyRepository.existsByName(companyName)) {
            throw new IllegalArgumentException("Company not found");
        }

        // Publish event for MemberService to handle authorization and offer creation
        RoleAppointmentOfferRequestedEvent event = 
            new RoleAppointmentOfferRequestedEvent(appointerId, targetMemberId, companyName, role, permissions);
        eventPublisher.publish(event);

        log.info("Role appointment requested: company={}, appointer={}, target={}, role={}", 
            companyName, appointerId, targetMemberId, role);
    }

    public void changeManagerPermissions(
            String token,
            String companyName,
            UUID targetMemberId,
            Set<ManagerPermission> newPermissions
    ) {
        UUID callerId = validateToken(token);

        if (callerId == null) {
            throw new IllegalArgumentException("A guest user cannot change manager permissions. Please log in.");
        }

        if (targetMemberId == null) {
            throw new IllegalArgumentException("Target member ID is required");
        }

        if (!companyRepository.existsByName(companyName)) {
            throw new IllegalArgumentException("Company not found: " + companyName);
        }

        // Publish event for MemberService/Handler to handle authorization and update
        ManagerPermissionsChangedEvent event = 
            new ManagerPermissionsChangedEvent(callerId, targetMemberId, companyName, newPermissions);
        eventPublisher.publish(event);

        log.info("Manager permissions change requested: company={}, caller={}, target={}", 
            companyName, callerId, targetMemberId);
    }

    public void respondToRoleAppointment(
            String token,
            UUID appointmentOfferId,
            boolean accepted
    ) {
        UUID responderId = validateToken(token);

        // Let the handler take care of member related stuff, just publish the response event
        RoleAppointmentOfferResponseEvent event = new RoleAppointmentOfferResponseEvent(
            appointmentOfferId,
            responderId,
            accepted
        );
        eventPublisher.publish(event);

        log.info("Role appointment response handled: responder={}, offer={}, accepted={}",
            responderId, appointmentOfferId, accepted);
    }

    private UUID validateToken(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Authentication token is required");
        }
        if (!sessionTokenService.isValid(token)) {
            throw new IllegalArgumentException("Invalid or expired authentication token");
        }
        return sessionTokenService.extractMemberId(token);
    }
}
