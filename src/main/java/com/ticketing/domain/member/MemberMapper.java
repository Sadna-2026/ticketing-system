package com.ticketing.domain.member;


public final class MemberMapper {

    private MemberMapper() {
    }

    public static MemberDto toDto(Member member) {
        if (member == null) {
            throw new IllegalArgumentException("member cannot be null");
        }

        return new MemberDto(
                member.getId(),
                member.getUsername(),
                member.getEmail()
        );
    }
}