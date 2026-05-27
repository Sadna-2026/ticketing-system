package com.ticketing.domain.lottery;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import com.ticketing.application.ISystemClock;
import com.ticketing.domain.event.Event;
import com.ticketing.domain.event.IEventRepository;
import com.ticketing.domain.event.InventoryZone;
import com.ticketing.domain.exception.OptimisticLockException;
import com.ticketing.domain.order.ActiveOrder;
import com.ticketing.domain.order.IOrderRepository;
import com.ticketing.domain.order.OrderItem;

public class LotteryDrawDomainService {

    private final ILotteryRepository lotteryRepository;
    private final IEventRepository eventRepository;
    private final IOrderRepository orderRepository;
    private final ISystemClock systemClock;
    private final Random random;

    public LotteryDrawDomainService(ILotteryRepository lotteryRepository,
                                    IEventRepository eventRepository,
                                    IOrderRepository orderRepository,
                                    ISystemClock systemClock,
                                    Random random) {
        if (lotteryRepository == null) throw new IllegalArgumentException("lotteryRepository is required");
        if (eventRepository == null) throw new IllegalArgumentException("eventRepository is required");
        if (orderRepository == null) throw new IllegalArgumentException("orderRepository is required");
        if (systemClock == null) throw new IllegalArgumentException("systemClock is required");
        if (random == null) throw new IllegalArgumentException("random is required");

        this.lotteryRepository = lotteryRepository;
        this.eventRepository = eventRepository;
        this.orderRepository = orderRepository;
        this.systemClock = systemClock;
        this.random = random;
    }

    /**
     * Randomly selects winners up to capacity from the registrants of the given event lottery.
     * Each winner receives a time-limited authorization (reservation in their ActiveOrder).
     */
    public List<ActiveOrder> draw(UUID eventId, int capacity) {
        if (capacity < 0) {
            throw new IllegalArgumentException("Capacity cannot be negative");
        }

        List<LotteryEntry> allEntries = lotteryRepository.findByEventId(eventId);
        List<LotteryEntry> winners = selectWinners(allEntries, capacity);

        if (winners.isEmpty()) {
            return new ArrayList<>();
        }

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Event not found: " + eventId));

        List<ActiveOrder> createdOrders = new ArrayList<>();

        for (LotteryEntry winner : winners) {
            // Generate a background session ID since this is an automated process
            UUID sessionId = UUID.randomUUID();
            ActiveOrder order = new ActiveOrder(UUID.randomUUID(), sessionId, winner.memberId(), eventId, systemClock.now());

            // Lock inventory and add to order
            InventoryZone zone = event.findZone(winner.zoneId());
            zone.lockGA(winner.quantity());

            OrderItem item = OrderItem.forGA(UUID.randomUUID(), winner.zoneId(), winner.quantity(), zone.getPricePerTicket());
            order.addItem(item);

            try {
                orderRepository.save(order);
            } catch (OptimisticLockException ex) {
                throw new IllegalStateException("Lottery draw order changed concurrently. Please retry.", ex);
            }
            createdOrders.add(order);
        }

        try {
            eventRepository.save(event);
        } catch (OptimisticLockException ex) {
            throw new IllegalStateException("Lottery draw event changed concurrently. Please retry.", ex);
        }
        return createdOrders;
    }

    private List<LotteryEntry> selectWinners(List<LotteryEntry> entries, int capacity) {
        List<LotteryEntry> pool = new ArrayList<>(entries);
        List<LotteryEntry> winners = new ArrayList<>();

        int numWinners = Math.min(pool.size(), capacity);
        for (int i = 0; i < numWinners; i++) {
            int index = random.nextInt(pool.size());
            winners.add(pool.remove(index));
        }

        return winners;
    }
}
