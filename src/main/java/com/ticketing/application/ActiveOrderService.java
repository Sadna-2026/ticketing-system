import java.util.UUID;

public class ActiveOrderService {


    /**
     * Creates an active order for the given session and event.
     * Can be called by guests (no token) or members (with token).
     *
     * @param token JWT token (null for guests)
     * @param eventId the event to order tickets for
     * @return the new order's UUID
     */
    public UUID createOrder(String token, UUID eventId) {
        UUID memberId = validateToken(token);
    }

    private UUID validateToken(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Authentication token is required");
        }
        if (!tokenService.isValid(token)) {
            throw new IllegalArgumentException("Invalid or expired authentication token");
        }
        return tokenService.extractMemberId(token);
    }
}