package com.ticketing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.client.AutoConfigureMockRestServiceServer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;

import com.ticketing.application.auth.SessionTokenData;
import com.ticketing.application.auth.SessionTokenService;
import com.ticketing.application.services.OrderService;
import com.ticketing.domain.event.Event;
import com.ticketing.domain.event.EventCategory;
import com.ticketing.domain.event.EventSchedule;
import com.ticketing.domain.event.IEventRepository;
import com.ticketing.domain.event.LockTimerDuration;
import com.ticketing.domain.member.IMemberRepository;
import com.ticketing.domain.member.Member;
import com.ticketing.domain.order.ActiveOrder;
import com.ticketing.domain.order.CompletedPurchase;
import com.ticketing.domain.order.IOrderRepository;
import com.ticketing.domain.order.OrderItem;
import com.ticketing.infrastructure.gateway.ExternalSystemsHandshakeRunner;

@org.junit.jupiter.api.Tag("slow")
@SpringBootTest(properties = {
    "ticketing.external.base-url=http://external.test/api/",
    "ticketing.external.ticket-url=http://external.test/api/",
    "ticketing.persistence=memory"
})
@AutoConfigureMockRestServiceServer
class CheckoutRobustnessTest {

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

    @MockBean
    private ExternalSystemsHandshakeRunner handshakeRunner;

    private UUID memberId;
    private UUID sessionId;
    private Event event;
    private ActiveOrder order;
    private String token;

    @BeforeEach
    void setUp() {
        mockServer.reset();
        
        memberId = UUID.randomUUID();
        sessionId = UUID.randomUUID();
        
        token = sessionTokenService.generateMemberToken(
            new SessionTokenData(sessionId, memberId, java.util.Set.of(), "user", "user@test.com", "MEMBER")
        );

        event = new Event(UUID.randomUUID(), "Company", "Event", "Desc", EventCategory.CONCERT,
                new EventSchedule(Instant.now().plus(java.time.Duration.ofDays(10)), 
                                  Instant.now().plus(java.time.Duration.ofDays(10)).plus(java.time.Duration.ofHours(3)), 
                                  Instant.now()),
                new LockTimerDuration(java.time.Duration.ofMinutes(15)));

        UUID zoneId = UUID.randomUUID();
        event.addZone(com.ticketing.domain.event.InventoryZone.createGA(zoneId, "GA", new BigDecimal("100.00"), 100));
        event.findZone(zoneId).lockGA(1);

        order = new ActiveOrder(sessionId, memberId, event.getId(), Instant.now());
        order.addItem(OrderItem.forGA(UUID.randomUUID(), zoneId, 1, new BigDecimal("100.00")));

        when(orderRepository.findActiveByMemberId(memberId)).thenReturn(Optional.of(order));
        when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));
        
        Member member = new Member(memberId, "user", "user@test.com", "pass");
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
    }

    @Test
    void GivenExternalReturnsMinusOne_WhenCheckout_ThenFailsGracefully() {
        // Stub payment gateway to return "-1" (Declined)
        mockServer.expect(requestTo("http://external.test/api/"))
                .andRespond(withSuccess("-1", MediaType.TEXT_PLAIN));

        assertThatThrownBy(() -> orderService.checkout(token, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Payment failed");

        mockServer.verify();
    }

    @Test
    void GivenExternalReturnsMalformed_WhenCheckout_ThenFailsGracefully() {
        // Stub payment gateway to return malformed text instead of an ID
        mockServer.expect(requestTo("http://external.test/api/"))
                .andRespond(withSuccess("INVALID_RESPONSE", MediaType.TEXT_PLAIN));

        assertThatThrownBy(() -> orderService.checkout(token, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Payment failed");

        mockServer.verify();
    }

    @Test
    void GivenExternalTimesOut_WhenCheckout_ThenFailsGracefully() {
        // Stub payment gateway to return server error (simulating timeout or 503)
        mockServer.expect(requestTo("http://external.test/api/"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> orderService.checkout(token, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Payment failed");

        mockServer.verify();
    }

    @Test
    void GivenPaymentConnectionTimeout_WhenCheckout_ThenFailsGracefullyAndOrderNotCommitted() {
        // A real socket timeout (not a 5xx) surfaces from RestTemplate as a ResourceAccessException.
        mockServer.expect(requestTo("http://external.test/api/"))
                .andRespond(request -> {
                    throw new SocketTimeoutException("simulated read timeout");
                });

        assertThatThrownBy(() -> orderService.checkout(token, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Payment failed");

        // No partial commit: the order is reverted to active, not completed.
        assertThat(order.isActive()).isTrue();
        mockServer.verify();
    }

    @Test
    void GivenDbDropsAndRecovers_WhenCheckout_ThenSecondAttemptSucceeds() {
        // First request to payment gateway (succeeds)
        mockServer.expect(requestTo("http://external.test/api/"))
                .andRespond(withSuccess("55000", MediaType.TEXT_PLAIN)); // Payment
        mockServer.expect(requestTo("http://external.test/api/"))
                .andRespond(withSuccess("99000", MediaType.TEXT_PLAIN)); // Tickets
        
        // DB drops! First save throws exception
        org.mockito.Mockito.doThrow(new DataAccessResourceFailureException("Connection refused - DB dropped"))
                .when(orderRepository).save(any(CompletedPurchase.class));

        assertThatThrownBy(() -> orderService.checkout(token, null))
                .isInstanceOf(DataAccessResourceFailureException.class);
        
        mockServer.verify();

        // --- Simulated Recovery ---
        mockServer.reset();
        
        // Second attempt: we try again, DB is back, gateways work
        mockServer.expect(requestTo("http://external.test/api/"))
                .andRespond(withSuccess("55001", MediaType.TEXT_PLAIN)); // Payment
        mockServer.expect(requestTo("http://external.test/api/"))
                .andRespond(withSuccess("99001", MediaType.TEXT_PLAIN)); // Tickets

        // Recreate order and event to simulate fetching a clean state from the DB after rollback
        event = new Event(event.getId(), "Company", "Event", "Desc", EventCategory.CONCERT,
                new EventSchedule(Instant.now().plus(java.time.Duration.ofDays(10)), 
                                  Instant.now().plus(java.time.Duration.ofDays(10)).plus(java.time.Duration.ofHours(3)), 
                                  Instant.now()),
                new LockTimerDuration(java.time.Duration.ofMinutes(15)));
        UUID zoneId = UUID.randomUUID();
        event.addZone(com.ticketing.domain.event.InventoryZone.createGA(zoneId, "GA", new BigDecimal("100.00"), 100));
        event.findZone(zoneId).lockGA(1);

        order = new ActiveOrder(sessionId, memberId, event.getId(), Instant.now());
        order.addItem(OrderItem.forGA(UUID.randomUUID(), zoneId, 1, new BigDecimal("100.00")));
        when(orderRepository.findActiveByMemberId(memberId)).thenReturn(Optional.of(order));
        when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));
        
        org.mockito.Mockito.doNothing().when(orderRepository).save(any(CompletedPurchase.class)); 
        
        var result = orderService.checkout(token, null);

        assertThat(result).isNotNull();
        assertThat(result.chargedAmount()).isEqualTo(new BigDecimal("100.00"));
        
        mockServer.verify();
        // Verify DB was called twice (first drop, second recovery)
        verify(orderRepository, times(2)).save(any(CompletedPurchase.class));
    }
}
