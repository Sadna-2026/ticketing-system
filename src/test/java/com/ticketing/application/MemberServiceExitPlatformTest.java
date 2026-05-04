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
import com.ticketing.domain.member.response.MemberExitResponse;
import com.ticketing.domain.member.response.RegisterResponse;
import com.ticketing.infrastructure.InMemoryMemberRepository;
import com.ticketing.infrastructure.InMemorySessionTokenRepository;
import com.ticketing.infrastructure.Interface.IMemberRepository;
import com.ticketing.infrastructure.Interface.ISessionTokenRepository;
import com.ticketing.infrastructure.PasswordEncryptionUtils;

class MemberServiceExitPlatformTest {

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
    @DisplayName("SuccessfulMemberExit — member session token is invalidated")
    void GivenLoggedInMember_WhenExitPlatform_ThenMemberSessionTokenInvalidated() {
        // Arrange
        RegisterResponse registerResponse = registerMember("tamar", "tamar@example.com");
        String memberToken = registerResponse.sessionToken();

        assertTrue(sessionTokenService.isValid(memberToken));
        assertNotNull(sessionTokenService.extractMemberId(memberToken));

        // Act
        MemberExitResponse response = memberService.exitPlatform(memberToken);

        // Assert
        assertTrue(response.success());
        assertEquals("Member null exited platform successfully.", response.message());

        assertFalse(sessionTokenService.isValid(memberToken));
    }

    @Test
    @DisplayName("ExitWithoutLogin — guest token is denied and remains valid")
    void GivenGuestToken_WhenExitPlatform_ThenSystemDeniesRequestAndGuestTokenStaysValid() {
        // Arrange
        String guestToken = sessionTokenService.generateGuestToken();

        assertTrue(sessionTokenService.isValid(guestToken));
        assertNull(sessionTokenService.extractMemberId(guestToken));

        // Act
        MemberExitResponse response = memberService.exitPlatform(guestToken);

        // Assert
        assertFalse(response.success());
        assertEquals("No authenticated member session exists.", response.message());

        assertTrue(sessionTokenService.isValid(guestToken));
        assertNull(sessionTokenService.extractMemberId(guestToken));
    }

    @Test
    @DisplayName("ExitWithoutLogin — invalid token is denied")
    void GivenInvalidToken_WhenExitPlatform_ThenSystemDeniesRequest() {
        // Arrange
        String invalidToken = "not.a.real.jwt";

        // Act
        MemberExitResponse response = memberService.exitPlatform(invalidToken);

        // Assert
        assertFalse(response.success());
        assertEquals("No authenticated member session exists.", response.message());
    }

    @Test
    @DisplayName("ExitWithoutLogin — null token is denied")
    void GivenNullToken_WhenExitPlatform_ThenSystemDeniesRequest() {
        // Arrange

        // Act
        MemberExitResponse response = memberService.exitPlatform(null);

        // Assert
        assertFalse(response.success());
        assertEquals("No authenticated member session exists.", response.message());
    }

    @Test
    @DisplayName("ExitWithoutLogin — blank token is denied")
    void GivenBlankToken_WhenExitPlatform_ThenSystemDeniesRequest() {
        // Arrange

        // Act
        MemberExitResponse response = memberService.exitPlatform("   ");

        // Assert
        assertFalse(response.success());
        assertEquals("No authenticated member session exists.", response.message());
    }

    @Test
    @DisplayName("ExitWithoutLogin — already exited member token is denied")
    void GivenAlreadyExitedMemberToken_WhenExitPlatformAgain_ThenSystemDeniesRequest() {
        // Arrange
        RegisterResponse registerResponse = registerMember("tamar", "tamar@example.com");
        String memberToken = registerResponse.sessionToken();

        MemberExitResponse firstExit = memberService.exitPlatform(memberToken);

        assertTrue(firstExit.success());
        assertFalse(sessionTokenService.isValid(memberToken));

        // Act
        MemberExitResponse secondExit = memberService.exitPlatform(memberToken);

        // Assert
        assertFalse(secondExit.success());
        assertEquals("No authenticated member session exists.", secondExit.message());
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
