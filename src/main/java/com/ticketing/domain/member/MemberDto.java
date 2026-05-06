package com.ticketing.domain.member;

import java.time.LocalDate;
import java.util.UUID;

public record MemberDto(
        UUID memberId,
        String username,
        String email,
        String phoneNumber,
        LocalDate dateOfBirth
) {}
