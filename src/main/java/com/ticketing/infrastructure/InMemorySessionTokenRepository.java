package com.ticketing.infrastructure;


import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Repository;

import com.ticketing.domain.auth.ISessionTokenRepository;
import com.ticketing.domain.auth.SessionToken;


@Repository
public class InMemorySessionTokenRepository implements ISessionTokenRepository {

    private final ConcurrentHashMap<UUID, SessionToken> tokens = new ConcurrentHashMap<>();

    @Override
    public void save(SessionToken sessionToken) {
        if (sessionToken == null) {
            throw new IllegalArgumentException("sessionToken cannot be null");
        }

        tokens.put(sessionToken.getTokenId(), sessionToken);
    }

    @Override
    public Optional<SessionToken> findByTokenId(UUID tokenId) {
        if (tokenId == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(tokens.get(tokenId));
    }

    @Override
    public void revoke(UUID tokenId, String reason) {
        if (tokenId == null) {
            return;
        }

        SessionToken token = tokens.get(tokenId);

        if (token != null) {
            token.revoke(reason);
        }
    }

    @Override
    public void revokeAllBySessionId(UUID sessionId, String reason) {
        if (sessionId == null) {
            return;
        }

        for (SessionToken token : tokens.values()) {
            if (token.getSessionId().equals(sessionId) && token.isActive()) {
                token.revoke(reason);
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
                token.revoke(reason);
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
}