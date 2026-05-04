package com.ticketing.domain.member;

import java.util.UUID;

public record MemberDto(
        UUID memberId,
        String username,
        String email
) {}
