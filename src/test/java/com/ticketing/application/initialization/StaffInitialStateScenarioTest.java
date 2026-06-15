package com.ticketing.application.initialization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Set;

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
import com.ticketing.domain.event.CouponDiscount;
import com.ticketing.domain.event.Event;
import com.ticketing.domain.event.InventoryZone;
import com.ticketing.domain.event.VenueLayout;
import com.ticketing.domain.member.ManagerPermission;
import com.ticketing.domain.member.Member;
import com.ticketing.domain.member.StaffAppointment;
import com.ticketing.infrastructure.InMemoryCompanyRepository;
import com.ticketing.infrastructure.InMemoryEventPublisher;
import com.ticketing.infrastructure.InMemoryEventRepository;
import com.ticketing.infrastructure.InMemoryMemberRepository;
import com.ticketing.infrastructure.InMemoryOrderRepository;
import com.ticketing.infrastructure.InMemorySessionTokenRepository;
import com.ticketing.infrastructure.PasswordEncryptionUtils;

/**
 * Acceptance tests for the V3-INIT-STATE staff demo scenario (#415).
 */
@DisplayName("Staff initial-state scenario (V3-INIT-STATE)")
class StaffInitialStateScenarioTest {

    private InMemoryMemberRepository memberRepository;
    private InMemoryCompanyRepository companyRepository;
    private InMemoryEventRepository eventRepository;
    private SessionTokenService sessionTokenService;
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
        sessionTokenService = new SessionTokenService(secret, 120, tokenRepository);
        PasswordEncryptionUtils passwordEncryptionUtils = new PasswordEncryptionUtils();
        ISystemClock systemClock = () -> Instant.parse("2030-06-01T12:00:00Z");

        InitializationService initializationService = new InitializationService(
                companyRepository, memberRepository, eventPublisher, sessionTokenService, notificationService);
        CompanyService companyService = initializationService.initialize();

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

    @Test
    @DisplayName("Staff scenario file meets all acceptance criteria")
    void givenStaffScenarioFile_whenExecute_thenAcceptanceCriteriaMet() {
        runStaffScenario();

        for (String user : List.of("u1", "u2", "u3", "u4")) {
            assertNotNull(memberRepository.findByUsername(user).orElse(null), user + " should exist");
        }

        Company company = companyRepository.findByName("p1").orElseThrow();
        CouponDiscount discount = (CouponDiscount) company.getDiscountPolicy();
        assertEquals(new BigDecimal("20"), discount.getPercentOff());
        assertEquals("sale123", discount.getCouponCode());

        Member u2 = memberRepository.findByUsername("u2").orElseThrow();
        assertTrue(u2.getStaffAppointment("p1").isOwner());

        Member u3 = memberRepository.findByUsername("u3").orElseThrow();
        StaffAppointment u3Appt = u3.getStaffAppointment("p1");
        assertTrue(u3Appt.isManager());
        assertEquals(Set.of(ManagerPermission.MAP_DEFINITION), u3Appt.getPermissions());

        Event event = eventRepository.findByCompanyName("p1").stream()
                .filter(e -> "e1".equals(e.getName()))
                .findFirst()
                .orElseThrow();

        InventoryZone standing = event.getZones().stream()
                .filter(z -> "Standing".equals(z.getName())).findFirst().orElseThrow();
        InventoryZone seating = event.getZones().stream()
                .filter(z -> "Seating".equals(z.getName())).findFirst().orElseThrow();
        assertEquals(30, standing.getMaxCapacity());
        assertEquals(new BigDecimal("50"), standing.getPricePerTicket());
        assertEquals(100, seating.getSeats().size());
        assertEquals(new BigDecimal("100"), seating.getPricePerTicket());

        VenueLayout layout = event.getVenueLayout();
        assertNotNull(layout);
        assertEquals(10, layout.getRows());
        assertEquals(10, layout.getCols());
        assertEquals(100, layout.getCells().size());
    }

