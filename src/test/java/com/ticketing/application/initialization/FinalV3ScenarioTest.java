package com.ticketing.application.initialization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ticketing.application.ISystemClock;
import com.ticketing.application.auth.ISessionTokenRepository;
import com.ticketing.application.auth.SessionTokenService;
import com.ticketing.application.services.CompanyService;
import com.ticketing.application.services.EventService;
import com.ticketing.application.services.INotificationService;
import com.ticketing.application.services.MemberService;
import com.ticketing.domain.company.Company;
import com.ticketing.domain.event.Event;
import com.ticketing.domain.event.EventStatus;
import com.ticketing.domain.event.InventoryZone;
import com.ticketing.domain.event.LayoutCell;
import com.ticketing.domain.event.LayoutCellType;
import com.ticketing.domain.event.VenueLayout;
import com.ticketing.domain.member.Member;
import com.ticketing.infrastructure.InMemoryCompanyRepository;
import com.ticketing.infrastructure.InMemoryEventPublisher;
import com.ticketing.infrastructure.InMemoryEventRepository;
import com.ticketing.infrastructure.InMemoryMemberRepository;
import com.ticketing.infrastructure.InMemoryOrderRepository;
import com.ticketing.infrastructure.InMemorySessionTokenRepository;
import com.ticketing.infrastructure.PasswordEncryptionUtils;

/**
 * Acceptance tests for the FINAL V3 checking scenario (#561 — V3-INIT-STATE-FINAL).
 *
 * <p>Verifies the state produced by
 * {@code classpath:initial-state/final-v3-scenario.txt}: Admin exists from platform
 * initialization; User1..User4 are registered; User3 is over 18; User1 can login and
 * open company C1; events E1 and E2 each have 10 standing tickets and 10 assigned seats;
 * every E2 ticket costs 10 dollars; and no regular user is left logged in.
 */
@DisplayName("Final V3 checking scenario (V3-INIT-STATE-FINAL)")
class FinalV3ScenarioTest {

    private static final String SCENARIO_FILE = "classpath:initial-state/final-v3-scenario.txt";
    /** Fixed clock so User3's age is deterministic regardless of the real date. */
    private static final LocalDate CLOCK_DATE = LocalDate.of(2030, 6, 1);

    private InMemoryMemberRepository memberRepository;
    private InMemoryCompanyRepository companyRepository;
    private InMemoryEventRepository eventRepository;
    private CompanyService companyService;
    private InitialStateParser parser;
    private InitialStateExecutor executor;

