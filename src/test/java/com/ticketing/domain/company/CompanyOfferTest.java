package com.ticketing.domain.company;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.Set;
import java.util.Collections;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ticketing.domain.member.ManagerPermission;
import com.ticketing.domain.member.Member;
import com.ticketing.domain.member.PermissionDeniedException;
import com.ticketing.domain.member.RoleAppointmentOffer;
import com.ticketing.domain.member.StaffAppointment;
import com.ticketing.domain.member.StaffAppointment.StaffRole;

public class CompanyOfferTest {

    private Company company;
    private String companyName = "TestCompany";
    private UUID founderId = UUID.randomUUID();

    @BeforeEach
    public void setUp() {
        company = new Company(companyName, "A test company", founderId);
    }

    @Test
    public void testOwnerCanOfferRole() {
        Member owner = createStaff(companyName, StaffRole.OWNER);
        Member target = new Member(UUID.randomUUID(), "target", "target@test.com", "pass");

        RoleAppointmentOffer offer = company.offerRole(owner, target, StaffRole.MANAGER, Collections.emptySet());

        assertNotNull(offer);
        assertEquals(companyName, offer.getCompanyId());
        assertEquals(target.getId(), offer.getTargetMemberId());
        assertEquals(StaffRole.MANAGER, offer.getOfferedRole());
        assertTrue(offer.isPending());
    }

    @Test
    public void testManagerWithPersonnelMgmtCanOfferRole() {
        Member manager = createStaff(companyName, StaffRole.MANAGER, ManagerPermission.PERSONNEL_MGMT);
        Member target = new Member(UUID.randomUUID(), "target", "target@test.com", "pass");

        RoleAppointmentOffer offer = company.offerRole(manager, target, StaffRole.MANAGER, Collections.emptySet());

        assertNotNull(offer);
    }

    @Test
    public void testManagerWithoutPermissionCannotOfferRole() {
        Member manager = createStaff(companyName, StaffRole.MANAGER, ManagerPermission.MAP_DEFINITION);
        Member target = new Member(UUID.randomUUID(), "target", "target@test.com", "pass");

        assertThrows(PermissionDeniedException.class, () -> 
            company.offerRole(manager, target, StaffRole.MANAGER, Collections.emptySet())
        );
    }

    @Test
    public void testCannotPromoteExistingOwner() {
        Member owner = createStaff(companyName, StaffRole.OWNER);
        Member target = createStaff(companyName, StaffRole.OWNER);

        assertThrows(IllegalArgumentException.class, () -> 
            company.offerRole(owner, target, StaffRole.OWNER, Collections.emptySet())
        );
    }

    private Member createStaff(String companyId, StaffRole role, ManagerPermission... permissions) {
        Member member = new Member(UUID.randomUUID(), "staff", "staff@test.com", "pass");
        Set<ManagerPermission> perms = new HashSet<>();
        for (ManagerPermission p : permissions) perms.add(p);
        
        StaffAppointment appointment = new StaffAppointment(
            companyId,
            founderId,
            role,
            perms
        );
        member.addStaffAppointment(companyId, appointment);
        return member;
    }
}
