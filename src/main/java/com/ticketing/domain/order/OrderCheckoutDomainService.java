package com.ticketing.domain.order;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ticketing.application.ISystemClock;
import com.ticketing.domain.event.Event;
import com.ticketing.domain.event.IEventRepository;
import com.ticketing.domain.event.InventoryZone;
import com.ticketing.domain.event.PolicyResult;
import com.ticketing.domain.event.PurchaseContext;
import com.ticketing.domain.exception.OptimisticLockException;
import com.ticketing.domain.gateway.CustomerInfo;
import com.ticketing.domain.gateway.IPaymentGateway;
import com.ticketing.domain.gateway.ITicketSupplyGateway;
import com.ticketing.domain.gateway.PaymentDetails;
import com.ticketing.domain.gateway.PaymentResult;
import com.ticketing.domain.gateway.RefundResult;
import com.ticketing.domain.gateway.SupplyResult;
import com.ticketing.domain.gateway.TicketRequest;
import com.ticketing.domain.member.IMemberRepository;
import com.ticketing.domain.member.Member;

@org.springframework.stereotype.Service
public class OrderCheckoutDomainService {

    private static final Logger log = LoggerFactory.getLogger(OrderCheckoutDomainService.class);

    private final IOrderRepository orderRepository;
    private final IEventRepository eventRepository;
    private final IMemberRepository memberRepository;
    private final List<IPaymentGateway> paymentGateways;
    private final List<ITicketSupplyGateway> ticketSupplyGateways;
    private final ISystemClock systemClock;

    public OrderCheckoutDomainService(IOrderRepository orderRepository,
                                      IEventRepository eventRepository,
                                      IMemberRepository memberRepository,
                                      List<IPaymentGateway> paymentGateways,
                                      List<ITicketSupplyGateway> ticketSupplyGateways,
                                      ISystemClock systemClock) {
        this.orderRepository = orderRepository;
        this.eventRepository = eventRepository;
        this.memberRepository = memberRepository;
        this.paymentGateways = paymentGateways;
        this.ticketSupplyGateways = ticketSupplyGateways;
        this.systemClock = systemClock;
    }

    public UUID checkout(UUID sessionId, UUID orderId, UUID memberId, String couponCode) {
        ActiveOrder order = findActiveOrder(orderId);
        validateOrderOwnership(sessionId, order);
        Event event = findEvent(order.getEventId());
        validateOrderNotExpired(order, event);

        if (order.getItems().isEmpty()) {
            log.warn("Failed to checkout order {}: no items in order", order.getId());
            throw new IllegalStateException("Cannot checkout an empty order");
        }

        BuyerContactSnapshot buyerContact = buyerContactFor(memberId);
        LocalDate buyerDob = getBuyerDateOfBirth(memberId);

        CompletedPurchase purchase;
        try {
            purchase = processCheckout(order, event, buyerContact, couponCode, buyerDob);
        } catch (IllegalStateException e) {
            saveOrder(order);
            if (e.getMessage() != null && e.getMessage().contains("Ticket generation failed")) {
                releaseAllInventory(event, order);
                saveEvent(event);
            }
            log.warn("Failed to checkout order {}: {}", order.getId(), e.getMessage());
            throw e;
        }

        sellAllInventory(event, order);
        saveEvent(event);
        saveOrder(order);
        orderRepository.save(purchase);

        checkAndPublishSoldOut(event);
        log.info("Checkout complete: orderId={}, purchaseId={}, amount={}",
                order.getId(), purchase.purchaseId(), purchase.amount());
        return purchase.purchaseId();
    }

    public List<CompletedPurchase> refundEventPurchases(UUID eventId) {
        List<CompletedPurchase> purchases = orderRepository.findCompletedByEventId(eventId);
        for (CompletedPurchase purchase : purchases) {
            refundPayment(purchase.transactionId(), purchase.amount());
        }
        return purchases;
    }

    public List<CompletedPurchase> getPurchaseHistory(UUID memberId) {
        return orderRepository.findCompletedByMemberId(memberId);
    }

