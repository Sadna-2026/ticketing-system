package com.ticketing.application.services;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

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
import com.ticketing.domain.company.Company;
import com.ticketing.domain.company.ICompanyRepository;
import com.ticketing.domain.event.AndPolicy;
import com.ticketing.domain.event.Event;
import com.ticketing.domain.event.EventStatus;
import com.ticketing.domain.event.IDiscountPolicy;
import com.ticketing.domain.event.IEventRepository;
import com.ticketing.domain.event.IPurchasePolicy;
import com.ticketing.domain.event.InventoryZone;
import com.ticketing.domain.event.LayoutCell;
import com.ticketing.domain.event.MaxCompositeDiscount;
import com.ticketing.domain.event.OrPolicy;
import com.ticketing.domain.event.Seat;
import com.ticketing.domain.event.SumCompositeDiscount;
import com.ticketing.domain.event.VenueLayout;
import com.ticketing.domain.event.VenueMap;
import com.ticketing.domain.exception.OptimisticLockException;
import com.ticketing.domain.lottery.ILotteryRepository;
import com.ticketing.domain.lottery.LotteryEntry;
import com.ticketing.domain.member.IMemberRepository;
import com.ticketing.domain.member.ManagerPermission;
import com.ticketing.domain.member.Member;
import com.ticketing.domain.member.StaffAppointment;
import com.ticketing.domain.order.ActiveOrder;
import com.ticketing.domain.order.IOrderRepository;
import com.ticketing.domain.order.OrderItem;

/**
 * Application service for event lifecycle, inventory, policies and the lottery.
 *
 * <p>V3-10 (#268): each public use-case method is one atomic transaction. The class
 * default is {@code @Transactional(readOnly = true)} (queries); mutating use cases
 * override it with a read-write {@code @Transactional}. Inert in {@code memory}-mode
 * unit tests that build the service with {@code new} (no Spring proxy).
 */
@org.springframework.stereotype.Service
@Transactional(readOnly = true)
public class EventService {

    private static final Logger log = LoggerFactory.getLogger(EventService.class);

    private final IEventRepository eventRepository;
    private final ICompanyRepository companyRepository;
    private final IMemberRepository memberRepository;
    private final IOrderRepository orderRepository;
    private final ILotteryRepository lotteryRepository;
    private final ISessionTokenService sessionTokenService;
    private final ISystemClock systemClock;

    private final OrderService orderService;
    private final INotificationService notificationService;
    private final java.util.Random random;

    private final ConcurrentHashMap<UUID, Object> eventLocks = new ConcurrentHashMap<>();

    // Backward compat constructors for tests
    public EventService(IEventRepository eventRepository,
            ICompanyRepository companyRepository,
            IMemberRepository memberRepository,
            IOrderRepository orderRepository,
            ISessionTokenService sessionTokenService,
            ILotteryRepository lotteryRepository,
            ISystemClock systemClock,
            OrderService orderService) {
        this.eventRepository = eventRepository;
        this.companyRepository = companyRepository;
        this.memberRepository = memberRepository;
        this.orderRepository = orderRepository;
        this.lotteryRepository = lotteryRepository;
        this.sessionTokenService = sessionTokenService;
        this.systemClock = systemClock;
        this.orderService = orderService;
        this.notificationService = null;
        this.random = new java.util.Random();
    }

    public EventService(IEventRepository eventRepository,
            ICompanyRepository companyRepository,
            IMemberRepository memberRepository,
            IOrderRepository orderRepository,
            ISessionTokenService sessionTokenService,
            ILotteryRepository lotteryRepository,
            ISystemClock systemClock) {
        this(eventRepository, companyRepository, memberRepository, orderRepository,
                sessionTokenService, lotteryRepository, systemClock, null);
    }

    public EventService(IEventRepository eventRepository,
            ICompanyRepository companyRepository,
            IMemberRepository memberRepository,
            IOrderRepository orderRepository,
            ISessionTokenService sessionTokenService,
            OrderService orderService) {
        this(eventRepository, companyRepository, memberRepository, orderRepository,
                sessionTokenService, null, Instant::now, orderService);
    }

