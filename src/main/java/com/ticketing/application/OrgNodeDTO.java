package com.ticketing.application;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.ticketing.domain.member.ManagerPermission;
import com.ticketing.domain.member.StaffAppointment;

/**
 * A node in the organization hierarchy representing a staff member, 
 * their role, permissions, and direct subordinates.
 */
public record OrgNodeDTO(
        UUID memberId,
        String username,
        StaffAppointment.StaffRole role,
        Set<ManagerPermission> permissions,
        List<OrgNodeDTO> subordinates
) {}
