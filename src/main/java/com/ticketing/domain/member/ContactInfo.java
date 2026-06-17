package com.ticketing.domain.member;

import java.time.LocalDate;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

// value object representing user's contact information
@Embeddable
public class ContactInfo {
    @Column(name = "email")
    private String email;
    @Column(name = "first_name")
    private String firstName;
    @Column(name = "last_name")
    private String lastName;
    @Column(name = "phone_number")
    private String phoneNumber;
    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    // Required by JPA; do not use directly.
    protected ContactInfo() {
    }

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
