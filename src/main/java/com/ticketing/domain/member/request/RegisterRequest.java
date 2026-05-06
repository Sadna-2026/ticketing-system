package com.ticketing.domain.member.request;
import java.time.LocalDate;
public record RegisterRequest(
        String username,
        String email,
        String password,
        String phoneNumber,
        LocalDate dateOfBirth
) {
    public RegisterRequest(String username, String email, String password, String phoneNumber, String dateOfBirth) {
        this(username, email, password, phoneNumber, LocalDate.parse(dateOfBirth));
    }
}
