package com.ticketing.domain.member;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class StaffAppointment {

    private final String companyId;
    private UUID appointedByMemberId;
    private StaffRole role;
    private Set<ManagerPermission> managerPermissions;
    private Set<UUID> appointedStaffMemberIds;
    private boolean revoked;

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
