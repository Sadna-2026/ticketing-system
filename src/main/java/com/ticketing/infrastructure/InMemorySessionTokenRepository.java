package com.ticketing.infrastructure;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Repository;

import com.ticketing.application.auth.ISessionTokenRepository;
import com.ticketing.application.auth.SessionToken;
import com.ticketing.domain.exception.OptimisticLockException;

@Repository
public class InMemorySessionTokenRepository implements ISessionTokenRepository {

    private final ConcurrentHashMap<UUID, SessionToken> tokens = new ConcurrentHashMap<>();

    @Override
    public void save(SessionToken sessionToken) {
        if (sessionToken == null) {
            throw new IllegalArgumentException("sessionToken cannot be null");
        }

        tokens.compute(sessionToken.getTokenId(), (id, existing) -> {
            if (existing == null) {
                sessionToken.incrementVersion();
                SessionToken stored = sessionToken.detachedCopy();
                return stored;
            }
            if (sessionToken.getVersion() != existing.getVersion()) {
                throw new OptimisticLockException("SessionToken", id);
            }
            sessionToken.incrementVersion();
            SessionToken stored = sessionToken.detachedCopy();
            return stored;
        });
    }

    @Override
    public Optional<SessionToken> findByTokenId(UUID tokenId) {
        if (tokenId == null) {
            return Optional.empty();
        }

        SessionToken token = tokens.get(tokenId);
        return token != null ? Optional.of(token.detachedCopy()) : Optional.empty();
    }

    @Override
    public void revoke(UUID tokenId, String reason) {
        if (tokenId == null) {
            return;
        }

        revokeStored(tokenId, reason);
    }

    @Override
    public void revokeAllBySessionId(UUID sessionId, String reason) {
        if (sessionId == null) {
            return;
        }

        for (SessionToken token : tokens.values()) {
            if (token.getSessionId().equals(sessionId) && token.isActive()) {
                revokeStored(token.getTokenId(), reason);
            }
        }
    }

    @Override
    public void revokeAllByMemberId(UUID memberId, String reason) {
        if (memberId == null) {
            return;
        }

        for (SessionToken token : tokens.values()) {
            if (memberId.equals(token.getMemberId()) && token.isActive()) {
                revokeStored(token.getTokenId(), reason);
            }
        }
    }

    @Override
    public boolean isTokenActive(UUID tokenId) {
        if (tokenId == null) {
            return false;
        }

        SessionToken token = tokens.get(tokenId);
        return token != null && token.isActive();
    }

    @Override
    public void deleteExpiredTokens() {
        for (SessionToken token : tokens.values()) {
            if (token.isExpired()) {
                tokens.remove(token.getTokenId());
    }}}

    private void revokeStored(UUID tokenId, String reason) {
        tokens.computeIfPresent(tokenId, (id, existing) -> {
            SessionToken token = existing.detachedCopy();
            token.revoke(reason);
            token.incrementVersion();
            return token;
        });
    }
}
