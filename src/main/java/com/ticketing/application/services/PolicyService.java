package com.ticketing.application.services;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ticketing.application.auth.ISessionTokenService;
import com.ticketing.domain.company.Company;
import com.ticketing.domain.company.ICompanyRepository;
import com.ticketing.domain.event.AlwaysAllowPolicy;
import com.ticketing.domain.event.Event;
import com.ticketing.domain.event.IDiscountPolicy;
import com.ticketing.domain.event.IEventRepository;
import com.ticketing.domain.event.IPurchasePolicy;
import com.ticketing.domain.event.NoDiscountPolicy;
import com.ticketing.domain.exception.OptimisticLockException;
import com.ticketing.domain.member.IMemberRepository;
import com.ticketing.domain.member.ManagerPermission;
import com.ticketing.domain.member.Member;
import com.ticketing.domain.member.StaffAppointment;

/**
 * Application service for UC-II.4.3 — define and edit purchase/discount
 * policies at company or event scope.
 *
 * Authorization: Owner OR Manager with POLICY_MODIFICATION permission.
 * Company must be ACTIVE for any policy mutation.
 */
@org.springframework.stereotype.Service
public class PolicyService {

    private static final Logger log = LoggerFactory.getLogger(PolicyService.class);

    private final IEventRepository eventRepository;
    private final ICompanyRepository companyRepository;
    private final IMemberRepository memberRepository;
    private final ISessionTokenService sessionTokenService;

    public PolicyService(IEventRepository eventRepository,
                         ICompanyRepository companyRepository,
                         IMemberRepository memberRepository,
                         ISessionTokenService sessionTokenService) {
        this.eventRepository = eventRepository;
        this.companyRepository = companyRepository;
        this.memberRepository = memberRepository;
        this.sessionTokenService = sessionTokenService;
    }

    // ── Event-scoped purchase policy ────────────────────────────────

    public void setEventPurchasePolicy(String token, UUID eventId, IPurchasePolicy policy) {
        if (eventId == null) throw new IllegalArgumentException("eventId is required");
        if (policy == null) throw new IllegalArgumentException("policy is required");

        UUID memberId = authenticate(token);
        Event event = loadEvent(eventId);
        Company company = loadActiveCompany(event.getCompanyName());
        authorizePolicy(memberId, company.getName());

        event.setPurchasePolicy(policy);
        saveEvent(event);
        log.info("Event purchase policy updated: eventId={}, by={}", eventId, memberId);
    }

    public void removeEventPurchasePolicy(String token, UUID eventId) {
        if (eventId == null) throw new IllegalArgumentException("eventId is required");

        UUID memberId = authenticate(token);
        Event event = loadEvent(eventId);
        Company company = loadActiveCompany(event.getCompanyName());
        authorizePolicy(memberId, company.getName());

        event.setPurchasePolicy(new AlwaysAllowPolicy());
        saveEvent(event);
        log.info("Event purchase policy reset to default: eventId={}, by={}", eventId, memberId);
    }

    // ── Event-scoped discount policy ────────────────────────────────

    public void setEventDiscountPolicy(String token, UUID eventId, IDiscountPolicy policy) {
        if (eventId == null) throw new IllegalArgumentException("eventId is required");
        if (policy == null) throw new IllegalArgumentException("policy is required");

        UUID memberId = authenticate(token);
        Event event = loadEvent(eventId);
        Company company = loadActiveCompany(event.getCompanyName());
        authorizePolicy(memberId, company.getName());

        event.setDiscountPolicy(policy);
        saveEvent(event);
        log.info("Event discount policy updated: eventId={}, by={}", eventId, memberId);
    }

    public void removeEventDiscountPolicy(String token, UUID eventId) {
        if (eventId == null) throw new IllegalArgumentException("eventId is required");

        UUID memberId = authenticate(token);
        Event event = loadEvent(eventId);
        Company company = loadActiveCompany(event.getCompanyName());
        authorizePolicy(memberId, company.getName());

        event.setDiscountPolicy(new NoDiscountPolicy());
        saveEvent(event);
        log.info("Event discount policy reset to default: eventId={}, by={}", eventId, memberId);
    }

