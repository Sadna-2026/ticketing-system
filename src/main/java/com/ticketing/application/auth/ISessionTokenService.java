
package com.ticketing.application.auth;
import java.util.Set;
import java.util.UUID;

public interface ISessionTokenService {

    /**
     * Generates a guest token with a new sessionId.
     *
     * @return the generated JWT token string
     */
    String generateGuestToken();

    /**
     * Generates a member token while preserving an existing sessionId.
     *
     * Used when a guest logs in and becomes a member.
     *
     * @param sessionId the existing guest sessionId
     * @param memberId the logged-in member id
     * @param permissions the member permissions
     * @return the generated JWT token string
     */
    String generateMemberToken(UUID sessionId, UUID memberId, Set<String> permissions);

    /**
     * Generates a member token from full safe token data.
     *
     * @param tokenData the safe session/member data
     * @return the generated JWT token string
     */
    String generateMemberToken(SessionTokenData tokenData);

    /**
     * Extracts the sessionId from a valid active token.
     *
     * @param token JWT token, with or without "Bearer "
     * @return the session id
     */
    UUID extractSessionId(String token);

    /**
     * Extracts the memberId from a valid active token.
     *
     * @param token JWT token, with or without "Bearer "
     * @return the member id, or null for guest token
     */
    UUID extractMemberId(String token);

    /**
     * Extracts permissions from a valid active token.
     *
     * @param token JWT token, with or without "Bearer "
     * @return permission strings
     */
    Set<String> extractPermissions(String token);

    /**
     * Extracts all safe token data from a valid active token.
     *
     * @param token JWT token, with or without "Bearer "
     * @return token data
     */
    SessionTokenData extractTokenData(String token);

    /**
     * Checks whether a token is signed correctly, not expired, and not revoked.
     *
     * @param token JWT token, with or without "Bearer "
     * @return true if valid and active, false otherwise
     */
    boolean isValid(String token);

    /**
     * Revokes the given token.
     *
     * Used for logout or blocking an old token.
     *
     * @param token JWT token, with or without "Bearer "
     */
    void revokeToken(String token);

    /**
     * Logs out the current user.
     *
     * The current token is revoked and a new guest token is returned.
     *
     * @param token current JWT token, with or without "Bearer "
     * @return new guest JWT token
     */
    String logout(String token);

    boolean endSession(String token);
}