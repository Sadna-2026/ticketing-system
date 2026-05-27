package com.ticketing.domain.services;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ticketing.application.CreateEventRequest;
import com.ticketing.application.EditEventRequest;
import com.ticketing.application.dto.EventDetailsDTO;
import com.ticketing.domain.company.Company;
import com.ticketing.domain.company.ICompanyRepository;
import com.ticketing.domain.event.Event;
import com.ticketing.domain.event.IEventRepository;
import com.ticketing.domain.event.InventoryZone;
import com.ticketing.domain.event.Seat;
import com.ticketing.domain.event.VenueMap;
import com.ticketing.domain.exception.OptimisticLockException;
import com.ticketing.domain.member.IMemberRepository;
import com.ticketing.domain.member.ManagerPermission;
import com.ticketing.domain.member.Member;
import com.ticketing.domain.member.StaffAppointment;
import com.ticketing.domain.order.IOrderRepository;

@org.springframework.stereotype.Service
public class EventDomainService {

    private static final Logger log = LoggerFactory.getLogger(EventDomainService.class);

    private final IEventRepository eventRepository;
    private final ICompanyRepository companyRepository;
    private final IMemberRepository memberRepository;
    private final IOrderRepository orderRepository;

    // Per-event lock so inventory edits on Event A don't block Event B.
    private final ConcurrentHashMap<UUID, Object> eventLocks = new ConcurrentHashMap<>();

    public EventDomainService(IEventRepository eventRepository,
                                       ICompanyRepository companyRepository,
                                       IMemberRepository memberRepository,
                                       IOrderRepository orderRepository) {
        this.eventRepository = eventRepository;
        this.companyRepository = companyRepository;
        this.memberRepository = memberRepository;
        this.orderRepository = orderRepository;
    }

    private Object lockFor(UUID eventId) {
        return eventLocks.computeIfAbsent(eventId, k -> new Object());
    }

    public Event createEvent(UUID memberId, CreateEventRequest request) {
        Company company = loadActiveCompany(request.companyName());
        StaffAppointment appointment = loadAppointment(memberId, company.getName());
        authorize(appointment);

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
                new com.ticketing.domain.event.AlwaysAllowPolicy(),
                new com.ticketing.domain.event.NoDiscountPolicy(),
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
        return event;
    }

    public void cancelEvent(UUID memberId, UUID eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> {
                    log.warn("Event not found: eventId={}", eventId);
                    return new IllegalArgumentException("No event with id " + eventId);
                });

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
    }

    public EventDetailsDTO editEvent(UUID memberId, EditEventRequest request) {
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

    public void addSeatsToZone(UUID memberId, UUID eventId, UUID zoneId,
                               java.util.List<CreateEventRequest.SeatSpec> seats) {
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

    public void removeSeats(UUID memberId, UUID eventId, UUID zoneId,
                            java.util.List<UUID> seatIds) {
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

    public void increaseGACapacity(UUID memberId, UUID eventId, UUID zoneId, int delta) {
        synchronized (lockFor(eventId)) {
            Event event = loadEventForInventoryEdit(memberId, eventId);
            event.findZone(zoneId).increaseCapacity(delta);
            saveEvent(event);
            log.info("Inventory: GA capacity +{} on zone={} event={}", delta, zoneId, eventId);
        }
    }

    public void decreaseGACapacity(UUID memberId, UUID eventId, UUID zoneId, int delta) {
        synchronized (lockFor(eventId)) {
            Event event = loadEventForInventoryEdit(memberId, eventId);
            event.findZone(zoneId).decreaseCapacity(delta);
            saveEvent(event);
            log.info("Inventory: GA capacity -{} on zone={} event={}", delta, zoneId, eventId);
        }
    }

    public void setZonePrice(UUID memberId, UUID eventId, UUID zoneId,
                             java.math.BigDecimal newPrice) {
        synchronized (lockFor(eventId)) {
            Event event = loadEventForInventoryEdit(memberId, eventId);
            event.findZone(zoneId).setPricePerTicket(newPrice);
            saveEvent(event);
            log.info("Inventory: price={} on zone={} event={}", newPrice, zoneId, eventId);
        }
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

    public void publishEvent(UUID memberId, UUID eventId) {
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

    public void setEventPurchasePolicy(UUID memberId, UUID eventId, com.ticketing.domain.event.IPurchasePolicy policy) {
        Event event = eventRepository.findById(eventId).orElseThrow(() -> new IllegalArgumentException("Event not found: " + eventId));
        Company company = loadActiveCompany(event.getCompanyName());
        StaffAppointment appt = loadAppointment(memberId, company.getName());
        authorizePolicy(appt);
        event.setPurchasePolicy(policy);
        saveEvent(event);
    }

    public void removeEventPurchasePolicy(UUID memberId, UUID eventId) {
        Event event = eventRepository.findById(eventId).orElseThrow(() -> new IllegalArgumentException("Event not found: " + eventId));
        Company company = loadActiveCompany(event.getCompanyName());
        StaffAppointment appt = loadAppointment(memberId, company.getName());
        authorizePolicy(appt);
        event.setPurchasePolicy(new com.ticketing.domain.event.AlwaysAllowPolicy());
        saveEvent(event);
    }

    public void setEventDiscountPolicy(UUID memberId, UUID eventId, com.ticketing.domain.event.IDiscountPolicy policy) {
        Event event = eventRepository.findById(eventId).orElseThrow(() -> new IllegalArgumentException("Event not found: " + eventId));
        Company company = loadActiveCompany(event.getCompanyName());
        StaffAppointment appt = loadAppointment(memberId, company.getName());
        authorizePolicy(appt);
        event.setDiscountPolicy(policy);
        saveEvent(event);
    }

    public void removeEventDiscountPolicy(UUID memberId, UUID eventId) {
        Event event = eventRepository.findById(eventId).orElseThrow(() -> new IllegalArgumentException("Event not found: " + eventId));
        Company company = loadActiveCompany(event.getCompanyName());
        StaffAppointment appt = loadAppointment(memberId, company.getName());
        authorizePolicy(appt);
        event.setDiscountPolicy(new com.ticketing.domain.event.NoDiscountPolicy());
        saveEvent(event);
    }

    public com.ticketing.domain.event.IPurchasePolicy getEventPurchasePolicy(UUID eventId) {
        return eventRepository.findById(eventId).orElseThrow(() -> new IllegalArgumentException("Event not found: " + eventId)).getEventPurchasePolicy();
    }

    public com.ticketing.domain.event.IDiscountPolicy getEventDiscountPolicy(UUID eventId) {
        return eventRepository.findById(eventId).orElseThrow(() -> new IllegalArgumentException("Event not found: " + eventId)).getEventDiscountPolicy();
    }

    private void authorizePolicy(StaffAppointment appt) {
        boolean allowed = appt.isOwner()
                || (appt.isManager() && appt.hasPermission(ManagerPermission.POLICY_MODIFICATION));
        if (!allowed) {
            throw new SecurityException("Insufficient permissions: POLICY_MODIFICATION required");
        }
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
}
