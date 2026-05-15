package com.ticketing.domain.lottery;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ticketing.application.ISystemClock;
import com.ticketing.domain.event.AlwaysAllowPolicy;
import com.ticketing.domain.event.Event;
import com.ticketing.domain.event.EventCategory;
import com.ticketing.domain.event.EventSchedule;
import com.ticketing.domain.event.LockTimerDuration;
import com.ticketing.domain.event.LotteryWindow;
import com.ticketing.domain.event.NoDiscountPolicy;
import com.ticketing.domain.event.SaleMethod;
import com.ticketing.domain.order.ActiveOrder;
import com.ticketing.domain.order.OrderItem;
import com.ticketing.infrastructure.InMemoryEventRepository;
import com.ticketing.infrastructure.InMemoryLotteryRepository;
import com.ticketing.infrastructure.InMemoryOrderRepository;

public class LotteryDrawDomainServiceTest {

    private InMemoryLotteryRepository lotteryRepository;
    private InMemoryEventRepository eventRepository;
    private InMemoryOrderRepository orderRepository;
    private ISystemClock systemClock;
    
    private LotteryDrawDomainService domainService;
    
    private UUID eventId;
    private UUID zoneId;
    
    @BeforeEach
    void setUp() {
        lotteryRepository = new InMemoryLotteryRepository();
        eventRepository = new InMemoryEventRepository();
        orderRepository = new InMemoryOrderRepository();
        systemClock = () -> Instant.parse("2026-07-01T12:00:00Z");
        
        eventId = UUID.randomUUID();
        zoneId = UUID.randomUUID();
        
        Event event = new Event(
                eventId, "Lottery Corp", "Big Lottery Show", "desc",
                EventCategory.CONCERT, 
                new EventSchedule(Instant.now().plusSeconds(100000), Instant.now().plusSeconds(110000), Instant.now().plusSeconds(90000)), 
                new LockTimerDuration(java.time.Duration.ofMinutes(15)),
                new AlwaysAllowPolicy(), new NoDiscountPolicy(),
                SaleMethod.LOTTERY, new LotteryWindow(Instant.now().minusSeconds(10000), Instant.now().plusSeconds(10000)));
        
        event.addZone(com.ticketing.domain.event.InventoryZone.createGA(
                zoneId, "Floor", new BigDecimal("50.00"), 500));
        event.setVenueMap(new com.ticketing.domain.event.VenueMap(Map.of("Section A", zoneId)));
        event.publish();
        eventRepository.save(event);
    }

    @Test
    @DisplayName("Winners count = min(registrants, capacity)")
    void GivenRegistrantsAndCapacity_WhenDraw_ThenWinnerCountIsMinOfBoth() {
        // Register 5 members
        for (int i = 0; i < 5; i++) {
            lotteryRepository.save(new LotteryEntry(UUID.randomUUID(), eventId, UUID.randomUUID(), zoneId, 1, systemClock.now()));
        }
        
        // Capacity 3 (less than registrants) -> 3 winners
        domainService = new LotteryDrawDomainService(lotteryRepository, eventRepository, orderRepository, systemClock, new Random());
        List<ActiveOrder> winners3 = domainService.draw(eventId, 3);
        assertEquals(3, winners3.size());
        
        // Setup again
        setUp();
        for (int i = 0; i < 3; i++) {
            lotteryRepository.save(new LotteryEntry(UUID.randomUUID(), eventId, UUID.randomUUID(), zoneId, 1, systemClock.now()));
        }
        // Capacity 5 (more than registrants) -> 3 winners
        domainService = new LotteryDrawDomainService(lotteryRepository, eventRepository, orderRepository, systemClock, new Random());
        List<ActiveOrder> winnersAll = domainService.draw(eventId, 5);
        assertEquals(3, winnersAll.size());
    }

    @Test
    @DisplayName("No duplicate winners")
    void GivenRegistrants_WhenDraw_ThenNoDuplicateWinners() {
        for (int i = 0; i < 10; i++) {
            lotteryRepository.save(new LotteryEntry(UUID.randomUUID(), eventId, UUID.randomUUID(), zoneId, 1, systemClock.now()));
        }
        
        domainService = new LotteryDrawDomainService(lotteryRepository, eventRepository, orderRepository, systemClock, new Random());
        List<ActiveOrder> winners = domainService.draw(eventId, 5);
        
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
        
        domainService = new LotteryDrawDomainService(lotteryRepository, eventRepository, orderRepository, systemClock, new Random());
        List<ActiveOrder> winners = domainService.draw(eventId, 1);
        
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


    @Test
    @DisplayName("Deterministic in tests via injected Random - strict comparison")
    void GivenFixedRandom_WhenDraw_ThenDeterministicWinners() {
        long seed = 42L;
        
        // Generate 10 entries
        List<LotteryEntry> entries = java.util.stream.IntStream.range(0, 10).mapToObj(i -> 
            new LotteryEntry(UUID.randomUUID(), eventId, UUID.randomUUID(), zoneId, 1, systemClock.now())
        ).toList();
        
        entries.forEach(lotteryRepository::save);
        
        Random random1 = new Random(seed);
        LotteryDrawDomainService service1 = new LotteryDrawDomainService(lotteryRepository, eventRepository, orderRepository, systemClock, random1);
        List<ActiveOrder> winners1 = service1.draw(eventId, 5);
        
        // Reset the environment manually to preserve eventId and zoneId
        lotteryRepository = new InMemoryLotteryRepository();
        eventRepository = new InMemoryEventRepository();
        orderRepository = new InMemoryOrderRepository();
        
        Event event = new Event(
                eventId, "Lottery Corp", "Big Lottery Show", "desc",
                EventCategory.CONCERT, 
                new EventSchedule(Instant.now().plusSeconds(100000), Instant.now().plusSeconds(110000), Instant.now().plusSeconds(90000)), 
                new LockTimerDuration(java.time.Duration.ofMinutes(15)),
                new AlwaysAllowPolicy(), new NoDiscountPolicy(),
                SaleMethod.LOTTERY, new LotteryWindow(Instant.now().minusSeconds(10000), Instant.now().plusSeconds(10000)));
        
        event.addZone(com.ticketing.domain.event.InventoryZone.createGA(
                zoneId, "Floor", new BigDecimal("50.00"), 500));
        event.setVenueMap(new com.ticketing.domain.event.VenueMap(Map.of("Section A", zoneId)));
        event.publish();
        eventRepository.save(event);
        
        entries.forEach(lotteryRepository::save);
        
        Random random2 = new Random(seed);
        LotteryDrawDomainService service2 = new LotteryDrawDomainService(lotteryRepository, eventRepository, orderRepository, systemClock, random2);
        List<ActiveOrder> winners2 = service2.draw(eventId, 5);
        
        // They should select the exact same member IDs in the same order
        List<UUID> memberIds1 = winners1.stream().map(ActiveOrder::getMemberId).toList();
        List<UUID> memberIds2 = winners2.stream().map(ActiveOrder::getMemberId).toList();
        
        assertEquals(memberIds1, memberIds2);
    }
}
