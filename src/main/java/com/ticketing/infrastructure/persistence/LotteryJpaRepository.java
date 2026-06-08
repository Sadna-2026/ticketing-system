package com.ticketing.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ticketing.domain.lottery.LotteryEntry;

/**
 * Spring Data JPA repository for the {@link LotteryEntry} aggregate root.
 * Provides the derived queries the {@link JpaLotteryRepository} adapter needs to
 * satisfy the {@code ILotteryRepository} contract. Only instantiated when the JPA
 * persistence profile is active (see {@link JpaLotteryRepository}).
 */
public interface LotteryJpaRepository extends JpaRepository<LotteryEntry, UUID> {

    List<LotteryEntry> findByEventId(UUID eventId);

    List<LotteryEntry> findByMemberId(UUID memberId);

    Optional<LotteryEntry> findByEventIdAndMemberId(UUID eventId, UUID memberId);
}
