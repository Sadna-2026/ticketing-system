package com.ticketing.presentation.vaadin.presenters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
        when(sessionTokenService.isValid(anyString())).thenReturn(true);
    }

    // ── startGuestSession ──────────────────────────────────────────────────────

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
        verify(sessionTokenService).isValid("member-token");
        verifyNoInteractions(memberService);
    }

    // ── login — auto-token behaviour ───────────────────────────────────────────

    @Test
    void GivenNoSession_WhenLogin_ThenAutoGeneratesGuestTokenAndSucceeds() {
        UUID sessionId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        MemberDto member = member(memberId, "alice");
        when(sessionTokenService.generateGuestToken()).thenReturn("auto-guest-token");
        when(sessionTokenService.extractSessionId("auto-guest-token")).thenReturn(UUID.randomUUID());
        when(memberService.login(any(LoginRequest.class), eq("auto-guest-token")))
                .thenReturn(LoginResponse.success(member, "member-token"));
        when(sessionTokenService.extractSessionId("member-token")).thenReturn(sessionId);

        AuthResult result = presenter.login("alice", "secret1");

        assertTrue(result.success());
        verify(sessionTokenService).generateGuestToken();
        verify(memberService).login(any(LoginRequest.class), eq("auto-guest-token"));
        assertEquals("member-token", SessionContext.getSessionToken());
        assertEquals(memberId, SessionContext.getMemberId());
    }

    @Test
    void GivenNoSession_WhenGuestTokenGenerationFailsOnLogin_ThenLoginReturnsFailure() {
        when(sessionTokenService.generateGuestToken())
                .thenThrow(new IllegalStateException("token service unavailable"));

        AuthResult result = presenter.login("alice", "secret1");

        assertFalse(result.success());
        assertEquals("Login failed. Please try again.", result.message());
        verifyNoInteractions(memberService);
        assertNull(SessionContext.getSessionToken());
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

    // ── register — auto-token behaviour ───────────────────────────────────────

    @Test
    void GivenNoSession_WhenRegister_ThenAutoGeneratesGuestTokenAndSucceeds() {
        UUID memberId = UUID.randomUUID();
        MemberDto member = member(memberId, "newuser");
        when(sessionTokenService.generateGuestToken()).thenReturn("auto-guest-token");
        when(sessionTokenService.extractSessionId("auto-guest-token")).thenReturn(UUID.randomUUID());
        when(memberService.register(any(RegisterRequest.class), eq("auto-guest-token")))
                .thenReturn(RegisterResponse.success(member, "member-token"));
        when(sessionTokenService.extractSessionId("member-token")).thenReturn(UUID.randomUUID());

        AuthResult result = presenter.register("newuser", "new@example.com", "pass", "", null);

        assertTrue(result.success());
        verify(sessionTokenService).generateGuestToken();
        verify(memberService).register(any(RegisterRequest.class), eq("auto-guest-token"));
        assertEquals("member-token", SessionContext.getSessionToken());
    }

    @Test
    void GivenNoSession_WhenGuestTokenGenerationFailsOnRegister_ThenRegisterReturnsFailure() {
        when(sessionTokenService.generateGuestToken())
                .thenThrow(new IllegalStateException("token service unavailable"));

        AuthResult result = presenter.register("user", "u@e.com", "pass", "", null);

        assertFalse(result.success());
        assertEquals("Registration failed. Please try again.", result.message());
        verifyNoInteractions(memberService);
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

    // ── adminLogin — auto-token behaviour ─────────────────────────────────────

    @Test
    void GivenNoSession_WhenAdminLogin_ThenAutoGeneratesGuestToken() {
        UUID memberId = UUID.randomUUID();
        MemberDto admin = member(memberId, "admin");
        when(sessionTokenService.generateGuestToken()).thenReturn("auto-guest-token");
        when(sessionTokenService.extractSessionId("auto-guest-token")).thenReturn(UUID.randomUUID());
        when(adminService.adminLogin(any(LoginRequest.class), eq("auto-guest-token")))
                .thenReturn(LoginResponse.success(admin, "admin-token"));
        when(sessionTokenService.extractSessionId("admin-token")).thenReturn(UUID.randomUUID());
        when(sessionTokenService.extractPermissions("admin-token"))
                .thenReturn(java.util.Set.of("SYSTEM_ADMIN"));

        AuthResult result = presenter.adminLogin("admin", "adminpass");

        assertTrue(result.success());
        verify(sessionTokenService).generateGuestToken();
        verify(adminService).adminLogin(any(LoginRequest.class), eq("auto-guest-token"));
    }

    // ── member session guards ──────────────────────────────────────────────────

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

    // ── logout — no-session result ─────────────────────────────────────────────

    @Test
    void GivenMemberSession_WhenLogout_ThenNoSessionStateIsStored() {
        SessionContext.setSessionToken("member-token");
        SessionContext.setMemberId(UUID.randomUUID());
        SessionContext.setUsername("carol");
        SessionContext.setRole("Member");
        when(sessionTokenService.isValid("member-token")).thenReturn(true);
        when(memberService.logout("member-token")).thenReturn(LogoutResponse.success("guest-token"));
        when(sessionTokenService.endSession("guest-token")).thenReturn(true);

        AuthResult result = presenter.logout();

        assertTrue(result.success());
        assertEquals("Member logged out successfully.", result.message());
        // No token remains — truly no-session
        assertNull(SessionContext.getSessionToken());
        assertNull(SessionContext.getMemberId());
        assertNull(SessionContext.getUsername());
        assertNull(SessionContext.getRole());
        verify(sessionTokenService).endSession("guest-token");
    }

    // ── error paths ────────────────────────────────────────────────────────────

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
        when(sessionTokenService.isValid("member-token")).thenReturn(true);
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
        when(sessionTokenService.isValid("guest-token")).thenReturn(true);
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
        when(sessionTokenService.isValid("guest-token")).thenReturn(true);
        when(orderService.getActiveOrder("guest-token")).thenReturn(null);
        when(sessionTokenService.endSession("guest-token")).thenReturn(true);

        AuthResult result = presenter.logout();

        assertTrue(result.success());
        verify(orderService, never()).cancelOrder(any());
        verify(sessionTokenService).endSession("guest-token");
    }

    @Test
    void GivenGuestSession_WhenEndSessionFails_ThenClientStateIsStillCleared() {
        SessionContext.setSessionToken("guest-token");
        SessionContext.setRole("Guest");
        when(sessionTokenService.isValid("guest-token")).thenReturn(true);
        when(orderService.getActiveOrder("guest-token")).thenReturn(null);
        when(sessionTokenService.endSession("guest-token")).thenReturn(false);

        AuthResult result = presenter.logout();

        assertTrue(result.success());
        assertEquals("Guest session ended.", result.message());
        assertNull(SessionContext.getSessionToken());
    }

    @Test
    void GivenExpiredMemberToken_WhenLogout_ThenClientStateIsClearedWithoutCallingService() {
        SessionContext.setSessionToken("expired-member-token");
        SessionContext.setMemberId(UUID.randomUUID());
        SessionContext.setUsername("owner");
        SessionContext.setRole("Member");
        when(sessionTokenService.isValid("expired-member-token")).thenReturn(false);

        AuthResult result = presenter.logout();

        assertTrue(result.success());
        assertEquals("Your session had already ended. You have been signed out.", result.message());
        assertNull(SessionContext.getSessionToken());
        assertNull(SessionContext.getMemberId());
        verifyNoInteractions(memberService);
    }

    @Test
    void GivenExpiredMemberToken_WhenServiceRejectsLogout_ThenClientStateIsCleared() {
        SessionContext.setSessionToken("member-token");
        SessionContext.setMemberId(UUID.randomUUID());
        SessionContext.setRole("Member");
        when(sessionTokenService.isValid("member-token")).thenReturn(true);
        when(memberService.logout("member-token"))
                .thenReturn(LogoutResponse.failure("No authenticated member session exists."));

        AuthResult result = presenter.logout();

        assertTrue(result.success());
        assertEquals("Your session had already ended. You have been signed out.", result.message());
        assertNull(SessionContext.getSessionToken());
        assertNull(SessionContext.getMemberId());
    }

    @Test
    void GivenExpiredStoredToken_WhenReconcileStoredSession_ThenClientStateIsCleared() {
        SessionContext.setSessionToken("stale-token");
        SessionContext.setMemberId(UUID.randomUUID());
        SessionContext.setUsername("owner");
        when(sessionTokenService.isValid("stale-token")).thenReturn(false);

        assertTrue(presenter.reconcileStoredSession());
        assertNull(SessionContext.getSessionToken());
        assertNull(SessionContext.getMemberId());
    }

    @Test
    void GivenStaleMemberContext_WhenLogin_ThenUserCanSignInAgain() {
        UUID memberId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        SessionContext.setSessionToken("stale-token");
        SessionContext.setMemberId(memberId);
        SessionContext.setUsername("owner");
        SessionContext.setRole("Member");
        when(sessionTokenService.isValid("stale-token")).thenReturn(false);
        when(sessionTokenService.generateGuestToken()).thenReturn("fresh-guest");
        when(sessionTokenService.extractSessionId("fresh-guest")).thenReturn(sessionId);
        when(memberService.login(any(LoginRequest.class), eq("fresh-guest")))
                .thenReturn(LoginResponse.success(member(memberId, "owner"), "member-token"));
        when(sessionTokenService.extractSessionId("member-token")).thenReturn(sessionId);

        AuthResult result = presenter.login("owner", "owner123");

        assertTrue(result.success());
        assertEquals("member-token", SessionContext.getSessionToken());
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
