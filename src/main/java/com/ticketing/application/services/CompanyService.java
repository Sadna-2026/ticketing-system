package com.ticketing.application.services;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import com.ticketing.application.auth.ISessionTokenService;
import com.ticketing.application.dto.CompanyPublicDTO;
import com.ticketing.application.dto.CompanySummaryDTO;
import com.ticketing.application.dto.EventSummaryDTO;
import com.ticketing.application.dto.PurchaseRecordDTO;
import com.ticketing.domain.company.Company;
import com.ticketing.domain.company.CompanyStatus;
import com.ticketing.domain.company.ICompanyRepository;
import com.ticketing.domain.event.AlwaysAllowPolicy;
import com.ticketing.domain.event.CompanyOpenedEvent;
import com.ticketing.domain.event.Event;
import com.ticketing.domain.event.EventStatus;
import com.ticketing.domain.event.IDiscountPolicy;
import com.ticketing.domain.event.IEventPublisher;
import com.ticketing.domain.event.IEventRepository;
import com.ticketing.domain.event.IPurchasePolicy;
import com.ticketing.domain.event.NoDiscountPolicy;
import com.ticketing.domain.exception.OptimisticLockException;
import com.ticketing.domain.gateway.IPaymentGateway;
import com.ticketing.domain.gateway.RefundResult;
import com.ticketing.domain.member.IMemberRepository;
import com.ticketing.domain.member.ManagerPermission;
import com.ticketing.domain.member.Member;
import com.ticketing.domain.member.StaffAppointment;
import com.ticketing.domain.member.communication.ManagerPermissionsChangedEvent;
import com.ticketing.domain.member.communication.RelinquishOwnershipEvent;
import com.ticketing.domain.member.communication.RevokePersonnelEvent;
import com.ticketing.domain.member.communication.RoleAppointmentOfferRequestedEvent;
import com.ticketing.domain.member.communication.RoleAppointmentOfferResponseEvent;
import com.ticketing.domain.order.CompletedPurchase;
import com.ticketing.domain.order.IOrderRepository;

/**
 * Application service for company lifecycle, staffing and company-scoped policies.
 *
 * <p>V3-10 (#268): each public use-case method is one atomic transaction. The class
 * default is {@code @Transactional(readOnly = true)}; mutating use cases override it
 * with a read-write {@code @Transactional}. Use cases that only publish a domain
 * event (role-appointment workflow) are still annotated read-write so the
 * synchronous listener's writes commit/roll back together with the publish.
 */
@org.springframework.stereotype.Service
@Transactional(readOnly = true)
public class CompanyService {
    private static final Logger log = LoggerFactory.getLogger(CompanyService.class);
    private static final String ADMIN_PERMISSION = "SYSTEM_ADMIN";

    private final ICompanyRepository companyRepository;
    private final IEventPublisher eventPublisher;
    private final ISessionTokenService sessionTokenService;
    private final IMemberRepository memberRepository;
    private final IEventRepository eventRepository;
    private final IOrderRepository orderRepository;
    private final IPaymentGateway paymentGateway;
    private final INotificationService notificationService;

    private final ConcurrentHashMap<String, Deque<RefundJob>> pendingRefunds = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Object> companyLocks = new ConcurrentHashMap<>();

    public CompanyService(
            ICompanyRepository companyRepository,
            IEventPublisher eventPublisher,
            ISessionTokenService sessionTokenService,
            IMemberRepository memberRepository
    ) {
        this(companyRepository, eventPublisher, sessionTokenService, memberRepository, null, null, null, null);
    }

