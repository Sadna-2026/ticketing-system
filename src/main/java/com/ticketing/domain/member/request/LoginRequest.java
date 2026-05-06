package com.ticketing.domain.member.request;

/**
 * Credentials submitted by a guest who wants to become a logged-in member.
 */
public record LoginRequest(
        String username,
        String password
) {}
