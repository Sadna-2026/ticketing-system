package  com.ticketing.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ticketing.application.auth.ISessionTokenService;
import com.ticketing.domain.event.Event;
import com.ticketing.domain.event.InventoryZone;
import com.ticketing.domain.event.PolicyResult;
import com.ticketing.domain.gateway.PaymentDetails;
import com.ticketing.domain.gateway.PaymentResult;
import com.ticketing.domain.member.Member;
import com.ticketing.domain.order.ActiveOrder;
import com.ticketing.domain.order.BuyerContactSnapshot;
import com.ticketing.domain.order.OrderItem;
import com.ticketing.infrastructure.Interface.IActiveOrderRepository;
import com.ticketing.infrastructure.Interface.IEventRepository;
import com.ticketing.infrastructure.Interface.IMemberRepository;
import com.ticketing.infrastructure.Interface.IPaymentGateway;


public class ActiveOrderService {

    private static final Logger log = LoggerFactory.getLogger(ActiveOrderService.class);

    private final ISessionTokenService sessionTokenService;
    private final IActiveOrderRepository activeOrderRepository;
    private final IEventRepository eventRepository;
    private final ISystemClock systemClock;
    private final IMemberRepository memberRepository;
    private final IPaymentGateway paymentGateway;

    public ActiveOrderService(IActiveOrderRepository activeOrderRepository,
                        ISessionTokenService sessionTokenService,
                        IEventRepository eventRepository,
                        ISystemClock systemClock,
                    IMemberRepository memberRepository,
                    IPaymentGateway paymentGateway) {
        this.activeOrderRepository = activeOrderRepository;
        this.sessionTokenService = sessionTokenService;
        this.eventRepository = eventRepository;
        this.systemClock = systemClock;
        this.memberRepository = memberRepository;
        this.paymentGateway = paymentGateway;
    }

    /**
     * Creates an active order for the given session and event.
     * Can be called by guests (no token) or members (with token).
     *
     * @param token JWT token (null for guests)
     * @param eventId the event to order tickets for
     * @return the new order's UUID
     */
    public UUID createOrder(String token, UUID eventId) {
        // TODO: validate token throws exception cathc somewhere
        // validate token
        validateToken(token);
        UUID sessionId = sessionTokenService.extractSessionId(token);
        UUID memberId = sessionTokenService.extractMemberId(token);     // null for guests

        // Enforce max 1 active order per session
        activeOrderRepository.findActiveBySessionId(sessionId).ifPresent(existing -> {
            throw new IllegalStateException("Session already has an active order");
        });

        Event event = findEvent(eventId);

        if (!event.isPublished()) {
            throw new IllegalStateException("Event is not available for purchase");
        }

        ActiveOrder order = new ActiveOrder(UUID.randomUUID(), sessionId, memberId, eventId, systemClock.now());
        activeOrderRepository.save(order);

        log.info("Order created: orderId={}, sessionId={}, eventId={}", order.getId(), sessionId, eventId);
        return order.getId();
    }

    /**
     * Adds a specific assigned seat to the active order.
     */
    public UUID addSeatToOrder(String token, UUID orderId, UUID zoneId, UUID seatId) {
        // validate token
        validateToken(token);

        log.info("Adding seat to order: orderId={}, zoneId={}, seatId={}", orderId, zoneId, seatId);

        ActiveOrder order = findActiveOrder(orderId);
        Event event = findEvent(order.getEventId());
        validateOrderNotExpired(order, event);

        InventoryZone zone = event.findZone(zoneId);
        if (!zone.isAssigned()) {
            throw new IllegalStateException("Zone is not an assigned seating zone");
        }

        zone.lockSeat(seatId);
        eventRepository.save(event);

        OrderItem item = OrderItem.forSeat(UUID.randomUUID(), zoneId, seatId, zone.getPricePerTicket());
        order.addItem(item);
        activeOrderRepository.save(order);

        checkAndPublishSoldOut(event);

        log.info("Seat added to order: orderId={}, seatId={}", orderId, seatId);
        return item.getId();
    }
    
