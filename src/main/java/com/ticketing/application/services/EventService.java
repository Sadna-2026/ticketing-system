package com.ticketing.application.services;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ticketing.application.CreateEventRequest;
import com.ticketing.application.EditEventRequest;
import com.ticketing.application.ISystemClock;
import com.ticketing.application.SearchEventsRequest;
import com.ticketing.application.auth.ISessionTokenService;
import com.ticketing.application.dto.EventDetailsDTO;
import com.ticketing.application.dto.EventMapDTO;
import com.ticketing.application.dto.EventSummaryDTO;
import com.ticketing.application.dto.LotteryRegistrationRequest;
import com.ticketing.application.dto.LotteryRegistrationResponse;
import com.ticketing.domain.event.Event;
import com.ticketing.domain.event.EventStatus;
import com.ticketing.domain.event.IDiscountPolicy;
import com.ticketing.domain.event.IEventRepository;
import com.ticketing.domain.event.IPurchasePolicy;
import com.ticketing.domain.event.InventoryZone;
import com.ticketing.domain.event.Seat;
import com.ticketing.domain.exception.OptimisticLockException;
import com.ticketing.domain.lottery.ILotteryRepository;
import com.ticketing.domain.lottery.LotteryDrawDomainService;
import com.ticketing.domain.lottery.LotteryEntry;
import com.ticketing.domain.lottery.LotteryRegistrationDomainService;
import com.ticketing.domain.order.CompletedPurchase;
import com.ticketing.domain.services.EventDomainService;
import com.ticketing.domain.services.EventSearchDomainService;
import com.ticketing.domain.order.OrderCheckoutDomainService;

@org.springframework.stereotype.Service
public class EventService {

    private static final Logger log = LoggerFactory.getLogger(EventService.class);

    private final IEventRepository eventRepository;
    private final ISessionTokenService sessionTokenService;
    private final ISystemClock systemClock;

    private final LotteryRegistrationDomainService lotteryRegistrationService;
    private final OrderCheckoutDomainService orderCheckoutService;
    private final EventDomainService domainService;
    private final LotteryDrawDomainService lotteryDrawService;
    private final EventSearchDomainService eventSearchDomainService;
    private final INotificationService notificationService;

    // For backwards compatibility with tests
    public EventService(IEventRepository eventRepository,
                        com.ticketing.domain.company.ICompanyRepository companyRepository,
                        com.ticketing.domain.member.IMemberRepository memberRepository,
                        com.ticketing.domain.order.IOrderRepository orderRepository,
                        ISessionTokenService sessionTokenService,
                        ILotteryRepository lotteryRepository,
                        ISystemClock systemClock,
                        OrderService orderService) {
        this.eventRepository = eventRepository;
        this.sessionTokenService = sessionTokenService;
        this.systemClock = systemClock;
        this.orderCheckoutService = null;
        this.domainService = new EventDomainService(eventRepository, companyRepository, memberRepository, orderRepository);
        this.lotteryRegistrationService = new LotteryRegistrationDomainService(lotteryRepository);
        this.lotteryDrawService = null;
        this.eventSearchDomainService = new EventSearchDomainService(eventRepository, companyRepository);
        this.notificationService = null;
    }

    public EventService(IEventRepository eventRepository,
                        com.ticketing.domain.company.ICompanyRepository companyRepository,
                        com.ticketing.domain.member.IMemberRepository memberRepository,
                        com.ticketing.domain.order.IOrderRepository orderRepository,
                        ISessionTokenService sessionTokenService,
                        ILotteryRepository lotteryRepository,
                        ISystemClock systemClock) {
        this(eventRepository, companyRepository, memberRepository, orderRepository,
                sessionTokenService, lotteryRepository, systemClock, null);
    }

    public EventService(IEventRepository eventRepository,
                        com.ticketing.domain.company.ICompanyRepository companyRepository,
                        com.ticketing.domain.member.IMemberRepository memberRepository,
                        com.ticketing.domain.order.IOrderRepository orderRepository,
                        ISessionTokenService sessionTokenService,
                        OrderService orderService) {
        this(eventRepository, companyRepository, memberRepository, orderRepository,
                sessionTokenService, null, Instant::now, orderService);
    }