    public EventService(IEventRepository eventRepository,
            ICompanyRepository companyRepository,
            IMemberRepository memberRepository,
            IOrderRepository orderRepository,
            ISessionTokenService sessionTokenService) {
        this(eventRepository, companyRepository, memberRepository, orderRepository,
                sessionTokenService, null, Instant::now, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public EventService(IEventRepository eventRepository,
            ICompanyRepository companyRepository,
            IMemberRepository memberRepository,
            IOrderRepository orderRepository,
            ISessionTokenService sessionTokenService,
            ILotteryRepository lotteryRepository,
            ISystemClock systemClock,
            OrderService orderService,
            @org.springframework.beans.factory.annotation.Autowired(required = false) INotificationService notificationService) {
        this.eventRepository = eventRepository;
        this.companyRepository = companyRepository;
        this.memberRepository = memberRepository;
        this.orderRepository = orderRepository;
        this.lotteryRepository = lotteryRepository;
        this.sessionTokenService = sessionTokenService;
        this.systemClock = systemClock;
        this.orderService = orderService;
        this.notificationService = notificationService;
        this.random = new java.util.Random();
    }

    // ── Event CRUD ──────────────────────────────────────────────────

    @Transactional
    public UUID createEvent(String token, CreateEventRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request cannot be null");
        }
        UUID memberId = authenticateMember(token);
        rejectIfSuspended(memberId);

        Company company = loadActiveCompany(request.companyName());
        StaffAppointment appointment = loadAppointment(memberId, company.getName());
        authorizeEventCreation(appointment);

        log.info("Creating event: companyName={}, memberId={}, name={}",
                company.getName(), memberId, request.name());

        Event event = new Event(
                UUID.randomUUID(),
                company.getName(),
                request.name(),
                request.description(),
                request.category(),
                request.schedule(),
                request.lockTimerDuration(),
                company.getPurchasePolicy(),
                company.getDiscountPolicy(),
                request.saleMethod(),
                request.lotteryWindow());

        Map<String, UUID> zoneIdsByName = new LinkedHashMap<>();
        for (CreateEventRequest.ZoneSpec spec : request.zones()) {
            InventoryZone zone = buildZone(spec);
            if (zoneIdsByName.put(spec.name(), zone.getId()) != null) {
                throw new IllegalArgumentException(
                        "Duplicate zone name in request: " + spec.name());
            }
            event.addZone(zone);
        }

        VenueMap venueMap = buildVenueMap(request.sectionToZoneName(), zoneIdsByName);
        event.setVenueMap(venueMap);

        saveEvent(event);
        log.info("Event created: eventId={}, companyName={}, status=DRAFT",
                event.getId(), company.getName());

        if (orderService != null) {
            orderService.createQueue(token, event.getId());
            log.info("Default virtual queue created for event: eventId={}", event.getId());
        }

        return event.getId();
    }

    @Transactional
    public void cancelEvent(String token, UUID eventId) {
        if (eventId == null) {
            log.warn("Event cancellation denied: missing eventId");
            throw new IllegalArgumentException("eventId is required");
        }
        UUID memberId = authenticateMember(token);
        rejectIfSuspended(memberId);

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> {
                    log.warn("Event not found: eventId={}", eventId);
                    return new IllegalArgumentException("No event with id " + eventId);
                });

        Company company = companyRepository.findByName(event.getCompanyName())
                .orElseThrow(() -> {
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
        if (notificationService != null) {
            notificationService.notify(memberId.toString(), "Event was cancelled successfully.");
        }
    }

    @Transactional
    public void publishEvent(String token, UUID eventId) {
        if (eventId == null) {
            log.warn("Event publishing denied: missing eventId");
            throw new IllegalArgumentException("eventId is required");
        }
        UUID memberId = authenticateMember(token);
        rejectIfSuspended(memberId);

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> {
                    log.warn("Event not found: eventId={}", eventId);
                    return new IllegalArgumentException("Event not found: " + eventId);
                });
        Company company = loadActiveCompany(event.getCompanyName());
        StaffAppointment appt = loadAppointment(memberId, company.getName());
        if (!appt.hasPermission(ManagerPermission.EVENT_LIFECYCLE)) {
            throw new SecurityException("Insufficient permissions to publish events");
        }
        log.info("Publishing event: eventId={}, companyName={}", eventId, company.getName());
        event.publish();
        saveEvent(event);
    }

    @Transactional
    public EventDetailsDTO editEvent(String token, EditEventRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request cannot be null");
        }
        UUID memberId = authenticateMember(token);
        rejectIfSuspended(memberId);

        Event event = eventRepository.findById(request.eventId())
                .orElseThrow(() -> {
                    log.warn("Event not found: eventId={}", request.eventId());
                    return new IllegalArgumentException("Event not found: " + request.eventId());
                });

        Company company = loadActiveCompany(event.getCompanyName());
        StaffAppointment appt = loadAppointment(memberId, company.getName());
        authorizeEventCreation(appt);

        if (hasActiveReservations(event.getId())) {
            throw new IllegalStateException("Cannot edit event with active reservations");
        }

        if (!request.hasAnyChange()) {
            return EventDetailsDTO.from(event);
        }

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

    // ── Lottery ─────────────────────────────────────────────────────

    @Transactional
    public LotteryRegistrationResponse registerForLottery(String token, LotteryRegistrationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request cannot be null");
        }
        UUID memberId = authenticateMember(token);
        rejectIfSuspended(memberId);
        Event event = eventRepository.findById(request.eventId())
                .orElseThrow(() -> new IllegalArgumentException("Event not found: " + request.eventId()));
        Instant now = systemClock.now();
        LotteryEntry entry;
        try {
            if (!event.isLottery()) {
                throw new IllegalStateException("Event does not support lottery sale method.");
            }
            if (!event.isLotteryRegistrationOpen(now)) {
                throw new IllegalStateException("Lottery registration window is closed.");
            }
            if (lotteryRepository.findByEventAndMember(event.getId(), memberId).isPresent()) {
                throw new IllegalStateException("Member is already registered for this lottery.");
            }

            event.findZone(request.zoneId());
            entry = new LotteryEntry(
                    UUID.randomUUID(),
                    event.getId(),
                    memberId,
                    request.zoneId(),
                    request.quantity(),
                    now);
            lotteryRepository.save(entry);
        } catch (IllegalStateException e) {
            log.warn("Lottery registration denied: {}", e.getMessage());
            return LotteryRegistrationResponse.failure(e.getMessage());
        } catch (OptimisticLockException e) {
            log.warn("Lottery registration conflict: eventId={}, memberId={}", request.eventId(), memberId);
            return LotteryRegistrationResponse.failure("Lottery registration changed concurrently. Please retry.");
        }
        log.info("Lottery registration successful: memberId={}, eventId={}, entryId={}",
                memberId, event.getId(), entry.id());
        return LotteryRegistrationResponse.success(entry.id(), entry.registeredAt());
    }

