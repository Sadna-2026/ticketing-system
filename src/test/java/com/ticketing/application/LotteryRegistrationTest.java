package com.ticketing.application;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ticketing.application.auth.ISessionTokenService;
import com.ticketing.application.dto.LotteryRegistrationRequest;
import com.ticketing.application.dto.LotteryRegistrationResponse;
import com.ticketing.application.services.EventService;
import com.ticketing.domain.company.Company;
import com.ticketing.domain.event.AlwaysAllowPolicy;
import com.ticketing.domain.event.Event;
import com.ticketing.domain.event.EventCategory;
import com.ticketing.domain.event.EventSchedule;
import com.ticketing.domain.event.LockTimerDuration;
import com.ticketing.domain.event.LotteryWindow;
import com.ticketing.domain.event.NoDiscountPolicy;
import com.ticketing.domain.event.SaleMethod;
import com.ticketing.domain.member.Member;
import com.ticketing.domain.member.StaffAppointment;
import com.ticketing.domain.order.IOrderRepository;
import com.ticketing.infrastructure.InMemoryCompanyRepository;
import com.ticketing.infrastructure.InMemoryEventRepository;
import com.ticketing.infrastructure.InMemoryLotteryRepository;
import com.ticketing.infrastructure.InMemoryMemberRepository;

public class LotteryRegistrationTest {

    private static final String VALID_TOKEN = "valid-token";
    private static final String COMPANY_NAME = "Lottery Corp";

    private InMemoryEventRepository eventRepository;
    private InMemoryMemberRepository memberRepository;
    private InMemoryCompanyRepository companyRepository;
    private InMemoryLotteryRepository lotteryRepository;
    private ISessionTokenService sessionTokenService;
    private EventService eventService;

    private UUID memberId;
    private UUID eventId;
    private UUID zoneId;

    /** "now" for all tests — sits inside the lottery window */
    private static final Instant NOW = Instant.parse("2026-07-01T12:00:00Z");
    private static final Instant LOTTERY_OPEN  = NOW.minus(1, ChronoUnit.DAYS);
    private static final Instant LOTTERY_CLOSE = NOW.plus(1, ChronoUnit.DAYS);

    @BeforeEach
    void setUp() {
        eventRepository   = new InMemoryEventRepository();
        memberRepository  = new InMemoryMemberRepository();
        companyRepository = new InMemoryCompanyRepository();
        lotteryRepository = new InMemoryLotteryRepository();
        sessionTokenService = mock(ISessionTokenService.class);

        Clock fixedClock = Clock.fixed(NOW, ZoneOffset.UTC);

        eventService = new EventService(
                eventRepository, companyRepository, memberRepository,
                mock(IOrderRepository.class), sessionTokenService,
                lotteryRepository, fixedClock);

        // --- member ---
        memberId = UUID.randomUUID();
        Member member = new Member(memberId, "alice", "alice@example.com", "encryptedPw");
        memberRepository.save(member);

        // --- company ---
        Company company = new Company(COMPANY_NAME, "desc", memberId);
        companyRepository.save(company);

        // --- lottery event (PUBLISHED) ---
        eventId = UUID.randomUUID();
        Event event = new Event(
                eventId, COMPANY_NAME, "Big Lottery Show", "desc",
                EventCategory.CONCERT, defaultSchedule(), defaultLockTimer(),
                new AlwaysAllowPolicy(), new NoDiscountPolicy(),
                SaleMethod.LOTTERY, new LotteryWindow(LOTTERY_OPEN, LOTTERY_CLOSE));

        zoneId = UUID.randomUUID();
        event.addZone(com.ticketing.domain.event.InventoryZone.createGA(
                zoneId, "Floor", new BigDecimal("50.00"), 500));
        event.setVenueMap(new com.ticketing.domain.event.VenueMap(Map.of("Section A", zoneId)));
        event.publish();
        eventRepository.save(event);

        // --- token ---
        when(sessionTokenService.isValid(VALID_TOKEN)).thenReturn(true);
        when(sessionTokenService.extractMemberId(VALID_TOKEN)).thenReturn(memberId);
    }

    // ============================
    // Acceptance Tests
    // ============================

    @Test
    @DisplayName("SuccessfulLotteryRegistration — member registers for lottery and entry is persisted")
    void GivenLotteryEventWithOpenWindow_WhenRegisterForLottery_ThenEntryCreated() {
        LotteryRegistrationRequest request = new LotteryRegistrationRequest(eventId, zoneId, 2);

        LotteryRegistrationResponse response = eventService.registerForLottery(VALID_TOKEN, request);

        assertTrue(response.success());
        assertNotNull(response.lotteryEntryId());
        assertNotNull(response.registeredAt());
        // verify persisted
        assertTrue(lotteryRepository.findByEventAndMember(eventId, memberId).isPresent());
    }

