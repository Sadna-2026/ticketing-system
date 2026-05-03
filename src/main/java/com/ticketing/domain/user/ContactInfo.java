package com.ticketing.domain.user;

import java.time.LocalDate;
import java.util.Objects;

// value object representing user's contact information
public class ContactInfo {
    private final String email;
    private final String firstName;
    private final String lastName;
    private final String phoneNumber;
    private final LocalDate dateOfBirth;

    public ContactInfo(String email, String firstName, String lastName, String phoneNumber, LocalDate dateOfBirth) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email is required");
        }
        if (firstName == null || firstName.isBlank()) {
            throw new IllegalArgumentException("First name is required");
        }
        if (lastName == null || lastName.isBlank()) {
            throw new IllegalArgumentException("Last name is required");
        }
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
        this.phoneNumber = phoneNumber;
        this.dateOfBirth = dateOfBirth;
    }

    public String getEmail() { return email; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getPhoneNumber() { return phoneNumber; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }

    /**
     * Calculates the age of the person as of the given date.
     * Returns -1 if dateOfBirth is not set.
     */
    public int getAgeAsOf(LocalDate asOfDate) {
        if (dateOfBirth == null) return -1;
        return java.time.Period.between(dateOfBirth, asOfDate).getYears();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ContactInfo that = (ContactInfo) o;
        return Objects.equals(email, that.email) &&
               Objects.equals(firstName, that.firstName) &&
               Objects.equals(lastName, that.lastName) &&
               Objects.equals(phoneNumber, that.phoneNumber) &&
               Objects.equals(dateOfBirth, that.dateOfBirth);
    }

    @Override
    public String toString() {
        return "ContactInfo{email='" + email + "', name='" + firstName + " " + lastName + "'}";
    }

}
