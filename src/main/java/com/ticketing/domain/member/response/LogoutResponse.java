package com.ticketing.domain.member.response;

public record LogoutResponse(
        boolean success,
        String message,
        String sessionToken
) {
    public static LogoutResponse success(String guestToken) {
        return new LogoutResponse(
                true,
                "Member logged out successfully.",
                guestToken
        );
    }

    public static LogoutResponse failure(String message) {
        return new LogoutResponse(
                false,
                message,
                null
        );
    }
}