package com.ticketing.domain.order;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ticketing.domain.event.Event;
import com.ticketing.domain.event.InventoryZone;

public class TicketReservationDomainService {

    private static final Logger log = LoggerFactory.getLogger(TicketReservationDomainService.class);

    public UUID lockSeat(ActiveOrder order, Event event, UUID zoneId, UUID seatId) {
        InventoryZone zone = event.findZone(zoneId);
        zone.lockSeat(seatId);
        OrderItem item = OrderItem.forSeat(UUID.randomUUID(), zoneId, seatId, zone.getPricePerTicket());
        order.addItem(item);
        log.info("Seat added to order: orderId={}, seatId={}", order.getId(), seatId);
        return item.getId();
    }

    public UUID lockGA(ActiveOrder order, Event event, UUID zoneId, int quantity) {
        InventoryZone zone = event.findZone(zoneId);
        OrderItem item = order.findItemByZoneId(zoneId).orElse(null);
        if (item == null) {
            zone.lockGA(quantity);
            item = OrderItem.forGA(UUID.randomUUID(), zoneId, quantity, zone.getPricePerTicket());
            order.addItem(item);
        } else {
            zone.lockGA(quantity);
            item.updateQuantity(item.getQuantity() + quantity);
        }
        log.info("GA tickets added: orderId={}, zoneId={}, quantity={}", order.getId(), zoneId, quantity);
        return item.getId();
    }

    public void updateGAQuantity(ActiveOrder order, Event event, UUID zoneId, int newQuantity) {
        OrderItem item = order.findItemByZoneId(zoneId)
                .orElseThrow(() -> new IllegalArgumentException("No GA item found for zone: " + zoneId));

        InventoryZone zone = event.findZone(zoneId);
        int oldQuantity = item.getQuantity();
        int diff = newQuantity - oldQuantity;

        if (diff > 0) {
            zone.lockGA(diff);
        } else if (diff < 0) {
            zone.releaseGA(-diff);
        }

        item.updateQuantity(newQuantity);
    }

    public void releaseInventoryForItem(Event event, OrderItem item) {
        InventoryZone zone = event.findZone(item.getZoneId());
        if (item.isAssignedSeat()) {
            zone.releaseSeat(item.getSeatId());
        } else {
            zone.releaseGA(item.getQuantity());
        }
    }

    public void releaseAllInventory(Event event, ActiveOrder order) {
        for (OrderItem item : order.getItems()) {
            try {
                releaseInventoryForItem(event, item);
            } catch (Exception e) {
                log.error("Failed to release inventory for item: {}", item.getId(), e);
            }
        }
    }

    public void sellAllInventory(Event event, ActiveOrder order) {
        for (OrderItem item : order.getItems()) {
            InventoryZone zone = event.findZone(item.getZoneId());
            if (item.isAssignedSeat()) {
                zone.sellSeat(item.getSeatId());
            } else {
                zone.sellGA(item.getQuantity());
            }
        }
    }
}
