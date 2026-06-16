package com.ticketing.application.services;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticketing.application.auth.ISessionTokenService;
import com.ticketing.application.dto.MemberSummaryDTO;
import com.ticketing.application.dto.PurchaseRecordDTO;
import com.ticketing.application.dto.SuspensionDTO;
import com.ticketing.application.dto.SystemAnalyticsDTO;
import com.ticketing.domain.admin.Admin;
import com.ticketing.domain.admin.IAdminRepository;
import com.ticketing.domain.company.Company;
import com.ticketing.domain.company.ICompanyRepository;
import com.ticketing.domain.member.IMemberRepository;
import com.ticketing.domain.member.Member;
import com.ticketing.domain.member.MemberDto;
import com.ticketing.domain.member.StaffAppointment;
import com.ticketing.domain.member.Suspension;
import com.ticketing.domain.member.request.*;
import com.ticketing.domain.member.response.*;
import com.ticketing.domain.order.CompletedPurchase;
import com.ticketing.domain.order.IOrderRepository;
import com.ticketing.infrastructure.PasswordEncryptionUtils;

/**
 * Application service for system-admin use cases.
 *
 * <p>V3-10 (#268): class default {@code @Transactional(readOnly = true)} for queries;
 * mutating use cases override with read-write {@code @Transactional}.
 */
@Service
@Transactional(readOnly = true)
public class AdminService {

    private static final String ADMIN_PERMISSION = "SYSTEM_ADMIN";
    private static final Logger log = LoggerFactory.getLogger(AdminService.class);

    private final IMemberRepository memberRepository;
    private final ICompanyRepository companyRepository;
    private final ISessionTokenService sessionTokenService;
    private final IAdminRepository adminRepository;
    private final IOrderRepository orderRepository;
    private final PasswordEncryptionUtils passwordEncryptionUtils = new PasswordEncryptionUtils();
    private final INotificationService notificationService;
    private final SystemAnalyticsCollector analyticsCollector;

    public AdminService(
            IMemberRepository memberRepository,
            ICompanyRepository companyRepository,
            ISessionTokenService sessionTokenService,
            IAdminRepository adminRepository,
            IOrderRepository orderRepository) {
        this(memberRepository, companyRepository, sessionTokenService, adminRepository, orderRepository, null, null);
    }

    public AdminService(
            IMemberRepository memberRepository,
            ICompanyRepository companyRepository,
            ISessionTokenService sessionTokenService,
            IAdminRepository adminRepository,
            IOrderRepository orderRepository,
            INotificationService notificationService) {
        this(memberRepository, companyRepository, sessionTokenService, adminRepository, orderRepository,
                notificationService, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public AdminService(
            IMemberRepository memberRepository,
            ICompanyRepository companyRepository,
            ISessionTokenService sessionTokenService,
            IAdminRepository adminRepository,
            IOrderRepository orderRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false) INotificationService notificationService,
            @org.springframework.beans.factory.annotation.Autowired(required = false) SystemAnalyticsCollector analyticsCollector) {
        if (memberRepository == null || companyRepository == null || sessionTokenService == null || adminRepository == null || orderRepository == null) {
            throw new IllegalArgumentException("Dependencies cannot be null");
        }
        this.memberRepository = memberRepository;
        this.companyRepository = companyRepository;
        this.sessionTokenService = sessionTokenService;
        this.adminRepository = adminRepository;
        this.orderRepository = orderRepository;
        this.notificationService = notificationService;
        this.analyticsCollector = analyticsCollector;
    }

    @Transactional
    public boolean registerAdmin(UUID adminId, String username, String email, String password) {
        if (adminId == null) {
            throw new IllegalArgumentException("adminId is required");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username is required");
        }
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email is required");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("password is required");
        }

        String normalizedUsername = username.trim();
        if (adminRepository.existsByUsername(normalizedUsername)) {
            log.info("Admin registration skipped: username already exists ({})", normalizedUsername);
            return false;
        }

        String hashedPassword = passwordEncryptionUtils.hashPassword(password);
        Admin admin = new Admin(adminId, normalizedUsername, email.trim().toLowerCase(), hashedPassword);
        adminRepository.save(admin);
        log.info("System admin registered: id={}, username={}", adminId, normalizedUsername);
        return true;
    }

