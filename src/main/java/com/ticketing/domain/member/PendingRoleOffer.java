package com.ticketing.domain.member;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;

import com.ticketing.domain.member.StaffAppointment.StaffRole;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

@Entity
@Table(name = "pending_role_offers")
public class PendingRoleOffer {
    @Id
    @Column(name = "offer_id")
    private UUID offerId;
    @Column(name = "offered_by_member_id")
    private UUID offeredByMemberId;
    @Column(name = "company_name")
    private String companyName;
    @Enumerated(EnumType.STRING)
    @Column(name = "role")
    private StaffRole role;
    @ElementCollection
    @CollectionTable(
            name = "pending_role_offer_permissions",
            joinColumns = @JoinColumn(name = "offer_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "permission")
    private Set<ManagerPermission> permissions;
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    @Column(name = "due_date")
    private LocalDateTime dueDate;

    // Required by JPA; do not use directly.
    protected PendingRoleOffer() {
    }

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
