package com.ticketing.presentation.vaadin.presenters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.ticketing.application.dto.EventMapDTO;
import com.ticketing.application.dto.QueueEntryDto;
import com.ticketing.application.dto.VirtualQueueDto;
import com.ticketing.application.services.EventService;
import com.ticketing.application.services.OrderService;
import com.ticketing.presentation.vaadin.presenters.QueuePresenter.QueueResult;
import com.ticketing.presentation.vaadin.presenters.QueuePresenter.QueueStatusResult;
import com.ticketing.presentation.vaadin.testsupport.VaadinSessionExtension;
import com.ticketing.presentation.vaadin.util.SessionContext;

@DisplayName("QueuePresenter")
@ExtendWith(VaadinSessionExtension.class)
class QueuePresenterTest {

    private OrderService orderService;
    private EventService eventService;
    private QueuePresenter presenter;

    private static final UUID SESSION_ID = UUID.randomUUID();
    private static final UUID EVENT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        orderService = mock(OrderService.class);
        eventService = mock(EventService.class);
        presenter = new QueuePresenter(orderService, eventService);
    }

    // ── tryEnterOrQueue ──────────────────────────────────────────────

    @Test
    void GivenNoSession_WhenJoiningQueue_ThenNoServiceCallAndSessionErrorReturned() {
        QueueResult result = presenter.tryEnterOrQueue(EVENT_ID);

        assertFalse(result.success());
        assertFalse(result.queued());
        assertEquals("Start a guest or member session before joining a queue.", result.message());
        verifyNoInteractions(orderService);
    }

    @Test
    void GivenSession_WhenJoiningQueueWithNullEventId_ThenNoServiceCallAndEventIdErrorReturned() {
        guestSession();

        QueueResult result = presenter.tryEnterOrQueue(null);

        assertFalse(result.success());
        assertFalse(result.queued());
        assertEquals("Enter an event ID before joining the queue.", result.message());
        verifyNoInteractions(orderService);
    }

    @Test
    void GivenActiveQueueAtCapacity_WhenJoiningQueue_ThenUserIsPlacedInQueueAndEntryReturned() {
        guestSession();
        QueueEntryDto entry = queueEntry(SESSION_ID, "WAITING");
        when(eventService.getEventMap(EVENT_ID)).thenReturn(Optional.of(mock(EventMapDTO.class)));
        when(orderService.tryEnterOrQueue(EVENT_ID, SESSION_ID)).thenReturn(entry);

        QueueResult result = presenter.tryEnterOrQueue(EVENT_ID);

        assertTrue(result.success());
        assertTrue(result.queued());
        assertSame(entry, result.entry());
        assertTrue(result.message().contains("WAITING"));
        verify(orderService).tryEnterOrQueue(EVENT_ID, SESSION_ID);
    }

    @Test
    void GivenNoActiveQueueOrCapacityAvailable_WhenJoiningQueue_ThenUserAdmittedDirectlyWithNoEntry() {
        guestSession();
        when(eventService.getEventMap(EVENT_ID)).thenReturn(Optional.of(mock(EventMapDTO.class)));
        when(orderService.tryEnterOrQueue(EVENT_ID, SESSION_ID)).thenReturn(null);

        QueueResult result = presenter.tryEnterOrQueue(EVENT_ID);

        assertTrue(result.success());
        assertFalse(result.queued());
        assertNull(result.entry());
        assertTrue(result.message().contains("admitted directly"));
        verify(orderService).tryEnterOrQueue(EVENT_ID, SESSION_ID);
    }

    @Test
    void GivenEventNotFound_WhenJoiningQueue_ThenEventNotFoundErrorReturned() {
        guestSession();
        when(eventService.getEventMap(EVENT_ID)).thenReturn(Optional.empty());

        QueueResult result = presenter.tryEnterOrQueue(EVENT_ID);

        assertFalse(result.success());
        assertEquals("Event not found.", result.message());
        verifyNoInteractions(orderService);
    }

    @Test
    void GivenUnexpectedError_WhenJoiningQueue_ThenGenericUserFacingMessageReturned() {
        guestSession();
        when(eventService.getEventMap(EVENT_ID)).thenReturn(Optional.of(mock(EventMapDTO.class)));
        when(orderService.tryEnterOrQueue(EVENT_ID, SESSION_ID))
                .thenThrow(new RuntimeException("internal error"));

        QueueResult result = presenter.tryEnterOrQueue(EVENT_ID);

        assertFalse(result.success());
        assertEquals("Could not join the queue. Please try again.", result.message());
    }

    // ── checkQueueStatus ────────────────────────────────────────────

    @Test
    void GivenNoSession_WhenCheckingStatus_ThenNoServiceCallAndSessionErrorReturned() {
        QueueStatusResult result = presenter.checkQueueStatus(EVENT_ID);

        assertFalse(result.success());
        assertFalse(result.inQueue());
        assertEquals("Start a guest or member session before joining a queue.", result.message());
        verifyNoInteractions(orderService);
    }

    @Test
    void GivenSessionAndUserIsWaiting_WhenCheckingStatus_ThenWaitingResultReturned() {
        guestSession();
        QueueEntryDto entry = queueEntry(SESSION_ID, "WAITING");
        VirtualQueueDto queue = virtualQueue(EVENT_ID, List.of(entry));
        when(orderService.getQueueForEvent(EVENT_ID)).thenReturn(queue);

        QueueStatusResult result = presenter.checkQueueStatus(EVENT_ID);

        assertTrue(result.success());
        assertFalse(result.admitted());
        assertTrue(result.inQueue());
        assertSame(entry, result.entry());
        assertTrue(result.message().contains("WAITING"));
    }

    @Test
    void GivenUserAdmitted_WhenCheckingStatus_ThenAdmittedResultWithYourTurnMessageReturned() {
        guestSession();
        QueueEntryDto entry = queueEntry(SESSION_ID, "ADMITTED");
        VirtualQueueDto queue = virtualQueue(EVENT_ID, List.of(entry));
        when(orderService.getQueueForEvent(EVENT_ID)).thenReturn(queue);

        QueueStatusResult result = presenter.checkQueueStatus(EVENT_ID);

        assertTrue(result.success());
        assertTrue(result.admitted());
        assertTrue(result.inQueue());
        assertSame(entry, result.entry());
        assertTrue(result.message().contains("your turn"));
    }

    @Test
    void GivenUserNotInQueue_WhenCheckingStatus_ThenNotInQueueResultReturned() {
        guestSession();
        UUID otherSession = UUID.randomUUID();
        QueueEntryDto otherEntry = queueEntry(otherSession, "WAITING");
        VirtualQueueDto queue = virtualQueue(EVENT_ID, List.of(otherEntry));
        when(orderService.getQueueForEvent(EVENT_ID)).thenReturn(queue);

        QueueStatusResult result = presenter.checkQueueStatus(EVENT_ID);

        assertTrue(result.success());
        assertFalse(result.admitted());
        assertFalse(result.inQueue());
        assertNull(result.entry());
        assertTrue(result.message().contains("not in the queue"));
    }

    @Test
    void GivenNoQueueExistsForEvent_WhenCheckingStatus_ThenNotInQueueResultReturned() {
        guestSession();
        when(orderService.getQueueForEvent(EVENT_ID))
                .thenThrow(new IllegalStateException("No virtual queue for event: " + EVENT_ID));

        QueueStatusResult result = presenter.checkQueueStatus(EVENT_ID);

        assertTrue(result.success());
        assertFalse(result.inQueue());
        assertTrue(result.message().contains("No active queue"));
    }

    @Test
    void GivenUnexpectedError_WhenCheckingStatus_ThenGenericUserFacingMessageReturned() {
        guestSession();
        when(orderService.getQueueForEvent(EVENT_ID))
                .thenThrow(new RuntimeException("db timeout"));

        QueueStatusResult result = presenter.checkQueueStatus(EVENT_ID);

        assertFalse(result.success());
        assertEquals("Could not check queue status. Please try again.", result.message());
    }

    // ── helpers ─────────────────────────────────────────────────────

    private void guestSession() {
        SessionContext.setSessionToken("guest-token");
        SessionContext.setSessionId(SESSION_ID);
    }

    private static QueueEntryDto queueEntry(UUID sessionId, String status) {
        return new QueueEntryDto(UUID.randomUUID(), sessionId, Instant.now(), status);
    }

    private static VirtualQueueDto virtualQueue(UUID eventId, List<QueueEntryDto> entries) {
        return new VirtualQueueDto(UUID.randomUUID(), eventId, 100, 10, true, 50, entries.size(), entries);
    }
}
