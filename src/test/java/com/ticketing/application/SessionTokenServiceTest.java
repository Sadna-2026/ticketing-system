package com.ticketing.application;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ticketing.application.auth.SessionTokenData;
import com.ticketing.infrastructure.InMemorySessionTokenRepository;
import com.ticketing.infrastructure.Interface.ISessionTokenRepository;

class SessionTokenServiceTest {

    private SessionTokenService sessionTokenService;
    private ISessionTokenRepository sessionTokenRepository;

    @BeforeEach
    void setUp() {
        String secret = Base64.getEncoder().encodeToString(
                "01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8)
        );

        sessionTokenRepository = new InMemorySessionTokenRepository();
        sessionTokenService = new SessionTokenService(secret, 120, sessionTokenRepository);
    }

    @Test//UC 1 - Guest enters the site and receives a guest token
    void GivenGuestUser_WhenGenerateGuestToken_ThenTokenIsValidAndContainsOnlySessionId() {
        String token = sessionTokenService.generateGuestToken();

        assertNotNull(token);
        assertTrue(sessionTokenService.isValid(token));

        UUID sessionId = sessionTokenService.extractSessionId(token);

        assertNotNull(sessionId);
        assertNull(sessionTokenService.extractMemberId(token));
        assertTrue(sessionTokenService.extractPermissions(token).isEmpty());
    }

    @Test
    void GivenGuestToken_WhenMemberLogsIn_ThenMemberTokenPreservesSessionIdAndRevokesGuestToken() {
        String guestToken = sessionTokenService.generateGuestToken();
        UUID guestSessionId = sessionTokenService.extractSessionId(guestToken);

        UUID memberId = UUID.randomUUID();

        String memberToken = sessionTokenService.generateMemberToken(
                guestSessionId,
                memberId,
                Set.of("BUY_TICKET", "VIEW_ORDERS")
        );

        assertFalse(sessionTokenService.isValid(guestToken));
        assertTrue(sessionTokenService.isValid(memberToken));

        assertEquals(guestSessionId, sessionTokenService.extractSessionId(memberToken));
        assertEquals(memberId, sessionTokenService.extractMemberId(memberToken));
        assertEquals(
                Set.of("BUY_TICKET", "VIEW_ORDERS"),
                sessionTokenService.extractPermissions(memberToken)
        );
    }

    @Test
    void GivenMemberToken_WhenLogout_ThenOldTokenIsInvalidAndNewGuestTokenIsValid() {
        String guestToken = sessionTokenService.generateGuestToken();
        UUID sessionId = sessionTokenService.extractSessionId(guestToken);

        String memberToken = sessionTokenService.generateMemberToken(
                sessionId,
                UUID.randomUUID(),
                Set.of("BUY_TICKET")
        );

        assertTrue(sessionTokenService.isValid(memberToken));

        String newGuestToken = sessionTokenService.logout(memberToken);

        assertFalse(sessionTokenService.isValid(memberToken));
        assertTrue(sessionTokenService.isValid(newGuestToken));

        assertNull(sessionTokenService.extractMemberId(newGuestToken));
        assertTrue(sessionTokenService.extractPermissions(newGuestToken).isEmpty());
    }

    @Test
    void GivenMemberToken_WhenLogout_ThenNewGuestTokenHasDifferentSessionId() {
        String guestToken = sessionTokenService.generateGuestToken();
        UUID originalSessionId = sessionTokenService.extractSessionId(guestToken);

        String memberToken = sessionTokenService.generateMemberToken(
                originalSessionId,
                UUID.randomUUID(),
                Set.of("BUY_TICKET")
        );

        String newGuestToken = sessionTokenService.logout(memberToken);
        UUID newGuestSessionId = sessionTokenService.extractSessionId(newGuestToken);

        assertNotEquals(originalSessionId, newGuestSessionId);
    }

    @Test
    void GivenLoggedOutToken_WhenExtractSessionId_ThenThrowException() {
        String guestToken = sessionTokenService.generateGuestToken();
        UUID sessionId = sessionTokenService.extractSessionId(guestToken);

        String memberToken = sessionTokenService.generateMemberToken(
                sessionId,
                UUID.randomUUID(),
                Set.of("BUY_TICKET")
        );

        sessionTokenService.logout(memberToken);

        assertThrows(
                IllegalArgumentException.class,
                () -> sessionTokenService.extractSessionId(memberToken)
        );
    }

    @Test
    void GivenInvalidToken_WhenIsValid_ThenReturnFalse() {
        assertFalse(sessionTokenService.isValid("not.a.real.jwt"));
    }

