package com.ticketing.domain.member.communication;

import java.util.Set;
import java.util.UUID;

import com.ticketing.domain.event.IEvent;
import com.ticketing.domain.member.ManagerPermission;
import com.ticketing.domain.member.StaffAppointment.StaffRole;

public class RoleAppointmentOfferRequestedEvent implements IEvent {
    private final UUID appointerId;
    private final UUID targetMemberId;
    private final String companyName;
    private final StaffRole role;
    private final Set<ManagerPermission> permissions;

    public RoleAppointmentOfferRequestedEvent(UUID appointerId, UUID targetMemberId, String companyName, StaffRole role, Set<ManagerPermission> permissions) {
        this.appointerId = appointerId;
        this.targetMemberId = targetMemberId;
        this.companyName = companyName;
        this.role = role;
        this.permissions = permissions;
    }

    public UUID getAppointerId() { return appointerId; }
    public UUID getTargetMemberId() { return targetMemberId; }
    public String getCompanyName() { return companyName; }
    public StaffRole getRole() { return role; }
    public Set<ManagerPermission> getPermissions() { return permissions; }

    @Override
    public String getEventType() {
        return "RoleAppointmentOfferRequested";
    }
}
