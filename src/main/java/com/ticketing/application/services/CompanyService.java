package com.ticketing.application.services;

import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ticketing.application.auth.ISessionTokenService;
import com.ticketing.domain.company.Company;
import com.ticketing.domain.company.ICompanyRepository;
import com.ticketing.domain.event.CompanyOpenedEvent;
import com.ticketing.domain.event.IEventPublisher;
import com.ticketing.domain.exception.OptimisticLockException;
import com.ticketing.domain.member.ManagerPermission;
import com.ticketing.domain.member.StaffAppointment;
import com.ticketing.domain.member.communication.ManagerPermissionsChangedEvent;
import com.ticketing.domain.member.communication.RelinquishOwnershipEvent;
import com.ticketing.domain.member.communication.RevokePersonnelEvent;
import com.ticketing.domain.member.communication.RoleAppointmentOfferRequestedEvent;
import com.ticketing.domain.member.communication.RoleAppointmentOfferResponseEvent;

@org.springframework.stereotype.Service
public class CompanyService {
    private static final Logger log = LoggerFactory.getLogger(CompanyService.class);

    private final ICompanyRepository companyRepository;
    private final IEventPublisher eventPublisher;
    private final ISessionTokenService sessionTokenService;

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

        log.info("Creating company: founderId={}, name={}", founderId, name);

        if (founderId == null) {
            throw new IllegalArgumentException("A guest user cannot create a production company. Please log in.");
        }

        if (companyRepository.existsByName(name)) {
            throw new IllegalArgumentException("A production company with this name already exists.");
        }

        Company company = new Company(name, description, founderId);
        try {
            companyRepository.save(company);
        } catch (OptimisticLockException ex) {
            log.warn("Company creation conflict: companyName={}", name);
            throw new IllegalStateException("Company changed concurrently. Please retry.", ex);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("A production company with this name already exists.");
        }

        CompanyOpenedEvent event = new CompanyOpenedEvent(company.getName(), founderId);
        eventPublisher.publish(event);

        log.info("Company created: companyName={}, founderId={}", company.getName(), founderId);
        return company.getName();
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

    public void revokePersonnel(String token, String companyName, UUID targetMemberId) {
        UUID revokerId = validateToken(token);
        
        // Authorization check
        if (revokerId == null) {
            throw new IllegalArgumentException("A guest user cannot create a production company. Please log in.");
        }

        // Check if the company exists
        Company company = companyRepository.findByName(companyName)
            .orElseThrow(() -> new IllegalArgumentException("Company not found"));

        // Check that company is active
        if (!company.isActive()) {
            throw new IllegalArgumentException("Cannot revoke personnel from a suspended or closed company");
        }

        RevokePersonnelEvent event = new RevokePersonnelEvent(company, revokerId, targetMemberId);
        eventPublisher.publish(event);

        log.info("Personnel revocation requested: company={}, revoker={}, target={}", 
            companyName, revokerId, targetMemberId);
    }

    public void relinquishOwnership(String token, String companyName) {
        UUID ownerId = validateToken(token);

        // Authorization check
        if (ownerId == null) {
            throw new IllegalArgumentException("A guest user cannot relinquish ownership. Please log in.");
        }

        // Check if the company exists
        Company company = companyRepository.findByName(companyName)
            .orElseThrow(() -> new IllegalArgumentException("Company not found"));

        // Check that company is active
        if (!company.isActive()) {
            throw new IllegalArgumentException("Cannot relinquish ownership from a suspended or closed company");
        }

        RelinquishOwnershipEvent event = new RelinquishOwnershipEvent(company, ownerId);
        eventPublisher.publish(event);

        log.info("Ownership relinquishment requested: company={}, owner={}", companyName, ownerId);
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

