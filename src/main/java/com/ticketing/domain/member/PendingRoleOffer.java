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
        this(UUID.randomUUID(), offeredByMemberId, companyName, role, permissions, LocalDateTime.now(), dueDate);
    }

    private PendingRoleOffer(
            UUID offerId,
            UUID offeredByMemberId,
            String companyName,
            StaffRole role,
            Set<ManagerPermission> permissions,
            LocalDateTime createdAt,
            LocalDateTime dueDate
    ) {
        if (offerId == null) {
            throw new IllegalArgumentException("offerId cannot be null");
        }
        if (offeredByMemberId == null) {
            throw new IllegalArgumentException("offeredByMemberId cannot be null");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("createdAt cannot be null");
        }
        if (dueDate == null) {
            throw new IllegalArgumentException("dueDate cannot be null");
        }
        this.offerId = offerId;
        this.offeredByMemberId = offeredByMemberId;
        this.companyName = companyName;
        this.role = role;
        this.permissions = permissions != null ? Collections.unmodifiableSet(permissions) : Collections.emptySet();
        this.createdAt = createdAt;
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

    public PendingRoleOffer detachedCopy() {
        return new PendingRoleOffer(offerId, offeredByMemberId, companyName, role, permissions, createdAt, dueDate);
    }
}
