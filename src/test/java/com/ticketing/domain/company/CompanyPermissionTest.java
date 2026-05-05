package com.ticketing.domain.company;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ticketing.domain.member.ManagerPermission;
import com.ticketing.domain.member.Member;
import com.ticketing.domain.member.PermissionDeniedException;
import com.ticketing.domain.member.StaffAppointment;

public class CompanyPermissionTest {

    private Company company;
    private String companyName = "TestCompany";
    private UUID founderId = UUID.randomUUID();

    @BeforeEach
    public void setUp() {
        company = new Company(companyName, "A test company", founderId);
    }

    @Test
    public void testOwnerHasImplicitPermissions() {
        // Create an owner member
        UUID memberId = UUID.randomUUID();
        Member owner = new Member(memberId, "owner", "owner@example.com", "pass");
        
        // Give them an owner appointment
        StaffAppointment appointment = new StaffAppointment(
            companyName,
            founderId,
            StaffAppointment.StaffRole.OWNER,
            Collections.emptySet()
        );
        owner.addStaffAppointment(companyName, appointment);

        // Should NOT throw exception for any permission
        assertDoesNotThrow(() -> company.checkPermission(owner, ManagerPermission.POLICY_MODIFICATION));
        assertDoesNotThrow(() -> company.checkPermission(owner, ManagerPermission.MAP_DEFINITION));
        assertDoesNotThrow(() -> company.checkPermission(owner, ManagerPermission.PERSONNEL_MGMT));
        assertDoesNotThrow(() -> company.checkPermission(owner, ManagerPermission.VIEW_REPORTS));
    }

    @Test
    public void testManagerWithSpecificPermissionSucceeds() {
        UUID memberId = UUID.randomUUID();
        Member manager = new Member(memberId, "manager", "manager@example.com", "pass");
        
        // Give them only POLICY_MODIFICATION permission
        Set<ManagerPermission> permissions = new HashSet<>();
        permissions.add(ManagerPermission.POLICY_MODIFICATION);
        
        StaffAppointment appointment = new StaffAppointment(
            companyName,
            founderId,
            StaffAppointment.StaffRole.MANAGER,
            permissions
        );
        manager.addStaffAppointment(companyName, appointment);

        // Should succeed for POLICY_MODIFICATION
        assertDoesNotThrow(() -> company.checkPermission(manager, ManagerPermission.POLICY_MODIFICATION));
        
        // Should throw for others
        assertThrows(PermissionDeniedException.class, () -> company.checkPermission(manager, ManagerPermission.MAP_DEFINITION));
        assertThrows(PermissionDeniedException.class, () -> company.checkPermission(manager, ManagerPermission.PERSONNEL_MGMT));
    }

    @Test
    public void testManagerWithoutPermissionsThrows() {
        UUID memberId = UUID.randomUUID();
        Member manager = new Member(memberId, "manager", "manager@example.com", "pass");
        
        // No permissions granted
        StaffAppointment appointment = new StaffAppointment(
            companyName,
            founderId,
            StaffAppointment.StaffRole.MANAGER,
            Collections.emptySet()
        );
        manager.addStaffAppointment(companyName, appointment);

        // Should throw for all actions
        assertThrows(PermissionDeniedException.class, () -> company.checkPermission(manager, ManagerPermission.POLICY_MODIFICATION));
        assertThrows(PermissionDeniedException.class, () -> company.checkPermission(manager, ManagerPermission.MAP_DEFINITION));
        assertThrows(PermissionDeniedException.class, () -> company.checkPermission(manager, ManagerPermission.VIEW_REPORTS));
    }

    @Test
    public void testNonStaffMemberThrows() {
        UUID memberId = UUID.randomUUID();
        Member guest = new Member(memberId, "guest", "guest@example.com", "pass");
        
        // No appointment for this company

        // Should throw for all actions
        assertThrows(PermissionDeniedException.class, () -> company.checkPermission(guest, ManagerPermission.POLICY_MODIFICATION));
        assertThrows(PermissionDeniedException.class, () -> company.checkPermission(guest, ManagerPermission.MAP_DEFINITION));
    }

    @Test
    public void testDifferentCompanyAppointmentThrows() {
        UUID memberId = UUID.randomUUID();
        Member manager = new Member(memberId, "manager", "manager@example.com", "pass");
        
        // Appointment for a DIFFERENT company
        StaffAppointment appointment = new StaffAppointment(
            "OtherCompany",
            founderId,
            StaffAppointment.StaffRole.OWNER,
            Collections.emptySet()
        );
        manager.addStaffAppointment("OtherCompany", appointment);

        // Should throw for this company
        assertThrows(PermissionDeniedException.class, () -> company.checkPermission(manager, ManagerPermission.POLICY_MODIFICATION));
    }
}
