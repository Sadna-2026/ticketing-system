package com.ticketing.presentation.vaadin.util;

import java.util.Set;
import java.util.UUID;

import com.vaadin.flow.server.VaadinSession;

public final class SessionContext {

    private static final String SYSTEM_ADMIN_PERMISSION = "SYSTEM_ADMIN";

    private static final String SESSION_TOKEN_KEY = "sessionToken";
    private static final String SESSION_ID_KEY = "sessionId";
    private static final String MEMBER_ID_KEY = "memberId";
    private static final String USERNAME_KEY = "username";
    private static final String ROLE_KEY = "role";
    private static final String PERMISSIONS_KEY = "permissions";

    private SessionContext() {
    }

    public static void setSessionToken(String token) {
        set(SESSION_TOKEN_KEY, token);
    }

    public static String getSessionToken() {
        return get(SESSION_TOKEN_KEY, String.class);
    }

    public static void setSessionId(UUID sessionId) {
        set(SESSION_ID_KEY, sessionId);
    }

    public static UUID getSessionId() {
        return get(SESSION_ID_KEY, UUID.class);
    }

    public static void setMemberId(UUID memberId) {
        set(MEMBER_ID_KEY, memberId);
    }

    public static UUID getMemberId() {
        return get(MEMBER_ID_KEY, UUID.class);
    }

    public static void setUsername(String username) {
        set(USERNAME_KEY, username);
    }

    public static String getUsername() {
        return get(USERNAME_KEY, String.class);
    }

    public static void setRole(String role) {
        set(ROLE_KEY, role);
    }

    public static String getRole() {
        return get(ROLE_KEY, String.class);
    }

    public static void setPermissions(Set<String> permissions) {
        set(PERMISSIONS_KEY, permissions == null ? Set.of() : Set.copyOf(permissions));
    }

    @SuppressWarnings("unchecked")
    public static Set<String> getPermissions() {
        Set<String> permissions = get(PERMISSIONS_KEY, Set.class);
        return permissions == null ? Set.of() : permissions;
    }

    public static boolean hasSessionToken() {
        return getSessionToken() != null && !getSessionToken().isBlank();
    }

    public static boolean isGuestSession() {
        return hasSessionToken() && !isLoggedInMember();
    }

    public static boolean isLoggedInMember() {
        return getMemberId() != null;
    }

    public static boolean hasPermission(String permission) {
        return permission != null && getPermissions().contains(permission);
    }

    public static boolean isSystemAdmin() {
        return isLoggedInMember() && hasPermission(SYSTEM_ADMIN_PERMISSION);
    }

    public static UiState currentUiState() {
        return new UiState(
                hasSessionToken(),
                isGuestSession(),
                isLoggedInMember(),
                isSystemAdmin(),
                getUsername(),
                getRole()
        );
    }

    public static String currentSessionLabel() {
        UiState state = currentUiState();
        if (state.loggedInMember()) {
            if (state.username() != null && !state.username().isBlank()) {
                return "Current session: Member (" + state.username() + ")";
            }
            return "Current session: Member";
        }

        if (state.guest()) {
            return "Current session: Guest";
        }

        return "Current session: none";
    }

    public static void clear() {
        set(SESSION_TOKEN_KEY, null);
        set(SESSION_ID_KEY, null);
        set(MEMBER_ID_KEY, null);
        set(USERNAME_KEY, null);
        set(ROLE_KEY, null);
        set(PERMISSIONS_KEY, null);
    }

    public record UiState(
            boolean hasSessionToken,
            boolean guest,
            boolean loggedInMember,
            boolean systemAdmin,
            String username,
            String role
    ) {
        public boolean noSession() {
            return !hasSessionToken;
        }
    }

    private static void set(String key, Object value) {
        VaadinSession session = VaadinSession.getCurrent();
        if (session != null) {
            session.setAttribute(key, value);
        }
    }

    private static <T> T get(String key, Class<T> type) {
        VaadinSession session = VaadinSession.getCurrent();
        if (session == null) {
            return null;
        }

        Object value = session.getAttribute(key);
        if (type.isInstance(value)) {
            return type.cast(value);
        }

        return null;
    }
}
