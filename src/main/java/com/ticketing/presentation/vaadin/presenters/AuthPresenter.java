package com.ticketing.presentation.vaadin.presenters;

import java.time.LocalDate;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.ticketing.application.auth.ISessionTokenService;
import com.ticketing.application.services.AdminService;
import com.ticketing.application.services.MemberService;
import com.ticketing.application.services.OrderService;
import com.ticketing.application.services.SystemAnalyticsCollector;
import com.ticketing.domain.member.MemberDto;
import com.ticketing.domain.member.request.LoginRequest;
import com.ticketing.domain.member.request.RegisterRequest;
import com.ticketing.domain.member.response.LoginResponse;
import com.ticketing.domain.member.response.LogoutResponse;
import com.ticketing.domain.member.response.RegisterResponse;
import com.ticketing.presentation.vaadin.util.SessionContext;

@Component
public class AuthPresenter {

    private static final Logger logger = LoggerFactory.getLogger(AuthPresenter.class);

    private static final String ALREADY_MEMBER_SESSION_MESSAGE = "You are already logged in. Log out before switching accounts.";
    private static final String NO_MEMBER_SESSION_MESSAGE = "No authenticated member session exists.";
    private static final String NOT_AN_ADMIN_MESSAGE = "These credentials are not authorized for admin access.";

    private final MemberService memberService;
    private final AdminService adminService;
    private final ISessionTokenService sessionTokenService;
    private final OrderService orderService;
    private final SystemAnalyticsCollector analyticsCollector;

