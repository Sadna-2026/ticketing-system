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

    private static final String GUEST_ROLE = "Guest";
    private static final String MEMBER_ROLE = "Member";
    private static final String ADMIN_ROLE = "Admin";
    private static final String START_GUEST_SESSION_MESSAGE = "Start a guest session before logging in or registering.";
    private static final String ALREADY_MEMBER_SESSION_MESSAGE = "You are already logged in. Log out before switching accounts.";
    private static final String NO_MEMBER_SESSION_MESSAGE = "No authenticated member session exists.";
    private static final String NOT_AN_ADMIN_MESSAGE = "These credentials are not authorized for admin access.";

    private final MemberService memberService;
    private final AdminService adminService;
    private final ISessionTokenService sessionTokenService;
    private final OrderService orderService;

    public AuthPresenter(MemberService memberService, AdminService adminService, ISessionTokenService sessionTokenService, OrderService orderService) {
        this.memberService = memberService;
        this.adminService = adminService;
        this.sessionTokenService = sessionTokenService;
        this.orderService = orderService;
    }

    public AuthResult startGuestSession() {
        if (SessionContext.isLoggedInMember()) {
            return AuthResult.failure(ALREADY_MEMBER_SESSION_MESSAGE);
        }

        try {
            String guestToken = sessionTokenService.generateGuestToken();
            storeGuestSession(guestToken);
            return AuthResult.success("Guest session started.");
        } catch (RuntimeException ex) {
            return safeFailure("Could not start guest session.", ex);
        }
    }

    public AuthResult login(String username, String password) {
        String guestToken = SessionContext.getSessionToken();
        if (guestToken == null || guestToken.isBlank()) {
            return AuthResult.failure(START_GUEST_SESSION_MESSAGE);
        }
        if (SessionContext.isLoggedInMember()) {
            return AuthResult.failure(ALREADY_MEMBER_SESSION_MESSAGE);
        }

        try {
            LoginResponse response = memberService.login(new LoginRequest(username, password), guestToken);
            if (!response.success()) {
                return AuthResult.failure(response.message());
            }

            storeMemberSession(response.sessionToken(), response.member(), MEMBER_ROLE);
            return AuthResult.success(response.message());
        } catch (RuntimeException ex) {
            return safeFailure("Login failed. Please try again.", ex);
        }
    }

    /**
     * Logs in using the admin store. The returned session token carries the
     * SYSTEM_ADMIN permission, which the UI uses to expose admin-only screens.
     * If the issued token does not actually carry SYSTEM_ADMIN, the session is
     * rolled back to a fresh guest session and the attempt is reported as
     * "not authorized for admin access".
     */
    public AuthResult adminLogin(String username, String password) {
        String guestToken = SessionContext.getSessionToken();
        if (guestToken == null || guestToken.isBlank()) {
            return AuthResult.failure(START_GUEST_SESSION_MESSAGE);
        }
        if (SessionContext.isLoggedInMember()) {
            return AuthResult.failure(ALREADY_MEMBER_SESSION_MESSAGE);
        }

        try {
            LoginResponse response = adminService.adminLogin(new LoginRequest(username, password), guestToken);
            if (!response.success()) {
                return AuthResult.failure(response.message());
            }

            storeMemberSession(response.sessionToken(), response.member(), ADMIN_ROLE);

            if (!SessionContext.isSystemAdmin()) {
                // Defensive: if for any reason the issued token did not carry the admin
                // permission, do not leave the user with an over-privileged appearance.
                rollbackToGuest();
                return AuthResult.failure(NOT_AN_ADMIN_MESSAGE);
            }

            return AuthResult.success("Admin logged in successfully.");
        } catch (RuntimeException ex) {
            return safeFailure("Admin login failed. Please try again.", ex);
        }
    }

    public AuthResult register(
            String username,
            String email,
            String password,
            String phoneNumber,
            LocalDate dateOfBirth
    ) {
        String guestToken = SessionContext.getSessionToken();
        if (guestToken == null || guestToken.isBlank()) {
            return AuthResult.failure(START_GUEST_SESSION_MESSAGE);
        }
        if (SessionContext.isLoggedInMember()) {
            return AuthResult.failure(ALREADY_MEMBER_SESSION_MESSAGE);
        }

        try {
            RegisterRequest request = new RegisterRequest(username, email, password, phoneNumber, dateOfBirth);
            RegisterResponse response = memberService.register(request, guestToken);
            if (!response.success()) {
                return AuthResult.failure(response.message());
            }

            storeMemberSession(response.sessionToken(), response.member(), MEMBER_ROLE);
            return AuthResult.success(response.message());
        } catch (RuntimeException ex) {
            return safeFailure("Registration failed. Please try again.", ex);
        }
    }

    /**
     * Terminates the current session for both Members and Guests.
     * <p>
     * - For Members: Logs the member out, invalidating their token, and transitions them back to a guest session.
     * - For Guests: Cancels any active orders to immediately release reserved materials, then terminates the guest session.
     * 
     * @return AuthResult containing the success or failure message.
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

            storeGuestSession(response.sessionToken());
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

    private void storeGuestSession(String guestToken) {
        SessionContext.clear();
        SessionContext.setSessionToken(guestToken);
        SessionContext.setSessionId(extractSessionId(guestToken));
        SessionContext.setRole(GUEST_ROLE);
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

    private void rollbackToGuest() {
        String currentToken = SessionContext.getSessionToken();
        if (currentToken != null && !currentToken.isBlank()) {
            try {
                sessionTokenService.revokeToken(currentToken);
            } catch (RuntimeException ignored) {
                // best-effort revocation; UI session is cleared regardless below.
            }
        }
        try {
            storeGuestSession(sessionTokenService.generateGuestToken());
        } catch (RuntimeException ignored) {
            SessionContext.clear();
        }
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