    public EventService(IEventRepository eventRepository,
                        com.ticketing.domain.company.ICompanyRepository companyRepository,
                        com.ticketing.domain.member.IMemberRepository memberRepository,
                        com.ticketing.domain.order.IOrderRepository orderRepository,
                        ISessionTokenService sessionTokenService) {
        this(eventRepository, companyRepository, memberRepository, orderRepository,
                sessionTokenService, null, Instant::now, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public EventService(IEventRepository eventRepository,
                        ISessionTokenService sessionTokenService,
                        ILotteryRepository lotteryRepository,
                        OrderCheckoutDomainService orderCheckoutService,
                        EventDomainService domainService,
                        EventSearchDomainService eventSearchDomainService,
                        com.ticketing.domain.order.IOrderRepository orderRepository,
                        ISystemClock systemClock,
                        @org.springframework.beans.factory.annotation.Autowired(required = false) INotificationService notificationService) {
        this.eventRepository = eventRepository;
        this.sessionTokenService = sessionTokenService;
        this.systemClock = systemClock;
        this.orderCheckoutService = orderCheckoutService;
        this.domainService = domainService;
        this.lotteryRegistrationService = new LotteryRegistrationDomainService(lotteryRepository);
        this.lotteryDrawService = new com.ticketing.domain.lottery.LotteryDrawDomainService(lotteryRepository, eventRepository, orderRepository, systemClock, new java.util.Random());
        this.eventSearchDomainService = eventSearchDomainService;
        this.notificationService = notificationService;
    }

    public UUID createEvent(String token, CreateEventRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request cannot be null");
        }
        UUID memberId = authenticateMember(token);
        return domainService.createEvent(memberId, request).getId();
    }

    public LotteryRegistrationResponse registerForLottery(String token, LotteryRegistrationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request cannot be null");
        }
        UUID memberId = authenticateMember(token);
        Event event = eventRepository.findById(request.eventId())
                .orElseThrow(() -> new IllegalArgumentException("Event not found: " + request.eventId()));
        Instant now = systemClock.now();
        LotteryEntry entry;
        try {
            entry = lotteryRegistrationService.registerMember(event, memberId, request.zoneId(), request.quantity(), now);
        } catch (IllegalStateException e) {
            log.warn("Lottery registration denied: {}", e.getMessage());
            return LotteryRegistrationResponse.failure(e.getMessage());
        } catch (OptimisticLockException e) {
            log.warn("Lottery registration conflict: eventId={}, memberId={}", request.eventId(), memberId);
            return LotteryRegistrationResponse.failure("Lottery registration changed concurrently. Please retry.");
        }
        return LotteryRegistrationResponse.success(entry.id(), entry.registeredAt());
    }

    public List<com.ticketing.domain.order.ActiveOrder> drawLottery(String token, UUID eventId, int capacity) {
        if (eventId == null) throw new IllegalArgumentException("eventId is required");
        UUID memberId = authenticateMember(token);
        // Authorize if needed
        List<com.ticketing.domain.order.ActiveOrder> winners = lotteryDrawService.draw(eventId, capacity);
        if (notificationService != null) {
            for (com.ticketing.domain.order.ActiveOrder order : winners) {
                if (order.getMemberId() != null) {
                    notificationService.notify(order.getMemberId().toString(), "You have won the lottery! You can now purchase tickets for the event.");
                }
            }
        }
        return winners;
    }

    public void cancelEvent(String token, UUID eventId) {
        if (eventId == null) {
            log.warn("Event cancellation denied: missing eventId");
            throw new IllegalArgumentException("eventId is required");
        }
        UUID memberId = authenticateMember(token);
        domainService.cancelEvent(memberId, eventId);
        if (orderCheckoutService != null) {
            List<CompletedPurchase> refunds = orderCheckoutService.refundEventPurchases(eventId);
            if (notificationService != null) {
                for (CompletedPurchase p : refunds) {
                    if (p.memberId() != null) {
                        notificationService.notify(p.memberId().toString(), "The event you purchased tickets for has been cancelled and you have been refunded.");
                    }
                }
            }
        }
        if (notificationService != null) {
            notificationService.notify(memberId.toString(), "Event was cancelled successfully.");
        }
    }

