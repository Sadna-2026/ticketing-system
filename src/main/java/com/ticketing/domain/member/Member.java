package com.ticketing.domain.member;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Optional;
import java.util.Hashtable;
import java.util.List;
import java.util.UUID;

public class Member {

    private final UUID memberId;
    private String username;
    private Hashtable<String,StaffAppointment> staffAppointments; // key is CompanyId
    private String email;
    private String encryptedPassword;
    private String phoneNumber;
    private LocalDate dateOfBirth;
    private List<PendingRoleOffer> pendingOffers;
    private int version;

    public Member(UUID memberId, String username, String email, String encryptedPassword) {
        this(memberId, username, email, encryptedPassword, null, null);
    }

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
        this.pendingOffers = new ArrayList<>();

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

    public int getVersion() {
        return version;
    }

    public void incrementVersion() {
        this.version++;
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

    public void updatePhoneNumber(String newPhoneNumber) {
        if (newPhoneNumber == null || newPhoneNumber.isBlank()) {
            throw new IllegalArgumentException("newPhoneNumber cannot be null or blank");
        }
        this.phoneNumber = newPhoneNumber;
    }

    public void updateDateOfBirth(LocalDate newDateOfBirth) {
        if (newDateOfBirth == null) {
            throw new IllegalArgumentException("newDateOfBirth cannot be null");
        }
        this.dateOfBirth = newDateOfBirth;
    }

    public void updateEncryptedPassword(String newEncryptedPassword) {
        if (newEncryptedPassword == null || newEncryptedPassword.isBlank()) {
            throw new IllegalArgumentException("newEncryptedPassword cannot be null or blank");
        }
        this.encryptedPassword = newEncryptedPassword;
    }

    public synchronized void addStaffAppointment(String companyId, StaffAppointment appointment) {
        if (companyId == null || companyId.isBlank()) {
            throw new IllegalArgumentException("companyId cannot be null or blank");
        }

        if (appointment == null) {
            throw new IllegalArgumentException("appointment cannot be null");
        }

        staffAppointments.put(companyId, appointment);
    }

    public synchronized void removeStaffAppointment(String companyId) {
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

    public synchronized void clearStaffAppointments() {
        staffAppointments.clear();
    }

    public boolean hasStaffAppointment(String companyId, StaffAppointment.StaffRole role) {
        StaffAppointment appointment = getStaffAppointment(companyId);
        return appointment != null && appointment.getRole() == role;
    }

    public List<PendingRoleOffer> getPendingOffers() {
        synchronized(this) {
            return Collections.unmodifiableList(new ArrayList<>(pendingOffers));
        }
    }

    public Optional<PendingRoleOffer> findPendingOffer(UUID offerId) {
        if (offerId == null) {
            throw new IllegalArgumentException("offerId cannot be null");
        }

        synchronized(this) {
            return pendingOffers.stream()
                    .filter(offer -> offer.getOfferId().equals(offerId))
                    .findFirst();
        }
    }

    public synchronized void removePendingOffer(UUID offerId) {
        if (offerId == null) {
            throw new IllegalArgumentException("offerId cannot be null");
        }
        pendingOffers.removeIf(offer -> offer.getOfferId().equals(offerId));
    }

    public synchronized void addPendingOffer(PendingRoleOffer offer) {
        if (offer == null) {
            throw new IllegalArgumentException("offer cannot be null");
        }
        pendingOffers.add(offer);
    }

    public synchronized Member detachedCopy() {
        Member copy = new Member(memberId, username, email, encryptedPassword, phoneNumber, dateOfBirth);
        copy.version = this.version;
        for (String companyId : staffAppointments.keySet()) {
            copy.staffAppointments.put(companyId, staffAppointments.get(companyId).detachedCopy());
        }
        copy.pendingOffers = pendingOffers.stream()
                .map(PendingRoleOffer::detachedCopy)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        return copy;
    }
}
