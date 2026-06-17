package com.ticketing.domain.queue;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.annotation.Annotation;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.MappedSuperclass;

/**
 * V3-9: virtual queues are transient runtime state and must NOT be persisted to the DB.
 * This guard fails if a JPA mapping annotation is ever added to a queue type, so the
 * "queues stay in memory" decision can't silently regress. See CONTRIBUTING "Persistence (V3)".
 */
@DisplayName("Queue types stay transient (no JPA persistence)")
class QueueTransiencyTest {

    private static final List<Class<?>> QUEUE_TYPES = List.of(
            VirtualQueue.class, QueueEntry.class, QueueConfig.class, QueueEntryStatus.class);

    private static final List<Class<? extends Annotation>> JPA_MAPPING_ANNOTATIONS = List.of(
            Entity.class, Embeddable.class, MappedSuperclass.class);

    @Test
    void GivenQueueDomainTypes_WhenInspected_ThenNoneCarryJpaMappingAnnotations() {
        for (Class<?> type : QUEUE_TYPES) {
            for (Class<? extends Annotation> jpa : JPA_MAPPING_ANNOTATIONS) {
                assertFalse(type.isAnnotationPresent(jpa),
                        type.getSimpleName() + " must not be JPA-mapped (@" + jpa.getSimpleName()
                                + ") — virtual queues are transient and excluded from persistence (V3-9).");
            }
        }
    }
}
