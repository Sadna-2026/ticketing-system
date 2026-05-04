package com.ticketing.domain.member;

public record RegisterRequest(
        String username,
        String email,
        String password
) {}
