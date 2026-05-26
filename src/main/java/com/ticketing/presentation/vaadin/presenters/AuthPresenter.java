package com.ticketing.presentation.vaadin.presenters;

import java.time.LocalDate;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.ticketing.application.auth.ISessionTokenService;
import com.ticketing.application.services.MemberService;
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
    private static final String START_GUEST_SESSION_MESSAGE = "Start a guest session before logging in or registering.";
    private static final String ALREADY_MEMBER_SESSION_MESSAGE = "You are already logged in as a member. Log out before switching accounts.";
    private static final String NO_MEMBER_SESSION_MESSAGE = "No authenticated member session exists.";

    private final MemberService memberService;
    private final ISessionTokenService sessionTokenService;

    public AuthPresenter(MemberService memberService, ISessionTokenService sessionTokenService) {
        this.memberService = memberService;
        this.sessionTokenService = sessionTokenService;
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

            storeMemberSession(response.sessionToken(), response.member());
            return AuthResult.success(response.message());
        } catch (RuntimeException ex) {
            return safeFailure("Login failed. Please try again.", ex);
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

            storeMemberSession(response.sessionToken(), response.member());
            return AuthResult.success(response.message());
        } catch (RuntimeException ex) {
            return safeFailure("Registration failed. Please try again.", ex);
        }
    }

    public AuthResult logout() {
        String sessionToken = SessionContext.getSessionToken();
        if (sessionToken == null || sessionToken.isBlank() || !SessionContext.isLoggedInMember()) {
            return AuthResult.failure(NO_MEMBER_SESSION_MESSAGE);
        }

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

    public String currentSessionLabel() {
        if (SessionContext.isLoggedInMember()) {
            String username = SessionContext.getUsername();
            if (username != null && !username.isBlank()) {
                return "Current session: Member (" + username + ")";
            }
            return "Current session: Member";
        }

        if (SessionContext.hasSessionToken()) {
            return "Current session: Guest";
        }

        return "Current session: none";
    }

    private void storeGuestSession(String guestToken) {
        SessionContext.clear();
        SessionContext.setSessionToken(guestToken);
        SessionContext.setSessionId(extractSessionId(guestToken));
        SessionContext.setRole(GUEST_ROLE);
    }

    private void storeMemberSession(String memberToken, MemberDto member) {
        SessionContext.clear();
        SessionContext.setSessionToken(memberToken);
        SessionContext.setSessionId(extractSessionId(memberToken));
        if (member != null) {
            SessionContext.setMemberId(member.memberId());
            SessionContext.setUsername(member.username());
        }
        SessionContext.setRole(MEMBER_ROLE);
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
