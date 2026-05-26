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
import com.ticketing.domain.company.Company;
import com.ticketing.domain.company.ICompanyRepository;
import com.ticketing.domain.event.AlwaysAllowPolicy;
import com.ticketing.domain.event.Event;
import com.ticketing.domain.event.EventCreationDomainService;
import com.ticketing.domain.event.IDiscountPolicy;
import com.ticketing.domain.event.IEventRepository;
import com.ticketing.domain.event.IPurchasePolicy;
import com.ticketing.domain.event.InventoryZone;
import com.ticketing.domain.event.NoDiscountPolicy;
import com.ticketing.domain.event.Seat;
import com.ticketing.domain.exception.OptimisticLockException;
import com.ticketing.domain.lottery.ILotteryRepository;
import com.ticketing.domain.lottery.LotteryEntry;
import com.ticketing.domain.lottery.LotteryRegistrationDomainService;
import com.ticketing.domain.member.IMemberRepository;
import com.ticketing.domain.member.ManagerPermission;
import com.ticketing.domain.member.Member;
import com.ticketing.domain.member.StaffAppointment;
import com.ticketing.domain.order.IOrderRepository;

@org.springframework.stereotype.Service
public class EventService {

    private static final Logger log = LoggerFactory.getLogger(EventService.class);

    private final IEventRepository eventRepository;
    private final ICompanyRepository companyRepository;
    private final IMemberRepository memberRepository;
    private final IOrderRepository orderRepository;
    private final ISessionTokenService sessionTokenService;
    private final ILotteryRepository lotteryRepository;
    private final Clock clock;
    
    private final EventCreationDomainService eventCreationService;
    private final LotteryRegistrationDomainService lotteryRegistrationService;
    private final OrderService orderService;

    // Per-event lock so inventory edits on Event A don't block Event B.
    private final java.util.concurrent.ConcurrentHashMap<UUID, Object> eventLocks =
            new java.util.concurrent.ConcurrentHashMap<>();

    private Object lockFor(UUID eventId) {
        return eventLocks.computeIfAbsent(eventId, k -> new Object());
    }

    @org.springframework.beans.factory.annotation.Autowired
    public EventService(IEventRepository eventRepository,
                        ICompanyRepository companyRepository,
                        IMemberRepository memberRepository,
                        IOrderRepository orderRepository,
                        ISessionTokenService sessionTokenService,
                        ILotteryRepository lotteryRepository,
                        Clock clock,
                        OrderService orderService) {
        this.eventRepository = eventRepository;
        this.companyRepository = companyRepository;
        this.memberRepository = memberRepository;
        this.orderRepository = orderRepository;
        this.sessionTokenService = sessionTokenService;
        this.lotteryRepository = lotteryRepository;
        this.clock = clock;
        this.orderService = orderService;
        
        this.eventCreationService = new EventCreationDomainService();
        this.lotteryRegistrationService = new LotteryRegistrationDomainService(this.lotteryRepository);
    }


    public EventService(IEventRepository eventRepository,
                        ICompanyRepository companyRepository,
                        IMemberRepository memberRepository,
                        IOrderRepository orderRepository,
                        ISessionTokenService sessionTokenService,
                        ILotteryRepository lotteryRepository,
                        Clock clock) {
        this(eventRepository, companyRepository, memberRepository, orderRepository,
                sessionTokenService, lotteryRepository, clock, null);
    }

    public EventService(IEventRepository eventRepository,
                        ICompanyRepository companyRepository,
                        IMemberRepository memberRepository,
                        IOrderRepository orderRepository,
                        ISessionTokenService sessionTokenService,
                        OrderService orderService) {
        this(eventRepository, companyRepository, memberRepository, orderRepository,
                sessionTokenService, null, Clock.systemUTC(), orderService);
    }

