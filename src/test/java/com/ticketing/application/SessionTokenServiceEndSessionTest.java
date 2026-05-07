package com.ticketing.application;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ticketing.infrastructure.InMemorySessionTokenRepository;
import com.ticketing.infrastructure.Interface.ISessionTokenRepository;

class SessionTokenServiceEndSessionTest {

    private SessionTokenService sessionTokenService;

    @BeforeEach
    void setUp() {
        String secret = Base64.getEncoder().encodeToString(
                "01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8)
        );

        ISessionTokenRepository tokenRepository =
                new InMemorySessionTokenRepository();

        sessionTokenService = new SessionTokenService(
                secret,
                120,
                tokenRepository
        );
    }

    @Test
    @DisplayName("GuestExit — valid guest token is invalidated")
    void GivenValidGuestToken_WhenEndSession_ThenGuestTokenInvalidated() {
        // Arrange
        String guestToken = sessionTokenService.generateGuestToken();

        assertTrue(sessionTokenService.isValid(guestToken));
        assertNull(sessionTokenService.extractMemberId(guestToken));

        // Act
        boolean ended = sessionTokenService.endSession(guestToken);

        // Assert
        assertTrue(ended);
        assertFalse(sessionTokenService.isValid(guestToken));
    }

    @Test
    @DisplayName("MemberExit — valid member token is invalidated")
    void GivenValidMemberToken_WhenEndSession_ThenMemberTokenInvalidated() {
        // Arrange
        String guestToken = sessionTokenService.generateGuestToken();
        UUID sessionId = sessionTokenService.extractSessionId(guestToken);
        UUID memberId = UUID.randomUUID();

        String memberToken = sessionTokenService.generateMemberToken(
                sessionId,
                memberId,
                Set.of()
        );

        assertTrue(sessionTokenService.isValid(memberToken));
        assertEquals(memberId, sessionTokenService.extractMemberId(memberToken));

        // Act
        boolean ended = sessionTokenService.endSession(memberToken);

        // Assert
        assertTrue(ended);
        assertFalse(sessionTokenService.isValid(memberToken));
    }

    @Test
    @DisplayName("InvalidExit — invalid token is denied")
    void GivenInvalidToken_WhenEndSession_ThenReturnFalse() {
        // Arrange
        String invalidToken = "not.a.real.jwt";

        // Act
        boolean ended = sessionTokenService.endSession(invalidToken);

        // Assert
        assertFalse(ended);
    }

    @Test
    @DisplayName("InvalidExit — null token is denied")
    void GivenNullToken_WhenEndSession_ThenReturnFalse() {
        // Arrange

        // Act
        boolean ended = sessionTokenService.endSession(null);

        // Assert
        assertFalse(ended);
    }

    @Test
    @DisplayName("InvalidExit — blank token is denied")
    void GivenBlankToken_WhenEndSession_ThenReturnFalse() {
        // Arrange

        // Act
        boolean ended = sessionTokenService.endSession("   ");

        // Assert
        assertFalse(ended);
    }

    @Test
    @DisplayName("InvalidExit — already ended guest token is denied")
    void GivenAlreadyEndedGuestToken_WhenEndSessionAgain_ThenReturnFalse() {
        // Arrange
        String guestToken = sessionTokenService.generateGuestToken();

        boolean firstEnd = sessionTokenService.endSession(guestToken);
        assertTrue(firstEnd);
        assertFalse(sessionTokenService.isValid(guestToken));

        // Act
        boolean secondEnd = sessionTokenService.endSession(guestToken);

        // Assert
        assertFalse(secondEnd);
    }

    @Test
    @DisplayName("InvalidExit — already ended member token is denied")
    void GivenAlreadyEndedMemberToken_WhenEndSessionAgain_ThenReturnFalse() {
        // Arrange
        String guestToken = sessionTokenService.generateGuestToken();
        UUID sessionId = sessionTokenService.extractSessionId(guestToken);
        UUID memberId = UUID.randomUUID();

        String memberToken = sessionTokenService.generateMemberToken(
                sessionId,
                memberId,
                Set.of()
        );

        boolean firstEnd = sessionTokenService.endSession(memberToken);
        assertTrue(firstEnd);
        assertFalse(sessionTokenService.isValid(memberToken));

        // Act
        boolean secondEnd = sessionTokenService.endSession(memberToken);

        // Assert
        assertFalse(secondEnd);
    }
}