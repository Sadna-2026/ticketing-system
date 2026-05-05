package com.ticketing.domain.member.request;

public record RegisterRequest(
        String username,
        String email,
        String password,
        String phoneNumber,
        java.time.LocalDate dateOfBirth
) {
    public RegisterRequest(String username, String email, String password) {
        this(username, email, password, null, null);
    }
}