    public CompanyService(
            ICompanyRepository companyRepository,
            IEventPublisher eventPublisher,
            ISessionTokenService sessionTokenService,
            IMemberRepository memberRepository,
            IEventRepository eventRepository,
            IOrderRepository orderRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false) IPaymentGateway paymentGateway
    ) {
        this(companyRepository, eventPublisher, sessionTokenService, memberRepository,
                eventRepository, orderRepository, paymentGateway, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public CompanyService(
            ICompanyRepository companyRepository,
            IEventPublisher eventPublisher,
            ISessionTokenService sessionTokenService,
            IMemberRepository memberRepository,
            IEventRepository eventRepository,
            IOrderRepository orderRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false) IPaymentGateway paymentGateway,
            @org.springframework.beans.factory.annotation.Autowired(required = false) INotificationService notificationService
    ) {
        this.companyRepository = companyRepository;
        this.eventPublisher = eventPublisher;
        this.sessionTokenService = sessionTokenService;
        this.memberRepository = memberRepository;
        this.eventRepository = eventRepository;
        this.orderRepository = orderRepository;
        this.paymentGateway = paymentGateway;
        this.notificationService = notificationService;
    }

    // ── Company creation ───────────────────────────────────────────────

    @Transactional
    public String openProductionCompany(String token, String name, String description) {
        UUID founderId = validateToken(token);

        log.info("Creating company: founderId={}, name={}", founderId, name);

        if (founderId == null) {
            throw new IllegalArgumentException("A guest user cannot create a production company. Please log in.");
        }
        rejectIfSuspended(founderId);

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

    @Transactional
    public void offerRoleAppointment(
            String token,
            String companyName,
            UUID targetMemberId,
            StaffAppointment.StaffRole role,
            Set<com.ticketing.domain.member.ManagerPermission> permissions
    ) {
        UUID appointerId = validateToken(token);
        if (appointerId == null) {
            throw new IllegalArgumentException("A guest user cannot offer role appointments. Please log in.");
        }
        rejectIfSuspended(appointerId);

        if (!companyRepository.existsByName(companyName)) {
            throw new IllegalArgumentException("Company not found");
        }

        RoleAppointmentOfferRequestedEvent event =
            new RoleAppointmentOfferRequestedEvent(appointerId, targetMemberId, companyName, role, permissions);
        eventPublisher.publish(event);

        log.info("Role appointment requested: company={}, appointer={}, target={}, role={}",
            companyName, appointerId, targetMemberId, role);
    }

    @Transactional
    public void changeManagerPermissions(
            String token,
            String companyName,
            UUID targetMemberId,
            Set<com.ticketing.domain.member.ManagerPermission> newPermissions
    ) {
        UUID callerId = validateToken(token);

        if (callerId == null) {
            throw new IllegalArgumentException("A guest user cannot change manager permissions. Please log in.");
        }
        rejectIfSuspended(callerId);

        if (targetMemberId == null) {
            throw new IllegalArgumentException("Target member ID is required");
        }

        if (!companyRepository.existsByName(companyName)) {
            throw new IllegalArgumentException("Company not found: " + companyName);
        }

        ManagerPermissionsChangedEvent event =
            new ManagerPermissionsChangedEvent(callerId, targetMemberId, companyName, newPermissions);
        eventPublisher.publish(event);

        log.info("Manager permissions change requested: company={}, caller={}, target={}",
            companyName, callerId, targetMemberId);
    }

    @Transactional
    public void respondToRoleAppointment(
            String token,
            UUID appointmentOfferId,
            boolean accepted
    ) {
        UUID responderId = validateToken(token);
        if (responderId == null) {
            throw new IllegalArgumentException("A guest user cannot respond to role appointments. Please log in.");
        }
        rejectIfSuspended(responderId);

        RoleAppointmentOfferResponseEvent event = new RoleAppointmentOfferResponseEvent(
            appointmentOfferId,
            responderId,
            accepted
        );
        eventPublisher.publish(event);

        log.info("Role appointment response handled: responder={}, offer={}, accepted={}",
            responderId, appointmentOfferId, accepted);
    }

    @Transactional
    public void revokePersonnel(String token, String companyName, UUID targetMemberId) {
        UUID revokerId = validateToken(token);

        if (revokerId == null) {
            throw new IllegalArgumentException("A guest user cannot create a production company. Please log in.");
        }
        rejectIfSuspended(revokerId);

        Company company = companyRepository.findByName(companyName)
            .orElseThrow(() -> new IllegalArgumentException("Company not found"));

        if (!company.isActive()) {
            throw new IllegalArgumentException("Cannot revoke personnel from a suspended or closed company");
        }

        RevokePersonnelEvent event = new RevokePersonnelEvent(company, revokerId, targetMemberId);
        eventPublisher.publish(event);

        log.info("Personnel revocation requested: company={}, revoker={}, target={}",
            companyName, revokerId, targetMemberId);
    }

    @Transactional
    public void relinquishOwnership(String token, String companyName) {
        UUID ownerId = validateToken(token);

        if (ownerId == null) {
            throw new IllegalArgumentException("A guest user cannot relinquish ownership. Please log in.");
        }
        rejectIfSuspended(ownerId);

        Company company = companyRepository.findByName(companyName)
            .orElseThrow(() -> new IllegalArgumentException("Company not found"));

        if (!company.isActive()) {
            throw new IllegalArgumentException("Cannot relinquish ownership from a suspended or closed company");
        }

        RelinquishOwnershipEvent event = new RelinquishOwnershipEvent(company, ownerId);
        eventPublisher.publish(event);

        log.info("Ownership relinquishment requested: company={}, owner={}", companyName, ownerId);
    }

    // ── Company-scoped purchase policy ──────────────────────────────

    @Transactional
    public void setCompanyPurchasePolicy(String token, String companyName, IPurchasePolicy policy) {
        if (companyName == null || companyName.isBlank()) throw new IllegalArgumentException("companyName is required");
        if (policy == null) throw new IllegalArgumentException("policy is required");

        UUID memberId = authenticateMember(token);
        rejectIfSuspended(memberId);
        Company company = loadActiveCompanyForPolicy(companyName);
        authorizePolicy(memberId, company.getName());

        company.setPurchasePolicy(policy);
        saveCompany(company);
        log.info("Company purchase policy updated: company={}, by={}", companyName, memberId);
    }

    @Transactional
    public void removeCompanyPurchasePolicy(String token, String companyName) {
        if (companyName == null || companyName.isBlank()) throw new IllegalArgumentException("companyName is required");

        UUID memberId = authenticateMember(token);
        rejectIfSuspended(memberId);
        Company company = loadActiveCompanyForPolicy(companyName);
        authorizePolicy(memberId, company.getName());

        company.setPurchasePolicy(new AlwaysAllowPolicy());
        saveCompany(company);
        log.info("Company purchase policy reset to default: company={}, by={}", companyName, memberId);
    }

    // ── Company-scoped discount policy ──────────────────────────────

    @Transactional
    public void setCompanyDiscountPolicy(String token, String companyName, IDiscountPolicy policy) {
        if (companyName == null || companyName.isBlank()) throw new IllegalArgumentException("companyName is required");
        if (policy == null) throw new IllegalArgumentException("policy is required");

        UUID memberId = authenticateMember(token);
        rejectIfSuspended(memberId);
        Company company = loadActiveCompanyForPolicy(companyName);
        authorizePolicy(memberId, company.getName());

        company.setDiscountPolicy(policy);
        saveCompany(company);
        log.info("Company discount policy updated: company={}, by={}", companyName, memberId);
    }

    @Transactional
    public void removeCompanyDiscountPolicy(String token, String companyName) {
        if (companyName == null || companyName.isBlank()) throw new IllegalArgumentException("companyName is required");

        UUID memberId = authenticateMember(token);
        rejectIfSuspended(memberId);
        Company company = loadActiveCompanyForPolicy(companyName);
        authorizePolicy(memberId, company.getName());

        company.setDiscountPolicy(new NoDiscountPolicy());
        saveCompany(company);
        log.info("Company discount policy reset to default: company={}, by={}", companyName, memberId);
    }

    // ── Discount stacking ────────────────────────────────────────────

    @Transactional
    public void setDiscountStacking(String token, String companyName, boolean allow) {
        if (companyName == null || companyName.isBlank()) throw new IllegalArgumentException("companyName is required");

        UUID memberId = authenticateMember(token);
        rejectIfSuspended(memberId);
        Company company = loadActiveCompanyForPolicy(companyName);
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

    // ── Query (inlined from CompanyQueryDomainService) ──────────────

    public Optional<CompanyPublicDTO> getCompanyInfo(String companyName) {
        log.info("Company info requested: name={}", companyName);
        if (companyName == null || companyName.isBlank()) {
            return Optional.empty();
        }
        Optional<Company> maybe = companyRepository.findByName(companyName);
        if (maybe.isEmpty() || !canViewPublicInfo(null, maybe.get())) {
            log.info("Company info request denied: name={}, reason={}",
                    companyName, maybe.isEmpty() ? "unknown" : "not-active");
            return Optional.empty();
        }
        return Optional.of(toPublicDto(maybe.get()));
    }

    public Optional<CompanyPublicDTO> getCompanyInfoForLookup(String token, String companyName) {
        log.info("Session-aware company info requested: name={}", companyName);
        if (companyName == null || companyName.isBlank()) {
            return Optional.empty();
        }
        Optional<Company> maybe = companyRepository.findByName(companyName);
        if (maybe.isEmpty() || !canViewPublicInfo(token, maybe.get())) {
            log.info("Session-aware company info request denied: name={}, reason={}",
                    companyName, maybe.isEmpty() ? "unknown" : "not-visible");
            return Optional.empty();
        }
        return Optional.of(toPublicDto(maybe.get()));
    }

    public List<CompanySummaryDTO> searchCompanies(String query) {
        return companyRepository.findActiveCompanies(query).stream()
                .map(c -> new CompanySummaryDTO(c.getName()))
                .toList();
    }

    public List<CompanySummaryDTO> searchCompaniesForLookup(String token, String query) {
        return companyRepository.findLookupVisibleCompanies(lookupMemberId(token), lookupSystemAdmin(token), query)
                .stream()
                .map(c -> new CompanySummaryDTO(c.getName()))
                .toList();
    }

    // ── History (inlined from CompanyHistoryDomainService) ───────────

    public List<CompanySummaryDTO> searchFounderLifecycleCompanies(String token, String query) {
        UUID memberId = requireMember(token);
        rejectIfSuspended(memberId);
        return companyRepository.findFounderLifecycleCompanies(memberId, query).stream()
                .map(c -> new CompanySummaryDTO(c.getName()))
                .toList();
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

    // ── Lifecycle (inlined from CompanyLifecycleDomainService) ───────

    public void verifyFounderLifecycleAccess(String token, String companyName) {
        UUID memberId = requireMember(token);
        rejectIfSuspended(memberId);
        Company company = loadCompany(companyName);
        requireFounder(memberId, company);
    }

    @Transactional
    public void suspendCompany(String token, String companyName) {
        UUID memberId = requireMember(token);
        rejectIfSuspended(memberId);
        Company company = loadCompany(companyName);
        requireFounder(memberId, company);

        company.suspend();
        saveCompany(company);
        notifyCompanyStaff(company.getName(), "Company '" + company.getName() + "' has been suspended.");
        log.info("Company suspended: name={}, by={}", companyName, memberId);
    }

    @Transactional
    public void reopenCompany(String token, String companyName) {
        UUID memberId = requireMember(token);
        rejectIfSuspended(memberId);
        Company company = loadCompany(companyName);
        requireFounder(memberId, company);

        company.reopen();
        saveCompany(company);
        notifyCompanyStaff(company.getName(), "Company '" + company.getName() + "' has returned to activity.");
        log.info("Company reopened: name={}, by={}", companyName, memberId);
    }

    @Transactional
    public void permanentCloseByFounder(String token, String companyName) {
        UUID memberId = requireMember(token);
        rejectIfSuspended(memberId);
        synchronized (companyLock(companyName)) {
            Company company = loadCompany(companyName);
            requireFounder(memberId, company);
            runClose(company, false);
        }
    }

    @Transactional
    public void permanentCloseByAdmin(String token, String companyName) {
        UUID memberId = requireMember(token);
        rejectIfSuspended(memberId);
        if (!isAdmin(token)) {
            log.warn("Permanent close by admin ignored: insufficient permissions");
            throw new SecurityException("System admin permission required");
        }
        synchronized (companyLock(companyName)) {
            Company company = loadCompany(companyName);
            runClose(company, true);
        }
    }

    @Transactional
    public void retryPendingRefunds(String companyName) {
        synchronized (companyLock(companyName)) {
            Company company = loadCompany(companyName);
            if (company.getStatus() != CompanyStatus.PENDING_CLOSURE) {
                log.warn("Retry pending refunds ignored: company is not pending closure");
                throw new IllegalStateException("Company is not pending closure");
            }

            String key = normalizeCompanyKey(companyName);
            Deque<RefundJob> queue = pendingRefunds.get(key);
            if (queue == null || queue.isEmpty()) {
                queue = new ArrayDeque<>();
                for (CompletedPurchase p : orderRepository.findCompletedByCompanyName(companyName)) {
                    queue.add(new RefundJob(p.transactionId(), p.amount()));
                }
            }

            while (!queue.isEmpty()) {
                RefundJob job = queue.peek();
                RefundResult r = paymentGateway.refund(job.transactionId(), job.amount().doubleValue());
                if (!r.success()) {
                    log.warn("Retry refund still failing for company={}", companyName);
                    pendingRefunds.put(key, queue);
                    return;
                }
                queue.poll();
            }

            pendingRefunds.remove(key);
            company.completeClosure();
            saveCompany(company);
            notifyCompanyStaff(company.getName(), "Company '" + company.getName() + "' has been closed.");
            log.info("Pending closure completed: company={}", companyName);
        }
    }

    // ── Internal helpers ────────────────────────────────────────────

    private void runClose(Company company, boolean revokeRoles) {
        List<Member> staffToNotify = revokeRoles ? activeCompanyStaff(company.getName()) : List.of();
        List<Event> events = eventRepository.findByCompanyName(company.getName());
        for (Event e : events) {
            if (e.isCancelled()) continue;
            e.cancel();
            saveEvent(e);
        }

        Deque<RefundJob> failed = new ArrayDeque<>();
        List<CompletedPurchase> purchases = orderRepository.findCompletedByCompanyName(company.getName());
        for (CompletedPurchase p : purchases) {
            RefundResult result;
            try {
                result = paymentGateway.refund(p.transactionId(), p.amount().doubleValue());
            } catch (RuntimeException ex) {
                log.warn("Refund threw for company={}: {}", p.companyName(), ex.getMessage());
                result = RefundResult.failed(ex.getMessage());
            }
            if (!result.success()) {
                failed.add(new RefundJob(p.transactionId(), p.amount()));
            }
        }

        if (!failed.isEmpty()) {
            pendingRefunds.put(normalizeCompanyKey(company.getName()), failed);
            company.markPendingClosure();
            saveCompany(company);
            log.warn("Closure pending due to refund failures: company={}, failed={}",
                    company.getName(), failed.size());
            return;
        }

        if (revokeRoles) {
            revokeAllAppointments(company.getName());
        }

        company.close();
        saveCompany(company);
        String message = "Company '" + company.getName() + "' has been closed.";
        if (revokeRoles) {
            notifyMembers(staffToNotify, message);
        } else {
            notifyCompanyStaff(company.getName(), message);
        }
        log.info("Company permanently closed: name={}, revokedRoles={}", company.getName(), revokeRoles);
    }

    private void notifyCompanyStaff(String companyName, String message) {
        notifyMembers(activeCompanyStaff(companyName), message);
    }

    private List<Member> activeCompanyStaff(String companyName) {
        return memberRepository.findByCompanyAppointment(companyName).stream()
                .filter(member -> {
                    StaffAppointment appointment = member.getStaffAppointment(companyName);
                    return appointment != null && !appointment.isRevoked();
                })
                .toList();
    }

    private void notifyMembers(List<Member> members, String message) {
        if (notificationService == null) {
            return;
        }
        for (Member member : members) {
            notificationService.notify(member.getId().toString(), message);
        }
    }

    private void revokeAllAppointments(String companyName) {
        log.info("Revoking all staff appointments for company: {}", companyName);
        for (Member m : memberRepository.findByCompanyAppointment(companyName)) {
            m.removeStaffAppointment(companyName);
            saveMember(m);
        }
    }

    private void requireFounder(UUID memberId, Company company) {
        if (!memberId.equals(company.getFounderId())) {
            throw new SecurityException("Only the founder can perform this lifecycle action");
        }
        Member m = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Member not found: " + memberId));
        StaffAppointment appt = m.getStaffAppointment(company.getName());
        if (appt == null || !appt.isOwner()) {
            throw new SecurityException("Founder appointment missing or not owner");
        }
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

    private UUID authenticateMember(String token) {
        UUID memberId = validateToken(token);
        if (memberId == null) {
            throw new SecurityException("Guests cannot manage policies");
        }
        return memberId;
    }

    private void rejectIfSuspended(UUID memberId) {
        memberRepository.findById(memberId)
                .ifPresent(member -> member.rejectIfSuspended(Instant.now()));
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

    private Company loadActiveCompanyForPolicy(String companyName) {
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

    private void saveEvent(Event event) {
        try {
            eventRepository.save(event);
        } catch (OptimisticLockException ex) {
            log.warn("Event save conflict during company lifecycle: eventId={}", event.getId());
            throw new IllegalStateException("Event changed concurrently. Please retry.", ex);
        }
    }

    private void saveMember(Member member) {
        try {
            memberRepository.save(member);
        } catch (OptimisticLockException ex) {
            log.warn("Member save conflict during company lifecycle: memberId={}", member.getId());
            throw new IllegalStateException("Member changed concurrently. Please retry.", ex);
        }
    }

    private Object companyLock(String companyName) {
        return companyLocks.computeIfAbsent(normalizeCompanyKey(companyName), k -> new Object());
    }

    private static String normalizeCompanyKey(String companyName) {
        return companyName.toLowerCase().trim();
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

    private boolean canViewPublicInfo(String token, Company company) {
        if (company.isActive()) {
            return true;
        }
        if (company.getStatus() != CompanyStatus.SUSPENDED || token == null || token.isBlank()) {
            return false;
        }
        if (!sessionTokenService.isValid(token)) {
            return false;
        }
        if (isAdmin(token)) {
            return true;
        }
        UUID memberId = sessionTokenService.extractMemberId(token);
        return memberId != null && memberId.equals(company.getFounderId());
    }

    private UUID lookupMemberId(String token) {
        return validToken(token) ? sessionTokenService.extractMemberId(token) : null;
    }

    private boolean lookupSystemAdmin(String token) {
        return validToken(token) && isAdmin(token);
    }

    private boolean validToken(String token) {
        return token != null && !token.isBlank() && sessionTokenService.isValid(token);
    }

    private CompanyPublicDTO toPublicDto(Company company) {
        List<EventSummaryDTO> active = eventRepository.findByCompanyName(company.getName()).stream()
                .filter(e -> e.getStatus() == EventStatus.PUBLISHED || e.getStatus() == EventStatus.SOLD_OUT)
                .map(EventSummaryDTO::from)
                .toList();
        log.info("Company info provided: name={}", company.getName());
        return new CompanyPublicDTO(company.getName(), company.getDescription(), active);
    }

    private record RefundJob(String transactionId, java.math.BigDecimal amount) {}
}