    public EventService(IEventRepository eventRepository,
                        ICompanyRepository companyRepository,
                        IMemberRepository memberRepository,
                        IOrderRepository orderRepository,
                        ISessionTokenService sessionTokenService) {
        this(eventRepository, companyRepository, memberRepository, orderRepository,
                sessionTokenService, null, Clock.systemUTC(), null);
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
        Company company = loadActiveCompany(request.companyName());
        StaffAppointment appointment = loadAppointment(memberId, company.getName());
        authorize(appointment);

        log.info("Creating event: companyName={}, memberId={}, name={}",
                company.getName(), memberId, request.name());
        
        Event event = eventCreationService.createEventFromRequest(company, request);

        saveEvent(event);
        log.info("Event created: eventId={}, companyName={}, status=DRAFT",
                event.getId(), company.getName());
        return event.getId();
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

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> {
                    log.warn("Event not found: eventId={}", eventId);
                    return new IllegalArgumentException("No event with id " + eventId);
                });

        // company is looked up but we don't require it to be active — cancellation
        // can happen as cleanup even on a suspended company.
        Company company = companyRepository.findByName(event.getCompanyName())
                .orElseThrow(() ->{
                    log.warn("Company not found: companyName={}", event.getCompanyName());
                    return new IllegalArgumentException(
                            "Company not found: " + event.getCompanyName());
                });

        StaffAppointment appt = loadAppointment(memberId, company.getName());
        if (!appt.hasPermission(ManagerPermission.EVENT_LIFECYCLE)) {
            throw new SecurityException("Insufficient permissions to cancel events");
        }

        log.info("Cancelling event: eventId={}, companyName={}", eventId, company.getName());
        event.cancel();
        saveEvent(event);
        
        if (orderService != null) {
            orderService.refundEventPurchases(eventId);
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

        Event event = eventRepository.findById(request.eventId())
                .orElseThrow(() -> {
                    log.warn("Event not found: eventId={}", request.eventId());
                    return new IllegalArgumentException("Event not found: " + request.eventId());
                });

        Company company = loadActiveCompany(event.getCompanyName());
        StaffAppointment appt = loadAppointment(memberId, company.getName());
        authorize(appt);

        if (hasActiveReservations(event.getId())) {
            throw new IllegalStateException("Cannot edit event with active reservations");
        }

        if (!request.hasAnyChange()) {
            // nothing to do — return the current snapshot, no log spam
            return EventDetailsDTO.from(event);
        }

        // apply each provided field; null = leave alone
        if (request.name() != null) {
            log.info("editEvent: eventId={} name '{}' -> '{}'", event.getId(), event.getName(), request.name());
            event.setName(request.name());
        }
        if (request.description() != null) {
            log.info("editEvent: eventId={} description updated", event.getId());
            event.setDescription(request.description());
        }
        if (request.artist() != null) {
            log.info("editEvent: eventId={} artist '{}' -> '{}'", event.getId(), event.getArtist(), request.artist());
            event.setArtist(request.artist());
        }
        if (request.schedule() != null) {
            log.info("editEvent: eventId={} schedule updated to start={}",
                    event.getId(), request.schedule().getStartTime());
            event.setSchedule(request.schedule());
        }

        saveEvent(event);
        log.info("Event edited: eventId={}, companyName={}, by={}",
                event.getId(), company.getName(), memberId);
        return EventDetailsDTO.from(event);
    }

    private boolean hasActiveReservations(UUID eventId) {
        return !orderRepository.findActiveByEventId(eventId).isEmpty();
    }

    private void saveEvent(Event event) {
        try {
            eventRepository.save(event);
        } catch (OptimisticLockException ex) {
            log.warn("Event save conflict: eventId={}", event.getId());
            throw new IllegalStateException("Event changed concurrently. Please retry.", ex);
        }
    }

    // ── Event-scoped purchase policy ────────────────────────────────

    public void setEventPurchasePolicy(String token, UUID eventId, IPurchasePolicy policy) {
        if (eventId == null) throw new IllegalArgumentException("eventId is required");
        if (policy == null) throw new IllegalArgumentException("policy is required");

        UUID memberId = authenticateMember(token);
        Event event = loadEvent(eventId);
        Company company = loadActiveCompany(event.getCompanyName());
        authorizePolicy(memberId, company.getName());

        event.setPurchasePolicy(policy);
        saveEvent(event);
        log.info("Event purchase policy updated: eventId={}, by={}", eventId, memberId);
    }

    public void removeEventPurchasePolicy(String token, UUID eventId) {
        if (eventId == null) throw new IllegalArgumentException("eventId is required");

        UUID memberId = authenticateMember(token);
        Event event = loadEvent(eventId);
        Company company = loadActiveCompany(event.getCompanyName());
        authorizePolicy(memberId, company.getName());

        event.setPurchasePolicy(new AlwaysAllowPolicy());
        saveEvent(event);
        log.info("Event purchase policy reset to default: eventId={}, by={}", eventId, memberId);
    }

