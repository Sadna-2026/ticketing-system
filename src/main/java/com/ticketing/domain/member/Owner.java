package com.ticketing.domain.member;

public class Owner implements ProducerRole {

    @Override
    public String getRoleName() {
        return "Owner";
    }

    @Override
    public boolean canAppoint() {
        return true;
    }

    @Override
    public boolean canDefineMap() {
        return true;
    }

    @Override
    public boolean canManageInventory() {
        return true;
    }

    @Override
    public boolean canModifyPolicy() {
        return true;
    }

    @Override
    public boolean canManagePersonnel() {
        return true;
    }

    @Override
    public boolean canViewReports() {
        return true;
    }

    @Override
    public boolean canHandleInquiries() {
        return true;
    }

    @Override
    public boolean canHandleEventLifecycle() {
        return true;
    }
}