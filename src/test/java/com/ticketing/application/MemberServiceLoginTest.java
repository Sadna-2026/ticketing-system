package com.ticketing.application;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ticketing.application.dto.ActiveOrderDto;
import com.ticketing.domain.member.IMemberRepository;
import com.ticketing.domain.member.request.LoginRequest;
import com.ticketing.domain.member.request.RegisterRequest;
import com.ticketing.domain.member.response.LoginResponse;
import com.ticketing.domain.member.response.RegisterResponse;
import com.ticketing.domain.order.ActiveOrder;
import com.ticketing.infrastructure.InMemoryEventRepository;
import com.ticketing.infrastructure.InMemoryMemberRepository;
import com.ticketing.infrastructure.InMemoryOrderRepository;
import com.ticketing.infrastructure.InMemorySessionTokenRepository;
import com.ticketing.infrastructure.Interface.ISessionTokenRepository;
import com.ticketing.infrastructure.PasswordEncryptionUtils;

class MemberServiceLoginTest {

    private IMemberRepository memberRepository;
    private InMemoryOrderRepository orderRepository;
    private SessionTokenService sessionTokenService;
    private MemberService memberService;
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        String secret = Base64.getEncoder().encodeToString(
                "01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8)
        );

        ISessionTokenRepository tokenRepository = new InMemorySessionTokenRepository();

        memberRepository = new InMemoryMemberRepository();
        orderRepository = new InMemoryOrderRepository();
        sessionTokenService = new SessionTokenService(secret, 120, tokenRepository);
        PasswordEncryptionUtils passwordEncryptionUtils = new PasswordEncryptionUtils();

        memberService = new MemberService(
                memberRepository,
                passwordEncryptionUtils,
                sessionTokenService
        );

