package com.ticketing.presentation.vaadin.presenters;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.ticketing.application.dto.QueueEntryDto;
import com.ticketing.application.dto.VirtualQueueDto;
import com.ticketing.application.services.EventService;
import com.ticketing.application.services.OrderService;
import com.ticketing.presentation.vaadin.util.SessionContext;

@Component
public class QueuePresenter {

    private static final Logger logger = LoggerFactory.getLogger(QueuePresenter.class);

    private static final String NO_SESSION_MESSAGE = "Start a guest or member session before joining a queue.";
    private static final String NO_EVENT_MESSAGE = "Enter an event ID before joining the queue.";
    private static final String EVENT_NOT_FOUND_MESSAGE = "Event not found.";
    private static final String JOIN_FAILURE_MESSAGE = "Could not join the queue. Please try again.";
    private static final String STATUS_FAILURE_MESSAGE = "Could not check queue status. Please try again.";

    private final OrderService orderService;
    private final EventService eventService;

    public QueuePresenter(OrderService orderService, EventService eventService) {
        this.orderService = orderService;
        this.eventService = eventService;
    }

    /**
     * Explicit user action from QueueView: join the queue for an event.
     * Returns a failure result on errors so the form can show the reason.
     */
    public QueueResult tryEnterOrQueue(UUID eventId) {
        UUID sessionId = SessionContext.getSessionId();
        if (sessionId == null) {
            return QueueResult.failure(NO_SESSION_MESSAGE);
        }
        if (eventId == null) {
            return QueueResult.failure(NO_EVENT_MESSAGE);
        }
        try {
            if (eventService.getEventMap(eventId).isEmpty()) {
                return QueueResult.failure(EVENT_NOT_FOUND_MESSAGE);
            }
            QueueEntryDto entry = orderService.tryEnterOrQueue(eventId, sessionId);
            if (entry == null) {
                return QueueResult.directEntry(
                        "No queue is active for this event — you have been admitted directly.");
            }
            return QueueResult.queued(
                    "You have been placed in the virtual queue. Status: " + entry.getStatus() + ".", entry);
        } catch (IllegalArgumentException ex) {
            return QueueResult.failure(ex.getMessage());
        } catch (RuntimeException ex) {
            logger.warn(JOIN_FAILURE_MESSAGE, ex);
            return QueueResult.failure(JOIN_FAILURE_MESSAGE);
        }
    }

    /**
     * Transparent gate called by EventsView before loading the ticket map.
     * If the event's queue is at capacity and the session is not yet admitted,
     * returns QueueResult.queued so the caller can redirect to the waiting room.
     * Defaults to directEntry on any error so a broken queue never blocks the user.
     */
    public QueueResult enterQueueGate(UUID eventId) {
        UUID sessionId = SessionContext.getSessionId();
        if (sessionId == null || eventId == null) {
            return QueueResult.directEntry("No gate check needed.");
        }
        try {
            VirtualQueueDto existing;
            try {
                existing = orderService.getQueueForEvent(eventId);
            } catch (IllegalStateException ignored) {
                // No queue for this event — let the user through
                return QueueResult.directEntry("No active queue — entering directly.");
            }

            // If already admitted, let the user through
            boolean alreadyAdmitted = existing.getEntries().stream()
                    .anyMatch(e -> sessionId.equals(e.getSessionId()) && "ADMITTED".equals(e.getStatus()));
            if (alreadyAdmitted) {
                return QueueResult.directEntry("You have already been admitted — you can browse tickets.");
            }

            // If already waiting, redirect to waiting room without re-enqueuing
            boolean alreadyWaiting = existing.getEntries().stream()
                    .anyMatch(e -> sessionId.equals(e.getSessionId()) && "WAITING".equals(e.getStatus()));
            if (alreadyWaiting) {
                return QueueResult.queued("You are already in the virtual queue for this event.", null);
            }

            // Not in queue yet — try to enter
            QueueEntryDto entry = orderService.tryEnterOrQueue(eventId, sessionId);
            if (entry == null) {
                return QueueResult.directEntry("No queue active — entering directly.");
            }
            return QueueResult.queued("You have been placed in the virtual queue.", entry);
        } catch (RuntimeException ex) {
            logger.warn("Queue gate check failed for event {}, allowing through", eventId, ex);
            return QueueResult.directEntry("Queue check skipped — entering directly.");
        }
    }

