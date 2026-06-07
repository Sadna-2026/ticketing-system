package com.ticketing.presentation.vaadin.presenters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.ticketing.application.auth.ISessionTokenService;
import com.ticketing.application.dto.ActiveOrderDto;
import com.ticketing.application.services.AdminService;
import com.ticketing.application.services.MemberService;
import com.ticketing.application.services.OrderService;
import com.ticketing.domain.member.MemberDto;
import com.ticketing.domain.member.request.LoginRequest;
import com.ticketing.domain.member.request.RegisterRequest;
import com.ticketing.domain.member.response.LoginResponse;
import com.ticketing.domain.member.response.LogoutResponse;
import com.ticketing.domain.member.response.RegisterResponse;
import com.ticketing.presentation.vaadin.presenters.AuthPresenter.AuthResult;
import com.ticketing.presentation.vaadin.testsupport.VaadinSessionExtension;
import com.ticketing.presentation.vaadin.util.SessionContext;

@DisplayName("AuthPresenter")
@ExtendWith(VaadinSessionExtension.class)
class AuthPresenterTest {

    private MemberService memberService;
    private ISessionTokenService sessionTokenService;
    private AuthPresenter presenter;
    private AdminService adminService;
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        memberService = mock(MemberService.class);
        sessionTokenService = mock(ISessionTokenService.class);
        adminService = mock(AdminService.class);
        orderService = mock(OrderService.class);
        presenter = new AuthPresenter(memberService, adminService, sessionTokenService, orderService);
    }

    @Test
    void GivenNoSession_WhenStartGuestSession_ThenGuestSessionTokenIsStored() {
        UUID sessionId = UUID.randomUUID();
        when(sessionTokenService.generateGuestToken()).thenReturn("guest-token");
        when(sessionTokenService.extractSessionId("guest-token")).thenReturn(sessionId);

        AuthResult result = presenter.startGuestSession();

        assertTrue(result.success());
        assertEquals("Guest session started.", result.message());
        assertEquals("guest-token", SessionContext.getSessionToken());
        assertEquals(sessionId, SessionContext.getSessionId());
        assertEquals("Guest", SessionContext.getRole());
        assertNull(SessionContext.getMemberId());
    }

    @Test
    void GivenMemberSession_WhenStartGuestSession_ThenUserIsToldToLogoutFirstAndMemberSessionRemains() {
        UUID memberId = UUID.randomUUID();
        SessionContext.setSessionToken("member-token");
        SessionContext.setMemberId(memberId);
        SessionContext.setUsername("alice");
        SessionContext.setRole("Member");

        AuthResult result = presenter.startGuestSession();

        assertFalse(result.success());
        assertEquals("You are already logged in. Log out before switching accounts.", result.message());
        assertEquals("member-token", SessionContext.getSessionToken());
        assertEquals(memberId, SessionContext.getMemberId());
        assertEquals("alice", SessionContext.getUsername());
        assertEquals("Member", SessionContext.getRole());
        verifyNoMoreInteractions(sessionTokenService);
    }

    @Test
    void GivenGuestSessionAndValidRegistration_WhenRegister_ThenMemberSessionIsStored() {
        UUID sessionId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        MemberDto member = member(memberId, "alice");
        SessionContext.setSessionToken("guest-token");
        when(memberService.register(any(RegisterRequest.class), eq("guest-token")))
                .thenReturn(RegisterResponse.success(member, "member-token"));
        when(sessionTokenService.extractSessionId("member-token")).thenReturn(sessionId);

        AuthResult result = presenter.register(
                "alice",
                "alice@example.com",
                "secret1",
                "0500000000",
                LocalDate.of(2000, 1, 1)
        );

        assertTrue(result.success());
        assertEquals("Member registered and logged in successfully.", result.message());
        assertEquals("member-token", SessionContext.getSessionToken());
        assertEquals(sessionId, SessionContext.getSessionId());
        assertEquals(memberId, SessionContext.getMemberId());
        assertEquals("alice", SessionContext.getUsername());
        assertEquals("Member", SessionContext.getRole());
    }

    @Test
    void GivenGuestSessionAndValidLogin_WhenLogin_ThenMemberSessionIsStored() {
        UUID sessionId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        MemberDto member = member(memberId, "bob");
        SessionContext.setSessionToken("guest-token");
        when(memberService.login(any(LoginRequest.class), eq("guest-token")))
                .thenReturn(LoginResponse.success(member, "member-token"));
        when(sessionTokenService.extractSessionId("member-token")).thenReturn(sessionId);

        AuthResult result = presenter.login("bob", "secret1");

        assertTrue(result.success());
        assertEquals("Member logged in successfully.", result.message());
        assertEquals("member-token", SessionContext.getSessionToken());
        assertEquals(sessionId, SessionContext.getSessionId());
        assertEquals(memberId, SessionContext.getMemberId());
        assertEquals("bob", SessionContext.getUsername());
        assertEquals("Member", SessionContext.getRole());
    }

    @Test
    void GivenMemberSession_WhenLogout_ThenGuestSessionIsStored() {
        UUID guestSessionId = UUID.randomUUID();
        SessionContext.setSessionToken("member-token");
        SessionContext.setMemberId(UUID.randomUUID());
        SessionContext.setUsername("carol");
        SessionContext.setRole("Member");
        when(memberService.logout("member-token")).thenReturn(LogoutResponse.success("guest-token"));
        when(sessionTokenService.extractSessionId("guest-token")).thenReturn(guestSessionId);

        AuthResult result = presenter.logout();

        assertTrue(result.success());
        assertEquals("Member logged out successfully.", result.message());
        assertEquals("guest-token", SessionContext.getSessionToken());
        assertEquals(guestSessionId, SessionContext.getSessionId());
        assertEquals("Guest", SessionContext.getRole());
        assertNull(SessionContext.getMemberId());
        assertNull(SessionContext.getUsername());
    }

    @Test
    void GivenSuccessfulAuthAction_WhenPresenterUpdatesSession_ThenSessionContextStoresTokenAndMemberContext() {
        UUID sessionId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        SessionContext.setSessionToken("guest-token");
        when(memberService.login(any(LoginRequest.class), eq("guest-token")))
                .thenReturn(LoginResponse.success(member(memberId, "dana"), "member-token"));
        when(sessionTokenService.extractSessionId("member-token")).thenReturn(sessionId);

        presenter.login("dana", "secret1");

        assertEquals("member-token", SessionContext.getSessionToken());
        assertEquals(sessionId, SessionContext.getSessionId());
        assertEquals(memberId, SessionContext.getMemberId());
        assertEquals("dana", SessionContext.getUsername());
        assertEquals("Member", SessionContext.getRole());
    }

    @Test
    void GivenApplicationFailure_WhenLoginOrRegisterFails_ThenFailureMessageIsReturnedVerbatim() {
        SessionContext.setSessionToken("guest-token");
        when(memberService.login(any(LoginRequest.class), eq("guest-token")))
                .thenReturn(LoginResponse.failure("Invalid username or password."));
        when(memberService.register(any(RegisterRequest.class), eq("guest-token")))
                .thenReturn(RegisterResponse.failure("Email already in use."));

        AuthResult loginResult = presenter.login("bad-user", "wrong");
        AuthResult registerResult = presenter.register(
                "alice",
                "taken@example.com",
                "secret1",
                "0500000000",
                LocalDate.of(2000, 1, 1)
        );

        assertFalse(loginResult.success());
        assertEquals("Invalid username or password.", loginResult.message());
        assertFalse(registerResult.success());
        assertEquals("Email already in use.", registerResult.message());
    }

    @Test
    void GivenNoGuestSession_WhenLoginOrRegisterRequested_ThenUserIsToldToStartGuestSession() {
        AuthResult loginResult = presenter.login("alice", "secret1");
        AuthResult registerResult = presenter.register(
                "alice",
                "alice@example.com",
                "secret1",
                "0500000000",
                LocalDate.of(2000, 1, 1)
        );

        assertFalse(loginResult.success());
        assertEquals("Start a guest session before logging in or registering.", loginResult.message());
        assertFalse(registerResult.success());
        assertEquals("Start a guest session before logging in or registering.", registerResult.message());
        verifyNoInteractions(memberService);
    }

    @Test
    void GivenMemberSession_WhenLoginOrRegisterRequested_ThenUserIsToldToLogoutFirst() {
        SessionContext.setSessionToken("member-token");
        SessionContext.setMemberId(UUID.randomUUID());

        AuthResult loginResult = presenter.login("alice", "secret1");
        AuthResult registerResult = presenter.register(
                "alice",
                "alice@example.com",
                "secret1",
                "0500000000",
                LocalDate.of(2000, 1, 1)
        );

        assertFalse(loginResult.success());
        assertEquals("You are already logged in. Log out before switching accounts.", loginResult.message());
        assertFalse(registerResult.success());
        assertEquals("You are already logged in. Log out before switching accounts.", registerResult.message());
        verifyNoInteractions(memberService);
    }

    @Test
    void GivenTokenGenerationFails_WhenStartGuestSession_ThenGenericFailureMessageIsReturned() {
        when(sessionTokenService.generateGuestToken())
                .thenThrow(new IllegalStateException("database password leaked"));

        AuthResult result = presenter.startGuestSession();

        assertFalse(result.success());
        assertEquals("Could not start guest session.", result.message());
    }

    @Test
    void GivenLoginThrowsRuntimeException_WhenLogin_ThenGenericFailureMessageIsReturned() {
        SessionContext.setSessionToken("guest-token");
        when(memberService.login(any(LoginRequest.class), eq("guest-token")))
                .thenThrow(new IllegalStateException("internal login stack detail"));

        AuthResult result = presenter.login("alice", "secret1");

        assertFalse(result.success());
        assertEquals("Login failed. Please try again.", result.message());
    }

    @Test
    void GivenRegisterThrowsRuntimeException_WhenRegister_ThenGenericFailureMessageIsReturned() {
        SessionContext.setSessionToken("guest-token");
        when(memberService.register(any(RegisterRequest.class), eq("guest-token")))
                .thenThrow(new IllegalStateException("internal register stack detail"));

        AuthResult result = presenter.register(
                "alice",
                "alice@example.com",
                "secret1",
                "0500000000",
                LocalDate.of(2000, 1, 1)
        );

        assertFalse(result.success());
        assertEquals("Registration failed. Please try again.", result.message());
    }

    @Test
    void GivenLogoutThrowsRuntimeException_WhenLogout_ThenGenericFailureMessageIsReturned() {
        SessionContext.setSessionToken("member-token");
        SessionContext.setMemberId(UUID.randomUUID());
        when(memberService.logout("member-token"))
                .thenThrow(new IllegalStateException("internal logout stack detail"));

        AuthResult result = presenter.logout();

        assertFalse(result.success());
        assertEquals("Logout failed. Please try again.", result.message());
    }

    @Test
    void GivenGuestSessionWithActiveOrder_WhenLogout_ThenOrderIsCancelled() {
        SessionContext.setSessionToken("guest-token");
        SessionContext.setRole("Guest");
        when(orderService.getActiveOrder("guest-token")).thenReturn(mock(ActiveOrderDto.class));
        when(sessionTokenService.endSession("guest-token")).thenReturn(true);

        AuthResult result = presenter.logout();

        assertTrue(result.success());
        verify(orderService).cancelOrder("guest-token");
        verify(sessionTokenService).endSession("guest-token");
    }

    @Test
    void GivenGuestSessionWithoutActiveOrder_WhenLogout_ThenOrderIsNotCancelled() {
        SessionContext.setSessionToken("guest-token");
        SessionContext.setRole("Guest");
        when(orderService.getActiveOrder("guest-token")).thenReturn(null);
        when(sessionTokenService.endSession("guest-token")).thenReturn(true);

        AuthResult result = presenter.logout();

        assertTrue(result.success());
        verify(orderService, never()).cancelOrder(any());
        verify(sessionTokenService).endSession("guest-token");
    }

    @Test
    void GivenGuestSession_WhenEndSessionFails_ThenFailureMessageReturned() {
        SessionContext.setSessionToken("guest-token");
        SessionContext.setRole("Guest");
        when(orderService.getActiveOrder("guest-token")).thenReturn(null);
        when(sessionTokenService.endSession("guest-token")).thenReturn(false);

        AuthResult result = presenter.logout();

        assertFalse(result.success());
        assertEquals("Failed to exit guest session.", result.message());
    }

    @Test
    void GivenMemberSession_WhenCurrentSessionLabelRequested_ThenShowsMemberMode() {
        SessionContext.setSessionToken("member-token");
        SessionContext.setMemberId(UUID.randomUUID());
        SessionContext.setUsername("erin");
        SessionContext.setRole("Member");

        String label = presenter.currentSessionLabel();

        assertEquals("Current session: Member (erin)", label);
    }

    private MemberDto member(UUID memberId, String username) {
        return new MemberDto(
                memberId,
                username,
                username + "@example.com",
                "0500000000",
                LocalDate.of(2000, 1, 1)
        );
    }
}
