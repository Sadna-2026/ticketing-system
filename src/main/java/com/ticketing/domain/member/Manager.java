package com.ticketing.domain.member;

import java.util.List;

public class Manager implements ProducerRole {
    private List<ManagerPermission> permissions;

    public Manager(List<ManagerPermission> permissions) {
        this.permissions = permissions;
    }

    @Override
    public String getRoleName() {
        return "Manager";
    }

    @Override
    public boolean canAppoint() {
        return false; // Managers cannot appoint other producers
    }

    @Override
    public boolean canDefineMap() {
        return permissions.contains(ManagerPermission.MAP_DEFINITION);
    }

    @Override
    public boolean canManageInventory() {
        return permissions.contains(ManagerPermission.INVENTORY_MGMT);
    }

    @Override
    public boolean canModifyPolicy() {
        return permissions.contains(ManagerPermission.POLICY_MODIFICATION);
    }

    @Override
    public boolean canManagePersonnel() {
        return permissions.contains(ManagerPermission.PERSONNEL_MGMT);
    }

    @Override
    public boolean canViewReports() {
        return permissions.contains(ManagerPermission.VIEW_REPORTS);
    }

    @Override
    public boolean canHandleInquiries() {
        return permissions.contains(ManagerPermission.HANDLE_INQUIRIES);
    }

    @Override
    public boolean canHandleEventLifecycle() {
        return permissions.contains(ManagerPermission.EVENT_LIFECYCLE);
    }

}
