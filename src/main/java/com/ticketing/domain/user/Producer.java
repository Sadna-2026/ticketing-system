package com.ticketing.domain.user;

import java.util.UUID;

public class Producer {
    private String companyName;
    private UUID memberId;
    private UUID appointerId;
    private ProducerRole role;

    public Producer(String companyName, UUID memberId, UUID appointerId, ProducerRole role) {
        if (companyName == null || companyName.isEmpty()) {
            throw new IllegalArgumentException("Company name cannot be null or empty");
        }
        if (memberId == null) {
            throw new IllegalArgumentException("Member ID cannot be null");
        }
        if (role == null) {
            throw new IllegalArgumentException("Role cannot be null");
        }
        if (appointerId != null && appointerId.equals(memberId)) {
            throw new IllegalArgumentException("Appointer cannot be the same as the member");
        }
        if (appointerId != null && role.getRoleName().equals("Founder")) {
            throw new IllegalArgumentException("A Founder can't have an appointer");
        }
        if (appointerId == null && !(role.getRoleName().equals("Founder"))) {
            throw new IllegalArgumentException("Only a Founder can have a null appointer");
        }

        this.companyName = companyName;
        this.memberId = memberId;
        this.appointerId = appointerId;
        this.role = role;
    }

    public String getCompanyName() {
        return companyName;
    }

    public UUID getMemberId() {
        return memberId;
    }

    public UUID getAppointerId() {
        return appointerId;
    }

    public void setAppointerId(UUID appointerId) {
        this.appointerId = appointerId;
    }

    public void setRole(ProducerRole role) {
        this.role = role;
    }

    // call role permissions to check if the producer can perform an action
    public String getRoleName() {
        return role.getRoleName();
    }
    
    public boolean canAppoint() {
        return role.canAppoint();
    }

    public boolean canDefineMap() {
        return role.canDefineMap();
    }

    public boolean canManageInventory() {
        return role.canManageInventory();
    }

    public boolean canModifyPolicy() {
        return role.canModifyPolicy();
    }

    public boolean canManagePersonnel() {
        return role.canManagePersonnel();
    }

    public boolean canViewReports() {
        return role.canViewReports();
    }

    public boolean canHandleInquiries() {
        return role.canHandleInquiries();
    }

    public boolean canHandleEventLifecycle() {
        return role.canHandleEventLifecycle();
    }
}
