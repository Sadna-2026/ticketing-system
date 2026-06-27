package com.ticketing.domain.services;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ticketing.application.ISystemClock;
import com.ticketing.application.services.INotificationService;
import com.ticketing.domain.event.Event;
import com.ticketing.domain.event.IEventRepository;
import com.ticketing.domain.order.ActiveOrder;
import com.ticketing.domain.order.IOrderRepository;
import com.ticketing.domain.order.OrderItem;

@org.springframework.stereotype.Service
public class OrderTimeDomainService {
    private static final Logger log = LoggerFactory.getLogger(OrderTimeDomainService.class);

    private final IOrderRepository orderRepository;
    private final IEventRepository eventRepository;
    private final ISystemClock systemClock;
    private final INotificationService notificationService;

    public OrderTimeDomainService(IOrderRepository orderRepository,
                                  IEventRepository eventRepository,
                                  ISystemClock systemClock) {
        this(orderRepository, eventRepository, systemClock, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public OrderTimeDomainService(IOrderRepository orderRepository,
                                  IEventRepository eventRepository,
                                  ISystemClock systemClock,
                                  @org.springframework.beans.factory.annotation.Autowired(required = false)
                                  INotificationService notificationService) {
        this.orderRepository = orderRepository;
        this.eventRepository = eventRepository;
        this.systemClock = systemClock;
        this.notificationService = notificationService;
    }

    public void expireOrders() {
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
                log.error("Error expiring order: orderId={}", order.getId(), e);
            }
        }

        if (expiredCount > 0) {
            log.info("Expired {} orders", expiredCount);
        }
    }

    /**
     * Cancels a member's active cart and releases reserved tickets (e.g. on admin suspension).
     */
    public void cancelActiveOrderForMember(UUID memberId) {
        if (memberId == null) {
            return;
        }
        orderRepository.findActiveByMemberId(memberId).ifPresent(order -> {
            if (!order.isActive()) {
                return;
            }
            eventRepository.findById(order.getEventId()).ifPresent(event -> {
                releaseReservationsQuietly(event, order);
                event.reopenAvailabilityIfTicketsFreed();
                eventRepository.save(event);
            });
            order.cancel();
            orderRepository.save(order);
            log.info("Active order cancelled for member: memberId={}, orderId={}", memberId, order.getId());
        });
    }

    private void expireSingleOrder(ActiveOrder order, Event event) {
        releaseReservationsQuietly(event, order);
        eventRepository.save(event);

        order.expire();
        orderRepository.save(order);

        log.warn("Reservation expired: orderId={}, eventId={}", order.getId(), order.getEventId());

        if (notificationService != null && order.getMemberId() != null) {
            try {
                notificationService.notify(order.getMemberId().toString(),
                        "Your reservation has expired and your reserved tickets have been released.");
            } catch (RuntimeException e) {
                log.warn("Failed to notify member {} of order expiry", order.getMemberId(), e);
            }
        }
    }

    private static void releaseReservationsQuietly(Event event, ActiveOrder order) {
        for (OrderItem item : order.getItems()) {
            try {
                event.releaseReservationFor(item);
            } catch (Exception e) {
                log.error("Error releasing inventory for item: {}", item.getId(), e);
            }
        }
    }
}