    @Transactional
    public List<ActiveOrder> drawLottery(String token, UUID eventId, int capacity) {
        if (eventId == null)
            throw new IllegalArgumentException("eventId is required");
        UUID memberId = authenticateMember(token);
        rejectIfSuspended(memberId);

        if (capacity < 0) {
            throw new IllegalArgumentException("Capacity cannot be negative");
        }

        List<LotteryEntry> allEntries = lotteryRepository.findByEventId(eventId);
        List<LotteryEntry> winners = selectLotteryWinners(allEntries, capacity);

        if (winners.isEmpty()) {
            return new ArrayList<>();
        }

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Event not found: " + eventId));

        List<ActiveOrder> createdOrders = new ArrayList<>();

        for (LotteryEntry winner : winners) {
            UUID sessionId = UUID.randomUUID();
            ActiveOrder order = new ActiveOrder(UUID.randomUUID(), sessionId, winner.memberId(), eventId, systemClock.now());

            InventoryZone zone = event.findZone(winner.zoneId());
            zone.lockGA(winner.quantity());

            OrderItem item = OrderItem.forGA(UUID.randomUUID(), winner.zoneId(), winner.quantity(), zone.getPricePerTicket());
            order.addItem(item);

            try {
                orderRepository.save(order);
            } catch (OptimisticLockException ex) {
                throw new IllegalStateException("Lottery draw order changed concurrently. Please retry.", ex);
            }
            createdOrders.add(order);
        }

        saveEvent(event);

        if (notificationService != null) {
            Set<UUID> winnerMemberIds = new HashSet<>();
            for (ActiveOrder order : createdOrders) {
                if (order.getMemberId() != null) {
                    winnerMemberIds.add(order.getMemberId());
                    notificationService.notify(order.getMemberId().toString(),
                            "You have won the lottery! You can now purchase tickets for the event.");
                }
            }
            // Non-winners (registrants who were not selected) receive a result notification too.
            for (LotteryEntry entry : allEntries) {
                if (entry.memberId() != null && !winnerMemberIds.contains(entry.memberId())) {
                    notificationService.notify(entry.memberId().toString(),
                            "The lottery draw has concluded. Unfortunately, you were not selected this time.");
                }
            }
        }
        return createdOrders;
    }