    public AuthPresenter(MemberService memberService, AdminService adminService,
            ISessionTokenService sessionTokenService, OrderService orderService) {
        this(memberService, adminService, sessionTokenService, orderService, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public AuthPresenter(MemberService memberService, AdminService adminService,
            ISessionTokenService sessionTokenService, OrderService orderService,
            @org.springframework.beans.factory.annotation.Autowired(required = false) SystemAnalyticsCollector analyticsCollector) {
        this.memberService = memberService;
        this.adminService = adminService;
        this.sessionTokenService = sessionTokenService;
        this.orderService = orderService;
        this.analyticsCollector = analyticsCollector;
    }

    public AuthResult startGuestSession() {
        if (SessionContext.isLoggedInMember()) {
            return AuthResult.failure(ALREADY_MEMBER_SESSION_MESSAGE);
        }

        try {
            String guestToken = sessionTokenService.generateGuestToken();
            storeGuestSession(guestToken);
            recordVisitorEnter();
            return AuthResult.success("Guest session started.");
        } catch (RuntimeException ex) {
            return safeFailure("Could not start guest session.", ex);
        }
    }

    /**
     * Logs in a member. If no session token exists yet, a guest token is generated
     * automatically — the user does not need to click "Enter as guest" first.
     */
    public AuthResult login(String username, String password) {
        if (SessionContext.isLoggedInMember()) {
            return AuthResult.failure(ALREADY_MEMBER_SESSION_MESSAGE);
        }

        boolean createdNewGuestToken = SessionContext.getSessionToken() == null || SessionContext.getSessionToken().isBlank();

        try {
            String guestToken = ensureGuestToken();
            LoginResponse response = memberService.login(new LoginRequest(username, password), guestToken);
            if (!response.success()) {
                if (createdNewGuestToken) {
                    rollbackToNoSession();
                }
                return AuthResult.failure(response.message());
            }

            storeMemberSession(response.sessionToken(), response.member(), "Member");
            return AuthResult.success(response.message());
        } catch (RuntimeException ex) {
            if (createdNewGuestToken) {
                rollbackToNoSession();
            }
            return safeFailure("Login failed. Please try again.", ex);
        }
    }

    /**
     * Logs in using the admin store. If no session token exists yet, a guest token
     * is generated automatically. The returned token must carry SYSTEM_ADMIN; if it
     * does not, the session is rolled back to no-session state.
     */
    public AuthResult adminLogin(String username, String password) {
        if (SessionContext.isLoggedInMember()) {
            return AuthResult.failure(ALREADY_MEMBER_SESSION_MESSAGE);
        }

        boolean createdNewGuestToken = SessionContext.getSessionToken() == null || SessionContext.getSessionToken().isBlank();

        try {
            String guestToken = ensureGuestToken();
            LoginResponse response = adminService.adminLogin(new LoginRequest(username, password), guestToken);
            if (!response.success()) {
                if (createdNewGuestToken) {
                    rollbackToNoSession();
                }
                return AuthResult.failure(response.message());
            }

            storeMemberSession(response.sessionToken(), response.member(), "Admin");

            if (!SessionContext.isSystemAdmin()) {
                // Defensive: issued token did not carry the admin permission.
                rollbackToNoSession();
                return AuthResult.failure(NOT_AN_ADMIN_MESSAGE);
            }

            return AuthResult.success("Admin logged in successfully.");
        } catch (RuntimeException ex) {
            if (createdNewGuestToken) {
                rollbackToNoSession();
            }
            return safeFailure("Admin login failed. Please try again.", ex);
        }
    }

    /**
     * Registers a new member. If no session token exists yet, a guest token is
     * generated automatically — the user does not need to click "Enter as guest" first.
     */
    public AuthResult register(
            String username,
            String email,
            String password,
            String phoneNumber,
            LocalDate dateOfBirth
    ) {
        if (SessionContext.isLoggedInMember()) {
            return AuthResult.failure(ALREADY_MEMBER_SESSION_MESSAGE);
        }

        boolean createdNewGuestToken = SessionContext.getSessionToken() == null || SessionContext.getSessionToken().isBlank();

        try {
            String guestToken = ensureGuestToken();
            RegisterRequest request = new RegisterRequest(username, email, password, phoneNumber, dateOfBirth);
            RegisterResponse response = memberService.register(request, guestToken);
            if (!response.success()) {
                if (createdNewGuestToken) {
                    rollbackToNoSession();
                }
                return AuthResult.failure(response.message());
            }

            storeMemberSession(response.sessionToken(), response.member(), "Member");
            return AuthResult.success(response.message());
        } catch (RuntimeException ex) {
            if (createdNewGuestToken) {
                rollbackToNoSession();
            }
            return safeFailure("Registration failed. Please try again.", ex);
        }
    }

    /**
     * Terminates the current session.
     * <p>
     * - For Members: logs out via the service (which returns a new guest token),
     *   then immediately ends that guest token as well, leaving the user in a
     *   clean no-session state.
     * - For Guests: cancels any active order, then ends the guest session.
     * <p>
     * In both cases the result is a complete no-session state — no stale token remains.
     */
    public AuthResult logout() {
        String sessionToken = SessionContext.getSessionToken();
        if (sessionToken == null || sessionToken.isBlank()) {
            return AuthResult.failure("No active session exists.");
        }

        if (SessionContext.isLoggedInMember()) {
            return logoutMember(sessionToken);
        } else if (SessionContext.currentUiState().guest()) {
            return exitGuestSession(sessionToken);
        } else {
            return AuthResult.failure("Cannot log out from the current state.");
        }
    }

    private AuthResult logoutMember(String sessionToken) {
        try {
            LogoutResponse response = memberService.logout(sessionToken);
            if (!response.success()) {
                return AuthResult.failure(response.message());
            }

            // The service returns a fresh guest token. We end it immediately so the
            // user lands in a clean no-session state rather than a guest state.
            String returnedGuestToken = response.sessionToken();
            if (returnedGuestToken != null && !returnedGuestToken.isBlank()) {
                try {
                    sessionTokenService.endSession(returnedGuestToken);
                } catch (RuntimeException ignored) {
                    // best-effort; session context is cleared regardless below.
                }
            }

            SessionContext.clear();
            return AuthResult.success(response.message());
        } catch (RuntimeException ex) {
            return safeFailure("Logout failed. Please try again.", ex);
        }
    }

    private AuthResult exitGuestSession(String sessionToken) {
        try {
            if (orderService.getActiveOrder(sessionToken) != null) {
                orderService.cancelOrder(sessionToken);
            }
            boolean success = sessionTokenService.endSession(sessionToken);
            if (!success) {
                return AuthResult.failure("Failed to exit guest session.");
            }
            recordVisitorExit();
            SessionContext.clear();
            return AuthResult.success("Guest session ended.");
        } catch (RuntimeException ex) {
            return safeFailure("Could not exit guest session.", ex);
        }
    }

    public String currentSessionLabel() {
        return SessionContext.currentSessionLabel();
    }

    public SessionContext.UiState currentSessionState() {
        return SessionContext.currentUiState();
    }

    /**
     * Returns an existing guest token from the session, or silently generates a new
     * one if no token is present. This allows login, register, and admin-login to
     * work directly without requiring the user to click "Enter as guest" first.
     *
     * @throws RuntimeException if token generation fails (caller should catch and report)
     */
    private String ensureGuestToken() {
        String existing = SessionContext.getSessionToken();
        if (existing != null && !existing.isBlank()) {
            return existing;
        }
        String newToken = sessionTokenService.generateGuestToken();
        storeGuestSession(newToken);
        recordVisitorEnter();
        return newToken;
    }

    private void recordVisitorEnter() {
        if (analyticsCollector != null) {
            analyticsCollector.recordVisitorEnter();
        }
    }

    private void recordVisitorExit() {
        if (analyticsCollector != null) {
            analyticsCollector.recordVisitorExit();
        }
    }

    private void storeGuestSession(String guestToken) {
        SessionContext.clear();
        SessionContext.setSessionToken(guestToken);
        SessionContext.setSessionId(extractSessionId(guestToken));
        SessionContext.setRole("Guest");
        SessionContext.setPermissions(null);
    }

    private void storeMemberSession(String memberToken, MemberDto member, String roleLabel) {
        SessionContext.clear();
        SessionContext.setSessionToken(memberToken);
        SessionContext.setSessionId(extractSessionId(memberToken));
        SessionContext.setPermissions(sessionTokenService.extractPermissions(memberToken));
        if (member != null) {
            SessionContext.setMemberId(member.memberId());
            SessionContext.setUsername(member.username());
        }
        SessionContext.setRole(roleLabel);
    }

    private void rollbackToNoSession() {
        String currentToken = SessionContext.getSessionToken();
        if (currentToken != null && !currentToken.isBlank()) {
            try {
                sessionTokenService.revokeToken(currentToken);
            } catch (RuntimeException ignored) {
                // best-effort revocation; UI session is cleared regardless below.
            }
        }
        SessionContext.clear();
    }

    private UUID extractSessionId(String token) {
        return token == null || token.isBlank() ? null : sessionTokenService.extractSessionId(token);
    }

    private AuthResult safeFailure(String message, RuntimeException ex) {
        logger.warn(message, ex);
        return AuthResult.failure(message);
    }

    public record AuthResult(boolean success, String message) {

        public static AuthResult success(String message) {
            return new AuthResult(true, message);
        }

        public static AuthResult failure(String message) {
            return new AuthResult(false, message);
        }
    }
}
