package com.ticketing.domain.member;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "staff_appointments")
public class StaffAppointment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    // #510: optimistic-lock guard. Mutators (revoke, promoteToOwner, updateManagerPermissions,
    // add/removeAppointedStaffMember, updateAppointedBy) can be invoked concurrently from
    // different owners managing the same staff record. The Member root's @Version only catches
    // conflicts when the same Member is reloaded and saved; concurrent direct edits to a single
    // appointment row need their own version to reject the loser.
    @Version
    @Column(name = "version")
    private int version;

    @Column(name = "company_id")
    private String companyId;
    @Column(name = "appointed_by_member_id")
    private UUID appointedByMemberId;
    @Enumerated(EnumType.STRING)
    @Column(name = "role")
    private StaffRole role;
    @ElementCollection
    @CollectionTable(
            name = "staff_appointment_permissions",
            joinColumns = @JoinColumn(name = "staff_appointment_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "permission")
    private Set<ManagerPermission> managerPermissions;
    @ElementCollection
    @CollectionTable(
            name = "staff_appointment_appointed_staff",
            joinColumns = @JoinColumn(name = "staff_appointment_id"))
    @Column(name = "appointed_staff_member_id")
    private Set<UUID> appointedStaffMemberIds;
    @Column(name = "revoked")
    private boolean revoked;

    // Required by JPA; do not use directly.
    protected StaffAppointment() {
    }

    public StaffAppointment(
            String companyId,
            UUID appointedByMemberId,
            StaffRole role,
            Set<ManagerPermission> permissions
    ) {
        this(companyId, appointedByMemberId, role, permissions, Collections.emptySet());
    }

    public StaffAppointment(
            String companyId,
            UUID appointedByMemberId,
            StaffRole role,
            Set<ManagerPermission> permissions,
            Set<UUID> appointedStaffMemberIds
    ) {
        this(companyId, appointedByMemberId, role, permissions, appointedStaffMemberIds, false);
    }

    private StaffAppointment(
            String companyId,
            UUID appointedByMemberId,
            StaffRole role,
            Set<ManagerPermission> permissions,
            Set<UUID> appointedStaffMemberIds,
            boolean revoked
    ) {
        if (companyId == null || companyId.isBlank()) {
            throw new IllegalArgumentException("companyId cannot be null or blank");
        }
        if (role == null) {
            throw new IllegalArgumentException("role cannot be null");
        }

        this.companyId = normalizeCompanyId(companyId);
        this.appointedByMemberId = appointedByMemberId;
        this.role = role;
        this.managerPermissions = copyManagerPermissions(permissions);
        this.appointedStaffMemberIds = copyAppointedStaffIds(appointedStaffMemberIds);
        this.revoked = revoked;
    }

    public String getCompanyId() {
        return companyId;
    }

    public UUID getAppointedByMemberId() {
        return appointedByMemberId;
    }

    public StaffRole getRole() {
        return role;
    }

    public Set<ManagerPermission> getPermissions() {
        return managerPermissions;
    }

    public Set<UUID> getAppointedStaffMemberIds() {
        return Collections.unmodifiableSet(appointedStaffMemberIds);
    }

    public void addAppointedStaffMember(UUID memberId) {
        if (memberId == null) {
            throw new IllegalArgumentException("memberId cannot be null");
        }

        Set<UUID> updated = new HashSet<>(appointedStaffMemberIds);
        updated.add(memberId);
        this.appointedStaffMemberIds = copyAppointedStaffIds(updated);
    }

    public void addAppointedStaffMemberGroup(Set<UUID> memberIds) {
        if (memberIds == null) {
            throw new IllegalArgumentException("memberIds cannot be null");
        }

        Set<UUID> updated = new HashSet<>(appointedStaffMemberIds);
        updated.addAll(memberIds);
        this.appointedStaffMemberIds = copyAppointedStaffIds(updated);
    }

    public void removeAppointedStaffMember(UUID memberId) {
        if (memberId == null) {
            throw new IllegalArgumentException("memberId cannot be null");
        }

        Set<UUID> updated = new HashSet<>(appointedStaffMemberIds);
        updated.remove(memberId);
        this.appointedStaffMemberIds = copyAppointedStaffIds(updated);
    }

    public boolean isOwner() {
        return role == StaffRole.OWNER && !revoked;
    }

    public boolean isManager() {
        return role == StaffRole.MANAGER;
    }

    public boolean isRevoked() {
        return revoked;
    }

    public boolean isActive() {
        return !revoked;
    }

    public void revoke() {
        if (revoked) {
            throw new IllegalStateException("Appointment is already revoked.");
        }
        this.revoked = true;
    }

    public boolean hasPermission(ManagerPermission permission) {
        if (permission == null || revoked) {
            return false;
        }

        return isOwner() || managerPermissions.contains(permission);
    }

    public void promoteToOwner() {
        this.role = StaffRole.OWNER;
        this.managerPermissions = Collections.emptySet();
    }

    public void updateManagerPermissions(Set<ManagerPermission> newPermissions) {
        if (isOwner()) {
            throw new IllegalStateException("Owner does not need manager permissions.");
        }

        this.managerPermissions = copyManagerPermissions(newPermissions);
    }

    public void updateAppointedBy(UUID newAppointerId) {
        this.appointedByMemberId = newAppointerId;
    }

    public StaffAppointment detachedCopy() {
        return new StaffAppointment(
                companyId,
                appointedByMemberId,
                role,
                managerPermissions,
                appointedStaffMemberIds,
                revoked);
    }

    private static Set<ManagerPermission> copyManagerPermissions(Set<ManagerPermission> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            return Collections.emptySet();
        }
        for (ManagerPermission p : permissions) {
            if (p == null) {
                throw new IllegalArgumentException("Permissions set cannot contain null values.");
            }
        }
        return Collections.unmodifiableSet(new HashSet<>(permissions));
    }

    private Set<UUID> copyAppointedStaffIds(Set<UUID> appointedStaffMemberIds) {
        if (appointedStaffMemberIds == null || appointedStaffMemberIds.isEmpty()) {
            return Collections.emptySet();
        }

        return Collections.unmodifiableSet(new HashSet<>(appointedStaffMemberIds));
    }

    private String normalizeCompanyId(String companyId) {
        return companyId.trim().toLowerCase();
    }

    public enum StaffRole {
        OWNER,
        MANAGER
    }
}
