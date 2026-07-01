package com.ticketing.infrastructure.logging;

import static com.ticketing.infrastructure.logging.LogFileTestSupport.ERROR_LOG;
import static com.ticketing.infrastructure.logging.LogFileTestSupport.EVENT_LOG;
import static com.ticketing.infrastructure.logging.LogFileTestSupport.fileContains;
import static com.ticketing.infrastructure.logging.LogFileTestSupport.fileContainsLineWith;
import static com.ticketing.infrastructure.logging.LogFileTestSupport.waitForFileToContain;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.ticketing.application.auth.ISessionTokenService;
import com.ticketing.application.services.CompanyService;
import com.ticketing.application.services.EventService;
import com.ticketing.application.services.MemberService;
import com.ticketing.application.services.OrderService;
import com.ticketing.domain.company.Company;
import com.ticketing.domain.company.ICompanyRepository;
import com.ticketing.domain.event.AlwaysAllowPolicy;
import com.ticketing.domain.event.Event;
import com.ticketing.domain.event.EventCategory;
import com.ticketing.domain.event.EventSchedule;
import com.ticketing.domain.event.IEventRepository;
import com.ticketing.domain.event.InventoryZone;
import com.ticketing.domain.event.LockTimerDuration;
import com.ticketing.domain.event.NoDiscountPolicy;
import com.ticketing.domain.member.IMemberRepository;
import com.ticketing.domain.member.Member;
import com.ticketing.domain.member.request.RegisterRequest;
import com.ticketing.infrastructure.PasswordEncryptionUtils;

/**
 * Req 8: event.log records use-case scenarios with params; error.log records system errors only.
 * Both remain viewable while {@code ticketing.persistence=jpa} is active.
 */
@SpringBootTest(properties = {
        "ticketing.persistence=jpa",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "ticketing.seed.enabled=false",
        "ticketing.bootstrap.dataset=none",
        "ticketing.startup.initialize-platform=false"
})
@ActiveProfiles("test")
@DisplayName("Requirement 8 logging with JPA persistence")
class LoggingRequirement8JpaTest {

    private enum PositiveUseCase {
        MEMBER_REGISTRATION,
        ORDER_CREATION,
        COMPANY_CREATION
    }

    private enum NegativeUseCase {
        MEMBER_REGISTRATION_REJECTED,
        EMPTY_ORDER_CHECKOUT_REJECTED,
        EVENT_CANCELLATION_REJECTED
    }

    @Autowired
    private MemberService memberService;
    @Autowired
    private OrderService orderService;
    @Autowired
    private CompanyService companyService;
    @Autowired
    private EventService eventService;
    @Autowired
    private ISessionTokenService sessionTokenService;
    @Autowired
    private IMemberRepository memberRepository;
    @Autowired
    private ICompanyRepository companyRepository;
    @Autowired
    private IEventRepository eventRepository;

    private final PasswordEncryptionUtils passwords = new PasswordEncryptionUtils();

    @ParameterizedTest
    @EnumSource(PositiveUseCase.class)
    @DisplayName("A use-case writes an event-log entry (scenario + params)")
    void GivenSuccessfulUseCase_WhenInvoked_ThenEventLogRecordsScenarioAndParams(PositiveUseCase useCase)
            throws Exception {
        String expectedSnippet = triggerPositiveUseCase(useCase);

        assertTrue(
                waitForFileToContain(EVENT_LOG, expectedSnippet, Duration.ofSeconds(3)),
                () -> useCase + " should write '" + expectedSnippet + "' to logs/event.log");
        assertTrue(
                fileContainsLineWith(EVENT_LOG, expectedSnippet, "INFO"),
                () -> useCase + " should be logged at INFO in logs/event.log");
        assertFalse(
                fileContains(ERROR_LOG, expectedSnippet),
                () -> useCase + " must not duplicate into logs/error.log");
    }

