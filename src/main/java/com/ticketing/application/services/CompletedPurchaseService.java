package com.ticketing.application.services;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * Application service for completed purchases (paid orders).
 * Provides hierarchical sales reports scoped by org chart authority.
 */
public class CompletedPurchaseService {

    private static final Logger log = LoggerFactory.getLogger(CompletedPurchaseService.class);

    private final IOrderRepository orderRepository;
    private final ICompanyRepository companyRepository;
    private final IMemberRepository memberRepository;
    private final ISessionTokenService sessionTokenService;

    public CompletedPurchaseService(
            IOrderRepository orderRepository,
            ICompanyRepository companyRepository,
            IMemberRepository memberRepository,
            ISessionTokenService sessionTokenService
    ) {
        if (orderRepository == null) throw new IllegalArgumentException("orderRepository is required");
        if (companyRepository == null) throw new IllegalArgumentException("companyRepository is required");
        if (memberRepository == null) throw new IllegalArgumentException("memberRepository is required");
        if (sessionTokenService == null) throw new IllegalArgumentException("sessionTokenService is required");

        this.orderRepository = orderRepository;
        this.companyRepository = companyRepository;
        this.memberRepository = memberRepository;
        this.sessionTokenService = sessionTokenService;
    }

    /**
     * Generates a hierarchical sales report for a company.
     * Includes all purchases made by the requester and their subordinates.
     * Authorization: OWNER or (MANAGER + VIEW_REPORTS)
     *
     * @param token       authentication token of the requester
     * @param companyName name of the company
     * @return sales report including all subtree purchases
     * @throws IllegalArgumentException if company not found
     * @throws SecurityException        if not authorized or guest token
     */
    public SalesReportDTO getHierarchicalSalesReport(String token, String companyName) {
        // Step 1: Validate token (reject guest)
        UUID requesterId = requireMember(token);

        // Step 2: Find company
        Company company = companyRepository.findByName(companyName)
                .orElseThrow(() -> new IllegalArgumentException("Company not found: " + companyName));

        // Step 3: Load caller's StaffAppointment and check authorization
        Member requester = memberRepository.findById(requesterId)
                .orElseThrow(() -> new IllegalArgumentException("Member not found: " + requesterId));

        StaffAppointment requesterAppt = requester.getStaffAppointment(company.getName());
        if (requesterAppt == null) {
            throw new SecurityException(
                    "Member is not appointed to company: " + company.getName());
        }

        boolean authorized = requesterAppt.isOwner() ||
                (requesterAppt.isManager() && requesterAppt.hasPermission(ManagerPermission.VIEW_REPORTS));

        if (!authorized) {
            throw new SecurityException(
                    "Viewing sales report requires Owner role or (Manager + VIEW_REPORTS permission)");
        }

        // Step 4: Collect subtree member IDs via BFS
        Set<UUID> subtreeMemberIds = collectSubtreeMembers(requesterId, company.getName(), requesterAppt);

        // Step 5: Fetch all CompletedPurchase for companyName
        List<CompletedPurchase> allCompanyPurchases = orderRepository.findCompletedByCompanyName(company.getName());

        // Step 6: Filter purchases to those from the subtree
        List<PurchaseRecordDTO> subtreePurchases = allCompanyPurchases.stream()
                .filter(p -> subtreeMemberIds.contains(p.memberId()))
                .map(PurchaseRecordDTO::from)
                .toList();

        // Calculate totals
        BigDecimal totalRevenue = subtreePurchases.stream()
                .map(PurchaseRecordDTO::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        int totalPurchases = subtreePurchases.size();

        // Step 7: Build and return SalesReportDTO
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

    /**
     * Collects all member IDs in the subtree rooted at the given member (including the member itself).
     * Uses BFS to traverse the organizational hierarchy.
     *
     * @param rootMemberId root of the subtree
     * @param companyName  company name for context
     * @param rootAppt     staff appointment of the root member
     * @return set of member IDs in the subtree
     */
    private Set<UUID> collectSubtreeMembers(UUID rootMemberId, String companyName, StaffAppointment rootAppt) {
        Set<UUID> subtree = new HashSet<>();
        List<UUID> queue = new ArrayList<>();

        // Start with the requester
        queue.add(rootMemberId);
        subtree.add(rootMemberId);

        // BFS to collect all subordinates
        int index = 0;
        while (index < queue.size()) {
            UUID currentMemberId = queue.get(index);
            index++;

            // Get the current member's staff appointment to find appointed subordinates
            Member currentMember = memberRepository.findById(currentMemberId).orElse(null);
            if (currentMember == null) {
                continue;
            }

            StaffAppointment currentAppt = currentMember.getStaffAppointment(companyName);
            if (currentAppt == null) {
                continue;
            }

            // Add all directly appointed subordinates to the queue
            for (UUID subordinateId : currentAppt.getAppointedStaffMemberIds()) {
                if (!subtree.contains(subordinateId)) {
                    subtree.add(subordinateId);
                    queue.add(subordinateId);
                }
            }
        }

        return subtree;
    }

    /**
     * Validates token and extracts member ID. Rejects guest tokens.
     *
     * @param token authentication token
     * @return member ID
     * @throws IllegalArgumentException if token is null/blank/invalid
     * @throws SecurityException        if token is guest (no member ID)
     */
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
