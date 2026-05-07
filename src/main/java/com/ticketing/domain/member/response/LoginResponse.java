package com.ticketing.domain.member.response;

import com.ticketing.domain.member.MemberDto;

public record LoginResponse(
        boolean success,
        String message,
        MemberDto member,
        String sessionToken
) {
    public static LoginResponse success(MemberDto member, String sessionToken) {
        return new LoginResponse(
                true,
                "Member logged in successfully.",
                member,
                sessionToken
        );
    }

    public static LoginResponse failure(String message) {
        return new LoginResponse(
                false,
                message,
                null,
                null
        );
    }
}
