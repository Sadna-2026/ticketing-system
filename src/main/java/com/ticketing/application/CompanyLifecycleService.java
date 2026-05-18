package com.ticketing.application;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ticketing.application.auth.ISessionTokenService;
import com.ticketing.domain.company.Company;
import com.ticketing.domain.company.ICompanyRepository;
import com.ticketing.domain.event.Event;
import com.ticketing.domain.gateway.RefundResult;
import com.ticketing.domain.member.IMemberRepository;
import com.ticketing.domain.member.Member;
import com.ticketing.domain.member.StaffAppointment;
import com.ticketing.domain.order.CompletedPurchase;
import com.ticketing.domain.order.IOrderRepository;
import com.ticketing.infrastructure.Interface.IEventRepository;
import com.ticketing.infrastructure.Interface.IPaymentGateway;

/**
 * Suspend / reopen / permanent-close operations for production companies.
 * Founder OR System Admin actor (the latter only for adminClose).
 */
public class CompanyLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(CompanyLifecycleService.class);
    private static final String ADMIN_PERMISSION = "SYSTEM_ADMIN";

    private final ICompanyRepository companyRepository;
    private final IEventRepository eventRepository;
    private final IMemberRepository memberRepository;
    private final IOrderRepository orderRepository;
    private final IPaymentGateway paymentGateway;
    private final ISessionTokenService sessionTokenService;

    // queue of pending refund jobs per company, used when payment service is down
    private final ConcurrentHashMap<String, Deque<RefundJob>> pendingRefunds = new ConcurrentHashMap<>();

    /** Per-company lock for workflows with non-idempotent side effects (close / refund retry). */
    private final ConcurrentHashMap<String, Object> companyLocks = new ConcurrentHashMap<>();

    public CompanyLifecycleService(ICompanyRepository companyRepository,
                                   IEventRepository eventRepository,
                                   IMemberRepository memberRepository,
                                   IOrderRepository orderRepository,
                                   IPaymentGateway paymentGateway,
                                   ISessionTokenService sessionTokenService) {
        this.companyRepository = companyRepository;
        this.eventRepository = eventRepository;
        this.memberRepository = memberRepository;
        this.orderRepository = orderRepository;
        this.paymentGateway = paymentGateway;
        this.sessionTokenService = sessionTokenService;
    }

    public void suspendCompany(String token, String companyName) {
        UUID memberId = requireMember(token);
        Company company = loadCompany(companyName);
        requireFounder(memberId, company);

        company.suspend();
        companyRepository.save(company);
        log.info("Company suspended: name={}, by={}", companyName, memberId);
    }

    public void reopenCompany(String token, String companyName) {
        UUID memberId = requireMember(token);
        Company company = loadCompany(companyName);
        requireFounder(memberId, company);

        company.reopen();
        companyRepository.save(company);
        log.info("Company reopened: name={}, by={}", companyName, memberId);
    }

    public void permanentCloseByFounder(String token, String companyName) {
        UUID memberId = requireMember(token);
        synchronized (companyLock(companyName)) {
            Company company = loadCompany(companyName);
            requireFounder(memberId, company);
            runClose(company, false);
        }
    }

    public void permanentCloseByAdmin(String token, String companyName) {
        requireMember(token);
        if (!isAdmin(token)) {
            throw new SecurityException("System admin permission required");
        }
        synchronized (companyLock(companyName)) {
            Company company = loadCompany(companyName);
            runClose(company, true);
        }
    }

    public void retryPendingRefunds(String companyName) {
        synchronized (companyLock(companyName)) {
            Company company = loadCompany(companyName);
            if (company.getStatus() != com.ticketing.domain.company.CompanyStatus.PENDING_CLOSURE) {
                throw new IllegalStateException("Company is not pending closure");
            }

            String key = normalizeCompanyKey(companyName);
            Deque<RefundJob> queue = pendingRefunds.get(key);
            if (queue == null || queue.isEmpty()) {
                // The in-memory queue is lost on process restart, but the company can
                // still be in PENDING_CLOSURE per the persisted CompanyStatus. Rehydrate
                // by re-reading completed purchases from the repository.
                // V1 caveat: this re-attempts every refund, including ones that succeeded
                // in the original close. With the stub gateway this is harmless; for V2
                // we'd track per-purchase refund status to avoid double-refund.
                queue = new ArrayDeque<>();
                for (CompletedPurchase p : orderRepository.findCompletedByCompanyName(companyName)) {
                    queue.add(new RefundJob(p.transactionId(), p.amount()));
                }
            }

            while (!queue.isEmpty()) {
                RefundJob job = queue.peek();
                RefundResult r = paymentGateway.refund(job.transactionId, job.amount.doubleValue());
                if (!r.success()) {
                    log.warn("Retry refund still failing for company={}, transactionId={}",
                            companyName, job.transactionId);
                    pendingRefunds.put(key, queue);
                    return;
                }
                queue.poll();
            }

            pendingRefunds.remove(key);
            company.completeClosure();
            companyRepository.save(company);
            log.info("Pending closure completed: company={}", companyName);
        }
    }

    // --- internals ---

    private void runClose(Company company, boolean revokeRoles) {
        // 1) cancel future events of this company
        List<Event> events = eventRepository.findByCompanyName(company.getName());
        for (Event e : events) {
            if (e.isCancelled()) continue;
            // we treat any non-cancelled event as something to wind down, regardless of date —
            // simpler than threading a clock through here, and past events being marked cancelled is harmless
            e.cancel();
            eventRepository.save(e);
        }

        // 2) refund completed purchases
        Deque<RefundJob> failed = new ArrayDeque<>();
        List<CompletedPurchase> purchases = orderRepository.findCompletedByCompanyName(company.getName());
        for (CompletedPurchase p : purchases) {
            RefundResult result;
            try {
                result = paymentGateway.refund(p.transactionId(), p.amount().doubleValue());
            } catch (RuntimeException ex) {
                log.warn("Refund threw for txn={} ({}): {}", p.transactionId(), p.companyName(), ex.getMessage());
                result = RefundResult.failed(ex.getMessage());
            }
            if (!result.success()) {
                failed.add(new RefundJob(p.transactionId(), p.amount()));
            }
        }

        if (!failed.isEmpty()) {
            pendingRefunds.put(normalizeCompanyKey(company.getName()), failed);
            company.markPendingClosure();
            companyRepository.save(company);
            log.warn("Closure pending due to refund failures: company={}, failed={}",
                    company.getName(), failed.size());
            return;
        }

        // 3) optionally revoke all staff appointments (admin-forced flavor)
        if (revokeRoles) {
            revokeAllAppointments(company.getName());
        }

        // 4) finalise close
        company.close();
        companyRepository.save(company);
        log.info("Company permanently closed: name={}, revokedRoles={}", company.getName(), revokeRoles);
    }

    private void revokeAllAppointments(String companyName) {
        for (Member m : memberRepository.findByCompanyAppointment(companyName)) {
            m.removeStaffAppointment(companyName);
            memberRepository.save(m);
        }
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

    private Company loadCompany(String companyName) {
        return companyRepository.findByName(companyName)
                .orElseThrow(() -> new IllegalArgumentException("Company not found: " + companyName));
    }

    private Object companyLock(String companyName) {
        return companyLocks.computeIfAbsent(normalizeCompanyKey(companyName), k -> new Object());
    }

    private static String normalizeCompanyKey(String companyName) {
        return companyName.toLowerCase().trim();
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

    private record RefundJob(String transactionId, java.math.BigDecimal amount) {}
}
