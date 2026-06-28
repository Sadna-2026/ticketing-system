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
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.OutputCaptureExtension;

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
import com.ticketing.domain.member.Member;
import com.ticketing.domain.member.PendingRoleOffer;
import com.ticketing.domain.member.StaffAppointment;
import com.ticketing.infrastructure.InMemoryCompanyRepository;
import com.ticketing.infrastructure.InMemoryEventPublisher;
import com.ticketing.infrastructure.InMemoryEventRepository;
import com.ticketing.infrastructure.InMemoryMemberRepository;
import com.ticketing.infrastructure.InMemoryOrderRepository;
import com.ticketing.infrastructure.InMemorySessionTokenRepository;
import com.ticketing.infrastructure.PasswordEncryptionUtils;

@DisplayName("InitialStateExecutor")
@ExtendWith(OutputCaptureExtension.class)
class InitialStateExecutorTest {

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
    @DisplayName("Happy path: registers members, opens a company and offers a manager role")
    void givenValidSequence_whenExecute_thenStateIsBooted() {
        List<InitialStateOperation> ops = parser.parse("""
                guest-registration(rina, rina@example.com, secret1, 050-000-0000, 1990-01-01);
                guest-registration(dana, dana@example.com, secret2);
                login(rina, secret1);
                open-production-company(rina_token, "Demo Co", "A demo company");
                appoint-manager(rina_token, "Demo Co", dana);
                """, "test.txt");

        executor.execute(ops);

        Member rina = memberRepository.findByUsername("rina").orElseThrow();
        Member dana = memberRepository.findByUsername("dana").orElseThrow();
        assertEquals("rina@example.com", rina.getEmail());

        assertTrue(companyRepository.existsByName("Demo Co"));
        StaffAppointment rinaAppt = rina.getStaffAppointment("Demo Co");
        assertNotNull(rinaAppt, "rina should have a staff appointment for the company she opened");
        assertTrue(rinaAppt.isOwner(), "rina should be the owner of the company she opened");

        List<PendingRoleOffer> offers = dana.getPendingOffers();
        assertEquals(1, offers.size(), "dana should have exactly one pending role offer");
        assertEquals(StaffAppointment.StaffRole.MANAGER, offers.get(0).getRole());
    }

