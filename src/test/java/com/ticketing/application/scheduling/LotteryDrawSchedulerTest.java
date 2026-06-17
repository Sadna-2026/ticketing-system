package com.ticketing.application.scheduling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;

import com.ticketing.application.ISystemClock;
import com.ticketing.application.auth.ISessionTokenService;
import com.ticketing.application.services.EventService;
import com.ticketing.domain.event.AlwaysAllowPolicy;
import com.ticketing.domain.event.Event;
import com.ticketing.domain.event.EventCategory;
import com.ticketing.domain.event.EventSchedule;
import com.ticketing.domain.event.InventoryZone;
import com.ticketing.domain.event.LockTimerDuration;
import com.ticketing.domain.event.LotteryWindow;
import com.ticketing.domain.event.NoDiscountPolicy;
import com.ticketing.domain.event.SaleMethod;
import com.ticketing.domain.event.VenueMap;
import com.ticketing.domain.lottery.LotteryEntry;
import com.ticketing.domain.order.ActiveOrder;
import com.ticketing.infrastructure.InMemoryCompanyRepository;
import com.ticketing.infrastructure.InMemoryEventRepository;
import com.ticketing.infrastructure.InMemoryLotteryRepository;
import com.ticketing.infrastructure.InMemoryMemberRepository;
import com.ticketing.infrastructure.InMemoryOrderRepository;

@DisplayName("LotteryDrawScheduler")
class LotteryDrawSchedulerTest {

    private static final String COMPANY = "Acme Events";
    private static final Instant NOW = Instant.parse("2026-07-01T12:00:00Z");

    private InMemoryEventRepository eventRepository;
    private InMemoryLotteryRepository lotteryRepository;
    private InMemoryOrderRepository orderRepository;
    private EventService eventService;
    private TaskScheduler taskScheduler;
    private LotteryDrawScheduler scheduler;
    private UUID eventId;
    private UUID zoneId;

    @BeforeEach
    void setUp() {
        eventRepository = new InMemoryEventRepository();
        lotteryRepository = new InMemoryLotteryRepository();
        orderRepository = new InMemoryOrderRepository();
        ISystemClock clock = () -> NOW;

        eventService = new EventService(eventRepository, new InMemoryCompanyRepository(),
                new InMemoryMemberRepository(), orderRepository,
                mock(ISessionTokenService.class), lotteryRepository, clock);

        // A scheduler that runs the scheduled task synchronously so assertions are deterministic.
        taskScheduler = mock(TaskScheduler.class);
        when(taskScheduler.schedule(any(Runnable.class), any(Instant.class))).thenAnswer(inv -> {
            ((Runnable) inv.getArgument(0)).run();
            return mock(ScheduledFuture.class);
        });

        scheduler = new LotteryDrawScheduler(eventService, eventRepository, taskScheduler, clock);

        eventId = UUID.randomUUID();
        zoneId = UUID.randomUUID();
    }

    private Event lotteryEvent(Instant windowClose, int capacity) {
        Event event = new Event(eventId, COMPANY, "Lottery Show", "desc",
                EventCategory.CONCERT,
                new EventSchedule(NOW.plusSeconds(86400), NOW.plusSeconds(90000), NOW.plusSeconds(82000)),
                new LockTimerDuration(Duration.ofMinutes(15)),
                new AlwaysAllowPolicy(), new NoDiscountPolicy(),
                SaleMethod.LOTTERY,
                new LotteryWindow(NOW.minusSeconds(86400), windowClose));
        event.addZone(InventoryZone.createGA(zoneId, "Floor", new BigDecimal("50.00"), capacity));
        event.setVenueMap(new VenueMap(Map.of("Floor", zoneId)));
        event.publish();
        eventRepository.save(event);
        return event;
    }

    private void register(int count) {
        for (int i = 0; i < count; i++) {
            lotteryRepository.save(new LotteryEntry(UUID.randomUUID(), eventId, UUID.randomUUID(), NOW));
        }
    }

    @Test
    @DisplayName("Scheduling a closed-window lottery runs the draw and creates winner orders")
    void GivenClosedWindowLottery_WhenScheduled_ThenDrawRunsAndWinnersCreated() {
        Event event = lotteryEvent(NOW.minusSeconds(3600), 3);
        register(5);

        scheduler.scheduleDrawFor(event);

        List<ActiveOrder> winners = orderRepository.findActiveByEventId(eventId);
        assertEquals(3, winners.size(), "capacity (3) bounds the number of winners");
        assertTrue(winners.stream().allMatch(ActiveOrder::isLotteryWin));
    }

    @Test
    @DisplayName("Startup rescheduling arms persisted lottery events")
    void GivenPersistedLotteryEvent_WhenRescheduleOnStartup_ThenDrawRuns() {
        lotteryEvent(NOW.minusSeconds(3600), 10);
        register(2);

        scheduler.rescheduleOnStartup();

        assertEquals(2, orderRepository.findActiveByEventId(eventId).size());
    }

    @Test
    @DisplayName("Non-lottery and window-less events are not scheduled")
    void GivenRegularEvent_WhenScheduleDrawFor_ThenNothingScheduled() {
        Event regular = new Event(eventId, COMPANY, "Regular Show", "desc",
                EventCategory.CONCERT,
                new EventSchedule(NOW.plusSeconds(86400), NOW.plusSeconds(90000), NOW.plusSeconds(82000)),
                new LockTimerDuration(Duration.ofMinutes(15)),
                new AlwaysAllowPolicy(), new NoDiscountPolicy(),
                SaleMethod.REGULAR, null);

        boolean scheduled = scheduler.scheduleDrawFor(regular);

        org.junit.jupiter.api.Assertions.assertFalse(scheduled);
        verify(taskScheduler, never()).schedule(any(Runnable.class), any(Instant.class));
    }
}
