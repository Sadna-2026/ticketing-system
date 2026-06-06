package com.ticketing.domain.lottery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
import com.ticketing.domain.order.ActiveOrder;
import com.ticketing.domain.order.OrderItem;
import com.ticketing.infrastructure.InMemoryCompanyRepository;
import com.ticketing.infrastructure.InMemoryEventRepository;
import com.ticketing.infrastructure.InMemoryLotteryRepository;
import com.ticketing.infrastructure.InMemoryMemberRepository;
import com.ticketing.infrastructure.InMemoryOrderRepository;

public class LotteryDrawDomainServiceTest {

    private InMemoryLotteryRepository lotteryRepository;
    private InMemoryEventRepository eventRepository;
    private InMemoryOrderRepository orderRepository;
    private ISystemClock systemClock;
    private ISessionTokenService sessionTokenService;
    private EventService eventService;

    private UUID eventId;
    private UUID zoneId;

    @BeforeEach
    void setUp() {
        lotteryRepository = new InMemoryLotteryRepository();
        eventRepository = new InMemoryEventRepository();
        orderRepository = new InMemoryOrderRepository();
        systemClock = () -> Instant.parse("2026-07-01T12:00:00Z");

        sessionTokenService = mock(ISessionTokenService.class);
        when(sessionTokenService.isValid(anyString())).thenReturn(true);
        when(sessionTokenService.extractMemberId(anyString())).thenReturn(UUID.randomUUID());

        eventId = UUID.randomUUID();
        zoneId = UUID.randomUUID();

        Event event = new Event(
                eventId, "Lottery Corp", "Big Lottery Show", "desc",
                EventCategory.CONCERT,
                new EventSchedule(Instant.now().plusSeconds(100000), Instant.now().plusSeconds(110000), Instant.now().plusSeconds(90000)),
                new LockTimerDuration(java.time.Duration.ofMinutes(15)),
                new AlwaysAllowPolicy(), new NoDiscountPolicy(),
                SaleMethod.LOTTERY, new LotteryWindow(Instant.now().minusSeconds(10000), Instant.now().plusSeconds(10000)));

        event.addZone(InventoryZone.createGA(zoneId, "Floor", new BigDecimal("50.00"), 500));
        event.setVenueMap(new VenueMap(Map.of("Section A", zoneId)));
        event.publish();
        eventRepository.save(event);

        eventService = new EventService(eventRepository,
                new InMemoryCompanyRepository(),
                new InMemoryMemberRepository(),
                orderRepository,
                sessionTokenService,
                lotteryRepository,
                systemClock);
    }

    @Test
    @DisplayName("Winners count = min(registrants, capacity)")
    void GivenRegistrantsAndCapacity_WhenDraw_ThenWinnerCountIsMinOfBoth() {
        for (int i = 0; i < 5; i++) {
            lotteryRepository.save(new LotteryEntry(UUID.randomUUID(), eventId, UUID.randomUUID(), zoneId, 1, systemClock.now()));
        }

        List<ActiveOrder> winners3 = eventService.drawLottery("token", eventId, 3);
        assertEquals(3, winners3.size());

        // Setup again
        setUp();
        for (int i = 0; i < 3; i++) {
            lotteryRepository.save(new LotteryEntry(UUID.randomUUID(), eventId, UUID.randomUUID(), zoneId, 1, systemClock.now()));
        }
        List<ActiveOrder> winnersAll = eventService.drawLottery("token", eventId, 5);
        assertEquals(3, winnersAll.size());
    }

    @Test
    @DisplayName("No duplicate winners")
    void GivenRegistrants_WhenDraw_ThenNoDuplicateWinners() {
        for (int i = 0; i < 10; i++) {
            lotteryRepository.save(new LotteryEntry(UUID.randomUUID(), eventId, UUID.randomUUID(), zoneId, 1, systemClock.now()));
        }

        List<ActiveOrder> winners = eventService.drawLottery("token", eventId, 5);

        assertEquals(5, winners.size());

        Set<UUID> uniqueMemberIds = winners.stream().map(ActiveOrder::getMemberId).collect(Collectors.toSet());
        assertEquals(5, uniqueMemberIds.size());
    }

    @Test
    @DisplayName("Winners receive reservation/authorization")
    void GivenRegistrants_WhenDraw_ThenWinnersReceiveReservation() {
        UUID memberId = UUID.randomUUID();
        int requestedQuantity = 2;
        lotteryRepository.save(new LotteryEntry(UUID.randomUUID(), eventId, memberId, zoneId, requestedQuantity, systemClock.now()));

        List<ActiveOrder> winners = eventService.drawLottery("token", eventId, 1);

        assertEquals(1, winners.size());
        ActiveOrder order = winners.get(0);

        assertEquals(memberId, order.getMemberId());
        assertEquals(eventId, order.getEventId());
        assertNotNull(order.getSessionId());

        List<OrderItem> items = order.getItems();
        assertEquals(1, items.size());
        OrderItem item = items.get(0);

        assertEquals(zoneId, item.getZoneId());
        assertEquals(requestedQuantity, item.getQuantity());
        assertTrue(item.isGA());

        // Verify event inventory was locked
        Event event = eventRepository.findById(eventId).get();
        assertEquals(500 - requestedQuantity, event.findZone(zoneId).getAvailableCount());
    }
}
