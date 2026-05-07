package com.ticketing.domain.member;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.UUID;

import org.junit.jupiter.api.Test;

public class PendingRoleOfferTest {

    @Test
    public void GivenDueDateInFuture_WhenIsExpired_ThenReturnsFalse() {
        LocalDateTime futureDueDate = LocalDateTime.now().plusDays(1);
        PendingRoleOffer offer = new PendingRoleOffer(UUID.randomUUID(), "TestCo", StaffAppointment.StaffRole.MANAGER, Collections.emptySet(), futureDueDate);

        assertFalse(offer.isExpired());
    }

    @Test
    public void GivenDueDateInPast_WhenIsExpired_ThenReturnsTrue() {
        LocalDateTime pastDueDate = LocalDateTime.now().minusDays(1);
        PendingRoleOffer offer = new PendingRoleOffer(UUID.randomUUID(), "TestCo", StaffAppointment.StaffRole.MANAGER, Collections.emptySet(), pastDueDate);

        assertTrue(offer.isExpired());
    }

    @Test
    public void GivenDueDateNow_WhenIsExpired_ThenReturnsFalse() {
        LocalDateTime now = LocalDateTime.now().plusSeconds(1);
        PendingRoleOffer offer = new PendingRoleOffer(UUID.randomUUID(), "TestCo", StaffAppointment.StaffRole.MANAGER, Collections.emptySet(), now);

        assertFalse(offer.isExpired());
    }
}