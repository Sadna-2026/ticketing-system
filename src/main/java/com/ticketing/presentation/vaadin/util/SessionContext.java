package com.ticketing.presentation.vaadin.util;

import java.util.UUID;

import com.vaadin.flow.server.VaadinSession;

public final class SessionContext {

    private static final String SESSION_TOKEN_KEY = "sessionToken";
    private static final String SESSION_ID_KEY = "sessionId";
    private static final String MEMBER_ID_KEY = "memberId";
    private static final String USERNAME_KEY = "username";
    private static final String ROLE_KEY = "role";

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

    public static boolean hasSessionToken() {
        return getSessionToken() != null && !getSessionToken().isBlank();
    }

    public static boolean isLoggedInMember() {
        return getMemberId() != null;
    }

    public static void clear() {
        set(SESSION_TOKEN_KEY, null);
        set(SESSION_ID_KEY, null);
        set(MEMBER_ID_KEY, null);
        set(USERNAME_KEY, null);
        set(ROLE_KEY, null);
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