    @Transactional
    public LoginResponse adminLogin(LoginRequest request, String guestToken) {
        if (request == null
                || request.username() == null || request.username().isBlank()
                || request.password() == null || request.password().isBlank()) {
            log.warn("Admin login failed: invalid credentials format");
            return LoginResponse.failure("Invalid credentials.");
        }
        if (guestToken == null || guestToken.isBlank() || !sessionTokenService.isValid(guestToken)) {
            log.warn("Admin login failed: invalid session token");
            return LoginResponse.failure("Invalid session token.");
        }
        if (sessionTokenService.extractMemberId(guestToken) != null) {
            log.warn("Admin login failed: session already member-bound");
            return LoginResponse.failure("Only guests can log in.");
        }

        String username = request.username().trim();
        Admin admin = adminRepository.findByUsername(username).orElse(null);
        if (admin == null) {
            log.warn("Admin login failed: unknown admin username ({})", username);
            return LoginResponse.failure("Invalid username or password.");
        }
        if (!passwordEncryptionUtils.matches(request.password(), admin.getEncryptedPassword())) {
            log.warn("Admin login failed: wrong password for username ({})", username);
            return LoginResponse.failure("Invalid username or password.");
        }

        UUID sessionId = sessionTokenService.extractSessionId(guestToken);
        String adminToken = sessionTokenService.generateMemberToken(
                sessionId,
                admin.getId(),
                Set.of(ADMIN_PERMISSION)
        );

        MemberDto adminAsDto = new MemberDto(admin.getId(), admin.getUsername(), admin.getEmail(), null, null);

        log.info("Admin logged in: id={}, username={}", admin.getId(), admin.getUsername());
        return LoginResponse.success(adminAsDto, adminToken);
    }

    @Transactional
    public synchronized void removeMember(String adminToken, UUID targetMemberId) {
        log.info("Admin remove member requested: targetMemberId={}", targetMemberId);
        if (!isAdmin(adminToken)) {
            log.warn("Admin remove member denied: missing system admin permission targetMemberId={}", targetMemberId);
            throw new SecurityException("System admin permission required");
        }

        Member target = memberRepository.findById(targetMemberId)
                .orElseThrow(() -> new IllegalArgumentException("Target member not found: " + targetMemberId));

        if (isSoleAdmin(targetMemberId)) {
            log.warn("Admin remove member denied: target is sole system admin targetMemberId={}", targetMemberId);
            throw new IllegalStateException("SoleAdminProtection: Cannot remove the last system admin");
        }

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

        memberRepository.delete(target);
        sessionTokenService.revokeMemberSessions(targetMemberId);
        log.info("Admin remove member completed: targetMemberId={}", targetMemberId);
    }

    @Transactional
    public Suspension suspendUser(String adminToken, UUID targetMemberId,
            Duration duration, String reason) {
        log.info("Admin suspend user requested: targetMemberId={}, permanent={}", targetMemberId, duration == null);
        if (!isAdmin(adminToken)) {
            throw new SecurityException("System admin permission required");
        }
        if (targetMemberId == null) {
            throw new IllegalArgumentException("targetMemberId is required");
        }
        Member target = memberRepository.findById(targetMemberId)
                .orElseThrow(() -> new IllegalArgumentException("Target member not found: " + targetMemberId));
        if (isSoleAdmin(targetMemberId)) {
            throw new IllegalStateException("Cannot suspend the last system admin");
        }
        UUID adminId = sessionTokenService.extractMemberId(adminToken);
        Suspension suspension = target.suspend(adminId != null ? adminId : UUID.randomUUID(), duration, reason);
        memberRepository.save(target);
        log.info("User suspended: targetMemberId={}, suspensionId={}, permanent={}, duration={}", targetMemberId, suspension.getSuspensionId(), suspension.isPermanent(), duration);
        if (notificationService != null) {
            notificationService.notify(targetMemberId.toString(),
                    "Your account has been suspended" + (reason != null ? ": " + reason : "."));
        }
        return suspension;
    }

