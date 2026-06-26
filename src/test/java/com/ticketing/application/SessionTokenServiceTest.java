package com.ticketing.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.Set;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.ticketing.application.auth.ISessionTokenRepository;
import com.ticketing.application.auth.SessionToken;
import com.ticketing.application.auth.SessionTokenData;
import com.ticketing.application.auth.SessionTokenService;
import com.ticketing.application.services.MemberService;
import com.ticketing.domain.member.request.LoginRequest;
import com.ticketing.domain.member.request.RegisterRequest;
import com.ticketing.domain.member.request.UpdateMemberDetailsRequest;
import com.ticketing.domain.member.response.LoginResponse;
import com.ticketing.domain.member.response.RegisterResponse;
import com.ticketing.domain.member.response.UpdateMemberDetailsResponse;
import com.ticketing.infrastructure.InMemoryMemberRepository;
import com.ticketing.infrastructure.InMemorySessionTokenRepository;
import com.ticketing.infrastructure.PasswordEncryptionUtils;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

@DisplayName("SessionTokenService")
class SessionTokenServiceTest {

    private static final String TEST_SECRET = Base64.getEncoder().encodeToString(
            "01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8));

    private static SecretKey signingKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(TEST_SECRET));
    }

    private static String buildSignedJwt(
            UUID tokenId,
            UUID sessionId,
            UUID memberId,
            Date issuedAt,
            Date expiresAt
    ) {
        var builder = Jwts.builder()
                .id(tokenId.toString())
                .issuer("ticketing-system")
                .issuedAt(issuedAt)
                .expiration(expiresAt)
                .claim("sessionId", sessionId.toString())
                .claim("permissions", Collections.emptyList())
                .signWith(signingKey(), Jwts.SIG.HS256);

        if (memberId != null) {
            builder.subject(memberId.toString());
            builder.claim("memberId", memberId.toString());
        }

        return builder.compact();
    }

    private static SessionTokenService newService(ISessionTokenRepository repository) {
        return new SessionTokenService(TEST_SECRET, 120, repository);
    }

    @Nested
    @DisplayName("Tokens")
    class Tokens {

        private SessionTokenService sessionTokenService;
        private ISessionTokenRepository sessionTokenRepository;

        @BeforeEach
        void setUp() {
            sessionTokenRepository = new InMemorySessionTokenRepository();
            sessionTokenService = newService(sessionTokenRepository);
        }

        @Test
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

    @Nested
    @DisplayName("End session")
    class EndSession {

        private SessionTokenService sessionTokenService;

        @BeforeEach
        void setUp() {
            sessionTokenService = newService(new InMemorySessionTokenRepository());
        }

        @Test
        @DisplayName("GuestExit — valid guest token is invalidated")
        void GivenValidGuestToken_WhenEndSession_ThenGuestTokenInvalidated() {
            String guestToken = sessionTokenService.generateGuestToken();

            assertTrue(sessionTokenService.isValid(guestToken));
            assertNull(sessionTokenService.extractMemberId(guestToken));

            boolean ended = sessionTokenService.endSession(guestToken);

            assertTrue(ended);
            assertFalse(sessionTokenService.isValid(guestToken));
        }

        @Test
        @DisplayName("MemberExit — valid member token is invalidated")
        void GivenValidMemberToken_WhenEndSession_ThenMemberTokenInvalidated() {
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

            boolean ended = sessionTokenService.endSession(memberToken);

            assertTrue(ended);
            assertFalse(sessionTokenService.isValid(memberToken));
        }

        @Test
        @DisplayName("InvalidExit — invalid token is denied")
        void GivenInvalidToken_WhenEndSession_ThenReturnFalse() {
            String invalidToken = "not.a.real.jwt";

            boolean ended = sessionTokenService.endSession(invalidToken);

            assertFalse(ended);
        }

        @Test
        @DisplayName("InvalidExit — null token is denied")
        void GivenNullToken_WhenEndSession_ThenReturnFalse() {

            boolean ended = sessionTokenService.endSession(null);

            assertFalse(ended);
        }

        @Test
        @DisplayName("InvalidExit — blank token is denied")
        void GivenBlankToken_WhenEndSession_ThenReturnFalse() {

            boolean ended = sessionTokenService.endSession("   ");

            assertFalse(ended);
        }

        @Test
        @DisplayName("InvalidExit — already ended guest token is denied")
        void GivenAlreadyEndedGuestToken_WhenEndSessionAgain_ThenReturnFalse() {
            String guestToken = sessionTokenService.generateGuestToken();

            boolean firstEnd = sessionTokenService.endSession(guestToken);
            assertTrue(firstEnd);
            assertFalse(sessionTokenService.isValid(guestToken));

            boolean secondEnd = sessionTokenService.endSession(guestToken);

            assertFalse(secondEnd);
        }

        @Test
        @DisplayName("InvalidExit — already ended member token is denied")
        void GivenAlreadyEndedMemberToken_WhenEndSessionAgain_ThenReturnFalse() {
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

            boolean secondEnd = sessionTokenService.endSession(memberToken);

            assertFalse(secondEnd);
        }
    }

    @Nested
    @DisplayName("Rejected tokens (#542)")
    class RejectedTokens {

        private InMemorySessionTokenRepository tokenRepository;
        private SessionTokenService sessionTokenService;

        @BeforeEach
        void setUp() {
            tokenRepository = new InMemorySessionTokenRepository();
            sessionTokenService = newService(tokenRepository);
        }

        @Test
        void GivenExpiredJwt_WhenValidated_ThenRejected() {
            UUID tokenId = UUID.randomUUID();
            UUID sessionId = UUID.randomUUID();
            Instant created = Instant.now().minusSeconds(7_200);
            Instant expired = Instant.now().minusSeconds(3_600);

            tokenRepository.save(new SessionToken(tokenId, sessionId, null, created, expired));

            String jwt = buildSignedJwt(
                    tokenId, sessionId, null, Date.from(created), Date.from(expired));

            assertFalse(sessionTokenService.isValid(jwt));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> sessionTokenService.extractSessionId(jwt));
        }

        @Test
        void GivenRepositoryExpiredButJwtNotExpired_WhenValidated_ThenRejected() {
            UUID tokenId = UUID.randomUUID();
            UUID sessionId = UUID.randomUUID();
            Instant created = Instant.now().minusSeconds(7_200);
            Instant repositoryExpiry = Instant.now().minusSeconds(60);

            tokenRepository.save(new SessionToken(tokenId, sessionId, null, created, repositoryExpiry));

            String jwt = buildSignedJwt(
                    tokenId,
                    sessionId,
                    null,
                    Date.from(created),
                    Date.from(Instant.now().plusSeconds(3_600)));

            assertFalse(sessionTokenService.isValid(jwt));
        }

        @Test
        void GivenMalformedToken_WhenValidated_ThenRejected() {
            assertFalse(sessionTokenService.isValid(null));
            assertFalse(sessionTokenService.isValid(""));
            assertFalse(sessionTokenService.isValid("   "));
            assertFalse(sessionTokenService.isValid("not-a-jwt"));
            assertFalse(sessionTokenService.isValid("a.b"));
        }

        @Test
        void GivenTamperedToken_WhenValidated_ThenRejected() {
            String valid = sessionTokenService.generateGuestToken();
            String[] parts = valid.split("\\.", 3);
            char[] payload = parts[1].toCharArray();
            payload[0] = payload[0] == 'A' ? 'B' : 'A';
            String tampered = parts[0] + "." + new String(payload) + "." + parts[2];

            assertFalse(sessionTokenService.isValid(tampered));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> sessionTokenService.extractTokenData(tampered));
        }

        @Test
        void GivenTokenSignedWithWrongSecret_WhenValidated_ThenRejected() {
            UUID tokenId = UUID.randomUUID();
            UUID sessionId = UUID.randomUUID();
            Date now = new Date();
            Date later = Date.from(Instant.now().plusSeconds(3_600));

            String foreignSecret = Base64.getEncoder().encodeToString(
                    "abcdefghijklmnopqrstuvwxyz123456".getBytes(StandardCharsets.UTF_8));
            SecretKey foreignKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(foreignSecret));

            String jwt = Jwts.builder()
                    .id(tokenId.toString())
                    .issuer("ticketing-system")
                    .issuedAt(now)
                    .expiration(later)
                    .claim("sessionId", sessionId.toString())
                    .claim("permissions", Collections.emptyList())
                    .signWith(foreignKey, Jwts.SIG.HS256)
                    .compact();

            tokenRepository.save(new SessionToken(
                    tokenId, sessionId, null, now.toInstant(), later.toInstant()));

            assertFalse(sessionTokenService.isValid(jwt));
        }

        @Test
        void GivenUnknownButWellSignedToken_WhenValidated_ThenRejected() {
            UUID tokenId = UUID.randomUUID();
            UUID sessionId = UUID.randomUUID();
            Date now = new Date();
            Date later = Date.from(Instant.now().plusSeconds(3_600));

            String jwt = buildSignedJwt(tokenId, sessionId, null, now, later);

            assertFalse(sessionTokenService.isValid(jwt));
        }
    }

    @Nested
    @DisplayName("Restart survival (#542)")
    class RestartSurvival {

        private InMemorySessionTokenRepository tokenRepository;
        private SessionTokenService sessionTokenService;

        @BeforeEach
        void setUp() {
            tokenRepository = new InMemorySessionTokenRepository();
            sessionTokenService = newService(tokenRepository);
        }

        @Test
        void GivenActiveToken_WhenServiceRestartedWithSameRepository_ThenTokenStillValid() {
            String guestToken = sessionTokenService.generateGuestToken();
            UUID sessionId = sessionTokenService.extractSessionId(guestToken);

            SessionTokenService restarted = newService(tokenRepository);

            assertTrue(restarted.isValid(guestToken));
            assertEquals(sessionId, restarted.extractSessionId(guestToken));
        }

        @Test
        void GivenMemberToken_WhenServiceRestartedWithSameRepository_ThenTokenStillValid() {
            UUID memberId = UUID.randomUUID();
            String guestToken = sessionTokenService.generateGuestToken();
            UUID sessionId = sessionTokenService.extractSessionId(guestToken);
            String memberToken = sessionTokenService.generateMemberToken(
                    sessionId, memberId, Set.of("VIEW_ORDERS"));

            SessionTokenService restarted = newService(tokenRepository);

            assertTrue(restarted.isValid(memberToken));
            assertEquals(memberId, restarted.extractMemberId(memberToken));
        }
    }

    @Nested
    @DisplayName("Concurrent sessions (#542)")
    class ConcurrentSessions {

        private SessionTokenService sessionTokenService;

        @BeforeEach
        void setUp() {
            sessionTokenService = newService(new InMemorySessionTokenRepository());
        }

        @Test
        void GivenSameMemberWithTwoSessions_WhenOneLogsOut_ThenOtherRemainsValid() {
            UUID memberId = UUID.randomUUID();

            String guestA = sessionTokenService.generateGuestToken();
            String tokenA = sessionTokenService.generateMemberToken(
                    sessionTokenService.extractSessionId(guestA), memberId, Set.of());

            String guestB = sessionTokenService.generateGuestToken();
            String tokenB = sessionTokenService.generateMemberToken(
                    sessionTokenService.extractSessionId(guestB), memberId, Set.of());

            assertTrue(sessionTokenService.isValid(tokenA));
            assertTrue(sessionTokenService.isValid(tokenB));

            sessionTokenService.logout(tokenA);

            assertFalse(sessionTokenService.isValid(tokenA));
            assertTrue(sessionTokenService.isValid(tokenB));
        }

        @Test
        void GivenSameMemberWithTwoSessions_WhenRevokeMemberSessions_ThenBothRejected() {
            UUID memberId = UUID.randomUUID();

            String tokenA = sessionTokenService.generateMemberToken(
                    UUID.randomUUID(), memberId, Set.of());
            String tokenB = sessionTokenService.generateMemberToken(
                    UUID.randomUUID(), memberId, Set.of());

            sessionTokenService.revokeMemberSessions(memberId);

            assertFalse(sessionTokenService.isValid(tokenA));
            assertFalse(sessionTokenService.isValid(tokenB));
        }
    }

    @Nested
    @DisplayName("Guest-only authorization (#542)")
    class GuestAuthorization {

        private SessionTokenService sessionTokenService;
        private MemberService memberService;

        @BeforeEach
        void setUp() {
            sessionTokenService = newService(new InMemorySessionTokenRepository());
            memberService = new MemberService(
                    new InMemoryMemberRepository(),
                    new PasswordEncryptionUtils(),
                    sessionTokenService);
        }

        private RegisterRequest registerRequest(String username, String email, String password) {
            return new RegisterRequest(username, email, password, "0501234567", "2000-01-01");
        }

        @Test
        void GivenNoToken_WhenRegister_ThenRejected() {
            RegisterResponse response = memberService.register(
                    registerRequest("guest", "guest@example.com", "secret1"),
                    null);

            assertFalse(response.success());
            assertEquals("Invalid session token.", response.message());
        }

        @Test
        void GivenInvalidToken_WhenRegister_ThenRejected() {
            RegisterResponse response = memberService.register(
                    registerRequest("guest", "guest@example.com", "secret1"),
                    "not.a.real.jwt");

            assertFalse(response.success());
            assertEquals("Invalid session token.", response.message());
        }

        @Test
        void GivenGuestToken_WhenRegister_ThenSucceeds() {
            String guestToken = sessionTokenService.generateGuestToken();

            RegisterResponse response = memberService.register(
                    registerRequest("guest", "guest@example.com", "secret1"),
                    guestToken);

            assertTrue(response.success());
            assertNotNull(response.sessionToken());
            assertNotNull(sessionTokenService.extractMemberId(response.sessionToken()));
        }

        @Test
        void GivenMemberToken_WhenRegister_ThenRejected() {
            RegisterResponse member = memberService.register(
                    registerRequest("member", "member@example.com", "secret1"),
                    sessionTokenService.generateGuestToken());

            RegisterResponse response = memberService.register(
                    registerRequest("other", "other@example.com", "secret2"),
                    member.sessionToken());

            assertFalse(response.success());
            assertEquals("Only guests can register.", response.message());
        }

        @Test
        void GivenGuestToken_WhenUpdateMemberDetails_ThenRejected() {
            String guestToken = sessionTokenService.generateGuestToken();

            UpdateMemberDetailsResponse response = memberService.updateIdentifyingDetails(
                    guestToken,
                    UUID.randomUUID(),
                    new UpdateMemberDetailsRequest(
                            "name", "name@example.com", "0501111111", LocalDate.of(1990, 1, 1)));

            assertFalse(response.success());
            assertEquals("No authenticated member session exists.", response.message());
        }

        @Test
        void GivenGuestToken_WhenLoginExistingMember_ThenSucceeds() {
            String guestToken = sessionTokenService.generateGuestToken();
            memberService.register(
                    registerRequest("loginUser", "login@example.com", "secret1"),
                    guestToken);

            String freshGuest = sessionTokenService.generateGuestToken();
            LoginResponse response = memberService.login(
                    new LoginRequest("loginUser", "secret1"),
                    freshGuest);

            assertTrue(response.success());
            assertNotNull(response.sessionToken());
        }

        @Test
        void GivenRevokedToken_WhenLogout_ThenOldTokenStaysInvalidAndNewGuestIssued() {
            String guestToken = sessionTokenService.generateGuestToken();
            sessionTokenService.revokeToken(guestToken);
            assertFalse(sessionTokenService.isValid(guestToken));

            String replacementGuest = sessionTokenService.logout(guestToken);

            assertFalse(sessionTokenService.isValid(guestToken));
            assertTrue(sessionTokenService.isValid(replacementGuest));
            assertNull(sessionTokenService.extractMemberId(replacementGuest));
        }

        @Test
        void GivenNullToken_WhenGetMemberDetails_ThenReturnsNull() {
            assertNull(memberService.getMemberDetails(null));
        }
    }
}
