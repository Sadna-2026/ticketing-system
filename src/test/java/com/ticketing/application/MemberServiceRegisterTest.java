package com.ticketing.application;

import java.nio.charset.StandardCharsets;
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

import com.ticketing.domain.member.Member;
import com.ticketing.domain.member.request.RegisterRequest;
import com.ticketing.domain.member.response.RegisterResponse;
import com.ticketing.infrastructure.InMemoryMemberRepository;
import com.ticketing.infrastructure.InMemorySessionTokenRepository;
import com.ticketing.domain.member.IMemberRepository;
import com.ticketing.infrastructure.Interface.ISessionTokenRepository;
import com.ticketing.infrastructure.PasswordEncryptionUtils;

class MemberServiceRegisterTest {

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
    @DisplayName("SuccessfulRegistration — guest with unique details is registered and auto-logged-in")
    void GivenGuestWithUniqueRegistrationDetails_WhenRegister_ThenMemberCreatedAndMemberTokenReturned() {
        // Arrange
        String guestToken = sessionTokenService.generateGuestToken();
        UUID originalSessionId = sessionTokenService.extractSessionId(guestToken);

        RegisterRequest request = new RegisterRequest(
                "tamar",
                "tamar@example.com",
                "123456"
        );

        // Act
        RegisterResponse response = memberService.register(request, guestToken);

        // Assert
        assertTrue(response.success());
        assertEquals("Member registered and logged in successfully.", response.message());
        assertNotNull(response.member());
        assertEquals("tamar", response.member().username());
        assertEquals("tamar@example.com", response.member().email());
        assertNotNull(response.sessionToken());

        assertFalse(sessionTokenService.isValid(guestToken));
        assertTrue(sessionTokenService.isValid(response.sessionToken()));

        assertEquals(
                originalSessionId,
                sessionTokenService.extractSessionId(response.sessionToken())
        );

        assertEquals(
                response.member().memberId(),
                sessionTokenService.extractMemberId(response.sessionToken())
        );
    }

    @Test
    @DisplayName("DuplicateRegistrationDetails — duplicate username is denied")
    void GivenExistingMemberWithSameUsername_WhenRegister_ThenRegistrationDeniedAndGuestStillConnected() {
        // Arrange
        String firstGuestToken = sessionTokenService.generateGuestToken();

        RegisterRequest firstRequest = new RegisterRequest(
                "tamar",
                "tamar@example.com",
                "123456"
        );

        RegisterResponse firstResponse = memberService.register(firstRequest, firstGuestToken);
        assertTrue(firstResponse.success());

        String secondGuestToken = sessionTokenService.generateGuestToken();

        RegisterRequest duplicateUsernameRequest = new RegisterRequest(
                "tamar",
                "other@example.com",
                "123456"
        );

        // Act
        RegisterResponse secondResponse =
                memberService.register(duplicateUsernameRequest, secondGuestToken);

        // Assert
        assertFalse(secondResponse.success());
        assertEquals("Username already in use.", secondResponse.message());
        assertNull(secondResponse.member());
        assertNull(secondResponse.sessionToken());

        assertTrue(sessionTokenService.isValid(secondGuestToken));
        assertNull(sessionTokenService.extractMemberId(secondGuestToken));
    }

    @Test
    @DisplayName("DuplicateRegistrationDetails — duplicate email is denied")
    void GivenExistingMemberWithSameEmail_WhenRegister_ThenRegistrationDeniedAndGuestStillConnected() {
        // Arrange
        String firstGuestToken = sessionTokenService.generateGuestToken();

        RegisterRequest firstRequest = new RegisterRequest(
                "tamar",
                "tamar@example.com",
                "123456"
        );

        RegisterResponse firstResponse = memberService.register(firstRequest, firstGuestToken);
        assertTrue(firstResponse.success());

        String secondGuestToken = sessionTokenService.generateGuestToken();

        RegisterRequest duplicateEmailRequest = new RegisterRequest(
                "other",
                "tamar@example.com",
                "123456"
        );

        // Act
        RegisterResponse secondResponse =
                memberService.register(duplicateEmailRequest, secondGuestToken);

        // Assert
        assertFalse(secondResponse.success());
        assertEquals("Email already in use.", secondResponse.message());
        assertNull(secondResponse.member());
        assertNull(secondResponse.sessionToken());

        assertTrue(sessionTokenService.isValid(secondGuestToken));
        assertNull(sessionTokenService.extractMemberId(secondGuestToken));
    }

