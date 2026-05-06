package com.ticketing.application.concurrency;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ticketing.application.CompanyService;
import com.ticketing.application.INotificationService;
import com.ticketing.application.auth.ISessionTokenService;
import com.ticketing.application.initialization.InitializationService;
import com.ticketing.domain.company.ICompanyRepository;
import com.ticketing.domain.event.IEventPublisher;
import com.ticketing.domain.member.IMemberRepository;
import com.ticketing.domain.member.Member;
import com.ticketing.infrastructure.InMemoryCompanyRepository;
import com.ticketing.infrastructure.InMemoryCompletedPurchaseRepository;
import com.ticketing.infrastructure.InMemoryEventPublisher;
import com.ticketing.infrastructure.InMemoryMemberRepository;

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
            notificationService,
            new InMemoryCompletedPurchaseRepository()
        );
        companyService = initService.initialize();
    }

    @Test
    public void testConcurrentCompanyCreationWithSameName() throws InterruptedException {
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
}