    // ── Checkout internals ──

    private CompletedPurchase processCheckout(ActiveOrder order, Event event,
                                               BuyerContactSnapshot buyerContact, String couponCode,
                                               LocalDate buyerDateOfBirth) {
        validatePurchasePolicy(event, order, order.getMemberId(), buyerDateOfBirth);

        BigDecimal finalAmount = event.getEventDiscountPolicy().priceAfterDiscount(order, couponCode, systemClock.now());

        order.startCheckout();

        PaymentResult payment = chargePayment(order, event, buyerContact, finalAmount);
        if (payment == null || !payment.success()) {
            order.revertToActive();
            throw new IllegalStateException("Payment failed: " + (payment != null ? payment.errorMessage() : "All gateways failed"));
        }

        SupplyResult supply = supplyTickets(order, event, buyerContact);
        if (supply == null || !supply.success()) {
            refundPayment(payment.transactionId(), finalAmount);
            order.cancel();
            throw new IllegalStateException("Ticket generation failed. Payment has been refunded: "
                    + (supply != null ? supply.errorMessage() : "All gateways failed"));
        }

        order.complete();

        return new CompletedPurchase(
                UUID.randomUUID(),
                event.getId(),
                event.getName(),
                event.getCompanyName(),
                order.getMemberId(),
                payment.transactionId(),
                finalAmount,
                systemClock.now());
    }

    public void refundPayment(String transactionId, BigDecimal amount) {
        RefundResult refund = null;
        for (IPaymentGateway gateway : paymentGateways) {
            try {
                refund = gateway.refund(transactionId, amount.doubleValue());
                if (refund != null && refund.success()) {
                    break;
                }
            } catch (Exception e) {
                log.error("Refund Gateway failed with exception", e);
            }
        }
        if (refund == null || !refund.success()) {
            log.error("ESCALATION: Refund failed after ticket supply failure: reason={}",
            refund != null ? refund.errorMessage() : "All gateways failed");
        }
    }

    // ── Inventory operations ──

    private void sellAllInventory(Event event, ActiveOrder order) {
        for (OrderItem item : order.getItems()) {
            InventoryZone zone = event.findZone(item.getZoneId());
            if (item.isAssignedSeat()) {
                zone.sellSeat(item.getSeatId());
            } else {
                zone.sellGA(item.getQuantity());
            }
        }
    }

    private void releaseAllInventory(Event event, ActiveOrder order) {
        for (OrderItem item : order.getItems()) {
            try {
                InventoryZone zone = event.findZone(item.getZoneId());
                if (item.isAssignedSeat()) {
                    zone.releaseSeat(item.getSeatId());
                } else {
                    zone.releaseGA(item.getQuantity());
                }
            } catch (Exception e) {
                log.error("Failed to release inventory for item: {}", item.getId(), e);
            }
        }
    }

    private void checkAndPublishSoldOut(Event event) {
        if (!event.hasAvailableTickets() && event.isPublished()) {
            event.markSoldOut();
            saveEvent(event);
        }
    }

    // ── Private helpers ──

