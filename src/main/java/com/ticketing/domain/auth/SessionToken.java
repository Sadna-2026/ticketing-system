package com.ticketing.domain.auth;

import java.time.Instant;
import java.util.UUID;
public class SessionToken {

    private final UUID tokenId;
    private final UUID sessionId;
    private final UUID memberId;
    private final Instant createdAt;
    private final Instant expiresAt;

    private boolean revoked;
    private Instant revokedAt;
    private String revokeReason;
    private int version;

    /**
     * Creates a new session token entity.
     *
     * @param tokenId the unique token identifier stored in the JWT ID claim
     * @param sessionId the session identifier shared between guest and member tokens
     * @param memberId the member identifier, or null for guest tokens
     * @param createdAt the time the token was created
     * @param expiresAt the time the token expires
     */
    public SessionToken(
            UUID tokenId,
            UUID sessionId,
            UUID memberId,
            Instant createdAt,
            Instant expiresAt
    ) {
        if (tokenId == null) {
            throw new IllegalArgumentException("tokenId cannot be null");
        }

        if (sessionId == null) {
            throw new IllegalArgumentException("sessionId cannot be null");
        }

        if (createdAt == null) {
            throw new IllegalArgumentException("createdAt cannot be null");
        }

        if (expiresAt == null) {
            throw new IllegalArgumentException("expiresAt cannot be null");
        }

        if (!expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("expiresAt must be after createdAt");
        }

        this.tokenId = tokenId;
        this.sessionId = sessionId;
        this.memberId = memberId;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.revoked = false;
    }

    private SessionToken(
            UUID tokenId,
            UUID sessionId,
            UUID memberId,
            Instant createdAt,
            Instant expiresAt,
            boolean revoked,
            Instant revokedAt,
            String revokeReason,
            int version
    ) {
        this(tokenId, sessionId, memberId, createdAt, expiresAt);
        this.revoked = revoked;
        this.revokedAt = revokedAt;
        this.revokeReason = revokeReason;
        this.version = version;
    }

    public UUID getTokenId() {
        return tokenId;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public UUID getMemberId() {
        return memberId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public boolean isRevoked() {
        return revoked;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public String getRevokeReason() {
        return revokeReason;
    }

    public int getVersion() {
        return version;
    }

    public void incrementVersion() {
        this.version++;
    }

    public boolean isGuestToken() {
        return memberId == null;
    }

    public boolean isMemberToken() {
        return memberId != null;
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public boolean isActive() {
        return !revoked && !isExpired();
    }

    public void revoke(String reason) {
        this.revoked = true;
        this.revokedAt = Instant.now();
        this.revokeReason = reason;
    }

    public SessionToken detachedCopy() {
        return new SessionToken(
                tokenId,
                sessionId,
                memberId,
                createdAt,
                expiresAt,
                revoked,
                revokedAt,
                revokeReason,
                version);
    }
}