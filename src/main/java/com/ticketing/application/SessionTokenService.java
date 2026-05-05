package com.ticketing.application;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.ticketing.application.auth.ISessionTokenService;
import com.ticketing.application.auth.SessionToken;
import com.ticketing.application.auth.SessionTokenData;
import com.ticketing.infrastructure.Interface.ISessionTokenRepository;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

@Service
public class SessionTokenService implements ISessionTokenService {

    private static final String ISSUER = "ticketing-system";

    private static final String CLAIM_SESSION_ID = "sessionId";
    private static final String CLAIM_MEMBER_ID = "memberId";
    private static final String CLAIM_PERMISSIONS = "permissions";
    private static final String CLAIM_USERNAME = "username";
    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_ROLE = "role";

    private final SecretKey signingKey;
    private final long expirationMinutes;
    private final ISessionTokenRepository sessionTokenRepository;

    public SessionTokenService(
            @Value("${security.jwt.secret}") String jwtSecret,
            @Value("${security.jwt.expiration-minutes:10}") long expirationMinutes, 
            ISessionTokenRepository sessionTokenRepository
    ) {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new IllegalArgumentException("jwtSecret cannot be null or blank");
        }

        if (expirationMinutes <= 0) {
            throw new IllegalArgumentException("expirationMinutes must be positive");
        }

