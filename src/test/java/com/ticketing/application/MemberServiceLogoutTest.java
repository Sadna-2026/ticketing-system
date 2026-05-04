package com.ticketing.application;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ticketing.domain.member.request.RegisterRequest;
import com.ticketing.domain.member.response.LogoutResponse;
import com.ticketing.domain.member.response.RegisterResponse;
import com.ticketing.infrastructure.InMemoryMemberRepository;
import com.ticketing.infrastructure.InMemorySessionTokenRepository;
import com.ticketing.infrastructure.Interface.IMemberRepository;
import com.ticketing.infrastructure.Interface.ISessionTokenRepository;
import com.ticketing.infrastructure.PasswordEncryptionUtils;

class MemberServiceLogoutTest {

    private IMemberRepository memberRepository;
    private PasswordEncryptionUtils passwordEncryptionUtils;
    private SessionTokenService sessionTokenService;
    private MemberService memberService;

    @BeforeEach
    void setUp() {
        String secret = Base64.getEncoder().encodeToString(
                "01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8)
        );

        ISessionTokenRepository tokenRepository = new InMemorySessionTokenRepository();

        memberRepository = new InMemoryMemberRepository();
        passwordEncryptionUtils = new PasswordEncryptionUtils();

        sessionTokenService = new SessionTokenService(
                secret,
                120,
                tokenRepository
        );

        memberService = new MemberService(
                memberRepository,
                passwordEncryptionUtils,
                sessionTokenService
        );
    }

    @Test
    @DisplayName("SuccessfulMemberLogout — member session is invalidated and visitor continues as guest")
    void GivenLoggedInMember_WhenLogout_ThenMemberTokenInvalidatedAndGuestTokenReturned() {
        // Arrange
        RegisterResponse registerResponse = registerMember("tamar", "tamar@example.com");
        String memberToken = registerResponse.sessionToken();

        assertTrue(sessionTokenService.isValid(memberToken));
        assertNotNull(sessionTokenService.extractMemberId(memberToken));

        // Act
        LogoutResponse logoutResponse = memberService.logout(memberToken);

        // Assert
        assertTrue(logoutResponse.success());
        assertEquals("Member logged out successfully.", logoutResponse.message());
        assertNotNull(logoutResponse.sessionToken());

        assertFalse(sessionTokenService.isValid(memberToken));

        assertTrue(sessionTokenService.isValid(logoutResponse.sessionToken()));
        assertNull(sessionTokenService.extractMemberId(logoutResponse.sessionToken()));
    }

    @Test
    @DisplayName("LogoutWithoutLogin — guest token is denied")
    void GivenGuestToken_WhenLogout_ThenSystemDeniesRequest() {
        // Arrange
        String guestToken = sessionTokenService.generateGuestToken();

        assertTrue(sessionTokenService.isValid(guestToken));
        assertNull(sessionTokenService.extractMemberId(guestToken));

        // Act
        LogoutResponse response = memberService.logout(guestToken);

        // Assert
        assertFalse(response.success());
        assertEquals("No authenticated member session exists.", response.message());
        assertNull(response.sessionToken());

        assertTrue(sessionTokenService.isValid(guestToken));
        assertNull(sessionTokenService.extractMemberId(guestToken));
    }

    @Test
    @DisplayName("LogoutWithoutLogin — already logged out member token is denied")
    void GivenAlreadyLoggedOutMemberToken_WhenLogoutAgain_ThenSystemDeniesRequest() {
        // Arrange
        RegisterResponse registerResponse = registerMember("tamar", "tamar@example.com");
        String memberToken = registerResponse.sessionToken();

        LogoutResponse firstLogout = memberService.logout(memberToken);
        assertTrue(firstLogout.success());

        // Act
        LogoutResponse secondLogout = memberService.logout(memberToken);

        // Assert
        assertFalse(secondLogout.success());
        assertEquals("No authenticated member session exists.", secondLogout.message());
        assertNull(secondLogout.sessionToken());
    }

    @Test
    @DisplayName("LogoutWithoutLogin — invalid token is denied")
    void GivenInvalidToken_WhenLogout_ThenSystemDeniesRequest() {
        // Arrange
        String invalidToken = "not.a.real.jwt";

        // Act
        LogoutResponse response = memberService.logout(invalidToken);

        // Assert
        assertFalse(response.success());
        assertEquals("No authenticated member session exists.", response.message());
        assertNull(response.sessionToken());
    }

    @Test
    @DisplayName("LogoutWithoutLogin — null token is denied")
    void GivenNullToken_WhenLogout_ThenSystemDeniesRequest() {
        // Arrange

        // Act
        LogoutResponse response = memberService.logout(null);

        // Assert
        assertFalse(response.success());
        assertEquals("No authenticated member session exists.", response.message());
        assertNull(response.sessionToken());
    }

    @Test
    @DisplayName("LogoutWithoutLogin — blank token is denied")
    void GivenBlankToken_WhenLogout_ThenSystemDeniesRequest() {
        // Arrange

        // Act
        LogoutResponse response = memberService.logout("   ");

        // Assert
        assertFalse(response.success());
        assertEquals("No authenticated member session exists.", response.message());
        assertNull(response.sessionToken());
    }

    private RegisterResponse registerMember(String username, String email) {
        String guestToken = sessionTokenService.generateGuestToken();

        RegisterRequest request = new RegisterRequest(
                username,
                email,
                "123456"
        );

        RegisterResponse response = memberService.register(request, guestToken);

        assertTrue(response.success());
        assertNotNull(response.sessionToken());

        return response;
    }
}
