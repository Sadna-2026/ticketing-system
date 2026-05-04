package com.ticketing.domain.user;

import java.util.Hashtable;
import java.util.UUID;

public class Member {

    private final UUID memberId;
    private final String username;
    private final Hashtable<String,StaffAppointment> staffAppointments; // key is CompanyId
    private final String email;
    private final String encryptedPassword;

    public Member(UUID memberId, String username, String email, String encryptedPassword) {
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

    }

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






}