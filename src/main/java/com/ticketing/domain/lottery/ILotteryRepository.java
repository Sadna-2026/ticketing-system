package com.ticketing.domain.lottery;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ILotteryRepository {
    Optional<LotteryEntry> findById(UUID id);
    List<LotteryEntry> findAll();
    List<LotteryEntry> findByEventId(UUID eventId);
    List<LotteryEntry> findByMemberId(UUID memberId);
    Optional<LotteryEntry> findByEventAndMember(UUID eventId, UUID memberId);
    void save(LotteryEntry entry);
    void delete(UUID id);
}
