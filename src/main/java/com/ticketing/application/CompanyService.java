package com.ticketing.application;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ticketing.application.auth.ISessionTokenService;
import com.ticketing.domain.company.Company;
import com.ticketing.domain.company.ICompanyRepository;
import com.ticketing.domain.event.CompanyOpenedEvent;
import com.ticketing.domain.event.IEventPublisher;

public class CompanyService {
    private static final Logger log = LoggerFactory.getLogger(CompanyService.class);

    private final ICompanyRepository companyRepository;
    private final IEventPublisher eventPublisher;
    private final ISessionTokenService sessionTokenService;
    private final com.ticketing.domain.member.IMemberRepository memberRepository;
    private final com.ticketing.domain.member.IRoleAppointmentOfferRepository offerRepository;
    private final Object lock = new Object();

    public CompanyService(
            ICompanyRepository companyRepository, 
            IEventPublisher eventPublisher, 
            ISessionTokenService sessionTokenService,
            com.ticketing.domain.member.IMemberRepository memberRepository,
            com.ticketing.domain.member.IRoleAppointmentOfferRepository offerRepository
    ) {
        this.companyRepository = companyRepository;
        this.eventPublisher = eventPublisher;
        this.sessionTokenService = sessionTokenService;
        this.memberRepository = memberRepository;
        this.offerRepository = offerRepository;
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

            Company company = new Company(name, description, founderId);
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
            com.ticketing.domain.member.StaffAppointment.StaffRole role, 
            java.util.Set<com.ticketing.domain.member.ManagerPermission> permissions
    ) {
        UUID appointerId = validateToken(token);
        
        com.ticketing.domain.member.Member appointer = memberRepository.findById(appointerId)
            .orElseThrow(() -> new IllegalArgumentException("Appointer not found"));
        com.ticketing.domain.member.Member target = memberRepository.findById(targetMemberId)
            .orElseThrow(() -> new IllegalArgumentException("Target member not found"));
        Company company = companyRepository.findByName(companyName)
            .orElseThrow(() -> new IllegalArgumentException("Company not found"));

        com.ticketing.domain.member.RoleAppointmentOffer offer = company.offerRole(appointer, target, role, permissions);
        offerRepository.save(offer);

        // Publish event for notification
        com.ticketing.domain.member.communication.RoleAppointmentOfferedEvent event = 
            new com.ticketing.domain.member.communication.RoleAppointmentOfferedEvent(offer.getOfferId(), companyName, targetMemberId);
        eventPublisher.publish(event);

        log.info("Role appointment offered: company={}, appointer={}, target={}, role={}", 
            companyName, appointerId, targetMemberId, role);
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
