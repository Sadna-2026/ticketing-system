package com.ticketing.infrastructure;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import com.ticketing.domain.exception.OptimisticLockException;
import com.ticketing.domain.lottery.ILotteryRepository;
import com.ticketing.domain.lottery.LotteryEntry;

@Repository
@ConditionalOnProperty(name = "ticketing.persistence", havingValue = "memory", matchIfMissing = true)
public class InMemoryLotteryRepository implements ILotteryRepository {

    private final ConcurrentHashMap<UUID, LotteryEntry> store = new ConcurrentHashMap<>();

    @Override
    public Optional<LotteryEntry> findById(UUID id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<LotteryEntry> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public List<LotteryEntry> findByEventId(UUID eventId) {
        if (eventId == null) return List.of();
        List<LotteryEntry> hits = new ArrayList<>();
        for (LotteryEntry e : store.values()) {
            if (eventId.equals(e.eventId())) hits.add(e);
        }
        return hits;
    }

    @Override
    public List<LotteryEntry> findByMemberId(UUID memberId) {
        if (memberId == null) return List.of();
        List<LotteryEntry> hits = new ArrayList<>();
        for (LotteryEntry e : store.values()) {
            if (memberId.equals(e.memberId())) hits.add(e);
        }
        return hits;
    }

    @Override
    public Optional<LotteryEntry> findByEventAndMember(UUID eventId, UUID memberId) {
        if (eventId == null || memberId == null) return Optional.empty();
        for (LotteryEntry e : store.values()) {
            if (eventId.equals(e.eventId()) && memberId.equals(e.memberId())) {
                return Optional.of(e);
            }
        }
        return Optional.empty();
    }

    @Override
    public void save(LotteryEntry entry) {
        if (entry == null) throw new IllegalArgumentException("entry is required");
        store.compute(entry.id(), (id, existing) -> {
            if (existing == null) {
                LotteryEntry stored = entry.incrementedVersionCopy();
                return stored;
            }
            if (entry.version() != existing.version()) {
                throw new OptimisticLockException("LotteryEntry", id);
            }
            LotteryEntry stored = entry.incrementedVersionCopy();
            return stored;
        });
    }

    @Override
    public void delete(UUID id) {
        if (id == null) return;
        store.remove(id);
    }

    @Override
    public void deleteAll() {
        store.clear();
    }
}
