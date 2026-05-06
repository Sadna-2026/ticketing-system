package com.ticketing.domain.member;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;

import com.ticketing.domain.member.StaffAppointment.StaffRole;

public class PendingRoleOffer {
    private final UUID offerId;
    private final UUID offeredByMemberId;
    private final String companyName;
    private final StaffRole role;
    private final Set<ManagerPermission> permissions;
    private final LocalDateTime createdAt;
    private final LocalDateTime dueDate;

    public PendingRoleOffer(UUID offeredByMemberId, String companyName, StaffRole role, Set<ManagerPermission> permissions, LocalDateTime dueDate) {
        if (offeredByMemberId == null) {
            throw new IllegalArgumentException("offeredByMemberId cannot be null");
        }
        if (dueDate == null) {
            throw new IllegalArgumentException("dueDate cannot be null");
        }
        this.offerId = UUID.randomUUID();
        this.offeredByMemberId = offeredByMemberId;
        this.companyName = companyName;
        this.role = role;
        this.permissions = permissions != null ? Collections.unmodifiableSet(permissions) : Collections.emptySet();
        this.createdAt = LocalDateTime.now();
        this.dueDate = dueDate;
    }

    public UUID getOfferId() { return offerId; }
    public UUID getOfferedByMemberId() { return offeredByMemberId; }
    public String getCompanyName() { return companyName; }
    public StaffRole getRole() { return role; }
    public Set<ManagerPermission> getPermissions() { return permissions; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getDueDate() { return dueDate; }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(dueDate);
    }
}