    private List<LotteryEntry> selectLotteryWinners(List<LotteryEntry> entries, int capacity) {
        List<LotteryEntry> pool = new ArrayList<>(entries);
        List<LotteryEntry> winners = new ArrayList<>();
        int numWinners = Math.min(pool.size(), capacity);
        for (int i = 0; i < numWinners; i++) {
            int index = random.nextInt(pool.size());
            winners.add(pool.remove(index));
        }
        return winners;
    }

    // ── Event-scoped purchase policy ────────────────────────────────

    @Transactional
    public void setEventPurchasePolicy(String token, UUID eventId, IPurchasePolicy policy) {
        if (eventId == null)
            throw new IllegalArgumentException("eventId is required");
        if (policy == null)
            throw new IllegalArgumentException("policy is required");
        UUID memberId = authenticateMember(token);
        rejectIfSuspended(memberId);
        Event event = eventRepository.findById(eventId).orElseThrow(() -> new IllegalArgumentException("Event not found: " + eventId));
        Company company = loadActiveCompany(event.getCompanyName());
        StaffAppointment appt = loadAppointment(memberId, company.getName());
        authorizePolicy(appt);
        event.setPurchasePolicy(policy);
        saveEvent(event);
    }

    @Transactional
    public void removeEventPurchasePolicy(String token, UUID eventId) {
        if (eventId == null)
            throw new IllegalArgumentException("eventId is required");
        UUID memberId = authenticateMember(token);
        rejectIfSuspended(memberId);
        Event event = eventRepository.findById(eventId).orElseThrow(() -> new IllegalArgumentException("Event not found: " + eventId));
        Company company = loadActiveCompany(event.getCompanyName());
        StaffAppointment appt = loadAppointment(memberId, company.getName());
        authorizePolicy(appt);
        event.setPurchasePolicy(company.getPurchasePolicy());
        saveEvent(event);
    }

    @Transactional
    public void addEventPurchasePolicy(String token, UUID eventId, IPurchasePolicy policy, boolean useOr) {
        if (eventId == null)
            throw new IllegalArgumentException("eventId is required");
        if (policy == null)
            throw new IllegalArgumentException("policy is required");
        UUID memberId = authenticateMember(token);
        rejectIfSuspended(memberId);
        Event event = eventRepository.findById(eventId).orElseThrow(() -> new IllegalArgumentException("Event not found: " + eventId));
        Company company = loadActiveCompany(event.getCompanyName());
        StaffAppointment appt = loadAppointment(memberId, company.getName());
        authorizePolicy(appt);
        IPurchasePolicy current = event.getEventPurchasePolicy();
        IPurchasePolicy composed = useOr
                ? new OrPolicy(List.of(current, policy))
                : new AndPolicy(List.of(current, policy));
        event.setPurchasePolicy(composed);
        saveEvent(event);
    }

    // ── Event-scoped discount policy ────────────────────────────────

    @Transactional
    public void setEventDiscountPolicy(String token, UUID eventId, IDiscountPolicy policy) {
        if (eventId == null)
            throw new IllegalArgumentException("eventId is required");
        if (policy == null)
            throw new IllegalArgumentException("policy is required");
        UUID memberId = authenticateMember(token);
        rejectIfSuspended(memberId);
        Event event = eventRepository.findById(eventId).orElseThrow(() -> new IllegalArgumentException("Event not found: " + eventId));
        Company company = loadActiveCompany(event.getCompanyName());
        StaffAppointment appt = loadAppointment(memberId, company.getName());
        authorizePolicy(appt);
        event.setDiscountPolicy(policy);
        saveEvent(event);
    }

    @Transactional
    public void removeEventDiscountPolicy(String token, UUID eventId) {
        if (eventId == null)
            throw new IllegalArgumentException("eventId is required");
        UUID memberId = authenticateMember(token);
        rejectIfSuspended(memberId);
        Event event = eventRepository.findById(eventId).orElseThrow(() -> new IllegalArgumentException("Event not found: " + eventId));
        Company company = loadActiveCompany(event.getCompanyName());
        StaffAppointment appt = loadAppointment(memberId, company.getName());
        authorizePolicy(appt);
        event.setDiscountPolicy(company.getDiscountPolicy());
        saveEvent(event);
    }

