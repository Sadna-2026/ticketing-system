package com.ticketing.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ticketing.application.auth.ISessionTokenService;
import com.ticketing.domain.event.IEventRepository;
import com.ticketing.domain.member.IMemberRepository;
import com.ticketing.domain.member.Member;
import com.ticketing.domain.queue.IQueueRepository;
import com.ticketing.domain.queue.QueueConfig;
import com.ticketing.domain.queue.QueueEntry;
import com.ticketing.domain.queue.VirtualQueue;
import com.ticketing.infrastructure.Interface.ISessionTokenRepository;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Application Service: QueueService
 *
 * Manages virtual queue mechanics and admin control.
 * Admin operations (create, configure, flush) require JWT auth with System Admin role.
 * User operations (enter/leave) do not require authentication.
 */
public class QueueManagementService {

    private static final Logger log = LoggerFactory.getLogger(QueueManagementService.class);

    private final IQueueRepository queueRepository;
    private final IEventRepository eventRepository;
    private final IMemberRepository memberRepository;
    private final ISessionTokenService tokenService;
    private final ISystemClock systemClock;

    public QueueManagementService(IQueueRepository queueRepository,
                        IEventRepository eventRepository,
                        IMemberRepository memberRepository,
                        ISessionTokenService tokenService,
                        ISystemClock systemClock) {
        this.queueRepository = queueRepository;
        this.eventRepository = eventRepository;
        this.memberRepository = memberRepository;
        this.tokenService = tokenService;
        this.systemClock = systemClock;
    }

    /**
     * Creates a virtual queue for an event (Admin action).
     */
    public UUID createQueue(String token, UUID eventId, int threshold, int flowRate) {
        UUID adminId = validateAdmin(token);
        log.info("Creating queue: adminId={}, eventId={}, threshold={}, flowRate={}", adminId, eventId, threshold, flowRate);

        eventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Event not found: " + eventId));

        queueRepository.findByEventId(eventId).ifPresent(existing -> {
            throw new IllegalStateException("A virtual queue already exists for this event");
        });

        QueueConfig config = new QueueConfig(threshold, flowRate);
        VirtualQueue queue = new VirtualQueue(UUID.randomUUID(), eventId, config);
        queueRepository.save(queue);
        log.info("Queue created: queueId={}, eventId={}", queue.getId(), eventId);
        return queue.getId();
    }

    /**
     * Determines if a user should be queued, and if so, adds them.
     * No authentication required (guests can be queued).
     *
     * @return a QueueEntryDto if queued, null if user can enter directly
     */
    public QueueEntryDto tryEnterOrQueue(UUID eventId, UUID sessionId) {
        log.info("Try enter or queue: eventId={}, sessionId={}", eventId, sessionId);

        VirtualQueue queue = queueRepository.findByEventId(eventId).orElse(null);
        if (queue == null || !queue.isActive()) {
            return null;
        }

        if (queue.shouldQueue()) {
            QueueEntry entry = queue.enqueue(sessionId, systemClock.now());
            queueRepository.save(queue);
            log.info("User queued: sessionId={}, eventId={}", sessionId, eventId);
            return entry.toQueueDto();
        } else {
            queue.userEnteredDirectly();
            queueRepository.save(queue);
            return null;
        }
    }

    /**
     * Admits the next batch of users from the queue.
     */
    public List<QueueEntryDto> admitNextBatch(String token, UUID eventId) {
        validateAdmin(token);
        log.info("Admitting next batch: eventId={}", eventId);

        VirtualQueue queue = findQueueByEvent(eventId);
        List<QueueEntry> admitted = queue.admitNextBatch();
        queueRepository.save(queue);
        log.info("Admitted {} users from queue for eventId={}", admitted.size(), eventId);
        return admitted.stream()
                .map(QueueEntry::toQueueDto)
                .collect(Collectors.toList());
    }

    /**
     * Records that a user has left the active purchasing phase.
     */
    public void userLeft(UUID eventId) {
        VirtualQueue queue = queueRepository.findByEventId(eventId).orElse(null);
        if (queue != null) {
            queue.userLeft();
            queueRepository.save(queue);
        }
    }

    /**
     * Updates queue configuration (Admin action).
     */
    public void updateConfig(String token, UUID eventId, int threshold, int flowRate) {
        UUID adminId = validateAdmin(token);
        log.info("Updating queue config: adminId={}, eventId={}, threshold={}, flowRate={}", adminId, eventId, threshold, flowRate);

        VirtualQueue queue = findQueueByEvent(eventId);
        queue.updateConfig(new QueueConfig(threshold, flowRate));
        queueRepository.save(queue);
        log.info("Queue config updated: eventId={}", eventId);
    }

    /**
     * Flushes/clears a queue (Admin emergency action).
     */
    public void flushQueue(String token, UUID eventId) {
        UUID adminId = validateAdmin(token);
        log.info("Flushing queue: adminId={}, eventId={}", adminId, eventId);

        VirtualQueue queue = findQueueByEvent(eventId);
        queue.flush();
        queueRepository.save(queue);
        log.info("Queue flushed: eventId={}", eventId);
    }

    /**
     * Gets all active virtual queues (Admin monitoring).
     */
    public List<VirtualQueueDto> getAllActiveQueues(String token) {
        validateAdmin(token);
        log.info("Getting all active queues");
        return queueRepository.findAllActive().stream()
                .map(VirtualQueue::toVirtualQueueDto)
                .collect(Collectors.toList());
    }

    /**
     * Gets the queue for a specific event.
     */
    public VirtualQueueDto getQueueForEvent(UUID eventId) {
        return findQueueByEvent(eventId).toVirtualQueueDto();
    }

    private VirtualQueue findQueueByEvent(UUID eventId) {
        return queueRepository.findByEventId(eventId)
                .orElseThrow(() -> new IllegalStateException("No virtual queue for event: " + eventId));
    }

    private UUID validateAdmin(String token) {
        UUID memberId = validateToken(token);
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Member not found"));
        if (!tokenService.extractPermissions(token).contains("Admin")) {
            throw new IllegalStateException("Only System Admins can perform this action");
        }
        return memberId;
    }

    private UUID validateToken(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Authentication token is required");
        }
        if (!tokenService.isValid(token)) {
            throw new IllegalArgumentException("Invalid or expired authentication token");
        }
        return tokenService.extractMemberId(token);
    }
}

