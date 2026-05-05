package com.ticketing.domain.company;

import java.util.Objects;
import java.util.UUID;

import com.ticketing.domain.event.IPurchasePolicy;

public class Company {
    private String name; // also the unique identifier for the company
    private String description;
    private final UUID founderId;
    private CompanyStatus status;

    public Company(String name, String description, UUID founderId) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Company name is required");
        }
        if (founderId == null) {
            throw new IllegalArgumentException("Founder ID is required");
        }

        this.name = name;
        this.description = description;
        this.founderId = founderId;
        this.status = CompanyStatus.ACTIVE;
    }

    // --- Getters ---
    public String getName() { return name; }
    public String getDescription() { return description; }
    public UUID getFounderId() { return founderId; }
    public CompanyStatus getStatus() { return status; }

    public boolean isActive() { return status == CompanyStatus.ACTIVE; }

    // --- Setters ---
    public void setName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Name cannot be blank");
        }
        this.name = name;
    }

    public void setDescription(String description) { 
        this.description = description;
    }

    // --- Domain Logic with Permission Checks ---

    /**
     * Example method: Edits the company policy.
     * Requires POLICY_MODIFICATION permission.
     */
    public void editPolicy(com.ticketing.domain.member.Member member) {
        checkPermission(member, com.ticketing.domain.member.ManagerPermission.POLICY_MODIFICATION);
        // Policy modification logic would go here
    }

    /**
     * Example method: Defines the venue map.
     * Requires MAP_DEFINITION permission.
     */
    public void defineMap(com.ticketing.domain.member.Member member) {
        checkPermission(member, com.ticketing.domain.member.ManagerPermission.MAP_DEFINITION);
        // Map definition logic would go here
    }

    /**
     * Example method: Manages company personnel.
     * Requires PERSONNEL_MGMT permission.
     */
    public void managePersonnel(com.ticketing.domain.member.Member member) {
        checkPermission(member, com.ticketing.domain.member.ManagerPermission.PERSONNEL_MGMT);
        // Personnel management logic would go here
    }

    /**
     * Example method: Views company reports.
     * Requires VIEW_REPORTS permission.
     */
    public void viewReports(com.ticketing.domain.member.Member member) {
        checkPermission(member, com.ticketing.domain.member.ManagerPermission.VIEW_REPORTS);
        // Report viewing logic would go here
    }

    /**
     * Internal helper to check domain-level permissions.
     * Role-based permission should be checked within the domain as per project standards.
     */
    private void checkPermission(com.ticketing.domain.member.Member member, com.ticketing.domain.member.ManagerPermission permission) {
        if (member == null) {
            throw new IllegalArgumentException("Member is required for permission check");
        }

        com.ticketing.domain.member.StaffAppointment appointment = member.getStaffAppointment(this.name);
        
        if (appointment == null || !appointment.hasPermission(permission)) {
            throw new com.ticketing.domain.member.PermissionDeniedException(
                "Member " + member.getId() + " does not have permission " + permission + " for company " + this.name
            );
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Company other = (Company)o;
        return Objects.equals(name, other.name); // Equality based on unique name
    }

}
