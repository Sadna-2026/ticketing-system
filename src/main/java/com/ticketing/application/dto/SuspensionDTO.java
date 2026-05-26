package com.ticketing.application.dto;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.ticketing.domain.member.Suspension;

/**
 * Read-only snapshot of a suspension record for admin viewing (UC-II.6.9).
 */
public record SuspensionDTO(
        UUID suspensionId,
        UUID memberId,
        String memberUsername,
        UUID imposedByAdminId,
        Instant startTime,
        Duration duration,
        Instant endTime,
        boolean permanent,
        boolean active,
        boolean cancelled,
        String reason
) {
    public static SuspensionDTO from(Suspension s, UUID memberId, String memberUsername, Instant now) {
        return new SuspensionDTO(
                s.getSuspensionId(),
                memberId,
                memberUsername,
                s.getImposedByAdminId(),
                s.getStartTime(),
                s.getDuration(),
                s.getEndTime(),
                s.isPermanent(),
                s.isActive(now),
                s.isCancelled(),
                s.getReason()
        );
    }
}
