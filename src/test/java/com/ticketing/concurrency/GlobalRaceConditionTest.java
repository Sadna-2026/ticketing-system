package com.ticketing.concurrency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ticketing.application.auth.ISessionTokenService;
import com.ticketing.application.initialization.InitializationService;
import com.ticketing.application.services.CompanyService;
import com.ticketing.application.services.INotificationService;
import com.ticketing.application.services.MemberService;
import com.ticketing.domain.member.Member;
import com.ticketing.domain.member.StaffAppointment;
import com.ticketing.domain.member.request.RegisterRequest;
import com.ticketing.infrastructure.InMemoryCompanyRepository;
import com.ticketing.infrastructure.InMemoryEventPublisher;
import com.ticketing.infrastructure.InMemoryMemberRepository;
import com.ticketing.infrastructure.PasswordEncryptionUtils;

public class GlobalRaceConditionTest {

    private InMemoryCompanyRepository companyRepo;
    private InMemoryMemberRepository memberRepo;
    private InMemoryEventPublisher eventPublisher;
    private ISessionTokenService tokenService;
    private INotificationService notificationService;
    private PasswordEncryptionUtils passwordUtils;
    
    private CompanyService companyService;
    private MemberService memberService;

    @BeforeEach
    public void setUp() {
        companyRepo = new InMemoryCompanyRepository();
        memberRepo = new InMemoryMemberRepository();
        eventPublisher = new InMemoryEventPublisher();
        tokenService = mock(ISessionTokenService.class);
        notificationService = mock(INotificationService.class);
        passwordUtils = new PasswordEncryptionUtils();

        InitializationService initService = new InitializationService(
            companyRepo, memberRepo, eventPublisher, tokenService, notificationService
        );
        companyService = initService.initialize();
        memberService = new MemberService(memberRepo, passwordUtils, tokenService);

        // Mock token service behavior
        when(tokenService.isValid(anyString())).thenReturn(true);
        when(tokenService.extractMemberId(anyString())).thenAnswer(inv -> {
            String t = inv.getArgument(0);
            try { return UUID.fromString(t); } catch (Exception e) { return null; }
        });
        when(tokenService.extractSessionId(anyString())).thenReturn(UUID.randomUUID());
    }

    @Test
    public void GivenConcurrentGuests_WhenRegisterSameUser_ThenOnlyOneSucceeds() throws InterruptedException {
        int threads = 20;
        String username = "uniqueUser";
        String email = "unique@test.com";
        RegisterRequest request = new RegisterRequest(
            username,
            email,
            "password123",
            "0501234567",
            "2000-01-01"
        );
        String guestToken = "guest-token";

        when(tokenService.extractMemberId(guestToken)).thenReturn(null);

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    var response = memberService.register(request, guestToken);
                    if (response.success()) successCount.incrementAndGet();
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(5, TimeUnit.SECONDS));
        executor.shutdown();

        assertEquals(1, successCount.get());
    }

    @Test
    public void GivenConcurrentFounders_WhenOpenSameCompany_ThenOnlyOneSucceeds() throws InterruptedException {
        int threads = 20;
        String companyName = "RaceCorp";
        UUID founderId = UUID.randomUUID();
        String token = founderId.toString();

        memberRepo.saveIfUsernameAndEmailAvailable(new Member(founderId, "founder", "f@f.com", "p"));

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    companyService.openProductionCompany(token, companyName, "Desc");
                    successCount.incrementAndGet();
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(5, TimeUnit.SECONDS));
        executor.shutdown();

        assertEquals(1, successCount.get());
    }

    @Test
    public void GivenConcurrentOffers_WhenOfferSameRole_ThenConsistentState() throws InterruptedException {
        int threads = 50;
        String companyName = "OfferCorp";
        UUID ownerId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        String token = ownerId.toString();

        Member owner = new Member(ownerId, "owner", "o@o.com", "p");
        memberRepo.saveIfUsernameAndEmailAvailable(owner);
        companyService.openProductionCompany(token, companyName, "Desc");

        Member target = new Member(targetId, "target", "t@t.com", "p");
        memberRepo.saveIfUsernameAndEmailAvailable(target);

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    companyService.offerRoleAppointment(token, companyName, targetId, StaffAppointment.StaffRole.MANAGER, Collections.emptySet());
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(5, TimeUnit.SECONDS));
        executor.shutdown();

        Member targetAfter = memberRepo.findById(targetId).orElseThrow();
        assertEquals(threads, targetAfter.getPendingOffers().size());
    }

    @Test
    public void GivenConcurrentAppointments_WhenAssignStaff_ThenConsistentState() throws InterruptedException {
        int threads = 50;
        UUID memberId = UUID.randomUUID();
        Member member = new Member(memberId, "staffer", "s@s.com", "p");
        memberRepo.saveIfUsernameAndEmailAvailable(member);

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    member.addStaffAppointment("Comp-" + index, 
                        new StaffAppointment("Comp-" + index, UUID.randomUUID(), StaffAppointment.StaffRole.MANAGER, Collections.emptySet()));
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(5, TimeUnit.SECONDS));
        executor.shutdown();

        int count = 0;
        for (int i = 0; i < threads; i++) {
            if (member.getStaffAppointment("Comp-" + i) != null) count++;
        }
        assertEquals(threads, count);
    }
}
