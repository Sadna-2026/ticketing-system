package com.ticketing.application.services;

import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ticketing.application.auth.ISessionTokenService;
import com.ticketing.domain.company.Company;
import com.ticketing.domain.company.ICompanyRepository;
import com.ticketing.domain.event.AlwaysAllowPolicy;
import com.ticketing.domain.event.CompanyOpenedEvent;
import com.ticketing.domain.event.IDiscountPolicy;
import com.ticketing.domain.event.IEventPublisher;
import com.ticketing.domain.event.IPurchasePolicy;
import com.ticketing.domain.event.NoDiscountPolicy;
import com.ticketing.domain.exception.OptimisticLockException;
import com.ticketing.domain.member.IMemberRepository;
import com.ticketing.domain.member.ManagerPermission;
import com.ticketing.domain.member.Member;
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
    private final IMemberRepository memberRepository;

    public CompanyService(
            ICompanyRepository companyRepository,
            IEventPublisher eventPublisher,
            ISessionTokenService sessionTokenService,
            IMemberRepository memberRepository
    ) {
        this.companyRepository = companyRepository;
        this.eventPublisher = eventPublisher;
        this.sessionTokenService = sessionTokenService;
        this.memberRepository = memberRepository;
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

    // ── Company-scoped purchase policy ──────────────────────────────

    public void setCompanyPurchasePolicy(String token, String companyName, IPurchasePolicy policy) {
        if (companyName == null || companyName.isBlank()) throw new IllegalArgumentException("companyName is required");
        if (policy == null) throw new IllegalArgumentException("policy is required");

        UUID memberId = authenticateMember(token);
        Company company = loadActiveCompany(companyName);
        authorizePolicy(memberId, company.getName());

        company.setPurchasePolicy(policy);
        saveCompany(company);
        log.info("Company purchase policy updated: company={}, by={}", companyName, memberId);
    }

    public void removeCompanyPurchasePolicy(String token, String companyName) {
        if (companyName == null || companyName.isBlank()) throw new IllegalArgumentException("companyName is required");

        UUID memberId = authenticateMember(token);
        Company company = loadActiveCompany(companyName);
        authorizePolicy(memberId, company.getName());

        company.setPurchasePolicy(new AlwaysAllowPolicy());
        saveCompany(company);
        log.info("Company purchase policy reset to default: company={}, by={}", companyName, memberId);
    }

    // ── Company-scoped discount policy ──────────────────────────────

    public void setCompanyDiscountPolicy(String token, String companyName, IDiscountPolicy policy) {
        if (companyName == null || companyName.isBlank()) throw new IllegalArgumentException("companyName is required");
        if (policy == null) throw new IllegalArgumentException("policy is required");

        UUID memberId = authenticateMember(token);
        Company company = loadActiveCompany(companyName);
        authorizePolicy(memberId, company.getName());

        company.setDiscountPolicy(policy);
        saveCompany(company);
        log.info("Company discount policy updated: company={}, by={}", companyName, memberId);
    }

    public void removeCompanyDiscountPolicy(String token, String companyName) {
        if (companyName == null || companyName.isBlank()) throw new IllegalArgumentException("companyName is required");

        UUID memberId = authenticateMember(token);
        Company company = loadActiveCompany(companyName);
        authorizePolicy(memberId, company.getName());

        company.setDiscountPolicy(new NoDiscountPolicy());
        saveCompany(company);
        log.info("Company discount policy reset to default: company={}, by={}", companyName, memberId);
    }

    // ── Discount stacking ────────────────────────────────────────────

    public void setDiscountStacking(String token, String companyName, boolean allow) {
        if (companyName == null || companyName.isBlank()) throw new IllegalArgumentException("companyName is required");

        UUID memberId = authenticateMember(token);
        Company company = loadActiveCompany(companyName);
        authorizePolicy(memberId, company.getName());

        company.setAllowDiscountStacking(allow);
        saveCompany(company);
        log.info("Company discount stacking set to {}: company={}, by={}", allow, companyName, memberId);
    }

    public boolean isDiscountStackingAllowed(String token, String companyName) {
        authenticateMember(token);
        return loadCompany(companyName).isAllowDiscountStacking();
    }

    // ── Read helpers (company policy queries) ───────────────────────

    public IPurchasePolicy getCompanyPurchasePolicy(String token, String companyName) {
        authenticateMember(token);
        return loadCompany(companyName).getPurchasePolicy();
    }

    public IDiscountPolicy getCompanyDiscountPolicy(String token, String companyName) {
        authenticateMember(token);
        return loadCompany(companyName).getDiscountPolicy();
    }

    // ── Internal helpers ────────────────────────────────────────────

    private UUID validateToken(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Authentication token is required");
        }
        if (!sessionTokenService.isValid(token)) {
            throw new IllegalArgumentException("Invalid or expired authentication token");
        }
        return sessionTokenService.extractMemberId(token);
    }

    private UUID authenticateMember(String token) {
        UUID memberId = validateToken(token);
        if (memberId == null) {
            throw new SecurityException("Guests cannot manage policies");
        }
        return memberId;
    }

    private void authorizePolicy(UUID memberId, String companyName) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Member not found: " + memberId));
        member.authorizePolicyModification(companyName);
    }

    private Company loadCompany(String companyName) {
        return companyRepository.findByName(companyName)
                .orElseThrow(() -> new IllegalArgumentException("Company not found: " + companyName));
    }

    private Company loadActiveCompany(String companyName) {
        Company company = loadCompany(companyName);
        if (!company.isActive()) {
            throw new IllegalStateException(
                    "Cannot modify policies on a suspended or closed company: " + companyName);
        }
        return company;
    }

    private void saveCompany(Company company) {
        try {
            companyRepository.save(company);
        } catch (OptimisticLockException ex) {
            log.warn("Company save conflict: company={}", company.getName());
            throw new IllegalStateException("Company changed concurrently. Please retry.", ex);
        }
    }
}