    // ── Company-scoped purchase policy ──────────────────────────────

    public void setCompanyPurchasePolicy(String token, String companyName, IPurchasePolicy policy) {
        if (companyName == null || companyName.isBlank()) throw new IllegalArgumentException("companyName is required");
        if (policy == null) throw new IllegalArgumentException("policy is required");

        UUID memberId = authenticate(token);
        Company company = loadActiveCompany(companyName);
        authorizePolicy(memberId, company.getName());

        company.setPurchasePolicy(policy);
        saveCompany(company);
        log.info("Company purchase policy updated: company={}, by={}", companyName, memberId);
    }

    public void removeCompanyPurchasePolicy(String token, String companyName) {
        if (companyName == null || companyName.isBlank()) throw new IllegalArgumentException("companyName is required");

        UUID memberId = authenticate(token);
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

        UUID memberId = authenticate(token);
        Company company = loadActiveCompany(companyName);
        authorizePolicy(memberId, company.getName());

        company.setDiscountPolicy(policy);
        saveCompany(company);
        log.info("Company discount policy updated: company={}, by={}", companyName, memberId);
    }

    public void removeCompanyDiscountPolicy(String token, String companyName) {
        if (companyName == null || companyName.isBlank()) throw new IllegalArgumentException("companyName is required");

        UUID memberId = authenticate(token);
        Company company = loadActiveCompany(companyName);
        authorizePolicy(memberId, company.getName());

        company.setDiscountPolicy(new NoDiscountPolicy());
        saveCompany(company);
        log.info("Company discount policy reset to default: company={}, by={}", companyName, memberId);
    }

    // ── Read helpers (for UI / query) ───────────────────────────────

    public IPurchasePolicy getEventPurchasePolicy(String token, UUID eventId) {
        authenticate(token);
        return loadEvent(eventId).getEventPurchasePolicy();
    }

    public IDiscountPolicy getEventDiscountPolicy(String token, UUID eventId) {
        authenticate(token);
        return loadEvent(eventId).getEventDiscountPolicy();
    }

    public IPurchasePolicy getCompanyPurchasePolicy(String token, String companyName) {
        authenticate(token);
        return loadCompany(companyName).getPurchasePolicy();
    }

    public IDiscountPolicy getCompanyDiscountPolicy(String token, String companyName) {
        authenticate(token);
        return loadCompany(companyName).getDiscountPolicy();
    }

    // ── Internal helpers ────────────────────────────────────────────

    private UUID authenticate(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Authentication token is required");
        }
        if (!sessionTokenService.isValid(token)) {
            throw new IllegalArgumentException("Invalid or expired authentication token");
        }
        UUID memberId = sessionTokenService.extractMemberId(token);
        if (memberId == null) {
            throw new SecurityException("Guests cannot manage policies");
        }
        return memberId;
    }

    private void authorizePolicy(UUID memberId, String companyName) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Member not found: " + memberId));
        StaffAppointment appt = member.getStaffAppointment(companyName);
        if (appt == null) {
            throw new SecurityException("Caller is not a staff member of company: " + companyName);
        }
        boolean allowed = appt.isOwner()
                || (appt.isManager() && appt.hasPermission(ManagerPermission.POLICY_MODIFICATION));
        if (!allowed) {
            throw new SecurityException("Insufficient permissions: POLICY_MODIFICATION required");
        }
    }

    private Event loadEvent(UUID eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Event not found: " + eventId));
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

    private void saveEvent(Event event) {
        try {
            eventRepository.save(event);
        } catch (OptimisticLockException ex) {
            log.warn("Event save conflict: eventId={}", event.getId());
            throw new IllegalStateException("Event changed concurrently. Please retry.", ex);
        }
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