    @Test
    @DisplayName("InvalidFields — blank username, invalid email, and short password are denied")
    void GivenInvalidRegistrationFields_WhenRegister_ThenRegistrationDeniedAndGuestStillConnected() {
        // Arrange
        String guestToken = sessionTokenService.generateGuestToken();

        RegisterRequest invalidRequest = new RegisterRequest(
                "",
                "bad-email",
                "123"
        );

        // Act
        RegisterResponse response = memberService.register(invalidRequest, guestToken);

        // Assert
        assertFalse(response.success());
        assertEquals("Invalid registration details.", response.message());
        assertNull(response.member());
        assertNull(response.sessionToken());

        assertTrue(sessionTokenService.isValid(guestToken));
        assertNull(sessionTokenService.extractMemberId(guestToken));
    }

    @Test
    @DisplayName("InvalidFields — null request is denied")
    void GivenNullRegisterRequest_WhenRegister_ThenRegistrationDeniedAndGuestStillConnected() {
        // Arrange
        String guestToken = sessionTokenService.generateGuestToken();

        // Act
        RegisterResponse response = memberService.register(null, guestToken);

        // Assert
        assertFalse(response.success());
        assertEquals("Invalid registration details.", response.message());
        assertNull(response.member());
        assertNull(response.sessionToken());

        assertTrue(sessionTokenService.isValid(guestToken));
        assertNull(sessionTokenService.extractMemberId(guestToken));
    }

    @Test
    @DisplayName("InvalidToken — invalid token is denied")
    void GivenInvalidSessionToken_WhenRegister_ThenRegistrationDenied() {
        // Arrange
        String invalidToken = "not.a.real.jwt";

        RegisterRequest request = new RegisterRequest(
                "tamar",
                "tamar@example.com",
                "123456"
        );

        // Act
        RegisterResponse response = memberService.register(request, invalidToken);

        // Assert
        assertFalse(response.success());
        assertEquals("Invalid session token.", response.message());
        assertNull(response.member());
        assertNull(response.sessionToken());
    }

    @Test
    @DisplayName("InvalidToken — null token is denied")
    void GivenNullSessionToken_WhenRegister_ThenRegistrationDenied() {
        // Arrange
        RegisterRequest request = new RegisterRequest(
                "tamar",
                "tamar@example.com",
                "123456"
        );

        // Act
        RegisterResponse response = memberService.register(request, null);

        // Assert
        assertFalse(response.success());
        assertEquals("Invalid session token.", response.message());
        assertNull(response.member());
        assertNull(response.sessionToken());
    }

    @Test
    @DisplayName("InvalidToken — blank token is denied")
    void GivenBlankSessionToken_WhenRegister_ThenRegistrationDenied() {
        // Arrange
        RegisterRequest request = new RegisterRequest(
                "tamar",
                "tamar@example.com",
                "123456"
        );

        // Act
        RegisterResponse response = memberService.register(request, "   ");

        // Assert
        assertFalse(response.success());
        assertEquals("Invalid session token.", response.message());
        assertNull(response.member());
        assertNull(response.sessionToken());
    }

    @Test
    @DisplayName("Only guests can register — member token is denied")
    void GivenMemberSessionToken_WhenRegister_ThenRegistrationDenied() {
        // Arrange
        String guestToken = sessionTokenService.generateGuestToken();

        RegisterRequest firstRequest = new RegisterRequest(
                "tamar",
                "tamar@example.com",
                "123456"
        );

        RegisterResponse firstResponse = memberService.register(firstRequest, guestToken);
        assertTrue(firstResponse.success());
        assertNotNull(firstResponse.sessionToken());

        RegisterRequest secondRequest = new RegisterRequest(
                "other",
                "other@example.com",
                "123456"
        );

        // Act
        RegisterResponse secondResponse =
                memberService.register(secondRequest, firstResponse.sessionToken());

        // Assert
        assertFalse(secondResponse.success());
        assertEquals("Only guests can register.", secondResponse.message());
        assertNull(secondResponse.member());
        assertNull(secondResponse.sessionToken());
    }

    @Test
    @DisplayName("PasswordSecurity — password is stored as BCrypt hash, not plaintext")
    void GivenSuccessfulRegistration_WhenInspectStoredMember_ThenPasswordIsHashedNotPlaintext() {
        // Arrange
        String guestToken = sessionTokenService.generateGuestToken();
        String rawPassword = "123456";

        RegisterRequest request = new RegisterRequest(
                "tamar",
                "tamar@example.com",
                rawPassword
        );

        // Act
        RegisterResponse response = memberService.register(request, guestToken);

        // Assert
        assertTrue(response.success());

        Member savedMember = memberRepository.findByUsername("tamar")
                .orElseThrow();

        assertNotEquals(rawPassword, savedMember.getEncryptedPassword());
        assertTrue(passwordEncryptionUtils.matches(
                rawPassword,
                savedMember.getEncryptedPassword()
        ));
    }
}