    @Transactional
    public void addEventDiscountPolicy(String token, UUID eventId, IDiscountPolicy policy, boolean useStacking) {
        if (eventId == null)
            throw new IllegalArgumentException("eventId is required");
        if (policy == null)
            throw new IllegalArgumentException("policy is required");
        UUID memberId = authenticateMember(token);
        rejectIfSuspended(memberId);
        Event event = eventRepository.findById(eventId).orElseThrow(() -> new IllegalArgumentException("Event not found: " + eventId));
        Company company = loadActiveCompany(event.getCompanyName());
        StaffAppointment appt = loadAppointment(memberId, company.getName());
        authorizePolicy(appt);
        IDiscountPolicy current = event.getEventDiscountPolicy();
        IDiscountPolicy composed = useStacking
                ? new SumCompositeDiscount(List.of(current, policy))
                : new MaxCompositeDiscount(List.of(current, policy));
        event.setDiscountPolicy(composed);
        saveEvent(event);
    }

    // ── Read helpers (event policy queries) ─────────────────────────

    public IPurchasePolicy getEventPurchasePolicy(String token, UUID eventId) {
        authenticateMember(token);
        return eventRepository.findById(eventId).orElseThrow(() -> new IllegalArgumentException("Event not found: " + eventId)).getEventPurchasePolicy();
    }

    public IDiscountPolicy getEventDiscountPolicy(String token, UUID eventId) {
        authenticateMember(token);
        return eventRepository.findById(eventId).orElseThrow(() -> new IllegalArgumentException("Event not found: " + eventId)).getEventDiscountPolicy();
    }

    // ── Inventory management ────────────────────────────────────────

    @Transactional
    public void addSeatsToZone(String token, UUID eventId, UUID zoneId,
            List<CreateEventRequest.SeatSpec> seats) {
        if (seats == null || seats.isEmpty()) {
            throw new IllegalArgumentException("seats list is required");
        }
        if (eventId == null) {
            log.warn("Invalid eventId: {}", eventId);
            throw new IllegalArgumentException("eventId is required");
        }
        UUID memberId = authenticateMember(token);
        rejectIfSuspended(memberId);
        synchronized (lockFor(eventId)) {
            Event event = loadEventForInventoryEdit(memberId, eventId);
            InventoryZone zone = event.findZone(zoneId);
            for (CreateEventRequest.SeatSpec spec : seats) {
                zone.addSeat(new Seat(UUID.randomUUID(), spec.row(), spec.seatNumber()));
            }
            saveEvent(event);
            log.info("Inventory: added {} seats to zone={} event={}", seats.size(), zoneId, eventId);
        }
    }

    @Transactional
    public void removeSeats(String token, UUID eventId, UUID zoneId,
            List<UUID> seatIds) {
        if (seatIds == null || seatIds.isEmpty()) {
            log.warn("Invalid seatIds list: {}", seatIds);
            throw new IllegalArgumentException("seatIds list is required");
        }
        if (eventId == null)
            throw new IllegalArgumentException("eventId is required");
        UUID memberId = authenticateMember(token);
        rejectIfSuspended(memberId);
        synchronized (lockFor(eventId)) {
            Event event = loadEventForInventoryEdit(memberId, eventId);
            InventoryZone zone = event.findZone(zoneId);
            for (UUID seatId : seatIds) {
                zone.removeSeat(seatId);
            }
            saveEvent(event);
            log.info("Inventory: removed {} seats from zone={} event={}", seatIds.size(), zoneId, eventId);
        }
    }

    @Transactional
    public void increaseGACapacity(String token, UUID eventId, UUID zoneId, int delta) {
        if (eventId == null) {
            log.warn("Invalid eventId: {}", eventId);
            throw new IllegalArgumentException("eventId is required");
        }
        UUID memberId = authenticateMember(token);
        rejectIfSuspended(memberId);
        synchronized (lockFor(eventId)) {
            Event event = loadEventForInventoryEdit(memberId, eventId);
            event.findZone(zoneId).increaseCapacity(delta);
            saveEvent(event);
            log.info("Inventory: GA capacity +{} on zone={} event={}", delta, zoneId, eventId);
        }
    }

