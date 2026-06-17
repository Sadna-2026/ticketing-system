package com.ticketing.application.scheduling;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import com.ticketing.application.ISystemClock;
import com.ticketing.application.services.EventService;
import com.ticketing.domain.event.Event;
import com.ticketing.domain.event.IEventRepository;

/**
 * Fires the automatic purchase-right lottery draw (requirement §II.3.6) the moment an event's
 * registration window closes — winners are then notified and the sale opens to them. Replaces
 * the old manual "Draw lottery" button.
 *
 * <p>Each lottery event gets a single one-shot task scheduled at its {@code registrationClose}
 * instant. On startup ({@link #rescheduleOnStartup}) every persisted lottery event is rearmed,
 * so the draw survives restarts (V3 §6 recovery / §7 persistence); a close time already in the
 * past runs almost immediately. The draw itself ({@link EventService#drawLotteryAutomatically})
 * is idempotent, so a duplicate or replayed trigger is a harmless no-op.
 */
@Component
public class LotteryDrawScheduler {

    private static final Logger log = LoggerFactory.getLogger(LotteryDrawScheduler.class);

    private final EventService eventService;
    private final IEventRepository eventRepository;
    private final TaskScheduler taskScheduler;
    private final ISystemClock clock;

    /** Tracks the live timer per event so an edited window cancels and replaces the old one. */
    private final ConcurrentHashMap<UUID, ScheduledFuture<?>> scheduled = new ConcurrentHashMap<>();

    public LotteryDrawScheduler(EventService eventService,
            IEventRepository eventRepository,
            TaskScheduler taskScheduler,
            ISystemClock clock) {
        this.eventService = eventService;
        this.eventRepository = eventRepository;
        this.taskScheduler = taskScheduler;
        this.clock = clock;
    }

    /** Rearm draws for all persisted lottery events after the context is ready. */
    @EventListener(ApplicationReadyEvent.class)
    public void rescheduleOnStartup() {
        int armed = 0;
        for (Event event : eventRepository.findAll()) {
            if (scheduleDrawFor(event)) {
                armed++;
            }
        }
        log.info("Lottery draw scheduler armed {} event(s) on startup", armed);
    }

    /** Reacts to a lottery event being published or having its window edited. */
    @EventListener
    public void onLotteryScheduleEvent(LotteryScheduleEvent event) {
        eventRepository.findById(event.eventId()).ifPresent(this::scheduleDrawFor);
    }

    /**
     * Arms (or rearms) the one-shot draw for a lottery event. No-op for non-lottery or
     * window-less events; cancels any timer for a cancelled event. Returns whether a draw
     * was scheduled.
     */
    public boolean scheduleDrawFor(Event event) {
        if (event == null || !event.isLottery() || event.getLotteryWindow() == null) {
            return false;
        }
        if (event.isCancelled()) {
            cancel(event.getId());
            return false;
        }
        scheduleDraw(event.getId(), event.getLotteryWindow().registrationClose());
        return true;
    }

    private void scheduleDraw(UUID eventId, Instant registrationClose) {
        ScheduledFuture<?> previous = scheduled.remove(eventId);
        if (previous != null) {
            previous.cancel(false);
        }
        Instant now = clock.now();
        // A close time already in the past runs shortly from now, on the scheduler thread,
        // so it executes after the publishing/editing transaction has committed.
        Instant runAt = registrationClose.isAfter(now) ? registrationClose : now.plusSeconds(1);
        ScheduledFuture<?> future = taskScheduler.schedule(() -> runDraw(eventId), runAt);
        scheduled.put(eventId, future);
        log.info("Scheduled automatic lottery draw: eventId={} at {}", eventId, runAt);
    }

    private void runDraw(UUID eventId) {
        scheduled.remove(eventId);
        try {
            int winners = eventService.drawLotteryAutomatically(eventId).size();
            log.info("Automatic lottery draw completed: eventId={}, winners={}", eventId, winners);
        } catch (RuntimeException ex) {
            log.warn("Automatic lottery draw failed: eventId={}: {}", eventId, ex.getMessage());
        }
    }

    private void cancel(UUID eventId) {
        ScheduledFuture<?> future = scheduled.remove(eventId);
        if (future != null) {
            future.cancel(false);
        }
    }
}