    @Test
    @DisplayName("u3 can edit venue layout but not manage policies")
    void givenStaffScenario_whenU3ChecksPermissions_thenLayoutAllowedPolicyDenied() {
        runStaffScenario();

        Member u3 = memberRepository.findByUsername("u3").orElseThrow();
        assertTrue(u3.getStaffAppointment("p1").hasPermission(ManagerPermission.MAP_DEFINITION));
        assertFalse(u3.getStaffAppointment("p1").hasPermission(ManagerPermission.POLICY_MODIFICATION));

        SecurityException denied = assertThrows(SecurityException.class,
                () -> u3.authorizePolicyModification("p1"));
        assertTrue(denied.getMessage().contains("POLICY_MODIFICATION"));
    }

    @Test
    @DisplayName("All users are logged out at the end of the staff scenario")
    void givenStaffScenario_whenComplete_thenSessionTokensAreRevoked() {
        runStaffScenario();

        String file = InitialStateFileLoader.load("classpath:initial-state/staff-demo-v3.txt");
        for (String user : List.of("u1", "u2", "u3", "u4")) {
            assertTrue(file.contains("logout(" + user + "_token)"),
                    "staff scenario must logout " + user);
        }

        // After logout, bound token symbols are cleared — a follow-up command cannot reuse them.
        InitialStateExecutionException ex = assertThrows(InitialStateExecutionException.class, () ->
                executor.execute(parser.parse("open-production-company(u1_token, p2);")));
        assertTrue(ex.getMessage().contains("u1_token"), ex.getMessage());
    }

    @Test
    @DisplayName("Commands must run in order (appoint owner only after company exists)")
    void givenAppointOwnerBeforeCompanyOpen_whenExecute_thenFails() {
        List<InitialStateOperation> ops = parser.parse("""
                guest-registration(u1, u1@example.com, secret1);
                guest-registration(u2, u2@example.com, secret2);
                login(u1, secret1);
                appoint-owner(u1_token, p1, u2);
                """);

        InitialStateExecutionException ex =
                assertThrows(InitialStateExecutionException.class, () -> executor.execute(ops));

        assertTrue(ex.getMessage().contains("appoint-owner"), ex.getMessage());
        assertFalse(companyRepository.existsByName("p1"));
    }

    @Test
    @DisplayName("Token from login is substituted into later commands")
    void givenLoginThenCompanyOpen_whenExecute_thenCompanyCreated() {
        executor.execute(parser.parse("""
                guest-registration(u1, u1@example.com, secret1);
                login(u1, secret1);
                open-production-company(u1_token, p1);
                """));
        assertTrue(companyRepository.existsByName("p1"));
    }

    @Test
    @DisplayName("Invalid command in staff scenario aborts with named operation")
    void givenBrokenStaffScenario_whenExecute_thenFailsNamingCommand() {
        String broken = InitialStateFileLoader.load("classpath:initial-state/staff-demo-v3.txt")
                .replace("set-company-coupon-discount", "set-invalid-coupon");

        InitialStateExecutionException ex =
                assertThrows(InitialStateExecutionException.class,
                        () -> executor.execute(parser.parse(broken)));

        assertTrue(ex.getMessage().contains("set-invalid-coupon"), ex.getMessage());
    }

    @Test
    @DisplayName("Logout revokes the member session token")
    void givenLoginThenLogout_whenExecute_thenTokenCannotBeReused() {
        InitialStateExecutionException ex = assertThrows(InitialStateExecutionException.class, () ->
                executor.execute(parser.parse("""
                        guest-registration(u1, u1@example.com, secret1);
                        login(u1, secret1);
                        open-production-company(u1_token, p1);
                        logout(u1_token);
                        open-production-company(u1_token, p2);
                        """)));

        assertTrue(ex.getMessage().contains("u1_token"), ex.getMessage());
        assertTrue(companyRepository.existsByName("p1"));
        assertFalse(companyRepository.existsByName("p2"));
    }

    private void runStaffScenario() {
        executor.execute(parser.parse(
                InitialStateFileLoader.load("classpath:initial-state/staff-demo-v3.txt")));
    }
}
