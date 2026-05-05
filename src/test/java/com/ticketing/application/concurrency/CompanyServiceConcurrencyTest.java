package com.ticketing.application.concurrency;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

import com.ticketing.application.CompanyService;
import com.ticketing.application.auth.ISessionTokenService;
import com.ticketing.application.initialization.InitializationService;
import com.ticketing.domain.company.ICompanyRepository;
import com.ticketing.domain.event.IEventPublisher;
import com.ticketing.domain.member.IMemberRepository;
import com.ticketing.domain.member.Member;
import com.ticketing.infrastructure.InMemoryCompanyRepository;
import com.ticketing.infrastructure.InMemoryEventPublisher;

/**
 * Concurrency tests for CompanyService
 * Tests that multiple threads cannot create companies with the same name simultaneously
 * Verifies thread-safety of company creation logic
 */
public class CompanyServiceConcurrencyTest {

    private ICompanyRepository companyRepository;
    private IMemberRepository memberRepository;
    private IEventPublisher eventPublisher;
    private ISessionTokenService sessionTokenService;
    private CompanyService companyService;

    @BeforeEach
    public void setUp() {
        companyRepository = new InMemoryCompanyRepository();
        memberRepository = new com.ticketing.infrastructure.InMemoryMemberRepository();
        eventPublisher = new InMemoryEventPublisher();

        sessionTokenService = mock(ISessionTokenService.class);
        when(sessionTokenService.isValid(anyString())).thenAnswer(invocation -> {
            String token = invocation.getArgument(0);
            return !token.isEmpty() && token.startsWith("valid-");
        });
        when(sessionTokenService.extractMemberId(anyString())).thenAnswer(invocation -> {
            String token = invocation.getArgument(0);
            if (token.startsWith("valid-")) {
                try {
                    return UUID.fromString(token.substring(6));
                } catch (Exception e) {
                    return null;
                }
            }
            return null;
        });

        InitializationService initService = new InitializationService(
            companyRepository,
            memberRepository,
            eventPublisher,
            sessionTokenService
        );
        companyService = initService.initialize();
    }

    // ===== Core Concurrency Tests =====

    @Test
    public void testMultipleThreadsCreatingSameCompanyName() throws InterruptedException {
        String companyName = "UniqueCompanyName";
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        // Create multiple members
        List<UUID> memberIds = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            UUID memberId = UUID.randomUUID();
            memberIds.add(memberId);
            Member member = new Member(memberId, "user" + i, "user" + i + "@example.com", "hashedPassword");
            memberRepository.saveIfUsernameAndEmailAvailable(member);
        }

        // Each thread tries to create the same company with different founders
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    UUID memberId = memberIds.get(threadId);
                    String token = "valid-" + memberId.toString();
                    
                    try {
                        companyService.openProductionCompany(token, companyName, "Description " + threadId);
                        successCount.incrementAndGet();
                    } catch (IllegalArgumentException e) {
                        // Expected - company already exists
                        if (e.getMessage().contains("already exists")) {
                            failureCount.incrementAndGet();
                        } else {
                            throw e;
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // Exactly one should succeed, rest should fail
        assertEquals(1, successCount.get(), "Exactly one thread should successfully create the company");
        assertEquals(threadCount - 1, failureCount.get(), "All other threads should fail");

        // Verify company exists exactly once in repository
        assertTrue(companyRepository.existsByName(companyName));
        List<com.ticketing.domain.company.Company> allCompanies = companyRepository.getAll();
        long companyCount = allCompanies.stream()
            .filter(c -> c.getName().equalsIgnoreCase(companyName))
            .count();
        assertEquals(1, companyCount, "Company should exist exactly once");
    }

    @Test
    public void testConcurrentCompanyCreationWithDifferentNames() throws InterruptedException {
        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        // Create multiple members
        List<UUID> memberIds = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            UUID memberId = UUID.randomUUID();
            memberIds.add(memberId);
            Member member = new Member(memberId, "user-diff-" + i, "user-diff-" + i + "@example.com", "hashedPassword");
            memberRepository.saveIfUsernameAndEmailAvailable(member);
        }

        // Each thread creates a different company
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    UUID memberId = memberIds.get(threadId);
                    String token = "valid-" + memberId.toString();
                    
                    String companyName = "Company" + threadId;
                    companyService.openProductionCompany(token, companyName, "Description " + threadId);
                    successCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // All should succeed since they have different names
        assertEquals(threadCount, successCount.get(), "All threads should successfully create their companies");

        // Verify all companies exist
        List<com.ticketing.domain.company.Company> allCompanies = companyRepository.getAll();
        // total companies = 10 from previous tests? No, @BeforeEach clears them? 
        // Actually, companyRepository is re-initialized in @BeforeEach.
        assertEquals(threadCount, allCompanies.size(), "All " + threadCount + " companies should exist");
    }

    @Test
    public void testRaceConditionWithRepository() throws InterruptedException {
        String companyName = "RaceConditionTest";
        int threadCount = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        // Create multiple members
        List<UUID> memberIds = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            UUID memberId = UUID.randomUUID();
            memberIds.add(memberId);
            Member member = new Member(memberId, "user-race-" + i, "user-race-" + i + "@example.com", "hashedPassword");
            memberRepository.saveIfUsernameAndEmailAvailable(member);
        }

        // All threads try to create company with same name simultaneously
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    UUID memberId = memberIds.get(threadId);
                    String token = "valid-" + memberId.toString();
                    
                    try {
                        companyService.openProductionCompany(token, companyName, "Description");
                        successCount.incrementAndGet();
                    } catch (IllegalArgumentException e) {
                        if (e.getMessage().contains("already exists")) {
                            failureCount.incrementAndGet();
                        } else {
                            throw e;
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // Exactly one thread should succeed
        assertEquals(1, successCount.get(), "Exactly one thread should succeed with 50 concurrent attempts");
        assertEquals(threadCount - 1, failureCount.get(), "All others should fail");
    }

    @Test
    public void testSequentialVsConcurrentConsistency() throws InterruptedException {
        // Test 1: Sequential creation of 5 different companies
        for (int i = 0; i < 5; i++) {
            UUID memberId = UUID.randomUUID();
            Member member = new Member(memberId, "sequser" + i, "sequser" + i + "@example.com", "hashedPassword");
            memberRepository.saveIfUsernameAndEmailAvailable(member);
            
            String token = "valid-" + memberId.toString();
            companyService.openProductionCompany(token, "SequentialCompany" + i, "Description");
        }

        // Test 2: Concurrent creation of 5 different companies
        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        List<UUID> concurrentMemberIds = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            UUID memberId = UUID.randomUUID();
            concurrentMemberIds.add(memberId);
            Member member = new Member(memberId, "concuser" + i, "concuser" + i + "@example.com", "hashedPassword");
            memberRepository.saveIfUsernameAndEmailAvailable(member);
        }

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    UUID memberId = concurrentMemberIds.get(threadId);
                    String token = "valid-" + memberId.toString();
                    companyService.openProductionCompany(token, "ConcurrentCompany" + threadId, "Description");
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // Verify total companies
        List<com.ticketing.domain.company.Company> allCompanies = companyRepository.getAll();
        assertEquals(10, allCompanies.size(), "Should have 10 companies total (5 sequential + 5 concurrent)");
    }
}
