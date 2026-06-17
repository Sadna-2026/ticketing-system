package com.ticketing.domain.event;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import com.ticketing.domain.order.ActiveOrder;
import com.ticketing.domain.order.SelectionRequest;

/**
 * Carries all buyer/order information a purchase policy might need.
 * Nullable fields (memberId, buyerDateOfBirth) are null for guest purchases.
 *
 * @param seatsBecomingAvailable assigned seats that will return to available inventory
 *                               after the current mutation (e.g. a pending removal), so
 *                               policies such as no-orphan can evaluate the post-mutation map
 */
public record PurchaseContext(
        ActiveOrder order,
        UUID memberId,
        LocalDate buyerDateOfBirth,
        Event event,
        Set<UUID> incomingSeatIds,
        Set<UUID> seatsBecomingAvailable
) {
    public PurchaseContext(ActiveOrder order, UUID memberId, LocalDate buyerDateOfBirth) {
        this(order, memberId, buyerDateOfBirth, null, Set.of(), Set.of());
    }

    public PurchaseContext(
            ActiveOrder order,
            UUID memberId,
            LocalDate buyerDateOfBirth,
            Event event,
            Set<UUID> incomingSeatIds
    ) {
        this(order, memberId, buyerDateOfBirth, event, incomingSeatIds, Set.of());
    }

    public PurchaseContext {
        incomingSeatIds = incomingSeatIds == null ? Set.of() : incomingSeatIds;
        seatsBecomingAvailable = seatsBecomingAvailable == null ? Set.of() : seatsBecomingAvailable;
    }

    public static PurchaseContext forOrder(
            Event event,
            ActiveOrder order,
            UUID memberId,
            LocalDate buyerDateOfBirth
    ) {
        return new PurchaseContext(order, memberId, buyerDateOfBirth, event, Set.of(), Set.of());
    }

    public static PurchaseContext forRemoval(
            Event event,
            ActiveOrder orderAfterRemoval,
            UUID memberId,
            LocalDate buyerDateOfBirth,
            Set<UUID> seatsBecomingAvailable
    ) {
        return new PurchaseContext(
                orderAfterRemoval, memberId, buyerDateOfBirth, event, Set.of(), seatsBecomingAvailable);
    }

    public static PurchaseContext forReservation(
            Event event,
            ActiveOrder order,
            UUID memberId,
            LocalDate buyerDateOfBirth,
            SelectionRequest request
    ) {
        ActiveOrder simulatedOrder = order.simulateWithAdditionalTickets(request.additionalTicketCount());
        return new PurchaseContext(
                simulatedOrder, memberId, buyerDateOfBirth, event, request.selectedSeatIds(), Set.of());
    }
}
