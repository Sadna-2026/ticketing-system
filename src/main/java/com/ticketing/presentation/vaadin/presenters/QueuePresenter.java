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
     * Checks the current session's entry in the queue for the given event.
     * Returns ADMITTED when it is the user's turn.
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
                return QueueStatusResult.admitted("It's your turn! You have been admitted.", myEntry);
            }
            return QueueStatusResult.waiting(
                    "You are still in the virtual queue. Status: " + myEntry.getStatus() + ".", myEntry);
        } catch (IllegalStateException ex) {
            return QueueStatusResult.notInQueue("No active queue for this event.");
        } catch (RuntimeException ex) {
            logger.warn(STATUS_FAILURE_MESSAGE, ex);
            return QueueStatusResult.failure(STATUS_FAILURE_MESSAGE);
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
            QueueEntryDto entry) {

        public static QueueStatusResult admitted(String message, QueueEntryDto entry) {
            return new QueueStatusResult(true, true, true, message, entry);
        }

        public static QueueStatusResult waiting(String message, QueueEntryDto entry) {
            return new QueueStatusResult(true, false, true, message, entry);
        }

        public static QueueStatusResult notInQueue(String message) {
            return new QueueStatusResult(true, false, false, message, null);
        }

        public static QueueStatusResult failure(String message) {
            return new QueueStatusResult(false, false, false, message, null);
        }
    }
}
