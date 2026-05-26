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

public class OrderCheckoutDomainService {

    private static final Logger log = LoggerFactory.getLogger(OrderCheckoutDomainService.class);

    private final List<IPaymentGateway> paymentGateways;
    private final List<ITicketSupplyGateway> ticketSupplyGateways;
    private final ISystemClock systemClock;

    public OrderCheckoutDomainService(List<IPaymentGateway> paymentGateways,
                                      List<ITicketSupplyGateway> ticketSupplyGateways,
                                      ISystemClock systemClock) {
        this.paymentGateways = paymentGateways;
        this.ticketSupplyGateways = ticketSupplyGateways;
        this.systemClock = systemClock;
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
