import java.util.Set;
import java.util.UUID;


public interface ISessionTokenService {

    /**
     * Generates a guest token with a new sessionId.
     * The sessionId is generated internally (UUID). The token carries only
     * the sessionId, with no memberId or permissions.
     *
     * @return the generated JWT token string
     */
    String generateGuestToken();

    /**
     * Generates a member token preserving an existing sessionId.
     * Used on login to upgrade a guest token — the sessionId from the guest token
     * is preserved so that any in-progress order remains linked.
     *
     * @param sessionId the sessionId from the existing guest token (via extractSessionId)
     * @param memberId the member's unique identifier
     * @param permissions the set of permission strings to encode in the token
     * @return the generated JWT token string
     */
    String generateMemberToken(UUID sessionId, UUID memberId, Set<String> permissions);

    /**
     * Extracts the session ID from a valid token.
     * Always present in both guest and member tokens.
     *
     * @param token the JWT token string
     * @return the session's UUID
     * @throws IllegalArgumentException if the token is invalid or expired
     */
    UUID extractSessionId(String token);

    /**
     * Extracts the member ID from a valid member token.
     * Returns null if the token is a guest token (no memberId).
     *
     * @param token the JWT token string
     * @return the member's UUID, or null for guest tokens
     * @throws IllegalArgumentException if the token is invalid or expired
     */
    UUID extractMemberId(String token);

    /**
     * Extracts the permissions from a valid token.
     * Returns an empty set for guest tokens.
     *
     * @param token the JWT token string
     * @return the set of permission strings
     * @throws IllegalArgumentException if the token is invalid or expired
     */
    Set<String> extractPermissions(String token);

    /**
     * Validates whether the given token is valid and not expired.
     *
     * @param token the JWT token string
     * @return true if the token is valid and not expired, false otherwise
     */
    boolean isValid(String token);
}
