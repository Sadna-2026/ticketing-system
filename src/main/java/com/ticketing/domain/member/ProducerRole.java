package com.ticketing.domain.member;

public interface ProducerRole {
    String getRoleName();
    boolean canAppoint();
    boolean canDefineMap();
    boolean canManageInventory();
    boolean canModifyPolicy();
    boolean canManagePersonnel();
    boolean canViewReports();
    boolean canHandleInquiries();
    boolean canHandleEventLifecycle();
}
