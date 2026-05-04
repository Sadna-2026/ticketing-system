package com.ticketing.domain.member.response;


import java.util.UUID;

import com.ticketing.domain.member.MemberDto;

public record RegisterResponse(
        boolean success,
        String message,
        UUID memberId,
        MemberDto member,
        String sessionToken
) {
    public static RegisterResponse success(MemberDto member, String sessionToken) {
        return new RegisterResponse(
                true,
                "Member registered and logged in successfully.",
                member.memberId(),
                member,
                sessionToken
        );
    }

    public static RegisterResponse failure(String message) {
        return new RegisterResponse(
                false,
                message,
                null,
                null,
                null
        );
    }
}
