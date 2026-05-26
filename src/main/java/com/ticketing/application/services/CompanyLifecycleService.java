package com.ticketing.application.services;

import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ticketing.application.auth.ISessionTokenService;
import com.ticketing.domain.services.CompanyLifecycleDomainService;

/**
 * Suspend / reopen / permanent-close operations for production companies.
 * Founder OR System Admin actor (the latter only for adminClose).
 */
@org.springframework.stereotype.Service
public class CompanyLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(CompanyLifecycleService.class);
    private static final String ADMIN_PERMISSION = "SYSTEM_ADMIN";

    private final ISessionTokenService sessionTokenService;
    private final CompanyLifecycleDomainService domainService;

    public CompanyLifecycleService(com.ticketing.domain.company.ICompanyRepository companyRepository,
                                   com.ticketing.domain.event.IEventRepository eventRepository,
                                   com.ticketing.domain.member.IMemberRepository memberRepository,
                                   com.ticketing.domain.order.IOrderRepository orderRepository,
                                   com.ticketing.domain.gateway.IPaymentGateway paymentGateway,
                                   ISessionTokenService sessionTokenService) {
        this.sessionTokenService = sessionTokenService;
        this.domainService = new CompanyLifecycleDomainService(companyRepository, eventRepository, memberRepository, orderRepository, paymentGateway);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public CompanyLifecycleService(ISessionTokenService sessionTokenService,
                                   CompanyLifecycleDomainService domainService) {
        this.sessionTokenService = sessionTokenService;
        this.domainService = domainService;
    }

    public void suspendCompany(String token, String companyName) {
        UUID memberId = requireMember(token);
        domainService.suspendCompany(memberId, companyName);
    }

    public void reopenCompany(String token, String companyName) {
        UUID memberId = requireMember(token);
        domainService.reopenCompany(memberId, companyName);
    }

    public void permanentCloseByFounder(String token, String companyName) {
        UUID memberId = requireMember(token);
        domainService.permanentCloseByFounder(memberId, companyName);
    }

    public void permanentCloseByAdmin(String token, String companyName) {
        requireMember(token);
        if (!isAdmin(token)) {
            log.warn("Permanent close by admin ignored: insufficient permissions");
            throw new SecurityException("System admin permission required");
        }
        domainService.permanentCloseByAdmin(companyName);
    }

    public void retryPendingRefunds(String companyName) {
        // No user authorization is performed here in the original code,
        // it acts as a system level retry.
        domainService.retryPendingRefunds(companyName);
    }

    private UUID requireMember(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Authentication token is required");
        }
        if (!sessionTokenService.isValid(token)) {
            throw new IllegalArgumentException("Invalid or expired authentication token");
        }
        UUID id = sessionTokenService.extractMemberId(token);
        if (id == null) {
            throw new SecurityException("Guests cannot perform this action");
        }
        return id;
    }

    private boolean isAdmin(String token) {
        Set<String> perms = sessionTokenService.extractPermissions(token);
        return perms != null && perms.contains(ADMIN_PERMISSION);
    }
}

