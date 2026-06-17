package com.ticketing.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.ticketing.application.auth.SessionToken;
import com.ticketing.domain.admin.Admin;
import com.ticketing.domain.exception.OptimisticLockException;
import com.ticketing.domain.lottery.LotteryEntry;
import com.ticketing.domain.member.Member;
import com.ticketing.domain.queue.QueueConfig;
import com.ticketing.domain.queue.VirtualQueue;

class InMemoryOptimisticLockingTest {

    @Test
    public void GivenTwoAdminSnapshots_WhenSecondSaveIsStale_ThenOptimisticLockException() {
        InMemoryAdminRepository repository = new InMemoryAdminRepository();
        UUID id = UUID.randomUUID();
        repository.save(new Admin(id, "root", "root@example.com", "encPw"));

        Admin first = repository.findById(id).orElseThrow();
        Admin second = repository.findById(id).orElseThrow();

        first.setEmail("first@example.com");
        repository.save(first);

        second.setEmail("second@example.com");
        assertThrows(OptimisticLockException.class, () -> repository.save(second));
    }

    @Test
    public void GivenTwoMemberSnapshots_WhenSecondSaveIsStale_ThenOptimisticLockException() {
        InMemoryMemberRepository repository = new InMemoryMemberRepository();
        UUID id = UUID.randomUUID();
        repository.saveIfUsernameAndEmailAvailable(new Member(id, "member", "member@example.com", "secret"));

        Member first = repository.findById(id).orElseThrow();
        Member second = repository.findById(id).orElseThrow();

        first.updatePhoneNumber("0501111111");
        repository.save(first);

        second.updatePhoneNumber("0502222222");
        assertThrows(OptimisticLockException.class, () -> repository.save(second));
    }

    @Test
    public void GivenTwoQueueSnapshots_WhenSecondSaveIsStale_ThenOptimisticLockException() {
        InMemoryQueueRepository repository = new InMemoryQueueRepository();
        UUID queueId = UUID.randomUUID();
        repository.save(new VirtualQueue(queueId, UUID.randomUUID(), new QueueConfig(2, 1)));

        VirtualQueue first = repository.findById(queueId).orElseThrow();
        VirtualQueue second = repository.findById(queueId).orElseThrow();

        first.userEnteredDirectly();
        repository.save(first);

        second.userEnteredDirectly();
        assertThrows(OptimisticLockException.class, () -> repository.save(second));
    }

    @Test
    public void GivenTwoLotterySnapshots_WhenSecondSaveIsStale_ThenOptimisticLockException() {
        InMemoryLotteryRepository repository = new InMemoryLotteryRepository();
        UUID entryId = UUID.randomUUID();
        LotteryEntry entry = new LotteryEntry(
                entryId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                Instant.now());
        repository.save(entry);

        LotteryEntry first = repository.findById(entryId).orElseThrow();
        LotteryEntry second = repository.findById(entryId).orElseThrow();

        repository.save(first);

        assertThrows(OptimisticLockException.class, () -> repository.save(second));
    }

    @Test
    public void GivenTwoSessionTokenSnapshots_WhenSecondSaveIsStale_ThenOptimisticLockException() {
        InMemorySessionTokenRepository repository = new InMemorySessionTokenRepository();
        UUID tokenId = UUID.randomUUID();
        Instant now = Instant.now();
        SessionToken token = new SessionToken(
                tokenId,
                UUID.randomUUID(),
                null,
                now,
                now.plusSeconds(60));
        repository.save(token);

        SessionToken first = repository.findByTokenId(tokenId).orElseThrow();
        SessionToken second = repository.findByTokenId(tokenId).orElseThrow();

        first.revoke("first");
        repository.save(first);

        second.revoke("second");
        assertThrows(OptimisticLockException.class, () -> repository.save(second));
    }

    @Test
    public void GivenConcurrentSavesOnDifferentQueues_WhenRunInParallel_ThenAllPersist() throws InterruptedException {
        InMemoryQueueRepository repository = new InMemoryQueueRepository();
        int count = 20;
        ExecutorService executor = Executors.newFixedThreadPool(count);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(count);
        AtomicInteger successes = new AtomicInteger();
        List<UUID> ids = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            UUID queueId = UUID.randomUUID();
            ids.add(queueId);
            executor.submit(() -> {
                try {
                    start.await();
                    repository.save(new VirtualQueue(queueId, UUID.randomUUID(), new QueueConfig(2, 1)));
                    successes.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        assertTrue(done.await(5, TimeUnit.SECONDS));
        executor.shutdown();

        assertEquals(count, successes.get());
        for (UUID id : ids) {
            assertTrue(repository.findById(id).isPresent());
        }
    }
}