    @Test
    @DisplayName("A forced system error writes an error-log entry")
    void GivenForcedSystemError_WhenTriggered_ThenErrorLogRecordsIt() throws Exception {
        UUID sessionId = UUID.randomUUID();
        String marker = "Member ID is null";

        assertThatThrownBy(() -> sessionTokenService.generateMemberToken(sessionId, null, Set.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("memberId cannot be null");

        assertTrue(
                waitForFileToContain(ERROR_LOG, marker, Duration.ofSeconds(3)),
                "system error should be persisted to logs/error.log");
        assertTrue(
                fileContainsLineWith(ERROR_LOG, marker, "ERROR"),
                "system error should be written with ERROR level in logs/error.log");
        assertFalse(
                fileContains(EVENT_LOG, marker),
                "system error must not be duplicated into logs/event.log");
    }

    @ParameterizedTest
    @EnumSource(NegativeUseCase.class)
    @DisplayName("A negative scenario writes ONLY an event-log entry (no error-log)")
    void GivenNegativeUseCase_WhenInvoked_ThenOnlyEventLogIsWritten(NegativeUseCase useCase) throws Exception {
        String expectedSnippet = triggerNegativeUseCase(useCase);

        assertTrue(
                waitForFileToContain(EVENT_LOG, expectedSnippet, Duration.ofSeconds(3)),
                () -> useCase + " should write '" + expectedSnippet + "' to logs/event.log");
        assertFalse(
                fileContains(ERROR_LOG, expectedSnippet),
                () -> useCase + " must not appear in logs/error.log");
    }

    private String triggerPositiveUseCase(PositiveUseCase useCase) {
        return switch (useCase) {
            case MEMBER_REGISTRATION -> memberRegistrationSnippet();
            case ORDER_CREATION -> orderCreationSnippet();
            case COMPANY_CREATION -> companyCreationSnippet();
        };
    }

    private String triggerNegativeUseCase(NegativeUseCase useCase) {
        return switch (useCase) {
            case MEMBER_REGISTRATION_REJECTED -> memberRegistrationRejectedSnippet();
            case EMPTY_ORDER_CHECKOUT_REJECTED -> emptyCheckoutRejectedSnippet();
            case EVENT_CANCELLATION_REJECTED -> eventCancellationRejectedSnippet();
        };
    }

    private String memberRegistrationSnippet() {
        String marker = "req8-member-" + UUID.randomUUID();
        String guestToken = sessionTokenService.generateGuestToken();
        memberService.register(
                new RegisterRequest(marker, marker + "@example.com", "secret1", "0501234567", "2000-01-01"),
                guestToken);
        return "Registration attempt: username=" + marker + ", email=" + marker + "@example.com";
    }

    private String orderCreationSnippet() {
        UUID eventId = UUID.randomUUID();
        UUID zoneId = UUID.randomUUID();
        eventRepository.save(publishedGaEvent(eventId, zoneId, 5));

        UUID memberId = UUID.randomUUID();
        memberRepository.save(member(memberId, "req8-order-" + UUID.randomUUID()));
        String token = memberToken(memberId);

        UUID orderId = orderService.createOrder(token, eventId);
        return "Order created: orderId=" + orderId;
    }

    private String companyCreationSnippet() {
        UUID founderId = UUID.randomUUID();
        memberRepository.save(member(founderId, "req8-founder-" + UUID.randomUUID()));
        String token = memberToken(founderId);
        String companyName = "Req8 Co " + UUID.randomUUID();

        companyService.openProductionCompany(token, companyName, "Requirement 8 logging test");
        return "Creating company: founderId=" + founderId + ", name=" + companyName;
    }

    private String memberRegistrationRejectedSnippet() {
        memberService.register(
                new RegisterRequest("req8-reject", "req8-reject@example.com", "secret1", "0501234567", "2000-01-01"),
                null);
        return "Failed to register member: invalid session token";
    }

    private String emptyCheckoutRejectedSnippet() {
        UUID eventId = UUID.randomUUID();
        UUID zoneId = UUID.randomUUID();
        eventRepository.save(publishedGaEvent(eventId, zoneId, 5));

        UUID memberId = UUID.randomUUID();
        memberRepository.save(member(memberId, "req8-empty-" + UUID.randomUUID()));
        String token = memberToken(memberId);

        UUID orderId = orderService.createOrder(token, eventId);
        assertThatThrownBy(() -> orderService.checkout(token, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("empty order");
        return "Failed to checkout order " + orderId + ": no items in order";
    }

    private String eventCancellationRejectedSnippet() {
        assertThatThrownBy(() -> eventService.cancelEvent("irrelevant-token", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("eventId is required");
        return "Event cancellation denied: missing eventId";
    }

    private String memberToken(UUID memberId) {
        String guestToken = sessionTokenService.generateGuestToken();
        UUID sessionId = sessionTokenService.extractSessionId(guestToken);
        return sessionTokenService.generateMemberToken(sessionId, memberId, Set.of());
    }

    private Member member(UUID id, String username) {
        return new Member(id, username, username + "@test.local", passwords.hashPassword("password123"));
    }

    private Event publishedGaEvent(UUID eventId, UUID zoneId, int capacity) {
        if (!companyRepository.existsByName("Acme Productions")) {
            companyRepository.save(new Company("Acme Productions", "desc", UUID.randomUUID()));
        }
        Instant start = Instant.now().plus(Duration.ofDays(30));
        Event event = new Event(
                eventId, "Acme Productions", "Race Fest", "desc",
                EventCategory.CONCERT,
                new EventSchedule(start, start.plus(Duration.ofHours(3)), start.minus(Duration.ofHours(1))),
                new LockTimerDuration(Duration.ofMinutes(30)),
                new AlwaysAllowPolicy(), new NoDiscountPolicy());
        event.addZone(InventoryZone.createGA(zoneId, "Floor", new BigDecimal("45.00"), capacity));
        event.publish();
        return event;
    }
}
