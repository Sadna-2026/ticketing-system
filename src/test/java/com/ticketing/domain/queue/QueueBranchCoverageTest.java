package com.ticketing.domain.queue;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class QueueBranchCoverageTest {

    @Test
    void GivenQueueConfig_WhenConstructingAndComparing_ThenValidationAndEqualityBranchesAreCovered() {
        QueueConfig config = new QueueConfig(2, 3);
        QueueConfig same = new QueueConfig(2, 3);
        QueueConfig different = new QueueConfig(3, 2);

        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> new QueueConfig(0, 1)),
                () -> assertThrows(IllegalArgumentException.class, () -> new QueueConfig(1, 0)),
                () -> assertEquals(2, config.getThreshold()),
                () -> assertEquals(3, config.getFlowRate()),
                () -> assertEquals(config, config),
                () -> assertEquals(config, same),
                () -> assertNotEquals(config, different),
                () -> assertNotEquals(config, null),
                () -> assertNotEquals(config, "config"),
                () -> assertEquals(config.hashCode(), same.hashCode())
        );
    }

    @Test
    void GivenQueueEntry_WhenTransitioningAndCopying_ThenBranchesAreCovered() {
        UUID id = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        Instant joinedAt = Instant.parse("2026-01-01T00:00:00Z");
        QueueEntry entry = new QueueEntry(id, sessionId, joinedAt);
        QueueEntry sameId = new QueueEntry(id, UUID.randomUUID(), joinedAt.plusSeconds(1));
        QueueEntry different = new QueueEntry(UUID.randomUUID(), sessionId, joinedAt);

        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> new QueueEntry(null, sessionId, joinedAt)),
                () -> assertThrows(IllegalArgumentException.class, () -> new QueueEntry(id, null, joinedAt)),
                () -> assertThrows(IllegalArgumentException.class, () -> new QueueEntry(id, sessionId, null)),
                () -> assertTrue(entry.isWaiting()),
                () -> assertEquals(QueueEntryStatus.WAITING, entry.getStatus()),
                () -> assertNotNull(entry.toQueueDto()),
                () -> assertEquals(entry, entry),
                () -> assertEquals(entry, sameId),
                () -> assertNotEquals(entry, different),
                () -> assertNotEquals(entry, null),
                () -> assertNotEquals(entry, "entry")
        );

        entry.admit();
        assertEquals(QueueEntryStatus.ADMITTED, entry.getStatus());
        assertFalse(entry.isWaiting());
        assertThrows(IllegalStateException.class, entry::admit);
        entry.leave();
        assertEquals(QueueEntryStatus.LEFT, entry.getStatus());

        QueueEntry copy = entry.detachedCopy();
        assertEquals(QueueEntryStatus.LEFT, copy.getStatus());
        assertEquals(entry, copy);
    }

    @Test
    void GivenVirtualQueue_WhenConstructingInvalidInputs_ThenValidationRejectsThem() {
        UUID id = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        QueueConfig config = new QueueConfig(2, 1);

        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> new VirtualQueue(null, eventId, config)),
                () -> assertThrows(IllegalArgumentException.class, () -> new VirtualQueue(id, null, config)),
                () -> assertThrows(IllegalArgumentException.class, () -> new VirtualQueue(id, eventId, null))
        );
    }

    @Test
    void GivenVirtualQueue_WhenUsersEnterAdmitFlushAndCopy_ThenBranchesAreCovered() {
        UUID id = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        VirtualQueue queue = new VirtualQueue(id, eventId, new QueueConfig(2, 2));
        Instant base = Instant.parse("2026-01-01T00:00:00Z");

        assertAll(
                () -> assertEquals(id, queue.getId()),
                () -> assertEquals(eventId, queue.getEventId()),
                () -> assertTrue(queue.isActive()),
                () -> assertFalse(queue.shouldQueue()),
                () -> assertEquals(0, queue.getCurrentActiveUsers()),
                () -> assertEquals(0, queue.getWaitingCount()),
                () -> assertEquals(0, queue.getVersion()),
                () -> assertNotNull(queue.toVirtualQueueDto()),
                () -> assertThrows(IllegalArgumentException.class, () -> queue.updateConfig(null))
        );

        queue.userLeft();
        assertEquals(0, queue.getCurrentActiveUsers());
        queue.userEnteredDirectly();
        assertFalse(queue.shouldQueue());
        queue.userEnteredDirectly();
        assertTrue(queue.shouldQueue());

        QueueEntry later = queue.enqueue(UUID.randomUUID(), base.plusSeconds(10));
        QueueEntry earlier = queue.enqueue(UUID.randomUUID(), base);
        QueueEntry latest = queue.enqueue(UUID.randomUUID(), base.plusSeconds(20));
        assertEquals(3, queue.getWaitingCount());

        List<QueueEntry> admitted = queue.admitNextBatch();
        assertEquals(2, admitted.size());
        assertEquals(earlier.getId(), admitted.get(0).getId());
        assertEquals(later.getId(), admitted.get(1).getId());
        assertEquals(1, queue.getWaitingCount());
        assertTrue(latest.isWaiting());

        queue.flush();
        assertEquals(0, queue.getWaitingCount());
        assertEquals(QueueEntryStatus.LEFT, latest.getStatus());

        queue.deactivate();
        assertFalse(queue.isActive());
        assertFalse(queue.shouldQueue());
        queue.activate();
        assertTrue(queue.isActive());
        assertTrue(queue.shouldQueue());

        queue.updateConfig(new QueueConfig(5, 1));
        assertEquals(5, queue.getConfig().getThreshold());
        queue.incrementVersion();
        assertEquals(1, queue.getVersion());

        VirtualQueue copy = queue.detachedCopy();
        assertEquals(queue.getId(), copy.getId());
        assertEquals(queue.getEntries().size(), copy.getEntries().size());
        assertThrows(UnsupportedOperationException.class, () -> queue.getEntries().add(earlier));
    }

    @Test
    void GivenVirtualQueues_WhenComparing_ThenEqualityBranchesAreCovered() {
        UUID id = UUID.randomUUID();
        VirtualQueue first = new VirtualQueue(id, UUID.randomUUID(), new QueueConfig(1, 1));
        VirtualQueue sameId = new VirtualQueue(id, UUID.randomUUID(), new QueueConfig(2, 2));
        VirtualQueue different = new VirtualQueue(UUID.randomUUID(), UUID.randomUUID(), new QueueConfig(1, 1));

        assertAll(
                () -> assertEquals(first, first),
                () -> assertEquals(first, sameId),
                () -> assertNotEquals(first, different),
                () -> assertNotEquals(first, null),
                () -> assertNotEquals(first, "queue"),
                () -> assertEquals(first.hashCode(), sameId.hashCode())
        );
    }
}