    @BeforeEach
    void setUp() {
        String secret = Base64.getEncoder().encodeToString(
                "01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8));
        ISessionTokenRepository tokenRepository = new InMemorySessionTokenRepository();

        memberRepository = new InMemoryMemberRepository();
        companyRepository = new InMemoryCompanyRepository();
        eventRepository = new InMemoryEventRepository();
        InMemoryEventPublisher eventPublisher = new InMemoryEventPublisher();
        INotificationService notificationService = mock(INotificationService.class);
        SessionTokenService sessionTokenService = new SessionTokenService(secret, 120, tokenRepository);
        PasswordEncryptionUtils passwordEncryptionUtils = new PasswordEncryptionUtils();
        ISystemClock systemClock = () -> Instant.parse("2030-06-01T12:00:00Z");

        // Platform initialization is what registers the system administrator (Admin) and
        // wires the CompanyService the executor uses.
        InitializationService initializationService = new InitializationService(
                companyRepository, memberRepository, eventPublisher, sessionTokenService,
                notificationService);
        companyService = initializationService.initialize();

        MemberService memberService = new MemberService(
                memberRepository, passwordEncryptionUtils, sessionTokenService);

        EventService eventService = new EventService(
                eventRepository, companyRepository, memberRepository,
                new InMemoryOrderRepository(), sessionTokenService, null, systemClock, null);

        parser = new InitialStateParser();
        executor = new InitialStateExecutor(
                memberService, companyService, eventService, sessionTokenService,
                memberRepository, eventRepository, systemClock);
    }

    private void runFinalScenario() {
        executor.execute(parser.parse(InitialStateFileLoader.load(SCENARIO_FILE), "final-v3-scenario.txt"));
    }

    private Event event(String name) {
        return eventRepository.findByCompanyName("C1").stream()
                .filter(e -> name.equals(e.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("event " + name + " not found under C1"));
    }

    private InventoryZone zone(Event event, String zoneName) {
        return event.getZones().stream()
                .filter(z -> zoneName.equals(z.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("zone " + zoneName + " not found on " + event.getName()));
    }

    // NOTE: the "Admin exists as a registered system administrator after initialization"
    // acceptance criterion is covered by
    // com.ticketing.infrastructure.init.PlatformInitializationServiceTest
    // (GivenValidConfig_WhenInitialize_ThenPlatformActiveAndAdminExists). The Admin is a
    // separate aggregate in IAdminRepository, created by PlatformInitializationService before
    // this scenario's operational ops run — it is the precondition for this file.

    @Test
    @DisplayName("User1..User4 are registered and User3 is over 18")
    void givenFinalScenario_thenUsersRegisteredAndUser3IsAdult() {
        runFinalScenario();

        for (String user : List.of("u1", "u2", "u3", "u4")) {
            assertNotNull(memberRepository.findByUsername(user).orElse(null), user + " should be registered");
        }

        Member u3 = memberRepository.findByUsername("u3").orElseThrow();
        LocalDate dob = u3.getDateOfBirth();
        assertNotNull(dob, "User3 must have a date of birth to be checked as over 18");
        int age = java.time.Period.between(dob, CLOCK_DATE).getYears();
        assertTrue(age > 18, "User3 must be over 18, but age was " + age);
    }

    @Test
    @DisplayName("User1 can login during the scenario and opens company C1")
    void givenFinalScenario_thenUser1LoginSucceedsAndC1Exists() {
        // The scenario logs User1 in and then opens C1 with the login-bound token; if login
        // failed, execution would abort before C1 is created.
        runFinalScenario();

        Company c1 = companyRepository.findByName("C1").orElse(null);
        assertNotNull(c1, "company C1 should exist after the scenario");

        Member u1 = memberRepository.findByUsername("u1").orElseThrow();
        assertTrue(u1.getStaffAppointment("C1").isOwner(), "User1 should own the company it opened");
    }

    @Test
    @DisplayName("E1 exists under C1 with 10 standing tickets and 10 assigned seats")
    void givenFinalScenario_thenE1HasStandingAndSeatedInventory() {
        runFinalScenario();

        Event e1 = event("E1");
        assertEquals(10, zone(e1, "Standing").getMaxCapacity(), "E1 should have 10 standing tickets");
        assertEquals(10, zone(e1, "Seating").getSeats().size(), "E1 should have 10 assigned seats");
        assertEquals(EventStatus.PUBLISHED, e1.getStatus(), "E1 should be published (visible in listings)");
    }

    @Test
    @DisplayName("E2 exists under C1 with 10 standing tickets, 10 assigned seats, all priced 10 dollars")
    void givenFinalScenario_thenE2HasInventoryAllPricedTen() {
        runFinalScenario();

        Event e2 = event("E2");
        InventoryZone standing = zone(e2, "Standing");
        InventoryZone seating = zone(e2, "Seating");

        assertEquals(10, standing.getMaxCapacity(), "E2 should have 10 standing tickets");
        assertEquals(10, seating.getSeats().size(), "E2 should have 10 assigned seats");
        assertEquals(EventStatus.PUBLISHED, e2.getStatus(), "E2 should be published (visible in listings)");

        // Every E2 ticket costs exactly 10 dollars (price is per zone; both zones = 10).
        assertEquals(0, new BigDecimal("10").compareTo(standing.getPricePerTicket()),
                "every E2 standing ticket must cost 10 dollars");
        assertEquals(0, new BigDecimal("10").compareTo(seating.getPricePerTicket()),
                "every E2 seated ticket must cost 10 dollars");
    }

    @Test
    @DisplayName("Both events expose a general-admission cell so GA tickets are purchasable on the map")
    void givenFinalScenario_thenEventsHaveGaLayoutCell() {
        runFinalScenario();

        for (String name : List.of("E1", "E2")) {
            Event e = event(name);
            VenueLayout layout = e.getVenueLayout();
            assertNotNull(layout, name + " should have a venue layout");
            List<LayoutCell> gaCells = layout.cellsOfType(LayoutCellType.GENERAL_ADMISSION);
            assertEquals(1, gaCells.size(), name + " should have exactly one GA (Standing) cell on the map");
            assertEquals(zone(e, "Standing").getId(), gaCells.get(0).getZoneId(),
                    name + "'s GA cell must reference the Standing zone");
        }
    }

    @Test
    @DisplayName("No regular user is left logged in at the end (User1's token is revoked)")
    void givenFinalScenario_whenComplete_thenNoRegularUserLoggedIn() {
        runFinalScenario();

        // After logout, the bound token symbols are cleared, so a follow-up command that
        // reuses u1_token fails with an unbound-token error.
        InitialStateExecutionException ex = assertThrows(InitialStateExecutionException.class,
                () -> executor.execute(parser.parse("open-production-company(u1_token, C2);", "test.txt")));
        assertTrue(ex.getMessage().contains("u1_token"), ex.getMessage());

        // The scenario file logs out every registered user.
        String file = InitialStateFileLoader.load(SCENARIO_FILE);
        for (String user : List.of("u1", "u2", "u3", "u4")) {
            assertTrue(file.contains("logout(" + user + "_token)"),
                    "final scenario must logout " + user);
        }
    }

    @Test
    @DisplayName("The runner can be configured to use the final scenario file (loads and replays cleanly)")
    void givenFinalScenarioConfigured_whenLoaded_thenReplaysWithoutError() {
        // Proves the configured file path resolves and the full scenario applies end-to-end,
        // producing exactly the requested state (both events present under C1).
        runFinalScenario();

        assertNotNull(event("E1"));
        assertNotNull(event("E2"));
        assertEquals(2, eventRepository.findByCompanyName("C1").size(),
                "exactly E1 and E2 should exist under C1");
    }

    @Test
    @DisplayName("Invalid scenario (broken command) fails with a clear, located error")
    void givenBrokenFinalScenario_whenExecute_thenFailsNamingCommand() {
        String broken = InitialStateFileLoader.load(SCENARIO_FILE)
                .replace("open-production-company", "open-invalid-company");

        InitialStateExecutionException ex = assertThrows(InitialStateExecutionException.class,
                () -> executor.execute(parser.parse(broken, "final-v3-scenario.txt")));

        assertTrue(ex.getMessage().contains("open-invalid-company"), ex.getMessage());
    }
}