    @Test
    void GivenInvalidToken_WhenLogout_ThenThrowException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> sessionTokenService.logout("not.a.real.jwt")
        );
    }

    @Test
    void GivenFullSessionTokenData_WhenGenerateMemberToken_ThenAllSafeDataCanBeExtracted() {
        UUID sessionId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();

        SessionTokenData data = new SessionTokenData(
                sessionId,
                memberId,
                Set.of("BUY_TICKET", "VIEW_ORDERS"),
                "tamar",
                "tamar@example.com",
                "BUYER"
        );

        String token = sessionTokenService.generateMemberToken(data);

        SessionTokenData extracted = sessionTokenService.extractTokenData(token);

        assertEquals(sessionId, extracted.getSessionId());
        assertEquals(memberId, extracted.getMemberId());
        assertEquals(Set.of("BUY_TICKET", "VIEW_ORDERS"), extracted.getPermissions());
        assertEquals("tamar", extracted.getUsername());
        assertEquals("tamar@example.com", extracted.getEmail());
        assertEquals("BUYER", extracted.getRole());
        assertTrue(extracted.isMember());
        assertFalse(extracted.isGuest());
    }

    @Test
    void GivenNullSessionId_WhenCreateSessionTokenData_ThenThrowException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SessionTokenData(
                        null,
                        UUID.randomUUID(),
                        Set.of("BUY_TICKET"),
                        "tamar",
                        "tamar@example.com",
                        "BUYER"
                )
        );
    }

    @Test
    void GivenNullMemberId_WhenGenerateMemberToken_ThenThrowException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> sessionTokenService.generateMemberToken(
                        UUID.randomUUID(),
                        null,
                        Set.of("BUY_TICKET")
                )
        );
    }


    @Test
    void GivenGuestToken_WhenRevokeToken_ThenGuestTokenIsInvalid() {
        String guestToken = sessionTokenService.generateGuestToken();

        assertTrue(sessionTokenService.isValid(guestToken));

        sessionTokenService.revokeToken(guestToken);

        assertFalse(sessionTokenService.isValid(guestToken));
        assertThrows(
                IllegalArgumentException.class,
                () -> sessionTokenService.extractSessionId(guestToken)
        );
    }

    @Test
    void GivenMemberToken_WhenRevokeToken_ThenMemberTokenIsInvalid() {
        String guestToken = sessionTokenService.generateGuestToken();
        UUID sessionId = sessionTokenService.extractSessionId(guestToken);

        String memberToken = sessionTokenService.generateMemberToken(
                sessionId,
                UUID.randomUUID(),
                Set.of("BUY_TICKET")
        );

        assertTrue(sessionTokenService.isValid(memberToken));

        sessionTokenService.revokeToken(memberToken);

        assertFalse(sessionTokenService.isValid(memberToken));
        assertThrows(
                IllegalArgumentException.class,
                () -> sessionTokenService.extractMemberId(memberToken)
        );
    }

    @Test
    void GivenGuestToken_WhenLogout_ThenOldGuestTokenIsInvalidAndNewGuestTokenIsValid() {
        String oldGuestToken = sessionTokenService.generateGuestToken();
        UUID oldSessionId = sessionTokenService.extractSessionId(oldGuestToken);

        String newGuestToken = sessionTokenService.logout(oldGuestToken);
        UUID newSessionId = sessionTokenService.extractSessionId(newGuestToken);

        assertFalse(sessionTokenService.isValid(oldGuestToken));
        assertTrue(sessionTokenService.isValid(newGuestToken));

        assertNull(sessionTokenService.extractMemberId(newGuestToken));
        assertTrue(sessionTokenService.extractPermissions(newGuestToken).isEmpty());
        assertNotEquals(oldSessionId, newSessionId);
    }

    @Test
    void GivenTokenWithBearerPrefix_WhenValidateAndExtract_ThenWorksCorrectly() {
        String token = sessionTokenService.generateGuestToken();
        UUID expectedSessionId = sessionTokenService.extractSessionId(token);

        String bearerToken = "Bearer " + token;

        assertTrue(sessionTokenService.isValid(bearerToken));
        assertEquals(expectedSessionId, sessionTokenService.extractSessionId(bearerToken));
    }

    @Test
    void GivenMemberTokenWithBearerPrefix_WhenLogout_ThenOldTokenIsInvalidAndNewGuestTokenIsValid() {
        String guestToken = sessionTokenService.generateGuestToken();
        UUID sessionId = sessionTokenService.extractSessionId(guestToken);

        String memberToken = sessionTokenService.generateMemberToken(
                sessionId,
                UUID.randomUUID(),
                Set.of("BUY_TICKET")
        );

        String bearerMemberToken = "Bearer " + memberToken;

        String newGuestToken = sessionTokenService.logout(bearerMemberToken);

        assertFalse(sessionTokenService.isValid(memberToken));
        assertTrue(sessionTokenService.isValid(newGuestToken));
        assertNull(sessionTokenService.extractMemberId(newGuestToken));
     }

    @Test
     void GivenMemberTokenWithNullPermissions_WhenExtractPermissions_ThenReturnEmptySet() {
        String memberToken = sessionTokenService.generateMemberToken(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null
        );

        assertTrue(sessionTokenService.isValid(memberToken));
        assertTrue(sessionTokenService.extractPermissions(memberToken).isEmpty());
        }

    @Test
    void GivenGuestTokenUpgradedToMember_WhenUsingOldGuestToken_ThenThrowException() {
        String guestToken = sessionTokenService.generateGuestToken();
        UUID sessionId = sessionTokenService.extractSessionId(guestToken);

        sessionTokenService.generateMemberToken(
                sessionId,
                UUID.randomUUID(),
                Set.of("BUY_TICKET")
        );

        assertFalse(sessionTokenService.isValid(guestToken));

        assertThrows(
                IllegalArgumentException.class,
                () -> sessionTokenService.extractTokenData(guestToken)
        );
     }
}