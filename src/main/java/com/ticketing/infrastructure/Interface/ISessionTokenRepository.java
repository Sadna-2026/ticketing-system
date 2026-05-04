package com.ticketing.infrastructure.Interface;


import java.util.Optional;
import java.util.UUID;

import com.ticketing.application.auth.SessionToken;

public interface ISessionTokenRepository {

    /**
     * Saves a token record.
     *
     * @param sessionToken token metadata
     */
    void save(SessionToken sessionToken);

    /**
     * Finds a token by tokenId.
     *
     * @param tokenId JWT id claim
     * @return token if found
     */
    Optional<SessionToken> findByTokenId(UUID tokenId);

    /**
     * Revokes a specific token.
     *
     * @param tokenId token id
     * @param reason revocation reason
     */
    void revoke(UUID tokenId, String reason);

    /**
     * Revokes all active tokens for a session.
     *
     * Used when guest token is upgraded to member token.
     *
     * @param sessionId session id
     * @param reason revocation reason
     */
    void revokeAllBySessionId(UUID sessionId, String reason);

    /**
     * Checks whether a token exists, is not revoked, and is not expired.
     *
     * @param tokenId token id
     * @return true if active
     */
    boolean isTokenActive(UUID tokenId);

    void deleteExpiredTokens();

    void revokeAllByMemberId(UUID memberId, String reason);
}