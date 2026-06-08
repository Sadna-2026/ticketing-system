package com.ticketing.domain.member;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Hashtable;
import java.util.List;
import java.util.Optional;
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
    private List<Suspension> suspensions;
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
        this.suspensions = new ArrayList<>();
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
        return appointment != null && !appointment.isRevoked() && appointment.getRole() == role;
    }

    public void authorizePolicyModification(String companyName) {
        StaffAppointment appt = getStaffAppointment(companyName);
        if (appt == null) {
            throw new SecurityException("Caller is not a staff member of company: " + companyName);
        }
        boolean allowed = appt.isOwner()
                || (appt.isManager() && appt.hasPermission(ManagerPermission.POLICY_MODIFICATION));
        if (!allowed) {
            throw new SecurityException("Insufficient permissions: POLICY_MODIFICATION required");
        }
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

    // ── Suspension support (V2 UC-II.6.7 / UC-II.6.8) ──────────────

    /**
     * Domain method: creates and applies a suspension to this member.
     * @param adminId   the admin who imposed the suspension
     * @param duration  suspension length, or null for permanent
     * @param reason    human-readable reason
     * @return the created Suspension
     */
    public Suspension suspend(UUID adminId, Duration duration, String reason) {
        if (adminId == null) {
            throw new IllegalArgumentException("adminId is required");
        }
        Suspension suspension = new Suspension(adminId, Instant.now(), duration, reason);
        suspensions.add(suspension);
        return suspension;
    }

    /**
     * Domain method: cancels an active suspension by ID.
     * @param suspensionId the suspension to cancel
     */
    public void cancelSuspension(UUID suspensionId) {
        if (suspensionId == null) {
            throw new IllegalArgumentException("suspensionId is required");
        }
        Suspension suspension = suspensions.stream()
                .filter(s -> s.getSuspensionId().equals(suspensionId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Suspension not found: " + suspensionId));

        if (!suspension.isActive(Instant.now())) {
            throw new IllegalStateException("Suspension is not currently active");
        }
        suspension.cancel();
    }

    public synchronized void addSuspension(Suspension suspension) {
        if (suspension == null) {
            throw new IllegalArgumentException("suspension cannot be null");
        }
        suspensions.add(suspension);
    }

    /**
     * Returns true if the member has an active suspension at the given instant.
     */
    public boolean isSuspended(Instant now) {
        return getActiveSuspension(now) != null;
    }

    /**
     * Returns the currently active suspension, or null if not suspended.
     * If multiple are active, returns the one that expires latest (or permanent).
     */
    public Suspension getActiveSuspension(Instant now) {
        Suspension active = null;
        for (Suspension s : suspensions) {
            if (s.isActive(now)) {
                if (active == null) {
                    active = s;
                } else if (s.isPermanent()) {
                    active = s; // permanent wins
                } else if (!active.isPermanent() && s.getEndTime().isAfter(active.getEndTime())) {
                    active = s; // longer duration wins
                }
            }
        }
        return active;
    }

    public List<Suspension> getSuspensions() {
        synchronized (this) {
            return Collections.unmodifiableList(new ArrayList<>(suspensions));
        }
    }

    /**
     * Guards state-changing operations. Call this at the domain boundary
     * to block suspended users from performing mutations.
     */
    public void rejectIfSuspended(Instant now) {
        Suspension active = getActiveSuspension(now);
        if (active != null) {
            String msg = active.isPermanent()
                    ? "Account is permanently suspended"
                    : "Account is suspended until " + active.getEndTime();
            if (active.getReason() != null) {
                msg += ". Reason: " + active.getReason();
            }
            throw new IllegalStateException(msg);
        }
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
        copy.suspensions = suspensions.stream()
                .map(Suspension::detachedCopy)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        return copy;
    }
}
