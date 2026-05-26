package com.ticketing.domain.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.ticketing.application.auth.ISessionTokenService;
import com.ticketing.application.dto.PurchaseRecordDTO;
import com.ticketing.domain.company.Company;
import com.ticketing.domain.company.ICompanyRepository;
import com.ticketing.domain.member.IMemberRepository;
import com.ticketing.domain.member.ManagerPermission;
import com.ticketing.domain.member.Member;
import com.ticketing.domain.member.StaffAppointment;
import com.ticketing.domain.order.IOrderRepository;

@Service
public class CompanyHistoryDomainService {

    private static final Logger log = LoggerFactory.getLogger(CompanyHistoryDomainService.class);

    private final ICompanyRepository companyRepository;
    private final IMemberRepository memberRepository;
    private final IOrderRepository orderRepository;
    private final ISessionTokenService sessionTokenService;

    public CompanyHistoryDomainService(ICompanyRepository companyRepository,
                                       IMemberRepository memberRepository,
                                       IOrderRepository orderRepository,
                                       ISessionTokenService sessionTokenService) {
        this.companyRepository = companyRepository;
        this.memberRepository = memberRepository;
        this.orderRepository = orderRepository;
        this.sessionTokenService = sessionTokenService;
    }

    public List<PurchaseRecordDTO> getPurchaseHistory(String token, String companyName) {
        UUID memberId = requireMember(token);
        Company company = companyRepository.findByName(companyName)
                .orElseThrow(() -> new IllegalArgumentException("Company not found: " + companyName));
        Member m = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Member not found: " + memberId));
        StaffAppointment appt = m.getStaffAppointment(company.getName());
        boolean allowed = appt != null
                && (appt.isOwner() || appt.hasPermission(ManagerPermission.VIEW_REPORTS));
        if (!allowed) {
            log.warn("Purchase history request denied: company={}, by={}", company.getName(), memberId);
            throw new SecurityException(
                    "Viewing purchase history requires Owner role or VIEW_REPORTS permission");
        }

        log.info("Purchase history requested: company={}, by={}", company.getName(), memberId);
        return orderRepository.findCompletedByCompanyName(company.getName()).stream()
                .map(PurchaseRecordDTO::from)
                .toList();
    }

    private UUID requireMember(String token) {
        if (token == null || token.isBlank()) {
            log.warn("Purchase history request denied: missing token");
            throw new IllegalArgumentException("Authentication token is required");
        }
        if (!sessionTokenService.isValid(token)) {
            log.warn("Purchase history request denied: invalid token");
            throw new IllegalArgumentException("Invalid or expired authentication token");
        }
        UUID id = sessionTokenService.extractMemberId(token);
        if (id == null) {
            log.warn("Purchase history request denied: guest token used");
            throw new SecurityException("Guests cannot view purchase history");
        }
        return id;
    }
}
