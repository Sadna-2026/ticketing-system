package com.ticketing.application;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ticketing.application.auth.ISessionTokenService;
import com.ticketing.domain.company.Company;
import com.ticketing.domain.company.ICompanyRepository;
import com.ticketing.domain.member.IMemberRepository;
import com.ticketing.domain.member.ManagerPermission;
import com.ticketing.domain.member.Member;
import com.ticketing.domain.member.StaffAppointment;
import com.ticketing.domain.order.ICompletedPurchaseRepository;

/**
 * Read-side history queries for a company's completed purchases.
 * Caller must be Owner/Founder, or a Manager holding VIEW_REPORTS.
 */
public class CompanyHistoryService {

    private static final Logger log = LoggerFactory.getLogger(CompanyHistoryService.class);

    private final ICompanyRepository companyRepository;
    private final IMemberRepository memberRepository;
    private final ICompletedPurchaseRepository purchaseRepository;
    private final ISessionTokenService sessionTokenService;

    public CompanyHistoryService(ICompanyRepository companyRepository,
                                 IMemberRepository memberRepository,
                                 ICompletedPurchaseRepository purchaseRepository,
                                 ISessionTokenService sessionTokenService) {
        this.companyRepository = companyRepository;
        this.memberRepository = memberRepository;
        this.purchaseRepository = purchaseRepository;
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
            throw new SecurityException(
                    "Viewing purchase history requires Owner role or VIEW_REPORTS permission");
        }

        log.info("Purchase history requested: company={}, by={}", company.getName(), memberId);
        return purchaseRepository.findByCompanyName(company.getName()).stream()
                .map(PurchaseRecordDTO::from)
                .toList();
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
            throw new SecurityException("Guests cannot view purchase history");
        }
        return id;
    }
}
