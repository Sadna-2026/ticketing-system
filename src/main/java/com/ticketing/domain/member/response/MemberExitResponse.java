package com.ticketing.domain.member.response;

public record MemberExitResponse(
        boolean success,
        String message
) {
    public static MemberExitResponse successResponse(String username) {
        return new MemberExitResponse(
                true,
                "Member " + username + " exited platform successfully."
        );
    }

    public static MemberExitResponse failure(String message) {
        return new MemberExitResponse(
                false,
                message
        );
    }
}