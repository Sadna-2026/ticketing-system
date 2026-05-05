package com.ticketing.domain.member;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;

import com.ticketing.domain.member.StaffAppointment.StaffRole;

public class RoleAppointmentOffer {

    public enum OfferStatus {
        PENDING,
        ACCEPTED,
        REJECTED,
        EXPIRED
    }

    private final UUID offerId;
    private final String companyId;
    private final UUID targetMemberId;
    private final StaffRole offeredRole;
    private final Set<ManagerPermission> offeredPermissions;
    private OfferStatus status;
    private final LocalDateTime createdAt;
    private final LocalDateTime expiryDate;

    public RoleAppointmentOffer(
            String companyId,
            UUID targetMemberId,
            StaffRole offeredRole,
            Set<ManagerPermission> offeredPermissions
    ) {
        this.offerId = UUID.randomUUID();
        this.companyId = companyId;
        this.targetMemberId = targetMemberId;
        this.offeredRole = offeredRole;
        this.offeredPermissions = offeredPermissions != null ? 
            Collections.unmodifiableSet(offeredPermissions) : Collections.emptySet();
        this.status = OfferStatus.PENDING;
        this.createdAt = LocalDateTime.now();
        this.expiryDate = this.createdAt.plusDays(7); // Default expiry
    }

    public UUID getOfferId() { return offerId; }
    public String getCompanyId() { return companyId; }
    public UUID getTargetMemberId() { return targetMemberId; }
    public StaffRole getOfferedRole() { return offeredRole; }
    public Set<ManagerPermission> getOfferedPermissions() { return offeredPermissions; }
    public OfferStatus getStatus() { return status; }
    public boolean isPending() { return status == OfferStatus.PENDING; }

    public void accept() {
        if (status != OfferStatus.PENDING) throw new IllegalStateException("Only pending offers can be accepted");
        this.status = OfferStatus.ACCEPTED;
    }

    public void reject() {
        if (status != OfferStatus.PENDING) throw new IllegalStateException("Only pending offers can be rejected");
        this.status = OfferStatus.REJECTED;
    }
}
