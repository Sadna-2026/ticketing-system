package com.ticketing.domain.member.response;

import com.ticketing.domain.member.MemberDto;

public record UpdateMemberDetailsResponse(
        boolean success,
        String message,
        MemberDto member
) {
    public static UpdateMemberDetailsResponse success(MemberDto member) {
        return new UpdateMemberDetailsResponse(
                true,
                "Member details updated successfully.",
                member
        );
    }

    public static UpdateMemberDetailsResponse failure(String message) {
        return new UpdateMemberDetailsResponse(
                false,
                message,
                null
        );
    }
}
