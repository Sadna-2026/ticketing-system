package com.ticketing.application.services;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import com.ticketing.application.auth.ISessionTokenService;
import com.ticketing.application.dto.PurchaseRecordDTO;
import com.ticketing.application.dto.SalesReportDTO;
import com.ticketing.domain.company.Company;
import com.ticketing.domain.company.ICompanyRepository;
import com.ticketing.domain.member.IMemberRepository;
import com.ticketing.domain.member.ManagerPermission;
import com.ticketing.domain.member.Member;
import com.ticketing.domain.member.StaffAppointment;
import com.ticketing.domain.order.CompletedPurchase;
import com.ticketing.domain.order.IOrderRepository;

/**
 * Read-only application service for hierarchical sales reporting.
 *
 * <p>V3-10 (#268): the single use case is a read, so the whole class runs as one
 * read-only transaction.
 */
@org.springframework.stereotype.Service
@Transactional(readOnly = true)
public class CompletedPurchaseService {

    private static final Logger log = LoggerFactory.getLogger(CompletedPurchaseService.class);

    private final IOrderRepository orderRepository;
    private final ICompanyRepository companyRepository;
    private final IMemberRepository memberRepository;
    private final ISessionTokenService sessionTokenService;

    @org.springframework.beans.factory.annotation.Autowired
    public CompletedPurchaseService(
            IOrderRepository orderRepository,
            ICompanyRepository companyRepository,
            IMemberRepository memberRepository,
            ISessionTokenService sessionTokenService) {
        if (orderRepository == null) throw new IllegalArgumentException("orderRepository is required");
        if (companyRepository == null) throw new IllegalArgumentException("companyRepository is required");
        if (memberRepository == null) throw new IllegalArgumentException("memberRepository is required");
        if (sessionTokenService == null) throw new IllegalArgumentException("sessionTokenService is required");

        this.orderRepository = orderRepository;
        this.companyRepository = companyRepository;
        this.memberRepository = memberRepository;
        this.sessionTokenService = sessionTokenService;
    }

    public SalesReportDTO getHierarchicalSalesReport(String token, String companyName) {
        UUID requesterId = requireMember(token);

        Company company = companyRepository.findByName(companyName)
                .orElseThrow(() -> {
                    log.warn("Sales report request denied: company={}, by={}", companyName, requesterId);
                    return new IllegalArgumentException("Company not found: " + companyName);
                });

        Member requester = memberRepository.findById(requesterId)
                .orElseThrow(() -> {
                    log.warn("Sales report request denied: company={}, by={}", companyName, requesterId);
                    return new IllegalArgumentException("Member not found: " + requesterId);
                });

        StaffAppointment requesterAppt = requester.getStaffAppointment(company.getName());
        if (requesterAppt == null) {
            log.warn("Sales report request denied: company={}, by={}", companyName, requesterId);
            throw new SecurityException(
                    "Member is not appointed to company: " + company.getName());
        }

        boolean authorized = requesterAppt.isOwner() ||
                (requesterAppt.isManager() && requesterAppt.hasPermission(ManagerPermission.VIEW_REPORTS));

        if (!authorized) {
            log.warn("Sales report request denied: company={}, by={}", companyName, requesterId);
            throw new SecurityException(
                    "Viewing sales report requires Owner role or (Manager + VIEW_REPORTS permission)");
        }

        Set<UUID> subtreeMemberIds = collectSubtreeMembers(requesterId, company.getName());

        List<CompletedPurchase> allCompanyPurchases = orderRepository.findCompletedByCompanyName(company.getName());

        List<PurchaseRecordDTO> subtreePurchases = allCompanyPurchases.stream()
                .filter(p -> subtreeMemberIds.contains(p.memberId()))
                .map(PurchaseRecordDTO::from)
                .toList();

        BigDecimal totalRevenue = subtreePurchases.stream()
                .map(PurchaseRecordDTO::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        int totalPurchases = subtreePurchases.size();

        SalesReportDTO report = new SalesReportDTO(
                company.getName(),
                requesterId,
                new ArrayList<>(subtreePurchases),
                totalRevenue,
                totalPurchases
        );

        log.info("Hierarchical sales report generated: company={}, requester={}, purchaseCount={}",
                company.getName(), requesterId, totalPurchases);

        return report;
    }

    private Set<UUID> collectSubtreeMembers(UUID rootMemberId, String companyName) {
        Set<UUID> subtree = new HashSet<>();
        List<UUID> queue = new ArrayList<>();

        queue.add(rootMemberId);
        subtree.add(rootMemberId);

        int index = 0;
        while (index < queue.size()) {
            UUID currentMemberId = queue.get(index);
            index++;

            Member currentMember = memberRepository.findById(currentMemberId).orElse(null);
            if (currentMember == null) {
                continue;
            }

            StaffAppointment currentAppt = currentMember.getStaffAppointment(companyName);
            if (currentAppt == null) {
                continue;
            }

            for (UUID subordinateId : currentAppt.getAppointedStaffMemberIds()) {
                if (!subtree.contains(subordinateId)) {
                    subtree.add(subordinateId);
                    queue.add(subordinateId);
                }
            }
        }

        return subtree;
    }

    private UUID requireMember(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Authentication token is required");
        }
        if (!sessionTokenService.isValid(token)) {
            throw new IllegalArgumentException("Invalid or expired authentication token");
        }
        UUID memberId = sessionTokenService.extractMemberId(token);
        if (memberId == null) {
            throw new SecurityException("Guests cannot view sales reports");
        }
        return memberId;
    }
}
