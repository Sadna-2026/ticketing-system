package com.ticketing.domain.member;


import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class StaffAppointment {

    private final String companyId;
    private final UUID appointedByMemberId;
    private StaffRole role;
    private ManagerPermissions permissions;
    private Set<UUID> appointedStaffMemberIds;

    public StaffAppointment(
            String companyId,
            UUID appointedByMemberId,
            StaffRole role,
            Set<ManagerPermission> permissions
    ) {
        this(companyId, appointedByMemberId, role, new ManagerPermissions(permissions), Collections.emptySet());
    }

    public StaffAppointment(
            String companyId,
            UUID appointedByMemberId,
            StaffRole role,
            Set<ManagerPermission> permissions,
            Set<UUID> appointedStaffMemberIds
    ) {
        this(companyId, appointedByMemberId, role, new ManagerPermissions(permissions), appointedStaffMemberIds);
    }

    public StaffAppointment(
            String companyId,
            UUID appointedByMemberId,
            StaffRole role,
            ManagerPermissions permissions,
            Set<UUID> appointedStaffMemberIds
    ) {
        if (companyId == null || companyId.isBlank()) {
            throw new IllegalArgumentException("companyId cannot be null or blank");
        }
        if (role == null) {
            throw new IllegalArgumentException("role cannot be null");
        }
        if (permissions == null) {
            throw new IllegalArgumentException("permissions cannot be null");
        }

        this.companyId = normalizeCompanyId(companyId);
        this.appointedByMemberId = appointedByMemberId;
        this.role = role;
        this.permissions = permissions;
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
        return permissions.getPermissions();
    }

    public ManagerPermissions getManagerPermissions() {
        return permissions;
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

        return isOwner() || permissions.hasPermission(permission);
    }

    public void promoteToOwner() {
        this.role = StaffRole.OWNER;
        this.permissions = ManagerPermissions.empty();
    }

    public void updateManagerPermissions(ManagerPermissions newPermissions) {
        if (isOwner()) {
            throw new IllegalStateException("Owner does not need manager permissions.");
        }
        if (newPermissions == null) {
            throw new IllegalArgumentException("newPermissions cannot be null");
        }

        this.permissions = newPermissions;
    }

    public void updateManagerPermissions(Set<ManagerPermission> newPermissions) {
        updateManagerPermissions(new ManagerPermissions(newPermissions));
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