    @Test
    @DisplayName("Staff demo scenario boots company, roles, event, coupon and logouts")
    void givenStaffScenarioFile_whenExecute_thenFullStateIsReady() {
        String content = InitialStateFileLoader.load("classpath:initial-state/staff-demo-v3.txt");
        executor.execute(parser.parse(content, "staff-demo-v3.txt"));

        assertNotNull(memberRepository.findByUsername("u1").orElse(null));
        assertNotNull(memberRepository.findByUsername("u2").orElse(null));
        assertNotNull(memberRepository.findByUsername("u3").orElse(null));
        assertNotNull(memberRepository.findByUsername("u4").orElse(null));

        Company company = companyRepository.findByName("p1").orElseThrow();
        CouponDiscount discount = (CouponDiscount) company.getDiscountPolicy();
        assertEquals(new BigDecimal("20"), discount.getPercentOff());
        assertEquals("sale123", discount.getCouponCode());

        Member u2 = memberRepository.findByUsername("u2").orElseThrow();
        StaffAppointment u2Appt = u2.getStaffAppointment("p1");
        assertTrue(u2Appt.isOwner());

        Member u3 = memberRepository.findByUsername("u3").orElseThrow();
        StaffAppointment u3Appt = u3.getStaffAppointment("p1");
        assertTrue(u3Appt.isManager());
        assertEquals(Set.of(com.ticketing.domain.member.ManagerPermission.MAP_DEFINITION), u3Appt.getPermissions());

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
    @DisplayName("Failing step: open-production-company with an unbound token aborts and names the op")
    void givenUnboundTokenReference_whenExecute_thenThrowsNamingTheOp() {
        List<InitialStateOperation> ops = parser.parse("""
                guest-registration(rina, rina@example.com, secret1);
                open-production-company(ghost_token, "Demo Co", "desc");
                """, "test.txt");

        InitialStateExecutionException ex =
                assertThrows(InitialStateExecutionException.class, () -> executor.execute(ops));

        assertTrue(ex.getMessage().contains("ghost_token"),
                "message should name the unbound token: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("[EXECUTION ERROR] test.txt:2: open-production-company: unbound token reference 'ghost_token'"));

        assertFalse(companyRepository.existsByName("Demo Co"));
    }

    @Test
    @DisplayName("Failing step: appoint-manager for a nonexistent member aborts")
    void givenAppointUnknownMember_whenExecute_thenThrows() {
        List<InitialStateOperation> ops = parser.parse("""
                guest-registration(rina, rina@example.com, secret1);
                login(rina, secret1);
                open-production-company(rina_token, "Demo Co", "desc");
                appoint-manager(rina_token, "Demo Co", ghost);
                """, "test.txt");

        InitialStateExecutionException ex =
                assertThrows(InitialStateExecutionException.class, () -> executor.execute(ops));
        assertTrue(ex.getMessage().contains("ghost"),
                "message should reference the missing target: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("[EXECUTION ERROR] test.txt:4: appoint-manager: role appointment target member 'ghost' does not exist"));
    }

    @Test
    @DisplayName("Unknown operation name aborts and names the operation")
    void givenUnknownOperation_whenExecute_thenThrows() {
        List<InitialStateOperation> ops = parser.parse("teleport(rina, mars);", "test.txt");

        InitialStateExecutionException ex =
                assertThrows(InitialStateExecutionException.class, () -> executor.execute(ops));
        assertTrue(ex.getMessage().contains("teleport"),
                "message should name the unknown op: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("[EXECUTION ERROR] test.txt:1: teleport: Unknown initial-state operation 'teleport'"));
    }

    @Test
    @DisplayName("Wrong argument count aborts")
    void givenWrongArity_whenExecute_thenThrows() {
        List<InitialStateOperation> ops = parser.parse("login(rina);", "test.txt");

        InitialStateExecutionException ex =
                assertThrows(InitialStateExecutionException.class, () -> executor.execute(ops));
        assertTrue(ex.getMessage().contains("login"),
                "message should name the failing op: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("[EXECUTION ERROR] test.txt:1: login: login expects 2 argument(s) but got 1"));
    }

    @Test
    @DisplayName("Empty operation list is a no-op")
    void givenNoOperations_whenExecute_thenNothingHappens() {
        executor.execute(List.of());
        assertEquals(0, memberRepository.count());
    }

    // ── #543 init-file edge cases: every failure aborts with a located error (file:line, op #index/name) ──

    @Test
    @DisplayName("Duplicate registration: the same username twice aborts with a located error")
    void givenDuplicateRegistration_whenExecute_thenThrowsLocatedError() {
        List<InitialStateOperation> ops = parser.parse("""
                guest-registration(rina, rina@example.com, secret1);
                guest-registration(rina, other@example.com, secret2);
                """, "test.txt");

        InitialStateExecutionException ex =
                assertThrows(InitialStateExecutionException.class, () -> executor.execute(ops));
        assertTrue(ex.getMessage().contains("guest-registration"), "names the op: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("rina"), "names the duplicate user: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("[EXECUTION ERROR] test.txt:2:"),
                "located error in message: " + ex.getMessage());
        assertEquals(1, memberRepository.count(), "the duplicate must not create a second member");
    }

    @Test
    @DisplayName("Duplicate company name: opening the same company twice aborts with a located error")
    void givenDuplicateCompanyName_whenExecute_thenThrowsLocatedError() {
        List<InitialStateOperation> ops = parser.parse("""
                guest-registration(rina, rina@example.com, secret1);
                login(rina, secret1);
                open-production-company(rina_token, "Demo Co", "first");
                open-production-company(rina_token, "Demo Co", "second");
                """, "test.txt");

        InitialStateExecutionException ex =
                assertThrows(InitialStateExecutionException.class, () -> executor.execute(ops));
        assertTrue(ex.getMessage().contains("already exists"),
                "message should describe the duplicate: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("[EXECUTION ERROR] test.txt:4:"),
                "located error in message: " + ex.getMessage());
        assertTrue(companyRepository.existsByName("Demo Co"), "the first company still exists");
    }

    @Test
    @DisplayName("Out-of-order: logging in before the user is registered aborts with a located error")
    void givenLoginBeforeRegister_whenExecute_thenThrowsLocatedError() {
        List<InitialStateOperation> ops = parser.parse("login(rina, secret1);", "test.txt");

        InitialStateExecutionException ex =
                assertThrows(InitialStateExecutionException.class, () -> executor.execute(ops));
        assertTrue(ex.getMessage().contains("login"), "names the op: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("rina"), "names the unknown user: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("[EXECUTION ERROR] test.txt:1:"),
                "located error in message: " + ex.getMessage());
    }

    @Test
    @DisplayName("Reference to a non-existent company: appointing on a company never opened aborts")
    void givenAppointOnUnknownCompany_whenExecute_thenThrowsLocatedError() {
        List<InitialStateOperation> ops = parser.parse("""
                guest-registration(rina, rina@example.com, secret1);
                guest-registration(dana, dana@example.com, secret2);
                login(rina, secret1);
                appoint-manager(rina_token, "Ghost Co", dana);
                """, "test.txt");

        InitialStateExecutionException ex =
                assertThrows(InitialStateExecutionException.class, () -> executor.execute(ops));
        assertTrue(ex.getMessage().contains("Company not found")
                        || ex.getMessage().contains("Ghost Co"),
                "message should reference the missing company: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("[EXECUTION ERROR] test.txt:4:"),
                "located error in message: " + ex.getMessage());
    }

    @Test
    @DisplayName("Reference to a non-existent event: setting a layout on an event never created aborts")
    void givenSetLayoutOnUnknownEvent_whenExecute_thenThrowsLocatedError() {
        List<InitialStateOperation> ops = parser.parse("""
                guest-registration(rina, rina@example.com, secret1);
                login(rina, secret1);
                open-production-company(rina_token, "Demo Co", "desc");
                set-event-seating-layout(rina_token, "Demo Co", "GhostEvent", "Seating", 5, 5);
                """, "test.txt");

        InitialStateExecutionException ex =
                assertThrows(InitialStateExecutionException.class, () -> executor.execute(ops));
        assertTrue(ex.getMessage().contains("GhostEvent"),
                "message should reference the missing event: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("[EXECUTION ERROR] test.txt:4:"),
                "located error in message: " + ex.getMessage());
    }
}
