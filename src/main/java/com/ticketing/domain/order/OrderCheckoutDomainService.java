package com.ticketing.domain.order;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;

import com.ticketing.application.ISystemClock;
import com.ticketing.domain.event.Event;
import com.ticketing.domain.event.IEventRepository;
import com.ticketing.domain.event.PolicyResult;
import com.ticketing.domain.event.PurchaseContext;
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
    private final TicketReservationDomainService ticketReservationService;

    public OrderCheckoutDomainService(IOrderRepository orderRepository,
                                      IEventRepository eventRepository,
                                      IMemberRepository memberRepository,
                                      List<IPaymentGateway> paymentGateways,
                                      List<ITicketSupplyGateway> ticketSupplyGateways,
                                      ISystemClock systemClock,
                                      TicketReservationDomainService ticketReservationService) {
        this.orderRepository = orderRepository;
        this.eventRepository = eventRepository;
        this.memberRepository = memberRepository;
        this.paymentGateways = paymentGateways;
        this.ticketSupplyGateways = ticketSupplyGateways;
        this.systemClock = systemClock;
        this.ticketReservationService = ticketReservationService;
    }

    public UUID checkout(UUID sessionId, UUID orderId, UUID memberId, String couponCode) {
        ActiveOrder order = ticketReservationService.findActiveOrder(orderId);
        validateOrderOwnership(sessionId, order);
        Event event = findEvent(order.getEventId());
        validateOrderNotExpired(order, event);

        if (order.getItems().isEmpty()) {
            log.warn("Failed to checkout order {}: no items in order", order.getId());
            throw new IllegalStateException("Cannot checkout an empty order");
        }

        BuyerContactSnapshot buyerContact = buyerContactFor(memberId);
        LocalDate buyerDob = memberId != null && memberRepository != null
                ? memberRepository.findById(memberId).map(Member::getDateOfBirth).orElse(null)
                : null;

        CompletedPurchase purchase;
        try {
            purchase = processCheckout(order, event, buyerContact, couponCode, buyerDob);
        } catch (IllegalStateException e) {
            ticketReservationService.saveOrder(order);
            if (e.getMessage() != null && e.getMessage().contains("Ticket generation failed")) {
                ticketReservationService.releaseAllInventory(event, order);
                ticketReservationService.saveEvent(event);
            }
            log.warn("Failed to checkout order {}: {}", order.getId(), e.getMessage());
            throw e;
        }

        ticketReservationService.sellAllInventory(event, order);
        ticketReservationService.saveEvent(event);
        ticketReservationService.saveOrder(order);
        orderRepository.save(purchase);

        ticketReservationService.checkAndPublishSoldOut(event);
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

    public CompletedPurchase processCheckout(ActiveOrder order, Event event,
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

    // ── Private helpers ──

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

    private Event findEvent(UUID eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> {
                    log.warn("Event not found: eventId={}", eventId);
                    return new IllegalArgumentException("Event not found: " + eventId);
                });
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
