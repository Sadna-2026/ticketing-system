package com.ticketing.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ticketing.application.auth.ISessionTokenService;
import com.ticketing.application.dto.EventMapDTO;
import com.ticketing.application.services.EventService;
import com.ticketing.domain.company.Company;
import com.ticketing.domain.event.EventCategory;
import com.ticketing.domain.event.EventSchedule;
import com.ticketing.domain.event.LayoutCellType;
import com.ticketing.domain.event.LockTimerDuration;
import com.ticketing.domain.event.ZoneType;
import com.ticketing.domain.member.Member;
import com.ticketing.domain.member.StaffAppointment;
import com.ticketing.domain.order.IOrderRepository;
import com.ticketing.infrastructure.InMemoryCompanyRepository;
import com.ticketing.infrastructure.InMemoryEventRepository;
import com.ticketing.infrastructure.InMemoryLotteryRepository;
import com.ticketing.infrastructure.InMemoryMemberRepository;

@DisplayName("EventService.defineVenue (layout drives inventory)")
class DefineVenueServiceTest {

    private static final String COMPANY = "Hall Corp";
    private static final String OWNER_TOKEN = "owner-token";
    private static final Instant NOW = Instant.parse("2026-07-01T12:00:00Z");

    private InMemoryEventRepository eventRepository;
    private EventService eventService;

    @BeforeEach
    void setUp() {
        eventRepository = new InMemoryEventRepository();
        InMemoryCompanyRepository companyRepository = new InMemoryCompanyRepository();
        InMemoryMemberRepository memberRepository = new InMemoryMemberRepository();
        ISessionTokenService sessionTokenService = mock(ISessionTokenService.class);
        eventService = new EventService(eventRepository, companyRepository, memberRepository,
                mock(IOrderRepository.class), sessionTokenService, new InMemoryLotteryRepository(), () -> NOW);

        UUID ownerId = UUID.randomUUID();
        Member owner = new Member(ownerId, "owner", "owner@test.com", "pw");
        owner.addStaffAppointment(COMPANY,
                new StaffAppointment(COMPANY, null, StaffAppointment.StaffRole.OWNER, Set.of()));
        memberRepository.save(owner);
        companyRepository.save(new Company(COMPANY, "desc", ownerId));
        when(sessionTokenService.isValid(OWNER_TOKEN)).thenReturn(true);
        when(sessionTokenService.extractMemberId(OWNER_TOKEN)).thenReturn(ownerId);
    }

    @Test
    void GivenPaintedGrid_WhenDefineVenue_ThenInventoryAndLinkedLayoutAreCreated() {
        UUID eventId = eventService.defineVenue(OWNER_TOKEN, createRequest());

        EventMapDTO map = eventService.getEventMapForManagement(eventId).orElseThrow();
        assertEquals(2, map.zones().size());
        EventMapDTO.ZoneInfo seating = zone(map, "VIP");
        assertEquals(1, seating.seats().size());
        UUID seatId = seating.seats().get(0).id();

        // The visual layout's sellable cells point at the real inventory we just built.
        EventMapDTO.CellInfo seatCell = map.layout().cells().stream()
                .filter(c -> c.type() == LayoutCellType.SEAT).findFirst().orElseThrow();
        assertEquals(seating.id(), seatCell.zoneId());
        assertEquals(seatId, seatCell.seatId());

        EventMapDTO.CellInfo gaCell = map.layout().cells().stream()
                .filter(c -> c.type() == LayoutCellType.GENERAL_ADMISSION).findFirst().orElseThrow();
        assertEquals(zone(map, "Floor").id(), gaCell.zoneId());
    }

    @Test
    @DisplayName("Redefining a DRAFT hall replaces the buyable inventory (fixes stale-old-layout bug)")
    void GivenDraftEvent_WhenRedefineVenue_ThenBuyableInventoryIsReplaced() {
        UUID eventId = eventService.defineVenue(OWNER_TOKEN, createRequest());

        // Completely change the layout: drop VIP seating + Floor GA, add a single new GA zone.
        DefineVenueRequest redefine = new DefineVenueRequest(
                eventId, COMPANY, null, null, null, null, null, 1, 1,
                List.of(new CreateEventRequest.GAZoneSpec("Balcony", new BigDecimal("80.00"), 50)),
                Map.of("Balcony", "Balcony"),
                List.of(new DefineVenueRequest.CellSpec(0, 0, LayoutCellType.GENERAL_ADMISSION,
                        "Balcony", "Balcony", null, null)));
        eventService.defineVenue(OWNER_TOKEN, redefine);

        EventMapDTO map = eventService.getEventMapForManagement(eventId).orElseThrow();
        assertEquals(1, map.zones().size(), "old zones should be gone");
        EventMapDTO.ZoneInfo balcony = zone(map, "Balcony");
        assertEquals(ZoneType.GENERAL_ADMISSION, balcony.type());
        assertEquals(50, balcony.maxCapacity());
        assertTrue(map.zones().stream().noneMatch(z -> z.name().equals("VIP")), "stale seating zone removed");
        assertNotNull(map.layout());
        assertEquals(1, map.layout().cells().size());
    }

    private DefineVenueRequest createRequest() {
        return new DefineVenueRequest(
                null, COMPANY, "Big Show", "desc", EventCategory.CONCERT,
                schedule(), new LockTimerDuration(Duration.ofMinutes(15)), 1, 2,
                List.of(
                        new CreateEventRequest.GAZoneSpec("Floor", new BigDecimal("50.00"), 100),
                        new CreateEventRequest.AssignedZoneSpec("VIP", new BigDecimal("150.00"),
                                List.of(new CreateEventRequest.SeatSpec("A", "2")))),
                Map.of("Floor", "Floor", "VIP", "VIP"),
                List.of(
                        new DefineVenueRequest.CellSpec(0, 0, LayoutCellType.GENERAL_ADMISSION, "Floor", "Floor", null, null),
                        new DefineVenueRequest.CellSpec(0, 1, LayoutCellType.SEAT, null, "VIP", "A", "2")));
    }

    private static EventMapDTO.ZoneInfo zone(EventMapDTO map, String name) {
        return map.zones().stream().filter(z -> z.name().equals(name)).findFirst().orElseThrow();
    }

    private static EventSchedule schedule() {
        Instant start = NOW.plus(30, ChronoUnit.DAYS);
        return new EventSchedule(start, start.plus(3, ChronoUnit.HOURS), start.minus(1, ChronoUnit.HOURS));
    }
}