    public UUID checkout(String token, UUID orderId, String couponCode) {
      
        // TODO: validate token throws exception cathc somewhere
        validateToken(token);
        UUID sessionId = sessionTokenService.extractSessionId(token);
        UUID memberId = sessionTokenService.extractMemberId(token);     

        ActiveOrder order = findActiveOrder(orderId);
        Event event = findEvent(order.getEventId());
        validateOrderNotExpired(order, event);

        if (order.getItems().isEmpty()) {
            throw new IllegalStateException("Cannot checkout an empty order");
        }

        java.time.LocalDate buyerDateOfBirth = null;
        BuyerContactSnapshot buyerContact = BuyerContactSnapshot.empty();

        if (memberId != null) {
            Member member = memberRepository.findById(memberId).orElse(null);
            if (member != null) {
                buyerDateOfBirth = member.getDateOfBirth();
                buyerContact = new BuyerContactSnapshot(
                        member.getEmail(),
                        member.getUsername(),
                        member.getPhoneNumber()
                );
            }
        }
            
        PolicyResult validation = event.getEventPurchasePolicy().isAllowed(order, memberId);

        if (!validation.allowed()) {
            log.error("Checkout failed: policy violations: {}", validation.errorCode());
            throw new IllegalStateException("Purchase policy violations: " + String.join(", ", validation.errorCode()));
        }

        // Calculate event-level discounts (no company-level discounts in V1)
        BigDecimal discountAmount = event.getEventDiscountPolicy().applyTo(order, couponCode, systemClock.now());

        BigDecimal finalAmount = order.getTotalPrice().subtract(discountAmount);
        if (finalAmount.compareTo(BigDecimal.ZERO) < 0) {
            finalAmount = BigDecimal.ZERO;
        }

        // Start checkout
        order.startCheckout();
        activeOrderRepository.save(order);

        // Charge payment
        PaymentResult transactionId;
        try {
            transactionId = paymentGateway.charge(finalAmount,
                    new PaymentDetails(order.getId(), event.getId(), memberId, buyerContact.getEmail()));
        } catch (Exception e) {
            order.revertToActive();
            activeOrderRepository.save(order);
            log.error("Payment failed for order: orderId={}", orderId, e);
            throw new IllegalStateException("Payment failed: " + e.getMessage(), e);
        }

        // Generate tickets
        List<String> ticketCodes;
        try {
            ticketCodes = ticketSupplyService.generateTickets(order.getId(), order.getTotalTicketCount());
        } catch (TicketSupplyException e) {
            // Compensation: refund payment
            try {
                IPaymentGateway.refund(transactionId);
            } catch (PaymentException refundEx) {
                log.error("CRITICAL: Refund failed after ticket supply failure. orderId={}, txnId={}",
                        orderId, transactionId, refundEx);
                // eventPublisher.publish(new TicketSupplyFailedEvent(
                        // order.getId(), order.getSessionId(), transactionId, systemClock.now()));
            }

            releaseAllInventory(event, order);
            eventRepository.save(event);

            order.cancel();
            activeOrderRepository.save(order);
            log.error("Ticket generation failed: orderId={}", orderId, e);
            throw new IllegalStateException("Ticket generation failed. Payment has been refunded.", e);
        }

        // Mark inventory as sold
        sellAllInventory(event, order);
        eventRepository.save(event);

        // Complete the active order
        order.complete();
        activeOrderRepository.save(order);

        // Create immutable CompletedOrder snapshot
        ProductionCompany company = findCompany(event.getCompanyId());
        CompletedOrder completedOrder = createCompletedOrder(
                order, event, company, orderMemberId, buyerContact,
                finalAmount, discountAmount, transactionId);
        completedOrderRepository.save(completedOrder);

        // Publish events
        // eventPublisher.publish(new OrderCompletedEvent(
        //         completedOrder.getId(), orderMemberId, order.getEventId(), systemClock.now()));

        if (!event.hasAvailableTickets() && event.isPublished()) {
            event.markSoldOut();
            eventRepository.save(event);
            // eventPublisher.publish(new EventSoldOutEvent(event.getId(), event.getCompanyId(), systemClock.now()));
        }

        log.info("Checkout complete: orderId={}, completedOrderId={}, amount={}",
                orderId, completedOrder.getId(), finalAmount);
        return completedOrder.getId();
    }
    
     /* Adds GA tickets from a zone to the active order.*/
    public UUID addGATicketsToOrder(String token, UUID orderId, UUID zoneId, int quantity) {
        // validate token
        validateToken(token);
        log.info("Adding GA tickets: orderId={}, zoneId={}, quantity={}", orderId, zoneId, quantity);

        ActiveOrder order = findActiveOrder(orderId);
        Event event = findEvent(order.getEventId());
        validateOrderNotExpired(order, event);

        InventoryZone zone = event.findZone(zoneId);
        if (!zone.isGA()) {
            throw new IllegalStateException("Zone is not a General Admission zone");
        }

        Optional<OrderItem> existingItem = order.findItemByZoneId(zoneId);
        UUID itemId;
        if (existingItem.isPresent()) {
            int additionalQuantity = quantity;
            zone.lockGA(additionalQuantity);
            eventRepository.save(event);
            int oldQuantity = existingItem.get().getQuantity();
            existingItem.get().updateQuantity(oldQuantity + additionalQuantity);
            itemId = existingItem.get().getId();
        } else {
            zone.lockGA(quantity);
            eventRepository.save(event);
            OrderItem item = OrderItem.forGA(UUID.randomUUID(), zoneId, quantity, zone.getPricePerTicket());
            order.addItem(item);
            itemId = item.getId();
        }

        activeOrderRepository.save(order);
        checkAndPublishSoldOut(event);

        log.info("GA tickets added: orderId={}, zoneId={}, quantity={}", orderId, zoneId, quantity);
        return itemId;
    }


    private void validateToken(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Authentication token is required");
        }
        if (!sessionTokenService.isValid(token)) {
            throw new IllegalArgumentException("Invalid or expired authentication token");
        }
    }

    private Event findEvent(UUID eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Event not found: " + eventId));
    }

    private ActiveOrder findActiveOrder(UUID orderId) {
        ActiveOrder order = activeOrderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));
        if (!order.isActive()) {
            throw new IllegalStateException("Order is not active (status: " + order.getStatus() + ")");
        }
        return order;
    }

    private void validateOrderNotExpired(ActiveOrder order, Event event) {
        if (order.isExpiredAt(systemClock.now(), event.getLockTimerDuration().getDuration())) {
            throw new IllegalStateException("Order has expired");
        }
    }

    private void checkAndPublishSoldOut(Event event) {
        if (!event.hasAvailableTickets() && event.isPublished()) {
            event.markSoldOut();
            eventRepository.save(event);
            // TODO: send event sold out messages to all listeners
        }
    }


}

