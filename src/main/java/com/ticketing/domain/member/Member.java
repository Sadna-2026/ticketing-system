package com.ticketing.domain.member;

import java.time.LocalDate;
import java.util.Hashtable;
import java.util.UUID;

public class Member {

    private final UUID memberId;
    private String username;
    private Hashtable<String,StaffAppointment> staffAppointments; // key is CompanyId
    private String email;
    private String encryptedPassword;
    private String phoneNumber;
    private LocalDate dateOfBirth;

    public Member(UUID memberId, String username, String email, String encryptedPassword, String phoneNumber, LocalDate dateOfBirth) {
        if (memberId == null) {
            throw new IllegalArgumentException("memberId cannot be null");
        }

        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username cannot be null or blank");
        }

        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email cannot be null or blank");
        }

        if (encryptedPassword == null || encryptedPassword.isBlank()) {
            throw new IllegalArgumentException("encryptedPassword cannot be null or blank");
        }

        this.memberId = memberId;
        this.username = username;
        this.email = email;
        this.encryptedPassword = encryptedPassword;
        this.staffAppointments  = new Hashtable<>();
        this.phoneNumber = phoneNumber;
        this.dateOfBirth = dateOfBirth;
    }
    
    // Getters
    public UUID getId() {
        return memberId;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getEncryptedPassword() {
        return encryptedPassword;
    }

     public String getPhoneNumber() {
        return this.phoneNumber;
    }

     public LocalDate getDateOfBirth() {
        return this.dateOfBirth;
    }


    // setters 
    public void updateUsername(String newUsername) {
        if (newUsername == null || newUsername.isBlank()) {
            throw new IllegalArgumentException("newUsername cannot be null or blank");
        }
        this.username = newUsername;
    }

    public void updateEmail(String newEmail) {
        if (newEmail == null || newEmail.isBlank()) {
            throw new IllegalArgumentException("newEmail cannot be null or blank");
        }
        this.email = newEmail;
    }

    public void updateEncryptedPassword(String newEncryptedPassword) {
        if (newEncryptedPassword == null || newEncryptedPassword.isBlank()) {
            throw new IllegalArgumentException("newEncryptedPassword cannot be null or blank");
        }
        this.encryptedPassword = newEncryptedPassword;
    }

    public void addStaffAppointment(String companyId, StaffAppointment appointment) {
        if (companyId == null || companyId.isBlank()) {
            throw new IllegalArgumentException("companyId cannot be null or blank");
        }

        if (appointment == null) {
            throw new IllegalArgumentException("appointment cannot be null");
        }

        staffAppointments.put(companyId, appointment);
    }

    public void removeStaffAppointment(String companyId) {
        if (companyId == null || companyId.isBlank()) {
            throw new IllegalArgumentException("companyId cannot be null or blank");
        }

        staffAppointments.remove(companyId);
    }

    public StaffAppointment getStaffAppointment(String companyId) {
        if (companyId == null || companyId.isBlank()) {
            throw new IllegalArgumentException("companyId cannot be null or blank");
        }

        return staffAppointments.get(companyId);
    }

    public void clearStaffAppointments() {
        staffAppointments.clear();
    }
}