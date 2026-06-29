package com.ticketing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.client.AutoConfigureMockRestServiceServer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;

import com.ticketing.application.auth.SessionTokenData;
import com.ticketing.application.auth.SessionTokenService;
import com.ticketing.application.services.OrderService;
import com.ticketing.domain.event.Event;
import com.ticketing.domain.event.EventCategory;
import com.ticketing.domain.event.EventSchedule;
import com.ticketing.domain.event.IEventRepository;
import com.ticketing.domain.event.InventoryZone;
import com.ticketing.domain.event.LockTimerDuration;
import com.ticketing.domain.member.IMemberRepository;
import com.ticketing.domain.member.Member;
import com.ticketing.domain.order.ActiveOrder;
import com.ticketing.domain.order.FailedCheckoutRefund;
import com.ticketing.domain.order.IOrderRepository;
import com.ticketing.domain.order.OrderItem;
import com.ticketing.infrastructure.gateway.ExternalSystemsHandshakeRunner;
import com.ticketing.infrastructure.notification.WebSocketNotificationService;

/**
 * #530: every external dependency made to fail or timeout must be handled, with no crash,
 * no partial commit, and no money taken without a ticket delivered.
 *
 * <p>{@link CheckoutRobustnessTest} already covers the payment leg (decline, malformed
 * response, 500, socket timeout). This suite covers the gaps on the ticket-supply leg —
 * the integrity rule from the General Requirements: if payment succeeded but issuance
 * failed, the system must auto-refund or persist a {@code FailedCheckoutRefund} record
 * for retry, and the user must be told.
 *
 * <p>Both payment and ticket-supply gateways POST to the same external URL, distinguished
 * by an {@code action_type=pay} / {@code action_type=issue_ticket} parameter. The
 * {@link MockRestServiceServer} stubs each expectation in declaration order, so the
 * tests script the precise sequence the {@code OrderService} performs.
 */
