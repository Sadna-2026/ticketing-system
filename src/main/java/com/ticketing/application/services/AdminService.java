package com.ticketing.application.services;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.ticketing.application.auth.ISessionTokenService;
import com.ticketing.application.dto.PurchaseRecordDTO;
import com.ticketing.domain.admin.IAdminRepository;
import com.ticketing.domain.company.Company;
import com.ticketing.domain.company.ICompanyRepository;
import com.ticketing.domain.member.IMemberRepository;
import com.ticketing.domain.member.Member;
import com.ticketing.domain.member.StaffAppointment;
import com.ticketing.domain.member.Suspension;
import com.ticketing.domain.order.CompletedPurchase;
import com.ticketing.domain.order.IOrderRepository;

@Service
public class AdminService {

    private static final String ADMIN_PERMISSION = "SYSTEM_ADMIN";
    private static final Logger log = LoggerFactory.getLogger(AdminService.class);

    private final IMemberRepository memberRepository;
    private final ICompanyRepository companyRepository;
    private final ISessionTokenService sessionTokenService;
    private final IAdminRepository adminRepository;
    private final IOrderRepository orderRepository;

    public AdminService(
            IMemberRepository memberRepository,
            ICompanyRepository companyRepository,
            ISessionTokenService sessionTokenService,
            IAdminRepository adminRepository,
            IOrderRepository orderRepository
    ) {
        if (memberRepository == null || companyRepository == null || sessionTokenService == null || adminRepository == null || orderRepository == null) {
            throw new IllegalArgumentException("Dependencies cannot be null");
        }
        this.memberRepository = memberRepository;
        this.companyRepository = companyRepository;
        this.sessionTokenService = sessionTokenService;
        this.adminRepository = adminRepository;
        this.orderRepository = orderRepository;
    }

    public synchronized void removeMember(String adminToken, UUID targetMemberId) {
        log.info("Admin remove member requested: targetMemberId={}", targetMemberId);
        // 1. Validate Admin
        if (!isAdmin(adminToken)) {
            log.warn("Admin remove member denied: missing system admin permission targetMemberId={}", targetMemberId);
            throw new SecurityException("System admin permission required");
            
        }

        // 2. Validate Target
        Member target = memberRepository.findById(targetMemberId)
                .orElseThrow(() -> new IllegalArgumentException("Target member not found: " + targetMemberId));

        // 3. Sole Admin Check (Real)
        if (isSoleAdmin(targetMemberId)) {
            log.warn("Admin remove member denied: target is sole system admin targetMemberId={}", targetMemberId);
            throw new IllegalStateException("SoleAdminProtection: Cannot remove the last system admin");
        }

        // 4. Evaluate Company Integrity
        List<Company> allCompanies = companyRepository.getAll();
        for (Company company : allCompanies) {
            if (company.isActive() && target.hasStaffAppointment(company.getName(), StaffAppointment.StaffRole.OWNER)) {
                List<Member> companyMembers = memberRepository.findByCompanyAppointment(company.getName());
                long ownerCount = companyMembers.stream()
                        .filter(m -> m.hasStaffAppointment(company.getName(), StaffAppointment.StaffRole.OWNER))
                        .count();
                
                if (ownerCount <= 1) {
                    log.warn("Admin remove member denied: target is only owner targetMemberId={}, company={}",
        targetMemberId, company.getName());
                    throw new IllegalStateException("CompanyIntegrityBlock: Cannot remove the only owner of active company: " + company.getName());
                }
            }
        }

        // 5. Revoke Roles and Delete target member
        memberRepository.delete(target);

        // 6. Terminate Sessions
        sessionTokenService.revokeMemberSessions(targetMemberId);
        log.info("Admin remove member completed: targetMemberId={}", targetMemberId);
    }