    // ── Event-scoped discount policy ────────────────────────────────

    public void setEventDiscountPolicy(String token, UUID eventId, IDiscountPolicy policy) {
        if (eventId == null) throw new IllegalArgumentException("eventId is required");
        if (policy == null) throw new IllegalArgumentException("policy is required");

        UUID memberId = authenticateMember(token);
        Event event = loadEvent(eventId);
        Company company = loadActiveCompany(event.getCompanyName());
        authorizePolicy(memberId, company.getName());

        event.setDiscountPolicy(policy);
        saveEvent(event);
        log.info("Event discount policy updated: eventId={}, by={}", eventId, memberId);
    }

    public void removeEventDiscountPolicy(String token, UUID eventId) {
        if (eventId == null) throw new IllegalArgumentException("eventId is required");

        UUID memberId = authenticateMember(token);
        Event event = loadEvent(eventId);
        Company company = loadActiveCompany(event.getCompanyName());
        authorizePolicy(memberId, company.getName());

        event.setDiscountPolicy(new NoDiscountPolicy());
        saveEvent(event);
        log.info("Event discount policy reset to default: eventId={}, by={}", eventId, memberId);
    }

    // ── Read helpers (event policy queries) ─────────────────────────

    public IPurchasePolicy getEventPurchasePolicy(String token, UUID eventId) {
        authenticateMember(token);
        return loadEvent(eventId).getEventPurchasePolicy();
    }

    public IDiscountPolicy getEventDiscountPolicy(String token, UUID eventId) {
        authenticateMember(token);
        return loadEvent(eventId).getEventDiscountPolicy();
    }

    // --- UC-C.1: layout & inventory ---
    // Permission: INVENTORY_MGMT OR MAP_DEFINITION (per spec — not both, unlike createEvent).
    // Each method serialises only on its target event id, so unrelated events
    // can be edited in parallel.

    public void addSeatsToZone(String token, UUID eventId, UUID zoneId,
                               java.util.List<CreateEventRequest.SeatSpec> seats) {
        if (seats == null || seats.isEmpty()) {
            throw new IllegalArgumentException("seats list is required");
        }
        if (eventId == null) {
            log.warn("Invalid eventId: {}", eventId);
            throw new IllegalArgumentException("eventId is required");
        }
        synchronized (lockFor(eventId)) {
            Event event = loadEventForInventoryEdit(token, eventId);
            InventoryZone zone = event.findZone(zoneId);
            for (CreateEventRequest.SeatSpec spec : seats) {
                zone.addSeat(new Seat(UUID.randomUUID(), spec.row(), spec.seatNumber()));
            }
            saveEvent(event);
            log.info("Inventory: added {} seats to zone={} event={}", seats.size(), zoneId, eventId);
        }
    }

    public void removeSeats(String token, UUID eventId, UUID zoneId,
                            java.util.List<UUID> seatIds) {
        if (seatIds == null || seatIds.isEmpty()) {
            log.warn("Invalid seatIds list: {}", seatIds);
            throw new IllegalArgumentException("seatIds list is required");
        }
        if (eventId == null) throw new IllegalArgumentException("eventId is required");
        synchronized (lockFor(eventId)) {
            Event event = loadEventForInventoryEdit(token, eventId);
            InventoryZone zone = event.findZone(zoneId);
            for (UUID seatId : seatIds) {
                zone.removeSeat(seatId);
            }
            saveEvent(event);
            log.info("Inventory: removed {} seats from zone={} event={}", seatIds.size(), zoneId, eventId);
        }
    }

    public void increaseGACapacity(String token, UUID eventId, UUID zoneId, int delta) {
        if (eventId == null) {
            log.warn("Invalid eventId: {}", eventId);
            throw new IllegalArgumentException("eventId is required");
        }
        synchronized (lockFor(eventId)) {
            Event event = loadEventForInventoryEdit(token, eventId);
            event.findZone(zoneId).increaseCapacity(delta);
            saveEvent(event);
            log.info("Inventory: GA capacity +{} on zone={} event={}", delta, zoneId, eventId);
        }
    }