    private ActiveOrder findActiveOrder(UUID orderId) {
        ActiveOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> {
                    log.warn("Order not found: orderId={}", orderId);
                    return new IllegalArgumentException("Order not found: " + orderId);
                });
        if (!order.isActive()) {
            log.warn("Order is not active: orderId={}", orderId);
            throw new IllegalStateException("Order is not active (status: " + order.getStatus() + ")");
        }
        return order;
    }

    private Event findEvent(UUID eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> {
                    log.warn("Event not found: eventId={}", eventId);
                    return new IllegalArgumentException("Event not found: " + eventId);
                });
    }

    private void saveEvent(Event event) {
        try {
            eventRepository.save(event);
        } catch (OptimisticLockException ex) {
            log.warn("Event save conflict during checkout: eventId={}", event.getId());
            throw new IllegalStateException("Event inventory changed concurrently. Please retry.", ex);
        }
    }

    private void saveOrder(ActiveOrder order) {
        try {
            orderRepository.save(order);
        } catch (OptimisticLockException ex) {
            log.warn("Order save conflict: orderId={}", order.getId());
            throw new IllegalStateException("Order changed concurrently. Please retry.", ex);
        }
    }

    private void validateOrderOwnership(UUID sessionId, ActiveOrder order) {
        if (!order.getSessionId().equals(sessionId)) {
            throw new IllegalStateException("Order does not belong to this session");
        }
    }

    private void validateOrderNotExpired(ActiveOrder order, Event event) {
        if (order.isExpiredAt(systemClock.now(), event.getLockTimerDuration().getDuration())) {
            log.warn("Order has expired: orderId={}, eventId={}", order.getId(), event.getId());
            throw new IllegalStateException("Order has expired");
        }
    }

    private BuyerContactSnapshot buyerContactFor(UUID memberId) {
        if (memberId == null || memberRepository == null) {
            return BuyerContactSnapshot.empty();
        }
        return memberRepository.findById(memberId)
                .map(member -> new BuyerContactSnapshot(
                        member.getEmail(),
                        member.getUsername(),
                        member.getPhoneNumber()))
                .orElseGet(BuyerContactSnapshot::empty);
    }

    private LocalDate getBuyerDateOfBirth(UUID memberId) {
        if (memberId == null || memberRepository == null) {
            return null;
        }
        return memberRepository.findById(memberId).map(Member::getDateOfBirth).orElse(null);
    }

    private PaymentResult chargePayment(ActiveOrder order, Event event, BuyerContactSnapshot buyerContact, BigDecimal finalAmount) {
        PaymentResult payment = null;
        for (IPaymentGateway gateway : paymentGateways) {
            try {
                payment = gateway.charge(finalAmount,
                        new PaymentDetails(order.getId(), event.getId(), order.getMemberId(), buyerContact.getEmail()));
                if (payment != null && payment.success()) {
                    break;
                }
            } catch (Exception e) {
                log.error("Payment Gateway failed with exception", e);
            }
        }
        return payment;
    }

    private SupplyResult supplyTickets(ActiveOrder order, Event event, BuyerContactSnapshot buyerContact) {
        SupplyResult supply = null;
        for (ITicketSupplyGateway gateway : ticketSupplyGateways) {
            try {
                supply = gateway.issueTickets(ticketRequests(order, event),
                        new CustomerInfo(order.getMemberId() == null ? null : order.getMemberId().toString(),
                                buyerContact.getEmail(), buyerContact.getUsername()));
                if (supply != null && supply.success()) {
                    break;
                } else if (supply != null && supply.issuedTicketCodes() != null && !supply.issuedTicketCodes().isEmpty()) {
                    gateway.cancelTickets(supply.issuedTicketCodes());
                }
            } catch (Exception e) {
                log.error("Gateway failed with exception", e);
            }
        }
        return supply;
    }

    private void validatePurchasePolicy(Event event, ActiveOrder order, UUID memberId, LocalDate buyerDateOfBirth) {
        PurchaseContext ctx = new PurchaseContext(order, memberId, buyerDateOfBirth);
        PolicyResult validation = event.getEventPurchasePolicy().isAllowed(ctx);
        if (!validation.allowed()) {
            throw new IllegalStateException("Purchase policy violation: "
                    + validation.errorCode() + " — " + validation.reason());
        }
    }

    private List<TicketRequest> ticketRequests(ActiveOrder order, Event event) {
        List<TicketRequest> tickets = new ArrayList<>();
        for (OrderItem item : order.getItems()) {
            if (item.isAssignedSeat()) {
                tickets.add(new TicketRequest(event.getId().toString(),
                        item.getId().toString(), item.getSeatId().toString()));
            } else {
                for (int i = 0; i < item.getQuantity(); i++) {
                    tickets.add(new TicketRequest(event.getId().toString(),
                            item.getId() + "-" + (i + 1), null));
                }
            }
        }
        return tickets;
    }
}
