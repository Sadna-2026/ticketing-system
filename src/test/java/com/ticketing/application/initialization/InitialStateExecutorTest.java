package com.ticketing.application.initialization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ticketing.application.auth.ISessionTokenRepository;
import com.ticketing.application.auth.SessionTokenService;
import com.ticketing.application.services.CompanyService;
import com.ticketing.application.services.INotificationService;
import com.ticketing.application.services.MemberService;
import com.ticketing.domain.member.Member;
import com.ticketing.domain.member.PendingRoleOffer;
import com.ticketing.domain.member.StaffAppointment;
import com.ticketing.infrastructure.InMemoryCompanyRepository;
import com.ticketing.infrastructure.InMemoryEventPublisher;
import com.ticketing.infrastructure.InMemoryMemberRepository;
import com.ticketing.infrastructure.InMemorySessionTokenRepository;
import com.ticketing.infrastructure.PasswordEncryptionUtils;

@DisplayName("InitialStateExecutor")
class InitialStateExecutorTest {

    private InMemoryMemberRepository memberRepository;
    private InMemoryCompanyRepository companyRepository;
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
        InMemoryEventPublisher eventPublisher = new InMemoryEventPublisher();
        INotificationService notificationService = mock(INotificationService.class);
        sessionTokenService = new SessionTokenService(secret, 120, tokenRepository);
        PasswordEncryptionUtils passwordEncryptionUtils = new PasswordEncryptionUtils();

        // Wire the same repositories the real app uses, and register the synchronous
        // domain-event listeners (owner role on company-open, pending offer on appoint).
        InitializationService initializationService = new InitializationService(
                companyRepository, memberRepository, eventPublisher, sessionTokenService, notificationService);
        CompanyService companyService = initializationService.initialize();

        MemberService memberService = new MemberService(
                memberRepository, passwordEncryptionUtils, sessionTokenService);

        parser = new InitialStateParser();
        executor = new InitialStateExecutor(
                memberService, companyService, sessionTokenService, memberRepository);
    }

    @Test
    @DisplayName("Happy path: registers members, opens a company and offers a manager role")
    void givenValidSequence_whenExecute_thenStateIsBooted() {
        // Given a valid ordered sequence that registers two members, logs rina in,
        // opens a company owned by rina, and offers dana a manager role.
        List<InitialStateOperation> ops = parser.parse("""
                guest-registration(rina, rina@example.com, secret1, 050-000-0000, 1990-01-01);
                guest-registration(dana, dana@example.com, secret2);
                login(rina, secret1);
                open-production-company(rina_token, "Demo Co", "A demo company");
                appoint-manager(rina_token, "Demo Co", dana);
                """);

        // When
        executor.execute(ops);

        // Then both members are registered.
        Member rina = memberRepository.findByUsername("rina").orElseThrow();
        Member dana = memberRepository.findByUsername("dana").orElseThrow();
        assertEquals("rina@example.com", rina.getEmail());

        // And the company exists with rina as its owner.
        assertTrue(companyRepository.existsByName("Demo Co"));
        StaffAppointment rinaAppt = rina.getStaffAppointment("Demo Co");
        assertNotNull(rinaAppt, "rina should have a staff appointment for the company she opened");
        assertTrue(rinaAppt.isOwner(), "rina should be the owner of the company she opened");

        // And dana has a pending manager-role offer for that company.
        List<PendingRoleOffer> offers = dana.getPendingOffers();
        assertEquals(1, offers.size(), "dana should have exactly one pending role offer");
        assertEquals(StaffAppointment.StaffRole.MANAGER, offers.get(0).getRole());
    }

    @Test
    @DisplayName("Failing step: open-production-company with an unbound token aborts and names the op")
    void givenUnboundTokenReference_whenExecute_thenThrowsNamingTheOp() {
        // Given a sequence whose company-open references a token that was never bound.
        List<InitialStateOperation> ops = parser.parse("""
                guest-registration(rina, rina@example.com, secret1);
                open-production-company(ghost_token, "Demo Co", "desc");
                """);

        InitialStateExecutionException ex =
                assertThrows(InitialStateExecutionException.class, () -> executor.execute(ops));

        // The message names the failing operation (index 1) and the unbound symbol.
        assertTrue(ex.getMessage().contains("#1"), "message should name op index: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("open-production-company"),
                "message should name the op: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("ghost_token"),
                "message should name the unbound token: " + ex.getMessage());

        // And the offending company was not created.
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
                """);

        InitialStateExecutionException ex =
                assertThrows(InitialStateExecutionException.class, () -> executor.execute(ops));
        assertTrue(ex.getMessage().contains("ghost"),
                "message should reference the missing target: " + ex.getMessage());
    }

    @Test
    @DisplayName("Unknown operation name aborts and names the operation")
    void givenUnknownOperation_whenExecute_thenThrows() {
        List<InitialStateOperation> ops = parser.parse("teleport(rina, mars);");

        InitialStateExecutionException ex =
                assertThrows(InitialStateExecutionException.class, () -> executor.execute(ops));
        assertTrue(ex.getMessage().contains("teleport"),
                "message should name the unknown op: " + ex.getMessage());
    }

    @Test
    @DisplayName("Wrong argument count aborts")
    void givenWrongArity_whenExecute_thenThrows() {
        List<InitialStateOperation> ops = parser.parse("login(rina);");

        InitialStateExecutionException ex =
                assertThrows(InitialStateExecutionException.class, () -> executor.execute(ops));
        assertTrue(ex.getMessage().contains("login"),
                "message should name the failing op: " + ex.getMessage());
    }

    @Test
    @DisplayName("Empty operation list is a no-op")
    void givenNoOperations_whenExecute_thenNothingHappens() {
        executor.execute(List.of());
        assertEquals(0, memberRepository.count());
    }
}