    @Transactional
    public void decreaseGACapacity(String token, UUID eventId, UUID zoneId, int delta) {
        if (eventId == null) {
            log.warn("Invalid eventId: {}", eventId);
            throw new IllegalArgumentException("eventId is required");
        }
        UUID memberId = authenticateMember(token);
        rejectIfSuspended(memberId);
        synchronized (lockFor(eventId)) {
            Event event = loadEventForInventoryEdit(memberId, eventId);
            event.findZone(zoneId).decreaseCapacity(delta);
            saveEvent(event);
            log.info("Inventory: GA capacity -{} on zone={} event={}", delta, zoneId, eventId);
        }
    }

    @Transactional
    public void setZonePrice(String token, UUID eventId, UUID zoneId,
            java.math.BigDecimal newPrice) {
        if (eventId == null) {
            log.warn("Invalid eventId: {}", eventId);
            throw new IllegalArgumentException("eventId is required");
        }
        UUID memberId = authenticateMember(token);
        rejectIfSuspended(memberId);
        synchronized (lockFor(eventId)) {
            Event event = loadEventForInventoryEdit(memberId, eventId);
            event.findZone(zoneId).setPricePerTicket(newPrice);
            saveEvent(event);
            log.info("Inventory: price={} on zone={} event={}", newPrice, zoneId, eventId);
        }
    }

    // ── Query (event map) ───────────────────────────────────────────

    public Optional<EventMapDTO> getEventMap(UUID eventId) {
        return getEventMap(eventId, false);
    }

    public Optional<EventMapDTO> getEventMapForManagement(UUID eventId) {
        return getEventMap(eventId, true);
    }