        orderService = new OrderService(
                orderRepository,
                sessionTokenService,
                new InMemoryEventRepository(),
                new TestClock(Instant.parse("2026-06-01T10:00:00Z"))
        );
    }

    @Test
    @DisplayName("SuccessfulLogin - returns valid member token")
    void GivenGuestAndValidCredentials_WhenLogin_ThenGuestTokenUpgradedToMemberToken() {
        // Arrange
        RegisterResponse registered = registerMember("tamar", "tamar@example.com", "123456");
        memberService.logout(registered.sessionToken());

        String guestToken = sessionTokenService.generateGuestToken();
        UUID originalSessionId = sessionTokenService.extractSessionId(guestToken);

        // Act
        LoginResponse response = memberService.login(new LoginRequest("tamar", "123456"), guestToken);

        // Assert
        assertTrue(response.success());
        assertEquals("Member logged in successfully.", response.message());
        assertNotNull(response.member());
        assertEquals(registered.member().memberId(), response.member().memberId());
        assertEquals("tamar", response.member().username());
        assertEquals("tamar@example.com", response.member().email());
        assertNotNull(response.sessionToken());

        assertFalse(sessionTokenService.isValid(guestToken));
        assertTrue(sessionTokenService.isValid(response.sessionToken()));
        assertEquals(originalSessionId, sessionTokenService.extractSessionId(response.sessionToken()));
        assertEquals(registered.member().memberId(), sessionTokenService.extractMemberId(response.sessionToken()));
    }

    @Test
    @DisplayName("WrongPassword rejected")
    void GivenGuestAndWrongPassword_WhenLogin_ThenRejectedAndGuestStillConnected() {
        // Arrange
        registerMember("tamar", "tamar@example.com", "123456");
        String guestToken = sessionTokenService.generateGuestToken();

        // Act
        LoginResponse response = memberService.login(new LoginRequest("tamar", "wrong-password"), guestToken);

        // Assert
        assertFalse(response.success());
        assertEquals("Invalid username or password.", response.message());
        assertNull(response.member());
        assertNull(response.sessionToken());
        assertTrue(sessionTokenService.isValid(guestToken));
        assertNull(sessionTokenService.extractMemberId(guestToken));
    }

    @Test
    @DisplayName("NonexistentUser rejected")
    void GivenGuestAndNonexistentUsername_WhenLogin_ThenRejectedAndGuestStillConnected() {
        // Arrange
        String guestToken = sessionTokenService.generateGuestToken();

        // Act
        LoginResponse response = memberService.login(new LoginRequest("missing", "123456"), guestToken);

        // Assert
        assertFalse(response.success());
        assertEquals("Invalid username or password.", response.message());
        assertNull(response.member());
        assertNull(response.sessionToken());
        assertTrue(sessionTokenService.isValid(guestToken));
        assertNull(sessionTokenService.extractMemberId(guestToken));
    }

    @Test
    @DisplayName("Recoverable active order behavior follows member-exit rules")
    void GivenGuestWithActiveOrder_WhenLogin_ThenOrderRemainsRecoverableBySession() {
        // Arrange
        registerMember("tamar", "tamar@example.com", "123456");
        String guestToken = sessionTokenService.generateGuestToken();
        UUID sessionId = sessionTokenService.extractSessionId(guestToken);
        UUID orderId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        orderRepository.save(new ActiveOrder(orderId, sessionId, eventId, Instant.parse("2026-06-01T10:00:00Z")));

        // Act
        LoginResponse response = memberService.login(new LoginRequest("tamar", "123456"), guestToken);

        // Assert
        assertTrue(response.success());
        assertEquals(sessionId, sessionTokenService.extractSessionId(response.sessionToken()));
        assertTrue(orderRepository.findActiveBySessionId(sessionId).isPresent());

        ActiveOrderDto activeOrder = orderService.getActiveOrder(response.sessionToken(), orderId);
        assertEquals(orderId, activeOrder.getId());
        assertEquals(sessionId, activeOrder.getSessionId());
        assertEquals(eventId, activeOrder.getEventId());
    }

    @Test
    @DisplayName("InvalidLoginToken rejected")
    void GivenInvalidGuestToken_WhenLogin_ThenRejected() {
        // Arrange
        registerMember("tamar", "tamar@example.com", "123456");

        // Act
        LoginResponse response = memberService.login(new LoginRequest("tamar", "123456"), "not.a.real.jwt");

        // Assert
        assertFalse(response.success());
        assertEquals("Invalid session token.", response.message());
        assertNull(response.member());
        assertNull(response.sessionToken());
    }

    @Test
    @DisplayName("MemberToken rejected")
    void GivenMemberToken_WhenLogin_ThenRejected() {
        // Arrange
        RegisterResponse registered = registerMember("tamar", "tamar@example.com", "123456");

        // Act
        LoginResponse response = memberService.login(
                new LoginRequest("tamar", "123456"),
                registered.sessionToken()
        );

        // Assert
        assertFalse(response.success());
        assertEquals("Only guests can log in.", response.message());
        assertNull(response.member());
        assertNull(response.sessionToken());
        assertTrue(sessionTokenService.isValid(registered.sessionToken()));
    }

    @Test
    @DisplayName("InvalidCredentialsFormat rejected")
    void GivenBlankCredentials_WhenLogin_ThenRejectedAndGuestStillConnected() {
        // Arrange
        String guestToken = sessionTokenService.generateGuestToken();

        // Act
        LoginResponse response = memberService.login(new LoginRequest(" ", " "), guestToken);

        // Assert
        assertFalse(response.success());
        assertEquals("Invalid credentials.", response.message());
        assertNull(response.member());
        assertNull(response.sessionToken());
        assertTrue(sessionTokenService.isValid(guestToken));
        assertNull(sessionTokenService.extractMemberId(guestToken));
    }

    private RegisterResponse registerMember(String username, String email, String password) {
        String guestToken = sessionTokenService.generateGuestToken();

        RegisterResponse response = memberService.register(
                new RegisterRequest(username, email, password, "0501234567", "2000-01-01"),
                guestToken
        );

        assertTrue(response.success());
        assertNotNull(response.sessionToken());

        return response;
    }
}
