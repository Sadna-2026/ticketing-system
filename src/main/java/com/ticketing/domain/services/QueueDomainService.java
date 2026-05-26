package com.ticketing.domain.services;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ticketing.application.ISystemClock;
import com.ticketing.application.dto.QueueEntryDto;
import com.ticketing.application.dto.VirtualQueueDto;
import com.ticketing.domain.event.IEventRepository;
import com.ticketing.domain.exception.OptimisticLockException;
import com.ticketing.domain.queue.IQueueRepository;
import com.ticketing.domain.queue.QueueConfig;
import com.ticketing.domain.queue.QueueEntry;
import com.ticketing.domain.queue.VirtualQueue;

@org.springframework.stereotype.Service
public class QueueDomainService {

    private static final Logger log = LoggerFactory.getLogger(QueueDomainService.class);

    private final IQueueRepository queueRepository;
    private final IEventRepository eventRepository;
    private final ISystemClock systemClock;

    public QueueDomainService(IQueueRepository queueRepository, IEventRepository eventRepository, ISystemClock systemClock) {
        this.queueRepository = queueRepository;
        this.eventRepository = eventRepository;
        this.systemClock = systemClock;
    }

    private void saveQueue(VirtualQueue queue) {
        try {
            queueRepository.save(queue);
        } catch (OptimisticLockException ex) {
            log.warn("Queue save conflict: queueId={}", queue.getId());
            throw ex;
        }
    }

    public UUID createQueue(UUID eventId, int threshold, int flowRate) {
        eventRepository.findById(eventId)
                .orElseThrow(() -> {
                    log.warn("Event not found: eventId={}", eventId);
                    return new IllegalArgumentException("Event not found: " + eventId);
                });

        queueRepository.findByEventId(eventId).ifPresent(existing -> {
            log.warn("A virtual queue already exists for this event: eventId={}", eventId);
            throw new IllegalStateException("A virtual queue already exists for this event");
        });

        QueueConfig config = new QueueConfig(threshold, flowRate);
        VirtualQueue queue = new VirtualQueue(UUID.randomUUID(), eventId, config);
        saveQueue(queue);
        log.info("Queue created: queueId={}, eventId={}", queue.getId(), eventId);
        return queue.getId();
    }

    public QueueEntryDto tryEnterOrQueue(UUID eventId, UUID sessionId) {
        log.info("Try enter or queue: eventId={}, sessionId={}", eventId, sessionId);

        VirtualQueue queue = queueRepository.findByEventId(eventId).orElse(null);
        if (queue == null || !queue.isActive()) {
            return null;
        }

        if (queue.shouldQueue()) {
            QueueEntry entry = queue.enqueue(sessionId, systemClock.now());
            saveQueue(queue);
            log.info("User queued: sessionId={}, eventId={}", sessionId, eventId);
            return entry.toQueueDto();
        } else {
            queue.userEnteredDirectly();
            saveQueue(queue);
            return null;
        }
    }

    public List<QueueEntryDto> admitNextBatch(UUID eventId) {
        VirtualQueue queue = findQueueByEvent(eventId);
        List<QueueEntry> admitted = queue.admitNextBatch();
        saveQueue(queue);
        log.info("Admitted {} users from queue for eventId={}", admitted.size(), eventId);
        return admitted.stream()
                .map(QueueEntry::toQueueDto)
                .collect(Collectors.toList());
    }

    public void userLeft(UUID eventId) {
        VirtualQueue queue = queueRepository.findByEventId(eventId).orElse(null);
        if (queue != null) {
            queue.userLeft();
            saveQueue(queue);
        }
    }

    public void updateQueueConfig(UUID eventId, int threshold, int flowRate) {
        VirtualQueue queue = findQueueByEvent(eventId);
        queue.updateConfig(new QueueConfig(threshold, flowRate));
        saveQueue(queue);
        log.info("Queue config updated: eventId={}", eventId);
    }

    public void flushQueue(UUID eventId) {
        VirtualQueue queue = findQueueByEvent(eventId);
        queue.flush();
        saveQueue(queue);
        log.info("Queue flushed: eventId={}", eventId);
    }

    public List<VirtualQueueDto> getAllActiveQueues() {
        log.info("Getting all active queues");
        return queueRepository.findAllActive().stream()
                .map(VirtualQueue::toVirtualQueueDto)
                .collect(Collectors.toList());
    }

    public VirtualQueueDto getQueueForEvent(UUID eventId) {
        return findQueueByEvent(eventId).toVirtualQueueDto();
    }

    private VirtualQueue findQueueByEvent(UUID eventId) {
        return queueRepository.findByEventId(eventId)
                .orElseThrow(() -> new IllegalStateException("No virtual queue for event: " + eventId));
    }
}