    public void decreaseGACapacity(String token, UUID eventId, UUID zoneId, int delta) {
        if (eventId == null) {
            log.warn("Invalid eventId: {}", eventId);
            throw new IllegalArgumentException("eventId is required");
        }
        synchronized (lockFor(eventId)) {
            Event event = loadEventForInventoryEdit(token, eventId);
            event.findZone(zoneId).decreaseCapacity(delta);
            saveEvent(event);
            log.info("Inventory: GA capacity -{} on zone={} event={}", delta, zoneId, eventId);
        }
    }

    public void setZonePrice(String token, UUID eventId, UUID zoneId,
                             java.math.BigDecimal newPrice) {
        if (eventId == null) {
            log.warn("Invalid eventId: {}", eventId);
            throw new IllegalArgumentException("eventId is required");
        }
        synchronized (lockFor(eventId)) {
            Event event = loadEventForInventoryEdit(token, eventId);
            event.findZone(zoneId).setPricePerTicket(newPrice);
            saveEvent(event);
            log.info("Inventory: price={} on zone={} event={}", newPrice, zoneId, eventId);
        }
    }

    private Event loadEventForInventoryEdit(String token, UUID eventId) {
        UUID memberId = authenticateMember(token);
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> {
                    log.warn("Event not found: eventId={}", eventId);
                    return new IllegalArgumentException("Event not found: " + eventId);
                });
        if (event.isCancelled()) {
            log.warn("Cannot edit inventory on a cancelled event: eventId={}", eventId);
            throw new IllegalStateException("Cannot edit inventory on a cancelled event");
        }
        if (event.getStatus() == com.ticketing.domain.event.EventStatus.SOLD_OUT) {
            log.warn("Cannot edit inventory on a sold-out event: eventId={}", eventId);
            throw new IllegalStateException("Cannot edit inventory on a sold-out event");
        }
        Company company = loadActiveCompany(event.getCompanyName());
        StaffAppointment appt = loadAppointment(memberId, company.getName());
        authorizeInventory(appt);
        return event;
    }

    private void authorizeInventory(StaffAppointment appt) {
        boolean allowed = appt.isOwner()
                || (appt.isManager()
                    && (appt.hasPermission(ManagerPermission.INVENTORY_MGMT)
                        || appt.hasPermission(ManagerPermission.MAP_DEFINITION)));
        if (!allowed) {
            log.warn("Insufficient permissions to edit inventory");
            throw new SecurityException(
                    "Inventory edits require INVENTORY_MGMT or MAP_DEFINITION permission");
        }
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

    private Company loadActiveCompany(String companyName) {
        Company company = companyRepository.findByName(companyName)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Company not found: " + companyName));
        if (!company.isActive()) {
            throw new IllegalStateException(
                    "Cannot create events in a suspended or closed company: " + companyName);
        }
        return company;
    }

    private StaffAppointment loadAppointment(UUID memberId, String companyName) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Member not found: " + memberId));
        StaffAppointment appointment = member.getStaffAppointment(companyName);
        if (appointment == null) {
            throw new SecurityException(
                    "Caller is not a staff member of company: " + companyName);
        }
        return appointment;
    }

    private void authorize(StaffAppointment appointment) {
        boolean allowed = appointment.isOwner()
                || (appointment.isManager()
                    && appointment.hasPermission(ManagerPermission.MAP_DEFINITION)
                    && appointment.hasPermission(ManagerPermission.INVENTORY_MGMT));
        if (!allowed) {
            throw new SecurityException(
                    "Insufficient permissions to create events");
        }
    }

    private void authorizePolicy(UUID memberId, String companyName) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Member not found: " + memberId));
        StaffAppointment appt = member.getStaffAppointment(companyName);
        if (appt == null) {
            throw new SecurityException("Caller is not a staff member of company: " + companyName);
        }
        boolean allowed = appt.isOwner()
                || (appt.isManager() && appt.hasPermission(ManagerPermission.POLICY_MODIFICATION));
        if (!allowed) {
            throw new SecurityException("Insufficient permissions: POLICY_MODIFICATION required");
        }
    }

    private Event loadEvent(UUID eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Event not found: " + eventId));
    }

}

