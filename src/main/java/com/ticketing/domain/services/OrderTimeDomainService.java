package com.ticketing.domain.services;

import java.time.Instant;
import java.util.List;

import com.ticketing.application.ISystemClock;
import com.ticketing.domain.event.Event;
import com.ticketing.domain.event.IEventRepository;
import com.ticketing.domain.event.InventoryZone;
import com.ticketing.domain.order.ActiveOrder;
import com.ticketing.domain.order.IOrderRepository;
import com.ticketing.domain.order.OrderItem;

@org.springframework.stereotype.Service
public class OrderTimeDomainService {

    private final IOrderRepository orderRepository;
    private final IEventRepository eventRepository;
    private final ISystemClock systemClock;

    public OrderTimeDomainService(IOrderRepository orderRepository,
                                  IEventRepository eventRepository,
                                  ISystemClock systemClock) {
        this.orderRepository = orderRepository;
        this.eventRepository = eventRepository;
        this.systemClock = systemClock;
    }

    public int expireOrders() {
        Instant now = systemClock.now();
        List<ActiveOrder> activeOrders = orderRepository.findAllActive();

        int expiredCount = 0;
        for (ActiveOrder order : activeOrders) {
            try {
                Event event = eventRepository.findById(order.getEventId()).orElse(null);
                if (event == null) {
                    order.expire();
                    orderRepository.save(order);
                    expiredCount++;
                    continue;
                }

                if (order.isExpiredAt(now, event.getLockTimerDuration().getDuration())) {
                    expireSingleOrder(order, event);
                    expiredCount++;
                }
            } catch (Exception e) {
                // Ignore and continue with the next order
            }
        }

        return expiredCount;
    }

    private void expireSingleOrder(ActiveOrder order, Event event) {
        for (OrderItem item : order.getItems()) {
            try {
                InventoryZone zone = event.findZone(item.getZoneId());
                if (item.isAssignedSeat()) {
                    zone.releaseSeat(item.getSeatId());
                } else {
                    zone.releaseGA(item.getQuantity());
                }
            } catch (Exception e) {
                // Ignore and continue with the next item
            }
        }
        eventRepository.save(event);

        order.expire();
        orderRepository.save(order);
    }
}