    @Transactional
    public void cancelSuspension(String adminToken, UUID targetMemberId,
            UUID suspensionId) {
        log.info("Admin cancel suspension requested: targetMemberId={}, suspensionId={}", targetMemberId, suspensionId);
        if (!isAdmin(adminToken)) {
            throw new SecurityException("System admin permission required");
        }
        if (targetMemberId == null) throw new IllegalArgumentException("targetMemberId is required");
        if (suspensionId == null) throw new IllegalArgumentException("suspensionId is required");
        Member target = memberRepository.findById(targetMemberId)
                .orElseThrow(() -> new IllegalArgumentException("Target member not found: " + targetMemberId));
        target.cancelSuspension(suspensionId);
        memberRepository.save(target);
        log.info("Suspension cancelled: targetMemberId={}, suspensionId={}", targetMemberId, suspensionId);
        if (notificationService != null) {
            notificationService.notify(targetMemberId.toString(), "Your account suspension has been lifted.");
        }
    }

    public List<SuspensionDTO> listSuspensions(String adminToken, boolean activeOnly) {
        log.info("Admin list suspensions requested: activeOnly={}", activeOnly);
        if (!isAdmin(adminToken)) {
            throw new SecurityException("System admin permission required");
        }
        Instant now = Instant.now();
        List<Member> allMembers = memberRepository.findAll();
        List<SuspensionDTO> result = new ArrayList<>();
        for (Member member : allMembers) {
            for (Suspension suspension : member.getSuspensions()) {
                if (!activeOnly || suspension.isActive(now)) {
                    result.add(SuspensionDTO.from(suspension, member.getId(), member.getUsername(), now));
                }
            }
        }
        log.info("Admin list suspensions completed: resultCount={}", result.size());
        return result;
    }

    public List<MemberSummaryDTO> searchMembers(String adminToken, String usernameQuery) {
        if (!isAdmin(adminToken)) {
            throw new SecurityException("System admin permission required");
        }
        String query = usernameQuery == null ? "" : usernameQuery.trim().toLowerCase(Locale.ROOT);
        return memberRepository.findAll().stream()
                .filter(m -> query.isEmpty() || m.getUsername().toLowerCase(Locale.ROOT).contains(query))
                .map(m -> new MemberSummaryDTO(m.getId(), m.getUsername()))
                .collect(Collectors.toList());
    }

    public List<PurchaseRecordDTO> getGlobalPurchaseHistory(String adminToken, UUID buyerId, String companyName) {
        log.info("Admin global purchase history requested: buyerId={}, companyName={}", buyerId, companyName);
        if (!isAdmin(adminToken)) {
            log.warn("Admin global purchase history denied: missing system admin permission");
            throw new SecurityException("System admin permission required");
        }

        List<CompletedPurchase> purchases;
        if (buyerId != null) {
            purchases = orderRepository.findCompletedByMemberId(buyerId);
        } else if (companyName != null && !companyName.isBlank()) {
            purchases = orderRepository.findCompletedByCompanyName(companyName);
        } else {
            purchases = orderRepository.findAllCompleted();
        }
        log.info("Admin global purchase history completed: resultCount={}", purchases.size());

        return purchases.stream()
                .map(PurchaseRecordDTO::from)
                .collect(Collectors.toList());
    }

    public SystemAnalyticsDTO getSystemAnalytics(String adminToken) {
        log.info("Admin system analytics requested");
        if (!isAdmin(adminToken)) {
            log.warn("Admin system analytics denied: missing system admin permission");
            throw new SecurityException("System admin permission required");
        }
        if (analyticsCollector == null) {
            throw new IllegalStateException("System analytics are not available.");
        }
        SystemAnalyticsDTO analytics = SystemAnalyticsDTO.from(analyticsCollector.snapshot());
        log.info("Admin system analytics delivered: activeVisitors={}", analytics.activeVisitors());
        return analytics;
    }

    private boolean isAdmin(String token) {
        if (!sessionTokenService.isValid(token)) {
            return false;
        }
        Set<String> perms = sessionTokenService.extractPermissions(token);
        return perms != null && perms.contains(ADMIN_PERMISSION);
    }

    private boolean isSoleAdmin(UUID targetId) {
        boolean isTargetAdmin = adminRepository.findById(targetId).isPresent();
        if (!isTargetAdmin) {
            return false;
        }
        return adminRepository.findAll().size() <= 1;
    }
}
