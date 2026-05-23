package com.ticketing.domain.auth;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * DTO that represents the safe data stored inside a session JWT.
 *
 * This object is used both when creating a token and when extracting data from a token.
 *
 * Safe fields:
 * sessionId, memberId, permissions, username, email, and role.
 *
 * Never store:
 * password, encrypted password, password hash, salt, reset token, payment data,
 * or private sensitive data.
 */
public class SessionTokenData {

    private final UUID sessionId;
    private final UUID memberId;
    private final Set<String> permissions;

    private final String username;
    private final String email;
    private final String role;

    /**
     * Creates a new SessionTokenData object.
     *
     * For a guest token, memberId, username, email, and role may be null.
     * For a member token, memberId should not be null.
     *
     * @param sessionId the unique session identifier
     * @param memberId the unique member identifier, or null for guest tokens
     * @param permissions the set of permissions assigned to the member
     * @param username the member username, or null if not stored in the token
     * @param email the member email, or null if not stored in the token
     * @param role the member role, or null if not stored in the token
     */
    public SessionTokenData(
            UUID sessionId,
            UUID memberId,
            Set<String> permissions,
            String username,
            String email,
            String role
    ) {
        if (sessionId == null) {
            throw new IllegalArgumentException("sessionId cannot be null");
        }

        this.sessionId = sessionId;
        this.memberId = memberId;
        this.permissions = permissions == null
                ? Collections.emptySet()
                : Collections.unmodifiableSet(new HashSet<>(permissions));
        this.username = username;
        this.email = email;
        this.role = role;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public UUID getMemberId() {
        return memberId;
    }

    public Set<String> getPermissions() {
        return permissions;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getRole() {
        return role;
    }

    public boolean isGuest() {
        return memberId == null;
    }

    public boolean isMember() {
        return memberId != null;
    }
}