    public void publishEvent(String token, UUID eventId) {
        if (eventId == null) {
            log.warn("Event publishing denied: missing eventId");
            throw new IllegalArgumentException("eventId is required");
        }
        UUID memberId = authenticateMember(token);
        domainService.publishEvent(memberId, eventId);
    }

    public EventDetailsDTO editEvent(String token, EditEventRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request cannot be null");
        }
        UUID memberId = authenticateMember(token);
        return domainService.editEvent(memberId, request);
    }

    // ── Event-scoped purchase policy ────────────────────────────────

    public void setEventPurchasePolicy(String token, UUID eventId, IPurchasePolicy policy) {
        if (eventId == null) throw new IllegalArgumentException("eventId is required");
        if (policy == null) throw new IllegalArgumentException("policy is required");
        UUID memberId = authenticateMember(token);
        domainService.setEventPurchasePolicy(memberId, eventId, policy);
    }

    public void removeEventPurchasePolicy(String token, UUID eventId) {
        if (eventId == null) throw new IllegalArgumentException("eventId is required");
        UUID memberId = authenticateMember(token);
        domainService.removeEventPurchasePolicy(memberId, eventId);
    }

    public void addEventPurchasePolicy(String token, UUID eventId, IPurchasePolicy policy, boolean useOr) {
        if (eventId == null) throw new IllegalArgumentException("eventId is required");
        if (policy == null) throw new IllegalArgumentException("policy is required");
        UUID memberId = authenticateMember(token);
        domainService.addEventPurchasePolicy(memberId, eventId, policy, useOr);
    }

    // ── Event-scoped discount policy ────────────────────────────────

    public void setEventDiscountPolicy(String token, UUID eventId, IDiscountPolicy policy) {
        if (eventId == null) throw new IllegalArgumentException("eventId is required");
        if (policy == null) throw new IllegalArgumentException("policy is required");
        UUID memberId = authenticateMember(token);
        domainService.setEventDiscountPolicy(memberId, eventId, policy);
    }

    public void removeEventDiscountPolicy(String token, UUID eventId) {
        if (eventId == null) throw new IllegalArgumentException("eventId is required");
        UUID memberId = authenticateMember(token);
        domainService.removeEventDiscountPolicy(memberId, eventId);
    }

    public void addEventDiscountPolicy(String token, UUID eventId, IDiscountPolicy policy, boolean useStacking) {
        if (eventId == null) throw new IllegalArgumentException("eventId is required");
        if (policy == null) throw new IllegalArgumentException("policy is required");
        UUID memberId = authenticateMember(token);
        domainService.addEventDiscountPolicy(memberId, eventId, policy, useStacking);
    }

    // ── Read helpers (event policy queries) ─────────────────────────

    public IPurchasePolicy getEventPurchasePolicy(String token, UUID eventId) {
        authenticateMember(token);
        return domainService.getEventPurchasePolicy(eventId);
    }

    public IDiscountPolicy getEventDiscountPolicy(String token, UUID eventId) {
        authenticateMember(token);
        return domainService.getEventDiscountPolicy(eventId);
    }

    // --- UC-C.1: layout & inventory ---

    public void addSeatsToZone(String token, UUID eventId, UUID zoneId,
                               java.util.List<CreateEventRequest.SeatSpec> seats) {
        if (seats == null || seats.isEmpty()) {
            throw new IllegalArgumentException("seats list is required");
        }
        if (eventId == null) {
            log.warn("Invalid eventId: {}", eventId);
            throw new IllegalArgumentException("eventId is required");
        }
        UUID memberId = authenticateMember(token);
        domainService.addSeatsToZone(memberId, eventId, zoneId, seats);
    }

    public void removeSeats(String token, UUID eventId, UUID zoneId,
                            java.util.List<UUID> seatIds) {
        if (seatIds == null || seatIds.isEmpty()) {
            log.warn("Invalid seatIds list: {}", seatIds);
            throw new IllegalArgumentException("seatIds list is required");
        }
        if (eventId == null) throw new IllegalArgumentException("eventId is required");
        UUID memberId = authenticateMember(token);
        domainService.removeSeats(memberId, eventId, zoneId, seatIds);
    }

    public void increaseGACapacity(String token, UUID eventId, UUID zoneId, int delta) {
        if (eventId == null) {
            log.warn("Invalid eventId: {}", eventId);
            throw new IllegalArgumentException("eventId is required");
        }
        UUID memberId = authenticateMember(token);
        domainService.increaseGACapacity(memberId, eventId, zoneId, delta);
    }

    public void decreaseGACapacity(String token, UUID eventId, UUID zoneId, int delta) {
        if (eventId == null) {
            log.warn("Invalid eventId: {}", eventId);
            throw new IllegalArgumentException("eventId is required");
        }
        UUID memberId = authenticateMember(token);
        domainService.decreaseGACapacity(memberId, eventId, zoneId, delta);
    }

    public void setZonePrice(String token, UUID eventId, UUID zoneId,
                             java.math.BigDecimal newPrice) {
        if (eventId == null) {
            log.warn("Invalid eventId: {}", eventId);
            throw new IllegalArgumentException("eventId is required");
        }
        UUID memberId = authenticateMember(token);
        domainService.setZonePrice(memberId, eventId, zoneId, newPrice);
    }

    // ── Query (from EventQueryService) ───────────────────────────────

    public Optional<EventMapDTO> getEventMap(UUID eventId) {
        if (eventId == null) return Optional.empty();
        Optional<Event> maybe = eventRepository.findById(eventId);
        if (maybe.isEmpty()) {
            log.info("Event map request denied: id={}, reason=unknown", eventId);
            return Optional.empty();
        }
        Event event = maybe.get();
        if (!isBrowsable(event)) {
            log.info("Event map request denied: id={}, reason=status={}", eventId, event.getStatus());
            return Optional.empty();
        }
        if (event.getVenueMap() == null) {
            log.warn("Event map request denied: id={}, reason=no venueMap attached", eventId);
            return Optional.empty();
        }

        List<EventMapDTO.ZoneInfo> zoneDtos = new ArrayList<>();
        for (InventoryZone zone : event.getZones()) {
            zoneDtos.add(toZoneInfo(zone));
        }

        return Optional.of(new EventMapDTO(
                event.getId(),
                event.getName(),
                event.getCompanyName(),
                event.getStatus(),
                event.getVenueMap().getSectionToZone(),
                zoneDtos
        ));
    }

    // ── Search (from EventSearchService) ──────────────────────────────

    public List<EventSummaryDTO> searchEvents(SearchEventsRequest req) {
        return eventSearchDomainService.searchEvents(req);
    }

    // ── Private helpers (query) ───────────────────────────────────────

    private static boolean isBrowsable(Event e) {
        return e.getStatus() == EventStatus.PUBLISHED || e.getStatus() == EventStatus.SOLD_OUT;
    }

    private static EventMapDTO.ZoneInfo toZoneInfo(InventoryZone z) {
        if (z.isGA()) {
            return new EventMapDTO.ZoneInfo(
                    z.getId(), z.getName(), z.getType(), z.getPricePerTicket(),
                    z.getMaxCapacity(), z.getAvailableCount(), z.getSoldCount(),
                    List.of());
        }
        List<EventMapDTO.SeatInfo> seats = new ArrayList<>();
        for (Seat s : z.getSeats()) {
            seats.add(new EventMapDTO.SeatInfo(s.getId(), s.getRow(), s.getSeatNumber(), s.isAvailable()));
        }
        return new EventMapDTO.ZoneInfo(
                z.getId(), z.getName(), z.getType(), z.getPricePerTicket(),
                null, null, null,
                seats);
    }

    private UUID authenticateMember(String token) {
        if (token == null || token.isBlank()) {
            log.warn("Authentication token is required");
            throw new IllegalArgumentException("Authentication token is required");
        }
        if (!sessionTokenService.isValid(token)) {
            throw new IllegalArgumentException("Invalid or expired authentication token");
        }
        UUID memberId = sessionTokenService.extractMemberId(token);
        if (memberId == null) {
            throw new SecurityException("Guests cannot perform this action");
        }
        return memberId;
    }
}
