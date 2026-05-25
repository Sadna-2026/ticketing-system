package  com.ticketing.application.services;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ticketing.application.ISystemClock;
import com.ticketing.domain.event.Event;
import com.ticketing.domain.event.IEventRepository;
import com.ticketing.domain.event.InventoryZone;
import com.ticketing.domain.order.ActiveOrder;
import com.ticketing.domain.order.IOrderRepository;
import com.ticketing.domain.order.OrderItem;

@org.springframework.stereotype.Service
public class OrderTimeDomainService {
    private static final Logger log = LoggerFactory.getLogger(OrderTimeDomainService.class);

    private final IOrderRepository orderRepository;
    private final IEventRepository eventRepository;
    //private final IDomainEventPublisher eventPublisher;
    private final ISystemClock systemClock;

    public OrderTimeDomainService(IOrderRepository orderRepository,
                                  IEventRepository eventRepository,
                                  //IDomainEventPublisher eventPublisher,
                                  ISystemClock systemClock) {
        this.orderRepository = orderRepository;
        this.eventRepository = eventRepository;
        //this.eventPublisher = eventPublisher;
        this.systemClock = systemClock;
    }

    /**
     * Sweeps all active orders and expires any that have exceeded their
     * event's lock timer duration. Releases inventory for expired orders.
     */
    public void expireOrders() {
        Instant now = systemClock.now();
        List<ActiveOrder> activeOrders = orderRepository.findAllActive();
        log.info("Expiration sweep: checking {} active orders", activeOrders.size());

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
                log.error("Error releasing inventory for item: {}", item.getId(), e);
            }
        }
        eventRepository.save(event);

        order.expire();
        orderRepository.save(order);

        // eventPublisher.publish(new OrderExpiredEvent(
        //         order.getId(), order.getSessionId(), order.getEventId(), systemClock.now()));
        // log.info("Order expired: orderId={}, eventId={}", order.getId(), order.getEventId());
    }
}