    @Test
    @DisplayName("LotteryNotSupported — event uses REGULAR sale method, registration denied")
    void GivenRegularSaleEvent_WhenRegisterForLottery_ThenDenied() {
        // create a REGULAR event
        UUID regularEventId = UUID.randomUUID();
        UUID regularZoneId = UUID.randomUUID();
        Event regularEvent = new Event(
                regularEventId, COMPANY_NAME, "Regular Show", "desc",
                EventCategory.CONCERT, defaultSchedule(), defaultLockTimer(),
                new AlwaysAllowPolicy(), new NoDiscountPolicy(),
                SaleMethod.REGULAR, null);
        regularEvent.addZone(com.ticketing.domain.event.InventoryZone.createGA(
                regularZoneId, "Floor", new BigDecimal("50.00"), 500));
        regularEvent.setVenueMap(new com.ticketing.domain.event.VenueMap(Map.of("Section A", regularZoneId)));
        regularEvent.publish();
        eventRepository.save(regularEvent);

        LotteryRegistrationRequest request = new LotteryRegistrationRequest(regularEventId, regularZoneId, 1);

        LotteryRegistrationResponse response = eventService.registerForLottery(VALID_TOKEN, request);

        assertFalse(response.success());
        assertTrue(response.message().contains("does not support lottery"));
        assertNull(response.lotteryEntryId());
        // verify nothing persisted
        assertTrue(lotteryRepository.findByEventId(regularEventId).isEmpty());
    }

    @Test
    @DisplayName("DuplicateLotteryRegistration — member already registered, second attempt denied")
    void GivenMemberAlreadyRegistered_WhenRegisterForLottery_ThenDenied() {
        LotteryRegistrationRequest request = new LotteryRegistrationRequest(eventId, zoneId, 2);

        // first registration succeeds
        LotteryRegistrationResponse first = eventService.registerForLottery(VALID_TOKEN, request);
        assertTrue(first.success());

        // second registration denied
        LotteryRegistrationResponse second = eventService.registerForLottery(VALID_TOKEN, request);

        assertFalse(second.success());
        assertTrue(second.message().contains("already registered"));
        // only one entry in the repo
        assertEquals(1, lotteryRepository.findByEventId(eventId).size());
    }

    @Test
    @DisplayName("ClosedLotteryRegistration — registration window has closed, registration denied")
    void GivenClosedRegistrationWindow_WhenRegisterForLottery_ThenDenied() {
        // create an event whose lottery window is in the past
        UUID closedEventId = UUID.randomUUID();
        UUID closedZoneId = UUID.randomUUID();
        Instant pastOpen  = NOW.minus(10, ChronoUnit.DAYS);
        Instant pastClose = NOW.minus(5, ChronoUnit.DAYS);

        Event closedEvent = new Event(
                closedEventId, COMPANY_NAME, "Closed Lottery Show", "desc",
                EventCategory.CONCERT, defaultSchedule(), defaultLockTimer(),
                new AlwaysAllowPolicy(), new NoDiscountPolicy(),
                SaleMethod.LOTTERY, new LotteryWindow(pastOpen, pastClose));
        closedEvent.addZone(com.ticketing.domain.event.InventoryZone.createGA(
                closedZoneId, "Floor", new BigDecimal("50.00"), 500));
        closedEvent.setVenueMap(new com.ticketing.domain.event.VenueMap(Map.of("Section A", closedZoneId)));
        closedEvent.publish();
        eventRepository.save(closedEvent);

        LotteryRegistrationRequest request = new LotteryRegistrationRequest(closedEventId, closedZoneId, 1);

        LotteryRegistrationResponse response = eventService.registerForLottery(VALID_TOKEN, request);

        assertFalse(response.success());
        assertTrue(response.message().contains("closed"));
        assertTrue(lotteryRepository.findByEventId(closedEventId).isEmpty());
    }

    // --- helpers ---

    private static EventSchedule defaultSchedule() {
        Instant start = NOW.plus(30, ChronoUnit.DAYS);
        Instant end = start.plus(3, ChronoUnit.HOURS);
        Instant doors = start.minus(1, ChronoUnit.HOURS);
        return new EventSchedule(start, end, doors);
    }

    private static LockTimerDuration defaultLockTimer() {
        return new LockTimerDuration(Duration.ofMinutes(15));
    }
}
