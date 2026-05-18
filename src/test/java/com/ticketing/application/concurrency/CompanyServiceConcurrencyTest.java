package com.ticketing.application.concurrency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

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

        assertEquals(1, successCount[0], "Exactly one company creation should succeed");
        assertEquals(threadCount - 1, failureCount[0], "Other creations should fail");
    }

    @Test
    public void GivenTwoCompanies_WhenConcurrentSuspend_ThenBothSucceed() throws InterruptedException {
        UUID founder1 = UUID.randomUUID();
        UUID founder2 = UUID.randomUUID();
        String company1 = "CorpOne";
        String company2 = "CorpTwo";

        setupFounderWithCompany(founder1, company1);
        setupFounderWithCompany(founder2, company2);

        CompanyLifecycleService lifecycleService = newLifecycleService(companyRepository);

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);
        int[] failures = {0};

        ExecutorService executor = Executors.newFixedThreadPool(2);
        executor.submit(() -> runSuspend(lifecycleService, founder1, company1, startLatch, doneLatch, failures));
        executor.submit(() -> runSuspend(lifecycleService, founder2, company2, startLatch, doneLatch, failures));

        startLatch.countDown();
        doneLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(0, failures[0]);
        assertEquals(CompanyStatus.SUSPENDED,
                companyRepository.findByName(company1).orElseThrow().getStatus());
        assertEquals(CompanyStatus.SUSPENDED,
                companyRepository.findByName(company2).orElseThrow().getStatus());
    }

    @Test
    public void GivenBlockedSaveOnOneCompany_WhenSuspendAnotherCompany_ThenOtherCompletesFirst()
            throws InterruptedException {
        UUID founder1 = UUID.randomUUID();
        UUID founder2 = UUID.randomUUID();
        String company1 = "BlockingCorp";
        String company2 = "FreeCorp";

        BlockingCompanyRepository blockingRepo = new BlockingCompanyRepository(company1);
        setupFounderWithCompany(founder1, company1, blockingRepo);
        setupFounderWithCompany(founder2, company2, blockingRepo);
        blockingRepo.enableBlocking();

        CompanyLifecycleService lifecycleService = newLifecycleService(blockingRepo);

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch company1EnteredSave = new CountDownLatch(1);
        CountDownLatch company2Finished = new CountDownLatch(1);
        AtomicBoolean company2DoneBeforeRelease = new AtomicBoolean(false);

        blockingRepo.setOnBlockedSave(company1EnteredSave::countDown);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        executor.submit(() -> {
            try {
                startLatch.await();
                lifecycleService.suspendCompany(founder1.toString(), company1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                blockingRepo.releaseBlockedSave();
            }
        });
        executor.submit(() -> {
            try {
                startLatch.await();
                lifecycleService.suspendCompany(founder2.toString(), company2);
                company2Finished.countDown();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        startLatch.countDown();
        assertTrue(company1EnteredSave.await(3, TimeUnit.SECONDS),
                "Company1 suspend should reach save while blocked");
        company2DoneBeforeRelease.set(company2Finished.await(3, TimeUnit.SECONDS));
        blockingRepo.releaseBlockedSave();

        assertTrue(company2DoneBeforeRelease.get(),
                "Company2 suspend must finish while company1 save is blocked (not globally serialized)");
        assertEquals(CompanyStatus.SUSPENDED,
                blockingRepo.findByName(company2).orElseThrow().getStatus());
    }

    private void setupFounderWithCompany(UUID founderId, String companyName) {
        setupFounderWithCompany(founderId, companyName, companyRepository);
    }

    private void setupFounderWithCompany(UUID founderId, String companyName, ICompanyRepository repo) {
        Member member = new Member(founderId, "f-" + founderId, founderId + "@t.com", "p");
        member.addStaffAppointment(companyName, new StaffAppointment(
                companyName, founderId, StaffAppointment.StaffRole.OWNER, Collections.emptySet()));
        memberRepository.saveIfUsernameAndEmailAvailable(member);
        repo.save(new Company(companyName, "desc", founderId));
        when(sessionTokenService.extractMemberId(founderId.toString())).thenReturn(founderId);
        when(sessionTokenService.extractPermissions(anyString())).thenReturn(Set.of());
    }

    private CompanyLifecycleService newLifecycleService(ICompanyRepository repo) {
        return new CompanyLifecycleService(
                repo,
                new InMemoryEventRepository(),
                memberRepository,
                new InMemoryOrderRepository(),
                mock(IPaymentGateway.class),
                sessionTokenService);
    }

    private static void runSuspend(
            CompanyLifecycleService lifecycleService,
            UUID founderId,
            String companyName,
            CountDownLatch startLatch,
            CountDownLatch doneLatch,
            int[] failures) {
        try {
            startLatch.await();
            lifecycleService.suspendCompany(founderId.toString(), companyName);
        } catch (Exception e) {
            synchronized (failures) { failures[0]++; }
        } finally {
            doneLatch.countDown();
        }
    }

    /**
     * Blocks save for one company until released, to prove lifecycle ops on other companies are not serialized.
     */
    private static final class BlockingCompanyRepository implements ICompanyRepository {
        private final InMemoryCompanyRepository delegate = new InMemoryCompanyRepository();
        private final String blockedCompanyName;
        private final CountDownLatch releaseSave = new CountDownLatch(1);
        private volatile boolean blockingEnabled;
        private volatile Runnable onBlockedSave = () -> {};

        BlockingCompanyRepository(String blockedCompanyName) {
            this.blockedCompanyName = blockedCompanyName.toLowerCase().trim();
        }

        void enableBlocking() {
            this.blockingEnabled = true;
        }

        void setOnBlockedSave(Runnable onBlockedSave) {
            this.onBlockedSave = onBlockedSave;
        }

        void releaseBlockedSave() {
            releaseSave.countDown();
        }

        @Override
        public void save(Company entity) {
            if (blockingEnabled && blockedCompanyName.equals(entity.getName().toLowerCase().trim())) {
                onBlockedSave.run();
                try {
                    if (!releaseSave.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timed out waiting to release blocked save");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while blocking save", e);
                }
            }
            delegate.save(entity);
        }

        @Override
        public Optional<Company> findById(String id) {
            return delegate.findById(id);
        }

        @Override
        public boolean existsByName(String name) {
            return delegate.existsByName(name);
        }

        @Override
        public Optional<Company> findByName(String name) {
            return delegate.findByName(name);
        }

        @Override
        public List<Company> getAll() {
            return delegate.getAll();
        }

        @Override
        public void delete(String id) {
            delegate.delete(id);
        }
    }
}
