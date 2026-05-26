package com.ticketing.domain.services;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ticketing.domain.company.Company;
import com.ticketing.domain.company.ICompanyRepository;
import com.ticketing.domain.event.Event;
import com.ticketing.domain.event.IEventRepository;
import com.ticketing.domain.exception.OptimisticLockException;
import com.ticketing.domain.gateway.IPaymentGateway;
import com.ticketing.domain.gateway.RefundResult;
import com.ticketing.domain.member.IMemberRepository;
import com.ticketing.domain.member.Member;
import com.ticketing.domain.member.StaffAppointment;
import com.ticketing.domain.order.CompletedPurchase;
import com.ticketing.domain.order.IOrderRepository;

@org.springframework.stereotype.Service
public class CompanyLifecycleDomainService {

    private static final Logger log = LoggerFactory.getLogger(CompanyLifecycleDomainService.class);

    private final ICompanyRepository companyRepository;
    private final IEventRepository eventRepository;
    private final IMemberRepository memberRepository;
    private final IOrderRepository orderRepository;
    private final IPaymentGateway paymentGateway;

    // queue of pending refund jobs per company, used when payment service is down
    private final ConcurrentHashMap<String, Deque<RefundJob>> pendingRefunds = new ConcurrentHashMap<>();

    /** Per-company lock for workflows with non-idempotent side effects (close / refund retry). */
    private final ConcurrentHashMap<String, Object> companyLocks = new ConcurrentHashMap<>();

    public CompanyLifecycleDomainService(ICompanyRepository companyRepository,
                                         IEventRepository eventRepository,
                                         IMemberRepository memberRepository,
                                         IOrderRepository orderRepository,
                                         IPaymentGateway paymentGateway) {
        this.companyRepository = companyRepository;
        this.eventRepository = eventRepository;
        this.memberRepository = memberRepository;
        this.orderRepository = orderRepository;
        this.paymentGateway = paymentGateway;
    }

    public void suspendCompany(UUID memberId, String companyName) {
        Company company = loadCompany(companyName);
        requireFounder(memberId, company);

        company.suspend();
        saveCompany(company);
        log.info("Company suspended: name={}, by={}", companyName, memberId);
    }

    public void reopenCompany(UUID memberId, String companyName) {
        Company company = loadCompany(companyName);
        requireFounder(memberId, company);

        company.reopen();
        saveCompany(company);
        log.info("Company reopened: name={}, by={}", companyName, memberId);
    }

    public void permanentCloseByFounder(UUID memberId, String companyName) {
        synchronized (companyLock(companyName)) {
            Company company = loadCompany(companyName);
            requireFounder(memberId, company);
            runClose(company, false);
        }
    }

    public void permanentCloseByAdmin(String companyName) {
        synchronized (companyLock(companyName)) {
            Company company = loadCompany(companyName);
            runClose(company, true);
        }
    }

    public void retryPendingRefunds(String companyName) {
        synchronized (companyLock(companyName)) {
            Company company = loadCompany(companyName);
            if (company.getStatus() != com.ticketing.domain.company.CompanyStatus.PENDING_CLOSURE) {
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
            log.info("Pending closure completed: company={}", companyName);
        }
    }

    private void runClose(Company company, boolean revokeRoles) {
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
        log.info("Company permanently closed: name={}, revokedRoles={}", company.getName(), revokeRoles);
    }

    private void revokeAllAppointments(String companyName) {
        log.info("Revoking all staff appointments for company: {}", companyName);
        for (Member m : memberRepository.findByCompanyAppointment(companyName)) {
            m.removeStaffAppointment(companyName);
            saveMember(m);
        }
    }

    private Company loadCompany(String companyName) {
        return companyRepository.findByName(companyName)
                .orElseThrow(() -> new IllegalArgumentException("Company not found: " + companyName));
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
