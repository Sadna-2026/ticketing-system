package com.ticketing.application.concurrency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ticketing.application.CompanyLifecycleService;
import com.ticketing.application.CompanyService;
import com.ticketing.application.INotificationService;
import com.ticketing.application.auth.ISessionTokenService;
import com.ticketing.application.initialization.InitializationService;
import com.ticketing.domain.company.Company;
import com.ticketing.domain.company.CompanyStatus;
import com.ticketing.domain.company.ICompanyRepository;
import com.ticketing.domain.event.IEventPublisher;
import com.ticketing.domain.member.IMemberRepository;
import com.ticketing.domain.member.Member;
import com.ticketing.domain.member.StaffAppointment;
import com.ticketing.infrastructure.InMemoryCompanyRepository;
import com.ticketing.infrastructure.InMemoryEventPublisher;
import com.ticketing.infrastructure.InMemoryEventRepository;
import com.ticketing.infrastructure.InMemoryMemberRepository;
import com.ticketing.infrastructure.InMemoryOrderRepository;
import com.ticketing.infrastructure.Interface.IPaymentGateway;

/**
 * Concurrency tests for CompanyService
 */
public class CompanyServiceConcurrencyTest {

    private ICompanyRepository companyRepository;
    private IMemberRepository memberRepository;
    private IEventPublisher eventPublisher;
    private ISessionTokenService sessionTokenService;
    private INotificationService notificationService;
    private CompanyService companyService;

    @BeforeEach
    public void setUp() {
        companyRepository = new InMemoryCompanyRepository();
        memberRepository = new InMemoryMemberRepository();
        eventPublisher = new InMemoryEventPublisher();
        
        sessionTokenService = mock(ISessionTokenService.class);
        when(sessionTokenService.isValid(anyString())).thenReturn(true);
        when(sessionTokenService.extractMemberId(anyString())).thenAnswer(invocation -> {
            String token = invocation.getArgument(0);
            return UUID.fromString(token);
        });

        notificationService = mock(INotificationService.class);

        InitializationService initService = new InitializationService(
            companyRepository,
            memberRepository,
            eventPublisher,
            sessionTokenService,
            notificationService
        );
        companyService = initService.initialize();
    }

    @Test
    public void GivenConcurrentFounders_WhenOpenSameCompanyName_ThenOnlyOneSucceeds() throws InterruptedException {
        int threadCount = 10;
        String companyName = "ConcurrentCorp";
        String description = "Test description";
        
        UUID founderId = UUID.randomUUID();
        String token = founderId.toString();
        
        // Setup founder
        memberRepository.saveIfUsernameAndEmailAvailable(new Member(founderId, "founder", "f@t.com", "p"));

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        
        int[] successCount = {0};
        int[] failureCount = {0};

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    latch.await();
                    companyService.openProductionCompany(token, companyName, description);
                    synchronized (successCount) { successCount[0]++; }
                } catch (Exception e) {
                    synchronized (failureCount) { failureCount[0]++; }
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        latch.countDown();
        doneLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        // Exactly one should succeed, others fail due to duplicate name
        assertEquals(1, successCount[0], "Exactly one company creation should succeed");
        assertEquals(threadCount - 1, failureCount[0], "Other creations should fail");
    }

    @Test
    public void GivenTwoCompanies_WhenConcurrentSuspend_ThenBothSucceed() throws InterruptedException {
        UUID founder1 = UUID.randomUUID();
        UUID founder2 = UUID.randomUUID();
        String company1 = "CorpOne";
        String company2 = "CorpTwo";

        Member m1 = new Member(founder1, "f1", "f1@t.com", "p");
        m1.addStaffAppointment(company1, new StaffAppointment(
                company1, founder1, StaffAppointment.StaffRole.OWNER, Collections.emptySet()));
        memberRepository.saveIfUsernameAndEmailAvailable(m1);

        Member m2 = new Member(founder2, "f2", "f2@t.com", "p");
        m2.addStaffAppointment(company2, new StaffAppointment(
                company2, founder2, StaffAppointment.StaffRole.OWNER, Collections.emptySet()));
        memberRepository.saveIfUsernameAndEmailAvailable(m2);

        companyRepository.save(new Company(company1, "d1", founder1));
        companyRepository.save(new Company(company2, "d2", founder2));

        when(sessionTokenService.extractMemberId(founder1.toString())).thenReturn(founder1);
        when(sessionTokenService.extractMemberId(founder2.toString())).thenReturn(founder2);
        when(sessionTokenService.extractPermissions(anyString())).thenReturn(Set.of());

        CompanyLifecycleService lifecycleService = new CompanyLifecycleService(
                companyRepository,
                new InMemoryEventRepository(),
                memberRepository,
                new InMemoryOrderRepository(),
                mock(IPaymentGateway.class),
                sessionTokenService);

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);
        int[] failures = {0};

        ExecutorService executor = Executors.newFixedThreadPool(2);
        executor.submit(() -> {
            try {
                startLatch.await();
                lifecycleService.suspendCompany(founder1.toString(), company1);
            } catch (Exception e) {
                synchronized (failures) { failures[0]++; }
            } finally {
                doneLatch.countDown();
            }
        });
        executor.submit(() -> {
            try {
                startLatch.await();
                lifecycleService.suspendCompany(founder2.toString(), company2);
            } catch (Exception e) {
                synchronized (failures) { failures[0]++; }
            } finally {
                doneLatch.countDown();
            }
        });

        startLatch.countDown();
        doneLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(0, failures[0]);
        assertEquals(CompanyStatus.SUSPENDED,
                companyRepository.findByName(company1).orElseThrow().getStatus());
        assertEquals(CompanyStatus.SUSPENDED,
                companyRepository.findByName(company2).orElseThrow().getStatus());
    }
}