    private Optional<EventMapDTO> getEventMap(UUID eventId, boolean allowDraft) {
        if (eventId == null)
            return Optional.empty();
        Optional<Event> maybe = eventRepository.findById(eventId);
        if (maybe.isEmpty()) {
            log.info("Event map request denied: id={}, reason=unknown", eventId);
            return Optional.empty();
        }
        Event event = maybe.get();
        if (!allowDraft && !isBrowsable(event)) {
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
                zoneDtos,
                toLayoutInfo(event.getVenueLayout()),
                event.getDescription(),
                BuyerPolicyCatalog.purchaseRestrictions(event),
                BuyerPolicyCatalog.visibleDiscounts(event)));
    }

    // ── Visual hall layout (FIX-V2-25) ──────────────────────────────

    /**
     * Saves a visual grid layout for a DRAFT event ("save draft"). Validates that every
     * sellable cell points at a real zone/seat before persisting. DRAFT-only is enforced
     * by {@link Event#setVenueLayout}.
     */
    @Transactional
    public void setEventLayout(String token, UUID eventId, VenueLayout layout) {
        if (eventId == null) throw new IllegalArgumentException("eventId is required");
        if (layout == null) throw new IllegalArgumentException("layout is required");
        UUID memberId = authenticateMember(token);
        synchronized (lockFor(eventId)) {
            Event event = eventRepository.findById(eventId)
                    .orElseThrow(() -> new IllegalArgumentException("Event not found: " + eventId));
            Company company = loadActiveCompany(event.getCompanyName());
            StaffAppointment appt = loadAppointment(memberId, company.getName());
            authorizeMapDefinition(appt);
            validateLayoutReferences(event, layout);
            event.setVenueLayout(layout);
            saveEvent(event);
            log.info("Event layout saved: eventId={}, grid={}x{}, cells={}",
                    eventId, layout.getRows(), layout.getCols(), layout.getCells().size());
        }
    }

    /**
     * Validates the event's saved layout (used by the "Validate" action before publishing).
     * Throws {@link IllegalStateException} with the reason if the layout is missing, empty,
     * or references unknown zones/seats.
     */
    public void validateEventLayout(String token, UUID eventId) {
        if (eventId == null) throw new IllegalArgumentException("eventId is required");
        UUID memberId = authenticateMember(token);
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Event not found: " + eventId));
        Company company = loadActiveCompany(event.getCompanyName());
        StaffAppointment appt = loadAppointment(memberId, company.getName());
        authorizeMapDefinition(appt);
        VenueLayout layout = event.getVenueLayout();
        if (layout == null) {
            throw new IllegalStateException("No layout has been saved for this event");
        }
        if (!layout.hasSellableCell()) {
            throw new IllegalStateException("Layout must contain at least one seat or general-admission area");
        }
        validateLayoutReferences(event, layout);
    }

    private void validateLayoutReferences(Event event, VenueLayout layout) {
        Set<UUID> zoneIds = new HashSet<>();
        Set<UUID> seatIds = new HashSet<>();
        for (InventoryZone z : event.getZones()) {
            zoneIds.add(z.getId());
            if (z.isAssigned()) {
                for (Seat s : z.getSeats()) {
                    seatIds.add(s.getId());
                }
            }
        }
        for (LayoutCell cell : layout.getCells()) {
            if (cell.getZoneId() != null && !zoneIds.contains(cell.getZoneId())) {
                throw new IllegalArgumentException("Layout references unknown zone: " + cell.getZoneId());
            }
            if (cell.getSeatId() != null && !seatIds.contains(cell.getSeatId())) {
                throw new IllegalArgumentException("Layout references unknown seat: " + cell.getSeatId());
            }
        }
    }

    private void authorizeMapDefinition(StaffAppointment appt) {
        boolean allowed = appt.isOwner()
                || (appt.isManager() && appt.hasPermission(ManagerPermission.MAP_DEFINITION));
        if (!allowed) {
            throw new SecurityException("Map definition requires MAP_DEFINITION permission");
        }
    }

    private static EventMapDTO.LayoutInfo toLayoutInfo(VenueLayout layout) {
        if (layout == null) {
            return null;
        }
        List<EventMapDTO.CellInfo> cells = new ArrayList<>();
        for (LayoutCell c : layout.getCells()) {
            cells.add(new EventMapDTO.CellInfo(
                    c.getRow(), c.getCol(), c.getType(), c.getLabel(), c.getZoneId(), c.getSeatId()));
        }
        return new EventMapDTO.LayoutInfo(layout.getRows(), layout.getCols(), cells);
    }

    // ── Search (inlined from EventSearchDomainService) ──────────────

    public List<EventSummaryDTO> searchEvents(SearchEventsRequest req) {
        log.info("Event search requested: text={}, category={}, company={}",
                req == null ? null : req.text(), req == null ? null : req.category(), req == null ? null : req.companyName());
        SearchEventsRequest q = req == null ? SearchEventsRequest.empty() : req;
        Set<String> activeCompanyNames = activeCompanyNames();

        List<EventSummaryDTO> hits = eventRepository.findAll().stream()
                .filter(e -> isBrowsable(e))
                .filter(e -> activeCompanyNames.contains(e.getCompanyName()))
                .filter(e -> matchesText(e, q.text()))
                .filter(e -> matchesRegion(e, q.region()))
                .filter(e -> matchesCategory(e, q.category()))
                .filter(e -> matchesCompanyName(e, q.companyName()))
                .filter(e -> matchesPriceRange(e, q.minPrice(), q.maxPrice()))
                .filter(e -> matchesDateRange(e, q.fromDate(), q.toDate()))
                .map(EventSummaryDTO::from)
                .toList();

        log.info("Event search: text={}, category={}, company={}, hits={}",
                q.text(), q.category(), q.companyName(), hits.size());
        return hits;
    }

    public List<EventSummaryDTO> listCompanyEvents(String token, String companyName) {
        authenticateMember(token);
        if (companyName == null || companyName.isBlank()) {
            return List.of();
        }
        return eventRepository.findByCompanyName(companyName).stream()
                .map(EventSummaryDTO::from)
                .sorted(Comparator.comparing(EventSummaryDTO::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    // ── Private helpers ─────────────────────────────────────────────

    private Object lockFor(UUID eventId) {
        return eventLocks.computeIfAbsent(eventId, k -> new Object());
    }

    private Event loadEventForInventoryEdit(UUID memberId, UUID eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> {
                    log.warn("Event not found: eventId={}", eventId);
                    return new IllegalArgumentException("Event not found: " + eventId);
                });
        if (event.isCancelled()) {
            log.warn("Cannot edit inventory on a cancelled event: eventId={}", eventId);
            throw new IllegalStateException("Cannot edit inventory on a cancelled event");
        }
        if (event.getStatus() == EventStatus.SOLD_OUT) {
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

    private void authorizeEventCreation(StaffAppointment appointment) {
        boolean allowed = appointment.isOwner()
                || (appointment.isManager() && appointment.hasPermission(ManagerPermission.EVENT_LIFECYCLE));
        if (!allowed) {
            throw new SecurityException(
                    "Insufficient permissions: EVENT_LIFECYCLE required");
        }
    }

    private void authorizePolicy(StaffAppointment appt) {
        boolean allowed = appt.isOwner()
                || (appt.isManager() && appt.hasPermission(ManagerPermission.POLICY_MODIFICATION));
        if (!allowed) {
            throw new SecurityException("Insufficient permissions: POLICY_MODIFICATION required");
        }
    }

    private Company loadActiveCompany(String companyName) {
        Company company = companyRepository.findByName(companyName)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Company not found: " + companyName));
        if (!company.isActive()) {
            throw new IllegalStateException(
                    "Company is suspended or closed: " + companyName);
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

    private boolean hasActiveReservations(UUID eventId) {
        return !orderRepository.findActiveByEventId(eventId).isEmpty();
    }

    private InventoryZone buildZone(CreateEventRequest.ZoneSpec spec) {
        return switch (spec) {
            case CreateEventRequest.GAZoneSpec ga -> InventoryZone.createGA(
                    UUID.randomUUID(), ga.name(), ga.pricePerTicket(), ga.maxCapacity());
            case CreateEventRequest.AssignedZoneSpec a -> {
                InventoryZone zone = InventoryZone.createAssigned(
                        UUID.randomUUID(), a.name(), a.pricePerTicket());
                for (CreateEventRequest.SeatSpec seatSpec : a.seats()) {
                    zone.addSeat(new Seat(UUID.randomUUID(),
                            seatSpec.row(), seatSpec.seatNumber()));
                }
                yield zone;
            }
        };
    }

    private VenueMap buildVenueMap(Map<String, String> sectionToZoneName,
                                   Map<String, UUID> zoneIdsByName) {
        Map<String, UUID> sectionToZoneId = new HashMap<>(sectionToZoneName.size());
        for (Map.Entry<String, String> e : sectionToZoneName.entrySet()) {
            String zoneName = e.getValue();
            UUID zoneId = zoneIdsByName.get(zoneName);
            if (zoneId == null) {
                throw new IllegalArgumentException(
                        "Venue map section '" + e.getKey()
                                + "' references unknown zone: " + zoneName);
            }
            sectionToZoneId.put(e.getKey(), zoneId);
        }
        return new VenueMap(sectionToZoneId);
    }

    private void saveEvent(Event event) {
        try {
            eventRepository.save(event);
        } catch (OptimisticLockException ex) {
            log.warn("Event save conflict: eventId={}", event.getId());
            throw new IllegalStateException("Event changed concurrently. Please retry.", ex);
        }
    }

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

    private void rejectIfSuspended(UUID memberId) {
        memberRepository.findById(memberId)
                .ifPresent(member -> member.rejectIfSuspended(systemClock.now()));
    }

    // ── Search filter helpers ───────────────────────────────────────

    private Set<String> activeCompanyNames() {
        Set<String> names = new HashSet<>();
        for (Company c : companyRepository.getAll()) {
            if (c.isActive()) names.add(c.getName());
        }
        return names;
    }

    private static boolean matchesText(Event e, String text) {
        if (text == null || text.isBlank()) return true;
        String needle = text.toLowerCase();
        return contains(e.getName(), needle)
                || contains(e.getArtist(), needle)
                || contains(e.getDescription(), needle);
    }

    private static boolean contains(String haystack, String needleLower) {
        return haystack != null && haystack.toLowerCase().contains(needleLower);
    }

    private static boolean matchesRegion(Event e, String region) {
        if (region == null) return true;
        return region.equalsIgnoreCase(e.getRegion());
    }

    private static boolean matchesCategory(Event e, com.ticketing.domain.event.EventCategory category) {
        if (category == null) return true;
        return category == e.getCategory();
    }

    private static boolean matchesCompanyName(Event e, String companyName) {
        if (companyName == null) return true;
        return companyName.equals(e.getCompanyName());
    }

    private static boolean matchesPriceRange(Event e, BigDecimal min, BigDecimal max) {
        if (min == null && max == null) return true;
        for (InventoryZone z : e.getZones()) {
            BigDecimal p = z.getPricePerTicket();
            if (p == null) continue;
            if (min != null && p.compareTo(min) < 0) continue;
            if (max != null && p.compareTo(max) > 0) continue;
            return true;
        }
        return false;
    }

    private static boolean matchesDateRange(Event e, Instant from, Instant to) {
        if (from == null && to == null) return true;
        Instant start = e.getSchedule().getStartTime();
        if (from != null && start.isBefore(from)) return false;
        if (to != null && start.isAfter(to)) return false;
        return true;
    }
}