        if (sessionTokenRepository == null) {
            throw new IllegalArgumentException("sessionTokenRepository cannot be null");
        }

        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret));
        this.expirationMinutes = expirationMinutes;
        this.sessionTokenRepository = sessionTokenRepository;
    }

    @Override
    public String generateGuestToken() {
        SessionTokenData tokenData = new SessionTokenData(
                UUID.randomUUID(),
                null,
                Collections.emptySet(),
                null,
                null,
                null
        );

        return buildAndSaveToken(tokenData);
    }

    @Override
    public String generateMemberToken(UUID sessionId, UUID memberId, Set<String> permissions) {
        SessionTokenData tokenData = new SessionTokenData(
                sessionId,
                memberId,
                permissions,
                null,
                null,
                null
        );

        return generateMemberToken(tokenData);
    }

    @Override
    public String generateMemberToken(SessionTokenData tokenData) {
        if (tokenData == null) {
            throw new IllegalArgumentException("tokenData cannot be null");
        }

        if (tokenData.getMemberId() == null) {
            throw new IllegalArgumentException("memberId cannot be null for member token");
        }

        /*
         * Guest -> member upgrade:
         * keep the same sessionId, but revoke the old guest token.
         */
        sessionTokenRepository.revokeAllBySessionId(
                tokenData.getSessionId(),
                "UPGRADED_TO_MEMBER"
        );

        return buildAndSaveToken(tokenData);
    }

    @Override
    public UUID extractSessionId(String token) {
        return extractTokenData(token).getSessionId();
    }

    @Override
    public UUID extractMemberId(String token) {
        return extractTokenData(token).getMemberId();
    }

    @Override
    public Set<String> extractPermissions(String token) {
        return extractTokenData(token).getPermissions();
    }


    @Override
    public SessionTokenData extractTokenData(String token) {
        Claims claims = parseAndValidateClaims(token);

        UUID sessionId = getRequiredUuidClaim(claims, CLAIM_SESSION_ID);
        UUID memberId = getOptionalUuidClaim(claims, CLAIM_MEMBER_ID);
        Set<String> permissions = getPermissionsClaim(claims);

        String username = getOptionalStringClaim(claims, CLAIM_USERNAME);
        String email = getOptionalStringClaim(claims, CLAIM_EMAIL);
        String role = getOptionalStringClaim(claims, CLAIM_ROLE);

        return new SessionTokenData(
                sessionId,
                memberId,
                permissions,
                username,
                email,
                role
        );
    }

    @Override
    public boolean isValid(String token) {
        try {
            parseAndValidateClaims(token);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    @Override
    public void revokeToken(String token) {
        Claims claims = parseClaimsOnly(token);
        UUID tokenId = getTokenIdFromClaims(claims);

        sessionTokenRepository.revoke(tokenId, "LOGOUT");
    }

    @Override
    public String logout(String token) {
        revokeToken(token);
        return generateGuestToken();
    }

    @Override
    public void revokeMemberSessions(UUID memberId) {
        if (memberId != null) {
            sessionTokenRepository.revokeAllByMemberId(memberId, "MEMBER_REMOVED");
        }
    }

    private String buildAndSaveToken(SessionTokenData tokenData) {
        UUID tokenId = UUID.randomUUID();

        Instant now = Instant.now();
        Instant expiration = now.plusSeconds(expirationMinutes * 60);

        String jwt = buildJwt(tokenId, tokenData, now, expiration);

        SessionToken sessionToken = new SessionToken(
                tokenId,
                tokenData.getSessionId(),
                tokenData.getMemberId(),
                now,
                expiration
        );

        sessionTokenRepository.save(sessionToken);

        return jwt;
    }

    private String buildJwt(
            UUID tokenId,
            SessionTokenData tokenData,
            Instant now,
            Instant expiration
    ) {
        JwtBuilder builder = Jwts.builder()
                .id(tokenId.toString())
                .issuer(ISSUER)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .claim(CLAIM_SESSION_ID, tokenData.getSessionId().toString())
                .claim(CLAIM_PERMISSIONS, tokenData.getPermissions())
                .signWith(signingKey, Jwts.SIG.HS256);

        if (tokenData.getMemberId() != null) {
            builder.subject(tokenData.getMemberId().toString());
            builder.claim(CLAIM_MEMBER_ID, tokenData.getMemberId().toString());
        }

        if (tokenData.getUsername() != null) {
            builder.claim(CLAIM_USERNAME, tokenData.getUsername());
        }

        if (tokenData.getEmail() != null) {
            builder.claim(CLAIM_EMAIL, tokenData.getEmail());
        }

        if (tokenData.getRole() != null) {
            builder.claim(CLAIM_ROLE, tokenData.getRole());
        }

        return builder.compact();
    }

    private Claims parseAndValidateClaims(String rawToken) {
        Claims claims = parseClaimsOnly(rawToken);
        UUID tokenId = getTokenIdFromClaims(claims);

        if (!sessionTokenRepository.isTokenActive(tokenId)) {
            throw new IllegalArgumentException("Token is revoked, expired, or unknown");
        }

        return claims;
    }

    private Claims parseClaimsOnly(String rawToken) {
        try {
            String token = cleanBearerPrefix(rawToken);

            return Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(ISSUER)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

        } catch (JwtException | IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid or expired token", ex);
        }
    }

    private String cleanBearerPrefix(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Token cannot be null or blank");
        }

        String trimmed = token.trim();

        if (trimmed.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return trimmed.substring(7).trim();
        }

        return trimmed;
    }

    private UUID getTokenIdFromClaims(Claims claims) {
        String tokenIdString = claims.getId();

        if (tokenIdString == null || tokenIdString.isBlank()) {
            throw new IllegalArgumentException("Token does not contain token id");
        }

        return UUID.fromString(tokenIdString);
    }

    private UUID getRequiredUuidClaim(Claims claims, String claimName) {
        Object value = claims.get(claimName);

        if (value == null) {
            throw new IllegalArgumentException("Token does not contain required claim: " + claimName);
        }

        return UUID.fromString(value.toString());
    }

    private UUID getOptionalUuidClaim(Claims claims, String claimName) {
        Object value = claims.get(claimName);

        if (value == null) {
            return null;
        }

        return UUID.fromString(value.toString());
    }

    private String getOptionalStringClaim(Claims claims, String claimName) {
        Object value = claims.get(claimName);

        if (value == null) {
            return null;
        }

        return value.toString();
    }

    private Set<String> getPermissionsClaim(Claims claims) {
        Object permissionsClaim = claims.get(CLAIM_PERMISSIONS);

        if (permissionsClaim == null) {
            return Collections.emptySet();
        }

        Set<String> permissions = new HashSet<>();

        if (permissionsClaim instanceof Collection<?> collection) {
            for (Object permission : collection) {
                if (permission != null) {
                    permissions.add(permission.toString());
                }
            }

            return permissions;
        }

        return Collections.emptySet();
    }
}