    /**
     * Returns the event display name for the waiting room header, or null if not found.
     */
    public String loadEventName(UUID eventId) {
        if (eventId == null) {
            return null;
        }
        try {
            return eventService.getEventMap(eventId)
                    .map(map -> map.eventName())
                    .orElse(null);
        } catch (RuntimeException ex) {
            logger.warn("Could not load event name for {}", eventId, ex);
            return null;
        }
    }

    /**
     * Checks the current session's position and status in the queue.
     * Returns ADMITTED when it is the user's turn.
     * {@code waitingAhead} is the approximate number of people ahead of this session.
     */
    public QueueStatusResult checkQueueStatus(UUID eventId) {
        UUID sessionId = SessionContext.getSessionId();
        if (sessionId == null) {
            return QueueStatusResult.failure(NO_SESSION_MESSAGE);
        }
        if (eventId == null) {
            return QueueStatusResult.failure(NO_EVENT_MESSAGE);
        }
        try {
            VirtualQueueDto queue = orderService.getQueueForEvent(eventId);
            QueueEntryDto myEntry = queue.getEntries().stream()
                    .filter(e -> sessionId.equals(e.getSessionId()))
                    .findFirst()
                    .orElse(null);
            if (myEntry == null) {
                return QueueStatusResult.notInQueue("You are not in the queue for this event.");
            }
            if ("ADMITTED".equals(myEntry.getStatus())) {
                return QueueStatusResult.admitted("It's your turn! You have been admitted.", myEntry, 0);
            }
            // Count WAITING entries that joined before this session
            int waitingAhead = (int) queue.getEntries().stream()
                    .filter(e -> "WAITING".equals(e.getStatus())
                            && !sessionId.equals(e.getSessionId())
                            && myEntry.getJoinedAt() != null
                            && e.getJoinedAt() != null
                            && e.getJoinedAt().isBefore(myEntry.getJoinedAt()))
                    .count();
            return QueueStatusResult.waiting(
                    "You are still in the virtual queue. Status: " + myEntry.getStatus() + ".",
                    myEntry, waitingAhead);
        } catch (IllegalStateException ex) {
            return QueueStatusResult.notInQueue("No active queue for this event.");
        } catch (RuntimeException ex) {
            logger.warn(STATUS_FAILURE_MESSAGE, ex);
            return QueueStatusResult.failure(STATUS_FAILURE_MESSAGE);
        }
    }

    /**
     * Notifies the queue that this session is done browsing tickets for the given event.
     * Safe to call even when no queue exists for the event.
     */
    public void notifyLeft(UUID eventId) {
        if (eventId == null) {
            return;
        }
        try {
            orderService.userLeft(eventId);
        } catch (RuntimeException ex) {
            logger.warn("userLeft notification failed for event {}", eventId, ex);
        }
    }

    public String currentSessionLabel() {
        return SessionContext.currentSessionLabel();
    }

    public SessionContext.UiState currentSessionState() {
        return SessionContext.currentUiState();
    }

    public record QueueResult(boolean success, boolean queued, String message, QueueEntryDto entry) {

        public static QueueResult queued(String message, QueueEntryDto entry) {
            return new QueueResult(true, true, message, entry);
        }

        public static QueueResult directEntry(String message) {
            return new QueueResult(true, false, message, null);
        }

        public static QueueResult failure(String message) {
            return new QueueResult(false, false, message, null);
        }
    }

    public record QueueStatusResult(boolean success, boolean admitted, boolean inQueue, String message,
            QueueEntryDto entry, int waitingAhead) {

        public static QueueStatusResult admitted(String message, QueueEntryDto entry, int waitingAhead) {
            return new QueueStatusResult(true, true, true, message, entry, waitingAhead);
        }

        public static QueueStatusResult waiting(String message, QueueEntryDto entry, int waitingAhead) {
            return new QueueStatusResult(true, false, true, message, entry, waitingAhead);
        }

        public static QueueStatusResult notInQueue(String message) {
            return new QueueStatusResult(true, false, false, message, null, -1);
        }

        public static QueueStatusResult failure(String message) {
            return new QueueStatusResult(false, false, false, message, null, -1);
        }
    }
}
