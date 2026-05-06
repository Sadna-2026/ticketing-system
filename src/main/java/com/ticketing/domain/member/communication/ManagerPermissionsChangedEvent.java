package com.ticketing.domain.member.communication;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.ticketing.domain.event.IEvent;
import com.ticketing.domain.member.ManagerPermission;

/**
 * Event published when a request to change manager permissions is made.
 */
public class ManagerPermissionsChangedEvent implements IEvent {
    private final UUID callerId;
    private final UUID targetMemberId;
    private final String companyName;
    private final Set<ManagerPermission> newPermissions;

    public ManagerPermissionsChangedEvent(
            UUID callerId,
            UUID targetMemberId,
            String companyName,
            Set<ManagerPermission> newPermissions
    ) {
        this.callerId = callerId;
        this.targetMemberId = targetMemberId;
        this.companyName = companyName;
        this.newPermissions = newPermissions == null ? Collections.emptySet() : Collections.unmodifiableSet(new HashSet<>(newPermissions));
    }

    @Override
    public String getEventType() {
        return "ManagerPermissionsChangedEvent";
    }

    public UUID getCallerId() {
        return callerId;
    }

    public UUID getTargetMemberId() {
        return targetMemberId;
    }

    public String getCompanyName() {
        return companyName;
    }

    public Set<ManagerPermission> getNewPermissions() {
        return newPermissions;
    }
}
