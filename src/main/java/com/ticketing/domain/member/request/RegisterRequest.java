package com.ticketing.domain.member.request;

public record RegisterRequest(
        String username,
        String email,
        String password
) {}
