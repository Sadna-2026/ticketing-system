package com.ticketing.application;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ticketing.application.auth.ISessionTokenService;
import com.ticketing.domain.company.Company;
import com.ticketing.domain.company.ICompanyRepository;
import com.ticketing.domain.event.Event;
import com.ticketing.domain.event.IEventRepository;
import com.ticketing.domain.event.InventoryZone;
import com.ticketing.domain.event.Seat;
import com.ticketing.domain.event.VenueMap;
import com.ticketing.domain.member.IMemberRepository;
import com.ticketing.domain.member.ManagerPermission;
import com.ticketing.domain.member.Member;
import com.ticketing.domain.member.StaffAppointment;
import com.ticketing.infrastructure.Interface.IActiveOrderRepository;

public class EventService {

    private static final Logger log = LoggerFactory.getLogger(EventService.class);

    private final IEventRepository eventRepository;
    private final ICompanyRepository companyRepository;
    private final IMemberRepository memberRepository;
    private final IActiveOrderRepository activeOrderRepository;
    private final ISessionTokenService sessionTokenService;

    public EventService(IEventRepository eventRepository,
                        ICompanyRepository companyRepository,
                        IMemberRepository memberRepository,
                        IActiveOrderRepository activeOrderRepository,
                        ISessionTokenService sessionTokenService) {
        this.eventRepository = eventRepository;
        this.companyRepository = companyRepository;
        this.memberRepository = memberRepository;
        this.activeOrderRepository = activeOrderRepository;
        this.sessionTokenService = sessionTokenService;
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

        Event event = new Event(
                UUID.randomUUID(),
                company.getName(),
                request.name(),
                request.description(),
                request.category(),
                request.schedule(),
                request.lockTimerDuration());

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

        eventRepository.save(event);
        log.info("Event created: eventId={}, companyName={}, status=DRAFT",
                event.getId(), company.getName());
        return event.getId();
    }

    public void cancelEvent(String token, UUID eventId) {
        if (eventId == null) {
            throw new IllegalArgumentException("eventId is required");
        }
        UUID memberId = authenticateMember(token);

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("No event with id " + eventId));

        // company is looked up but we don't require it to be active — cancellation
        // can happen as cleanup even on a suspended company.
        Company company = companyRepository.findByName(event.getCompanyName())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Company not found: " + event.getCompanyName()));

        StaffAppointment appt = loadAppointment(memberId, company.getName());
        if (!appt.hasPermission(ManagerPermission.EVENT_LIFECYCLE)) {
            throw new SecurityException("Insufficient permissions to cancel events");
        }

        log.info("Cancelling event: eventId={}, companyName={}", eventId, company.getName());
        event.cancel();
        eventRepository.save(event);
        // TODO: refund completed purchases once that pipeline lands
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
                .orElseThrow(() -> new IllegalArgumentException("Event not found: " + request.eventId()));

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

        eventRepository.save(event);
        log.info("Event edited: eventId={}, companyName={}, by={}",
                event.getId(), company.getName(), memberId);
        return EventDetailsDTO.from(event);
    }

    private boolean hasActiveReservations(UUID eventId) {
        return !activeOrderRepository.findActiveByEventId(eventId).isEmpty();
    }

    private UUID authenticateMember(String token) {
        if (token == null || token.isBlank()) {
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

}
