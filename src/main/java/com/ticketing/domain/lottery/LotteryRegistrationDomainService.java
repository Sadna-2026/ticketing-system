package com.ticketing.domain.lottery;

import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ticketing.domain.event.Event;

public class LotteryRegistrationDomainService {
    private static final Logger log = LoggerFactory.getLogger(LotteryRegistrationDomainService.class);

    private final ILotteryRepository lotteryRepository;

    public LotteryRegistrationDomainService(ILotteryRepository lotteryRepository) {
        this.lotteryRepository = lotteryRepository;
    }

    public LotteryEntry registerMember(Event event, UUID memberId, UUID zoneId, int quantity, Instant now) {
        if (!event.isLottery()) {
            throw new IllegalStateException("Event does not support lottery sale method.");
        }

        if (!event.isLotteryRegistrationOpen(now)) {
            throw new IllegalStateException("Lottery registration window is closed.");
        }

        if (lotteryRepository.findByEventAndMember(event.getId(), memberId).isPresent()) {
            throw new IllegalStateException("Member is already registered for this lottery.");
        }

        event.findZone(zoneId);

        LotteryEntry entry = new LotteryEntry(
                UUID.randomUUID(),
                event.getId(),
                memberId,
                zoneId,
                quantity,
                now
        );

        lotteryRepository.save(entry);
        
        log.info("Lottery registration successful: memberId={}, eventId={}, entryId={}",
                memberId, event.getId(), entry.id());
                
        return entry;
    }
}
