package com.ticketing.domain.member.request;

import java.time.LocalDate;

/**
 * Full replacement request for member identifying details.
 */
public record UpdateMemberDetailsRequest(
        String username,
        String email,
        String phoneNumber,
        LocalDate dateOfBirth
) {
    public UpdateMemberDetailsRequest(String username, String email, String phoneNumber, String dateOfBirth) {
        this(username, email, phoneNumber, LocalDate.parse(dateOfBirth));
    }
}