@org.junit.jupiter.api.Tag("slow")
@SpringBootTest(properties = {
        "ticketing.external.base-url=http://external.test/api/",
        "ticketing.external.ticket-url=http://external.test/api/",
        "ticketing.persistence=memory"
})
@AutoConfigureMockRestServiceServer
@DisplayName("Ticket-supply gateway failure robustness (#530)")
class TicketSupplyRobustnessTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private MockRestServiceServer mockServer;

    @Autowired
    private SessionTokenService sessionTokenService;

    @MockBean
    private IOrderRepository orderRepository;

    @MockBean
    private IEventRepository eventRepository;

    @MockBean
    private IMemberRepository memberRepository;

    // Mock the concrete WebSocketNotificationService — that's what other Spring beans
    // (NotificationsPresenter) autowire by exact type. The mock also satisfies the
    // INotificationService interface OrderService depends on.
    @MockBean
    private WebSocketNotificationService notificationService;

    @MockBean
    private ExternalSystemsHandshakeRunner handshakeRunner;

    private UUID memberId;
    private Event event;
    private ActiveOrder order;
    private String token;

    @BeforeEach
    void setUp() {
        mockServer.reset();
        memberId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        token = sessionTokenService.generateMemberToken(
                new SessionTokenData(sessionId, memberId, java.util.Set.of(), "user", "user@test.com", "MEMBER"));

        event = new Event(UUID.randomUUID(), "Company", "Event", "Desc", EventCategory.CONCERT,
                new EventSchedule(
                        Instant.now().plus(java.time.Duration.ofDays(10)),
                        Instant.now().plus(java.time.Duration.ofDays(10)).plus(java.time.Duration.ofHours(3)),
                        Instant.now()),
                new LockTimerDuration(java.time.Duration.ofMinutes(15)));
        UUID zoneId = UUID.randomUUID();
        event.addZone(InventoryZone.createGA(zoneId, "GA", new BigDecimal("100.00"), 100));
        event.findZone(zoneId).lockGA(1);

        order = new ActiveOrder(sessionId, memberId, event.getId(), Instant.now());
        order.addItem(OrderItem.forGA(UUID.randomUUID(), zoneId, 1, new BigDecimal("100.00")));

        when(orderRepository.findActiveByMemberId(memberId)).thenReturn(Optional.of(order));
        when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));
        when(memberRepository.findById(memberId))
                .thenReturn(Optional.of(new Member(memberId, "user", "user@test.com", "pass")));
    }

    // ── Payment OK + supply fails → automatic refund ─────────────────────────────

    @Test
    @DisplayName("Given payment succeeds and supply fails, When checkout, Then automatic refund is issued and order reverts to active")
    void GivenPaymentOkSupplyFails_WhenCheckout_ThenAutomaticRefundIssuedAndOrderReverts() {
        // 1. Payment succeeds — returns transaction id 55000.
        mockServer.expect(requestTo("http://external.test/api/"))
                .andExpect(content().string(Matchers.containsString("action_type=pay")))
                .andRespond(withSuccess("55000", MediaType.TEXT_PLAIN));
        // 2. Supply fails — returns -1.
        mockServer.expect(requestTo("http://external.test/api/"))
                .andExpect(content().string(Matchers.containsString("action_type=issue_ticket")))
                .andRespond(withSuccess("-1", MediaType.TEXT_PLAIN));
        // 3. Refund must be invoked next — returns 1 (success).
        mockServer.expect(requestTo("http://external.test/api/"))
                .andExpect(content().string(Matchers.containsString("action_type=refund")))
                .andRespond(withSuccess("1", MediaType.TEXT_PLAIN));

        assertThatThrownBy(() -> orderService.checkout(token, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContainingAll("Ticket generation failed", "refunded");

        // Order must be back to ACTIVE — no partial completion.
        assertThat(order.isActive()).as("order reverts to active after supply failure").isTrue();
        // The full payment→supply→refund sequence was attempted exactly once.
        mockServer.verify();
        // No FailedCheckoutRefund was persisted (the refund succeeded synchronously).
        verify(orderRepository, never()).save(any(FailedCheckoutRefund.class));
    }

    // ── Supply fails AND refund fails → escalation row + user notified ────────────

    @Test
    @DisplayName("Given supply fails and refund also fails, When checkout, Then a FailedCheckoutRefund is persisted and the user is notified")
    void GivenSupplyFailsAndRefundAlsoFails_WhenCheckout_ThenPendingRefundPersistedAndUserNotified() {
        mockServer.expect(requestTo("http://external.test/api/"))
                .andRespond(withSuccess("55001", MediaType.TEXT_PLAIN)); // pay OK
        mockServer.expect(requestTo("http://external.test/api/"))
                .andRespond(withSuccess("-1", MediaType.TEXT_PLAIN));    // supply fails
        mockServer.expect(requestTo("http://external.test/api/"))
                .andRespond(withServerError());                           // refund also fails

        assertThatThrownBy(() -> orderService.checkout(token, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContainingAll("Ticket generation failed", "refund is pending");

        // A FailedCheckoutRefund row is persisted so a retry job can complete the refund later.
        verify(orderRepository, times(1)).save(any(FailedCheckoutRefund.class));
        // The user is told via the top-level checkout-failure notification. We don't pin the
        // exact wording here (the checkout() catch path already covers it); the *persisted*
        // FailedCheckoutRefund above is the durable promise to the user, and that's what we
        // actually need to assert.
        verify(notificationService).notify(org.mockito.ArgumentMatchers.eq(memberId.toString()),
                org.mockito.ArgumentMatchers.contains("refund is pending"));
        assertThat(order.isActive()).isTrue();
        mockServer.verify();
    }

    // ── Supply socket timeout → refund attempt, order reverts ─────────────────────

    @Test
    @DisplayName("Given supply times out at the socket level, When checkout, Then refund is attempted and the order reverts")
    void GivenSupplyTimesOutAtSocket_WhenCheckout_ThenRefundAttemptedAndOrderReverts() {
        mockServer.expect(requestTo("http://external.test/api/"))
                .andRespond(withSuccess("55002", MediaType.TEXT_PLAIN)); // pay OK
        mockServer.expect(requestTo("http://external.test/api/"))
                .andRespond(request -> { throw new SocketTimeoutException("supply read timeout"); });
        mockServer.expect(requestTo("http://external.test/api/"))
                .andRespond(withSuccess("1", MediaType.TEXT_PLAIN));     // refund succeeds

        assertThatThrownBy(() -> orderService.checkout(token, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Ticket generation failed");

        assertThat(order.isActive()).isTrue();
        mockServer.verify();
    }

    // ── Payment fails → supply and refund must never be called ────────────────────

    @Test
    @DisplayName("Given payment declines, When checkout, Then supply and refund gateways are never called")
    void GivenPaymentDeclines_WhenCheckout_ThenSupplyAndRefundAreNeverCalled() {
        // Only ONE expectation — the payment. If supply or refund were invoked, the
        // mockServer.verify() would fail "extra request" — that's the regression guard.
        mockServer.expect(requestTo("http://external.test/api/"))
                .andExpect(content().string(Matchers.containsString("action_type=pay")))
                .andRespond(withSuccess("-1", MediaType.TEXT_PLAIN));

        assertThatThrownBy(() -> orderService.checkout(token, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Payment failed");

        mockServer.verify();
        // FailedCheckoutRefund must never be saved on a payment-decline path —
        // no money was taken, nothing to refund. The user IS notified of the failure
        // via the top-level checkout() catch, but never with a "refund" payload because
        // there's nothing to refund.
        verify(orderRepository, never()).save(any(FailedCheckoutRefund.class));
        verify(notificationService, never()).notify(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.contains("refund"));
        assertThat(order.isActive()).isTrue();
    }

    // ── Both endpoints unresponsive → cleanest failure surface ───────────────────

    @Test
    @DisplayName("Given payment endpoint returns server error, When checkout, Then supply is never invoked and no refund row is saved")
    void GivenPaymentEndpointReturnsServerError_WhenCheckout_ThenSupplyIsNeverInvoked() {
        // Payment endpoint completely unavailable.
        mockServer.expect(requestTo("http://external.test/api/"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> orderService.checkout(token, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Payment failed");

        // The single payment expectation is the only HTTP call made — if supply or refund
        // were attempted, mockServer.verify() would flag the unexpected request.
        mockServer.verify();
        // And no FailedCheckoutRefund is saved — there's nothing to refund.
        verify(orderRepository, never()).save(any(FailedCheckoutRefund.class));
        assertThat(order.isActive()).isTrue();
    }
}
