package com.ticketing.domain.order;

import java.time.LocalDateTime;
import java.util.List;

public record ActiveOrder(String orderId, String userId, List<String> reservedTicketIds, LocalDateTime reservationExpiry, double basePrice) {
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(reservationExpiry);
    }

    public String getOrderId() { return orderId; }
    public String getUserId() { return userId; }
    public List<String> getReservedTicketIds() { return reservedTicketIds; }
    public double getBasePrice() { return basePrice; }
}





