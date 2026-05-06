package com.ticketing.domain.member;


import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class StaffAppointment {

    private final String companyId;
    private final UUID appointedByMemberId;
    private StaffRole role;
    private Set<ManagerPermission> permissions;
    private Set<UUID> appointedStaffMemberIds;

    public StaffAppointment(
            String companyId,
            UUID appointedByMemberId,
            StaffRole role,
            Set<ManagerPermission> permissions
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
        this.permissions = copyPermissions(permissions);
        this.appointedStaffMemberIds = Collections.emptySet();
    }

    public StaffAppointment(
            String companyId,
            UUID appointedByMemberId,
            StaffRole role,
            Set<ManagerPermission> permissions,
            Set<UUID> appointedStaffMemberIds
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
        this.permissions = copyPermissions(permissions);
        this.appointedStaffMemberIds = copyAppointedStaffIds(appointedStaffMemberIds);
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
        return Collections.unmodifiableSet(permissions);
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

    public boolean isOwner() {
        return role == StaffRole.OWNER;
    }

    public boolean isManager() {
        return role == StaffRole.MANAGER;
    }

    public boolean hasPermission(ManagerPermission permission) {
        if (permission == null) {
            return false;
        }

        return isOwner() || permissions.contains(permission);
    }

    public void promoteToOwner() {
        this.role = StaffRole.OWNER;
        this.permissions = Collections.emptySet();
    }

    public void updateManagerPermissions(Set<ManagerPermission> newPermissions) {
        if (isOwner()) {
            throw new IllegalStateException("Owner does not need manager permissions.");
        }

        this.permissions = copyPermissions(newPermissions);
    }

    private Set<ManagerPermission> copyPermissions(Set<ManagerPermission> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            return Collections.emptySet();
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