    /**
     * UC-II.6.7 — System admin suspends a user.
     *
     * @param adminToken  valid admin session token
     * @param targetMemberId  the member to suspend
     * @param duration  suspension length, or null for permanent
     * @param reason  human-readable reason shown to the user
     * @return the created Suspension
     */
    public synchronized Suspension suspendUser(String adminToken, UUID targetMemberId,
                                                Duration duration, String reason) {
        log.info("Admin suspend user requested: targetMemberId={}, permanent={}",
                targetMemberId, duration == null);

        if (!isAdmin(adminToken)) {
            log.warn("Suspend user denied: caller is not a system admin");
            throw new SecurityException("System admin permission required");
        }

        if (targetMemberId == null) {
            throw new IllegalArgumentException("targetMemberId is required");
        }

        Member target = memberRepository.findById(targetMemberId)
                .orElseThrow(() -> {
                    log.warn("Suspend user denied: target member not found id={}", targetMemberId);
                    return new IllegalArgumentException("Target member not found: " + targetMemberId);
                });

        // Don't allow suspending the sole system admin
        if (isSoleAdmin(targetMemberId)) {
            log.warn("Suspend user denied: target is sole system admin id={}", targetMemberId);
            throw new IllegalStateException("Cannot suspend the last system admin");
        }

        UUID adminId = sessionTokenService.extractMemberId(adminToken);
        Instant now = Instant.now();
        Suspension suspension = new Suspension(
                adminId != null ? adminId : UUID.randomUUID(),
                now, duration, reason);

        target.addSuspension(suspension);
        memberRepository.save(target);

        log.info("User suspended: targetMemberId={}, suspensionId={}, permanent={}, duration={}",
                targetMemberId, suspension.getSuspensionId(),
                suspension.isPermanent(), duration);
        return suspension;
    }

    /**
     * UC-II.6.8 — System admin cancels (lifts) an active suspension.
     *
     * @param adminToken     valid admin session token
     * @param targetMemberId the suspended member
     * @param suspensionId   the specific suspension to cancel
     */
    public synchronized void cancelSuspension(String adminToken, UUID targetMemberId,
                                               UUID suspensionId) {
        log.info("Admin cancel suspension requested: targetMemberId={}, suspensionId={}",
                targetMemberId, suspensionId);

        if (!isAdmin(adminToken)) {
            log.warn("Cancel suspension denied: caller is not a system admin");
            throw new SecurityException("System admin permission required");
        }

        if (targetMemberId == null) {
            throw new IllegalArgumentException("targetMemberId is required");
        }
        if (suspensionId == null) {
            throw new IllegalArgumentException("suspensionId is required");
        }

        Member target = memberRepository.findById(targetMemberId)
                .orElseThrow(() -> {
                    log.warn("Cancel suspension denied: member not found id={}", targetMemberId);
                    return new IllegalArgumentException("Target member not found: " + targetMemberId);
                });

        // Find the suspension by ID
        Suspension suspension = target.getSuspensions().stream()
                .filter(s -> s.getSuspensionId().equals(suspensionId))
                .findFirst()
                .orElseThrow(() -> {
                    log.warn("Cancel suspension denied: suspension not found id={}", suspensionId);
                    return new IllegalArgumentException("Suspension not found: " + suspensionId);
                });

        // Must be currently active to cancel
        if (!suspension.isActive(Instant.now())) {
            log.warn("Cancel suspension denied: suspension is not active id={}", suspensionId);
            throw new IllegalStateException("Suspension is not currently active");
        }

        suspension.cancel();
        memberRepository.save(target);

        log.info("Suspension cancelled: targetMemberId={}, suspensionId={}",
                targetMemberId, suspensionId);
    }

    private boolean isAdmin(String token) {
        if (!sessionTokenService.isValid(token)) {
            return false;
        }
        Set<String> perms = sessionTokenService.extractPermissions(token);
        return perms != null && perms.contains(ADMIN_PERMISSION);
    }

    private boolean isSoleAdmin(UUID targetId) {
        // Check if the target member is also a system admin
        boolean isTargetAdmin = adminRepository.findById(targetId).isPresent();
        if (!isTargetAdmin) {
            return false;
        }

        // If target is an admin, check if they are the last one
        return adminRepository.findAll().size() <= 1;
    }

    public List<PurchaseRecordDTO> getGlobalPurchaseHistory(String adminToken, UUID buyerId, String companyName) {
        log.info("Admin global purchase history requested: buyerId={}, companyName={}", buyerId, companyName);
        // 1. Authorization
        if (!isAdmin(adminToken)) {
            log.warn("Admin global purchase history denied: missing system admin permission");
            throw new SecurityException("System admin permission required");
        }

        // 2. Fetch based on filters
        List<CompletedPurchase> purchases;
        if (buyerId != null) {
            purchases = orderRepository.findCompletedByMemberId(buyerId);
        } else if (companyName != null && !companyName.isBlank()) {
            purchases = orderRepository.findCompletedByCompanyName(companyName);
        } else {
            purchases = orderRepository.findAllCompleted();
        }
        log.info("Admin global purchase history completed: resultCount={}", purchases.size());

        // 3. Map to DTOs
        return purchases.stream()
                .map(PurchaseRecordDTO::from)
                .collect(Collectors.toList());
    }
}
