package com.ticketing.application.services;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ticketing.application.CreateEventRequest;
import com.ticketing.application.EditEventRequest;
import com.ticketing.application.auth.ISessionTokenService;
import com.ticketing.application.dto.EventDetailsDTO;
import com.ticketing.application.dto.LotteryRegistrationRequest;
import com.ticketing.application.dto.LotteryRegistrationResponse;
import com.ticketing.domain.event.Event;
import com.ticketing.domain.event.IEventRepository;
import com.ticketing.domain.exception.OptimisticLockException;
import com.ticketing.domain.lottery.ILotteryRepository;
import com.ticketing.domain.lottery.LotteryEntry;
import com.ticketing.domain.lottery.LotteryRegistrationDomainService;
import com.ticketing.domain.services.EventDomainService;
import com.ticketing.domain.services.OrderDomainService;

@org.springframework.stereotype.Service
public class EventService {

    private static final Logger log = LoggerFactory.getLogger(EventService.class);

    private final IEventRepository eventRepository;
    private final ISessionTokenService sessionTokenService;
    private final Clock clock;
    
    private final LotteryRegistrationDomainService lotteryRegistrationService;
    private final OrderDomainService orderDomainService;
    private final EventDomainService domainService;

    // For backwards compatibility with tests
    public EventService(IEventRepository eventRepository,
                        com.ticketing.domain.company.ICompanyRepository companyRepository,
                        com.ticketing.domain.member.IMemberRepository memberRepository,
                        com.ticketing.domain.order.IOrderRepository orderRepository,
                        ISessionTokenService sessionTokenService,
                        ILotteryRepository lotteryRepository,
                        Clock clock,
                        OrderService orderService) {
        this.eventRepository = eventRepository;
        this.sessionTokenService = sessionTokenService;
        this.clock = clock;
        this.orderDomainService = null; // Tests relying on this will need to be updated if they trigger cancellations
        this.domainService = new EventDomainService(eventRepository, companyRepository, memberRepository, orderRepository);
        this.lotteryRegistrationService = new LotteryRegistrationDomainService(lotteryRepository);
    }

    public EventService(IEventRepository eventRepository,
                        com.ticketing.domain.company.ICompanyRepository companyRepository,
                        com.ticketing.domain.member.IMemberRepository memberRepository,
                        com.ticketing.domain.order.IOrderRepository orderRepository,
                        ISessionTokenService sessionTokenService,
                        ILotteryRepository lotteryRepository,
                        Clock clock) {
        this(eventRepository, companyRepository, memberRepository, orderRepository,
                sessionTokenService, lotteryRepository, clock, null);
    }

    public EventService(IEventRepository eventRepository,
                        com.ticketing.domain.company.ICompanyRepository companyRepository,
                        com.ticketing.domain.member.IMemberRepository memberRepository,
                        com.ticketing.domain.order.IOrderRepository orderRepository,
                        ISessionTokenService sessionTokenService,
                        OrderService orderService) {
        this(eventRepository, companyRepository, memberRepository, orderRepository,
                sessionTokenService, null, Clock.systemUTC(), orderService);
    }

    public EventService(IEventRepository eventRepository,
                        com.ticketing.domain.company.ICompanyRepository companyRepository,
                        com.ticketing.domain.member.IMemberRepository memberRepository,
                        com.ticketing.domain.order.IOrderRepository orderRepository,
                        ISessionTokenService sessionTokenService) {
        this(eventRepository, companyRepository, memberRepository, orderRepository,
                sessionTokenService, null, Clock.systemUTC(), null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public EventService(IEventRepository eventRepository,
                        ISessionTokenService sessionTokenService,
                        ILotteryRepository lotteryRepository,
                        Clock clock,
                        com.ticketing.domain.services.OrderDomainService orderDomainService,
                        EventDomainService domainService) {
        this.eventRepository = eventRepository;
        this.sessionTokenService = sessionTokenService;
        this.clock = clock;
        this.orderDomainService = orderDomainService;
        this.domainService = domainService;
        this.lotteryRegistrationService = new LotteryRegistrationDomainService(lotteryRepository);
    }

    /**
     * Creates a new DRAFT event with inventory zones and venue map.
     * Caller must be Owner/Founder, or a Manager with both
     * MAP_DEFINITION and INVENTORY_MGMT permissions.
     */
    public UUID createEvent(String token, CreateEventRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request cannot be null");
        }
        UUID memberId = authenticateMember(token);
        return domainService.createEvent(memberId, request).getId();
    }

    /**
     * UC-II.3.6 — Register a member for an event's purchase-right lottery.
     * Validates: event supports lottery, registration window is open, member not already registered.
     */
    public LotteryRegistrationResponse registerForLottery(String token, LotteryRegistrationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request cannot be null");
        }

        UUID memberId = authenticateMember(token);

        Event event = eventRepository.findById(request.eventId())
                .orElseThrow(() -> new IllegalArgumentException("Event not found: " + request.eventId()));

        Instant now = clock.instant();

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

    public void cancelEvent(String token, UUID eventId) {
        if (eventId == null) {
            log.warn("Event cancellation denied: missing eventId");
            throw new IllegalArgumentException("eventId is required");
        }
        UUID memberId = authenticateMember(token);
        
        domainService.cancelEvent(memberId, eventId);
        
        if (orderDomainService != null) {
            orderDomainService.refundEventPurchases(eventId);
        }
    }

    /**
     * Edits core event details (name, description, artist, schedule). Same auth
     * as createEvent (Owner OR Manager with MAP_DEFINITION + INVENTORY_MGMT).
     * Rejected if the event has any active reservations.
     */
    public EventDetailsDTO editEvent(String token, EditEventRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request cannot be null");
        }
        UUID memberId = authenticateMember(token);
        return domainService.editEvent(memberId, request);
    }

    // --- UC-C.1: layout & inventory ---
    // Permission: INVENTORY_MGMT OR MAP_DEFINITION (per spec — not both, unlike createEvent).

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

