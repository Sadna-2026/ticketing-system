package com.ticketing.application.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.ticketing.application.services.INotificationService;
import com.ticketing.domain.company.Company;
import com.ticketing.domain.company.ICompanyRepository;
import com.ticketing.domain.member.IMemberRepository;
import com.ticketing.domain.member.ManagerPermission;
import com.ticketing.domain.member.Member;
import com.ticketing.domain.member.PendingRoleOffer;
import com.ticketing.domain.member.PermissionDeniedException;
import com.ticketing.domain.member.StaffAppointment;
import com.ticketing.domain.member.communication.ManagerPermissionsChangedEvent;
import com.ticketing.domain.member.communication.RelinquishOwnershipEvent;
import com.ticketing.domain.member.communication.RevokePersonnelEvent;
import com.ticketing.domain.member.communication.RoleAppointmentOfferRequestedEvent;
import com.ticketing.domain.member.communication.RoleAppointmentOfferResponseEvent;

@DisplayName("Application event listeners")
class ApplicationListenerTest {

    @Nested
    @DisplayName("ManagerPermissionsChanged")
    class ManagerPermissionsChanged {

        private IMemberRepository memberRepository;
        private ICompanyRepository companyRepository;
        private ManagerPermissionsChangedHandler handler;
        private final String COMPANY_NAME = "TestCompany";

        @BeforeEach
        public void setUp() {
            memberRepository = mock(IMemberRepository.class);
            companyRepository = mock(ICompanyRepository.class);
            handler = new ManagerPermissionsChangedHandler(memberRepository, companyRepository);
        }

        @Test
        public void GivenFounderCaller_WhenHandlePermissionsChange_ThenManagerPermissionsUpdated() {
            UUID founderId = UUID.randomUUID();
            Member founder = new Member(founderId, "founder", "f@test.com", "pass");
            founder.addStaffAppointment(COMPANY_NAME, new StaffAppointment(COMPANY_NAME, null, StaffAppointment.StaffRole.OWNER, Collections.emptySet()));

            UUID managerId = UUID.randomUUID();
            Member manager = new Member(managerId, "manager", "m@test.com", "pass");
            manager.addStaffAppointment(COMPANY_NAME, new StaffAppointment(COMPANY_NAME, founderId, StaffAppointment.StaffRole.MANAGER, Collections.emptySet()));

            Company company = new Company(COMPANY_NAME, "desc", founderId);

            when(memberRepository.findById(founderId)).thenReturn(Optional.of(founder));
            when(memberRepository.findById(managerId)).thenReturn(Optional.of(manager));
            when(companyRepository.findByName(COMPANY_NAME)).thenReturn(Optional.of(company));

            Set<ManagerPermission> newPerms = Set.of(ManagerPermission.VIEW_REPORTS);
            ManagerPermissionsChangedEvent event = new ManagerPermissionsChangedEvent(founderId, managerId, COMPANY_NAME, newPerms);

            handler.handle(event);

            assertTrue(manager.getStaffAppointment(COMPANY_NAME).getPermissions().contains(ManagerPermission.VIEW_REPORTS));
            verify(memberRepository).save(manager);
        }

        @Test
        public void GivenDirectAppointer_WhenHandlePermissionsChange_ThenManagerPermissionsUpdated() {
            UUID founderId = UUID.randomUUID();
            UUID appointerId = UUID.randomUUID();
            Member appointer = new Member(appointerId, "appointer", "a@test.com", "pass");
            appointer.addStaffAppointment(COMPANY_NAME, new StaffAppointment(COMPANY_NAME, founderId, StaffAppointment.StaffRole.MANAGER, Collections.emptySet()));

            UUID managerId = UUID.randomUUID();
            Member manager = new Member(managerId, "manager", "m@test.com", "pass");
            manager.addStaffAppointment(COMPANY_NAME, new StaffAppointment(COMPANY_NAME, appointerId, StaffAppointment.StaffRole.MANAGER, Collections.emptySet()));

            Company company = new Company(COMPANY_NAME, "desc", founderId);

            when(memberRepository.findById(appointerId)).thenReturn(Optional.of(appointer));
            when(memberRepository.findById(managerId)).thenReturn(Optional.of(manager));
            when(companyRepository.findByName(COMPANY_NAME)).thenReturn(Optional.of(company));

            Set<ManagerPermission> newPerms = Set.of(ManagerPermission.VIEW_REPORTS);
            ManagerPermissionsChangedEvent event = new ManagerPermissionsChangedEvent(appointerId, managerId, COMPANY_NAME, newPerms);

            handler.handle(event);

            assertTrue(manager.getStaffAppointment(COMPANY_NAME).getPermissions().contains(ManagerPermission.VIEW_REPORTS));
            verify(memberRepository).save(manager);
        }

        @Test
        public void GivenSiblingOwner_WhenHandlePermissionsChange_ThenSecurityException() {
            UUID founderId = UUID.randomUUID();
            UUID owner2Id = UUID.randomUUID();

            Member founder = new Member(founderId, "founder", "f@test.com", "pass");
            founder.addStaffAppointment(COMPANY_NAME, new StaffAppointment(COMPANY_NAME, null, StaffAppointment.StaffRole.OWNER, Collections.emptySet()));

            Member owner2 = new Member(owner2Id, "owner2", "o2@test.com", "pass");
            owner2.addStaffAppointment(COMPANY_NAME, new StaffAppointment(COMPANY_NAME, founderId, StaffAppointment.StaffRole.OWNER, Collections.emptySet()));

            UUID managerId = UUID.randomUUID();
            Member manager = new Member(managerId, "manager", "m@test.com", "pass");
            manager.addStaffAppointment(COMPANY_NAME, new StaffAppointment(COMPANY_NAME, founderId, StaffAppointment.StaffRole.MANAGER, Collections.emptySet()));

            Company company = new Company(COMPANY_NAME, "desc", founderId);

            when(memberRepository.findById(owner2Id)).thenReturn(Optional.of(owner2));
            when(memberRepository.findById(managerId)).thenReturn(Optional.of(manager));
            when(companyRepository.findByName(COMPANY_NAME)).thenReturn(Optional.of(company));

            ManagerPermissionsChangedEvent event = new ManagerPermissionsChangedEvent(owner2Id, managerId, COMPANY_NAME, Set.of(ManagerPermission.VIEW_REPORTS));

            assertThrows(SecurityException.class, () -> handler.handle(event));
            verify(memberRepository, never()).save(any());
        }

        @Test
        public void GivenUnchangedPermissions_WhenHandlePermissionsChange_ThenNoSave() {
            UUID founderId = UUID.randomUUID();
            Member founder = new Member(founderId, "founder", "f@test.com", "pass");
            founder.addStaffAppointment(COMPANY_NAME, new StaffAppointment(COMPANY_NAME, null, StaffAppointment.StaffRole.OWNER, Collections.emptySet()));

            UUID managerId = UUID.randomUUID();
            Member manager = new Member(managerId, "manager", "m@test.com", "pass");
            Set<ManagerPermission> existingPerms = Set.of(ManagerPermission.VIEW_REPORTS);
            manager.addStaffAppointment(COMPANY_NAME, new StaffAppointment(COMPANY_NAME, founderId, StaffAppointment.StaffRole.MANAGER, existingPerms));

            Company company = new Company(COMPANY_NAME, "desc", founderId);

            when(memberRepository.findById(founderId)).thenReturn(Optional.of(founder));
            when(memberRepository.findById(managerId)).thenReturn(Optional.of(manager));
            when(companyRepository.findByName(COMPANY_NAME)).thenReturn(Optional.of(company));

            ManagerPermissionsChangedEvent event = new ManagerPermissionsChangedEvent(founderId, managerId, COMPANY_NAME, existingPerms);

            handler.handle(event);

            verify(memberRepository, never()).save(any());
        }

        @Test
        public void GivenTargetOwner_WhenHandlePermissionsChange_ThenIllegalArgumentException() {
            UUID founderId = UUID.randomUUID();
            Member founder = new Member(founderId, "founder", "f@test.com", "pass");
            founder.addStaffAppointment(COMPANY_NAME, new StaffAppointment(COMPANY_NAME, null, StaffAppointment.StaffRole.OWNER, Collections.emptySet()));

            UUID owner2Id = UUID.randomUUID();
            Member owner2 = new Member(owner2Id, "owner2", "o2@test.com", "pass");
            owner2.addStaffAppointment(COMPANY_NAME, new StaffAppointment(COMPANY_NAME, founderId, StaffAppointment.StaffRole.OWNER, Collections.emptySet()));

            Company company = new Company(COMPANY_NAME, "desc", founderId);

            when(memberRepository.findById(founderId)).thenReturn(Optional.of(founder));
            when(memberRepository.findById(owner2Id)).thenReturn(Optional.of(owner2));
            when(companyRepository.findByName(COMPANY_NAME)).thenReturn(Optional.of(company));

            ManagerPermissionsChangedEvent event = new ManagerPermissionsChangedEvent(founderId, owner2Id, COMPANY_NAME, Set.of(ManagerPermission.VIEW_REPORTS));

            assertThrows(IllegalArgumentException.class, () -> handler.handle(event));
        }
    }

    @Nested
    @DisplayName("RevokePersonnel")
    class RevokePersonnel {

        private IMemberRepository memberRepository;
        private INotificationService notificationService;
        private RevokePersonnelHandler handler;

        @BeforeEach
        public void setUp() {
            memberRepository = mock(IMemberRepository.class);
            notificationService = mock(INotificationService.class);
            handler = new RevokePersonnelHandler(memberRepository, notificationService);
        }

        /**
         * Fulfills Acceptance Test: "TargetIsFounder" / "Founder removal rejected"
         * V0 Requirement: Founder cannot be revoked. The system strictly denies the request.
         */
        @Test
        public void GivenTargetIsFounder_WhenHandle_ThenThrowsIllegalArgumentException() {
            UUID founderId = UUID.randomUUID();
            UUID revokerId = UUID.randomUUID(); // Someone else trying to revoke founder
            String companyName = "TestCo";
            Company company = new Company(companyName, "desc", founderId);

            Member founder = new Member(founderId, "founder", "founder@test.com", "pass");
            Member revoker = new Member(revokerId, "revoker", "revoker@test.com", "pass");

            when(memberRepository.findById(founderId)).thenReturn(Optional.of(founder));
            when(memberRepository.findById(revokerId)).thenReturn(Optional.of(revoker));

            RevokePersonnelEvent event = new RevokePersonnelEvent(company, revokerId, founderId);

            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
                handler.handle(event);
            });
            assertEquals("Cannot revoke the founder of the company.", exception.getMessage());
        }

        /**
         * Fulfills Acceptance Test: "Unauthorized Appointer" / "FounderOverrideAttempt"
         * V0 Requirement: Only the strict direct appointer can revoke the target's appointment.
         */
        @Test
        public void GivenUnauthorizedRevoker_WhenHandle_ThenThrowsIllegalArgumentException() {
            UUID founderId = UUID.randomUUID();
            UUID targetId = UUID.randomUUID();
            UUID unauthorizedRevokerId = UUID.randomUUID(); // Not the person who appointed target
            String companyName = "TestCo";
            Company company = new Company(companyName, "desc", founderId);

            Member target = new Member(targetId, "target", "target@test.com", "pass");
            // Target was appointed by the founder, NOT the unauthorized revoker
            StaffAppointment targetAppointment = new StaffAppointment(companyName, founderId, StaffAppointment.StaffRole.MANAGER, Collections.emptySet());
            target.addStaffAppointment(companyName, targetAppointment);

            Member unauthorizedRevoker = new Member(unauthorizedRevokerId, "revoker", "revoker@test.com", "pass");

            when(memberRepository.findById(targetId)).thenReturn(Optional.of(target));
            when(memberRepository.findById(unauthorizedRevokerId)).thenReturn(Optional.of(unauthorizedRevoker));

            // Attempt to revoke the target by someone who is not their appointer (founder in this case), should throw exception
            RevokePersonnelEvent event = new RevokePersonnelEvent(company, unauthorizedRevokerId, targetId);

            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
                handler.handle(event);
            });
            assertTrue(exception.getMessage().contains("Only the appointer can revoke their appointees"));
        }

        @Test
        public void GivenRevokedOwnerTriesToRevokeTheirAppointee_WhenHandle_ThenThrowsIllegalArgumentException() {
            UUID founderId = UUID.randomUUID();
            UUID revokedOwnerId = UUID.randomUUID();
            UUID targetId = UUID.randomUUID();
            String companyName = "TestCo";
            Company company = new Company(companyName, "desc", founderId);

            // revokedOwner was appointed by founder but has since been revoked
            Member revokedOwner = new Member(revokedOwnerId, "revokedOwner", "ro@test.com", "pass");
            StaffAppointment revokedOwnerAppt = new StaffAppointment(companyName, founderId, StaffAppointment.StaffRole.OWNER, Collections.emptySet());
            revokedOwnerAppt.addAppointedStaffMember(targetId);
            revokedOwnerAppt.revoke();
            revokedOwner.addStaffAppointment(companyName, revokedOwnerAppt);

            // target was appointed by revokedOwner
            Member target = new Member(targetId, "target", "target@test.com", "pass");
            StaffAppointment targetAppt = new StaffAppointment(companyName, revokedOwnerId, StaffAppointment.StaffRole.OWNER, Collections.emptySet());
            target.addStaffAppointment(companyName, targetAppt);

            when(memberRepository.findById(revokedOwnerId)).thenReturn(Optional.of(revokedOwner));
            when(memberRepository.findById(targetId)).thenReturn(Optional.of(target));

            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                    handler.handle(new RevokePersonnelEvent(company, revokedOwnerId, targetId)));
            assertTrue(exception.getMessage().contains("Revoker's own appointment has been revoked"));
        }

        /**
         * Fulfills Acceptance Test: "Hierarchy remains valid after non-cascading removal"
         * V1 fix: subordinates remain orphans under the revoked node (no re-parenting).
         */
        @Test
        public void GivenValidRevocationWithSubordinates_WhenHandle_ThenChildrenRemainOrphans() {
            UUID founderId = UUID.randomUUID();
            UUID targetManagerId = UUID.randomUUID();
            UUID subordinateId = UUID.randomUUID();
            String companyName = "TestCo";
            Company company = new Company(companyName, "desc", founderId);

            // Setup the Revoker (Founder)
            Member founder = new Member(founderId, "founder", "founder@test.com", "pass");
            StaffAppointment founderAppointment = new StaffAppointment(companyName, founderId, StaffAppointment.StaffRole.OWNER, Collections.emptySet());
            founderAppointment.addAppointedStaffMember(targetManagerId);
            founder.addStaffAppointment(companyName, founderAppointment);

            // Setup the Target (Manager)
            Member targetManager = new Member(targetManagerId, "manager", "manager@test.com", "pass");
            StaffAppointment targetAppointment = new StaffAppointment(companyName, founderId, StaffAppointment.StaffRole.MANAGER, Collections.emptySet());
            targetAppointment.addAppointedStaffMember(subordinateId); // Target has a subordinate
            targetManager.addStaffAppointment(companyName, targetAppointment);

            // Setup the Subordinate
            Member subordinate = new Member(subordinateId, "sub", "sub@test.com", "pass");
            StaffAppointment subAppointment = new StaffAppointment(companyName, targetManagerId, StaffAppointment.StaffRole.MANAGER, Collections.emptySet());
            subordinate.addStaffAppointment(companyName, subAppointment);

            when(memberRepository.findById(founderId)).thenReturn(Optional.of(founder));
            when(memberRepository.findById(targetManagerId)).thenReturn(Optional.of(targetManager));
            when(memberRepository.findById(subordinateId)).thenReturn(Optional.of(subordinate));

            RevokePersonnelEvent event = new RevokePersonnelEvent(company, founderId, targetManagerId);

            // Act
            handler.handle(event);

            assertFalse(founder.getStaffAppointment(companyName).getAppointedStaffMemberIds().contains(targetManagerId));
            assertFalse(founder.getStaffAppointment(companyName).getAppointedStaffMemberIds().contains(subordinateId));

            StaffAppointment revokedAppointment = targetManager.getStaffAppointment(companyName);
            assertNotNull(revokedAppointment);
            assertTrue(revokedAppointment.isRevoked());
            assertEquals(targetManagerId, subordinate.getStaffAppointment(companyName).getAppointedByMemberId());

            verify(memberRepository).save(founder);
            verify(memberRepository).save(targetManager);
            verify(memberRepository, never()).save(subordinate);

            verify(notificationService).notify(eq(targetManagerId.toString()), anyString());
            verify(notificationService, never()).notify(eq(subordinateId.toString()), anyString());
        }

        /**
         * Fulfills Acceptance Test: "Existing subordinate/sibling appointments remain intact"
         * V0 Requirement: Revoking a target does not affect siblings appointed by the same revoker.
         */
        @Test
        public void GivenSiblingAppointments_WhenHandle_ThenSiblingRemainsIntact() {
            UUID founderId = UUID.randomUUID();
            UUID targetId = UUID.randomUUID();
            UUID siblingId = UUID.randomUUID();
            String companyName = "TestCo";
            Company company = new Company(companyName, "desc", founderId);

            // Setup Founder
            Member founder = new Member(founderId, "founder", "founder@test.com", "pass");
            StaffAppointment founderAppt = new StaffAppointment(companyName, founderId, StaffAppointment.StaffRole.OWNER, Collections.emptySet());
            founderAppt.addAppointedStaffMember(targetId);
            founderAppt.addAppointedStaffMember(siblingId); // Founder manages both Target and Sibling
            founder.addStaffAppointment(companyName, founderAppt);

            // Setup Target
            Member target = new Member(targetId, "target", "target@test.com", "pass");
            target.addStaffAppointment(companyName, new StaffAppointment(companyName, founderId, StaffAppointment.StaffRole.MANAGER, Collections.emptySet()));

            // Setup Sibling
            Member sibling = new Member(siblingId, "sibling", "sibling@test.com", "pass");
            sibling.addStaffAppointment(companyName, new StaffAppointment(companyName, founderId, StaffAppointment.StaffRole.MANAGER, Collections.emptySet()));

            when(memberRepository.findById(founderId)).thenReturn(Optional.of(founder));
            when(memberRepository.findById(targetId)).thenReturn(Optional.of(target));
            when(memberRepository.findById(siblingId)).thenReturn(Optional.of(sibling));

            // Act: Revoke the target
            handler.handle(new RevokePersonnelEvent(company, founderId, targetId));

            assertFalse(founder.getStaffAppointment(companyName).getAppointedStaffMemberIds().contains(targetId));
            assertTrue(founder.getStaffAppointment(companyName).getAppointedStaffMemberIds().contains(siblingId));
            assertTrue(target.getStaffAppointment(companyName).isRevoked());
            assertNotNull(sibling.getStaffAppointment(companyName));
            assertFalse(sibling.getStaffAppointment(companyName).isRevoked());
        }

        /**
         * Fulfills Acceptance Test: "Cross-company roles unaffected"
         * V0 Requirement: Revocation removes X's role in THAT company only.
         */
        @Test
        public void GivenCrossCompanyRoles_WhenHandle_ThenOtherCompanyRoleUnaffected() {
            UUID founderId = UUID.randomUUID();
            UUID targetId = UUID.randomUUID();
            String targetCompany = "TestCo";
            String otherCompany = "OtherCo";
            Company company = new Company(targetCompany, "desc", founderId);

            // Setup Founder
            Member founder = new Member(founderId, "founder", "founder@test.com", "pass");
            StaffAppointment founderAppt = new StaffAppointment(targetCompany, founderId, StaffAppointment.StaffRole.OWNER, Collections.emptySet());
            founderAppt.addAppointedStaffMember(targetId);
            founder.addStaffAppointment(targetCompany, founderAppt);

            // Setup Target with roles in TWO companies
            Member target = new Member(targetId, "target", "target@test.com", "pass");
            target.addStaffAppointment(targetCompany, new StaffAppointment(targetCompany, founderId, StaffAppointment.StaffRole.MANAGER, Collections.emptySet()));
            target.addStaffAppointment(otherCompany, new StaffAppointment(otherCompany, UUID.randomUUID(), StaffAppointment.StaffRole.MANAGER, Collections.emptySet()));

            when(memberRepository.findById(founderId)).thenReturn(Optional.of(founder));
            when(memberRepository.findById(targetId)).thenReturn(Optional.of(target));

            // Act: Revoke from TestCo
            handler.handle(new RevokePersonnelEvent(company, founderId, targetId));

            assertTrue(target.getStaffAppointment(targetCompany).isRevoked());
            assertNotNull(target.getStaffAppointment(otherCompany));
            assertFalse(target.getStaffAppointment(otherCompany).isRevoked());
        }
    }

    @Nested
    @DisplayName("RelinquishOwnership")
    class RelinquishOwnership {

        private IMemberRepository memberRepository;
        private INotificationService notificationService;
        private RelinquishOwnershipHandler handler;

        @BeforeEach
        public void setUp() {
            memberRepository = mock(IMemberRepository.class);
            notificationService = mock(INotificationService.class);
            handler = new RelinquishOwnershipHandler(memberRepository, notificationService);
        }

        /**
         * Fulfills Acceptance Test: "Owner relinquishes successfully when others remain"
         */
        @Test
        public void GivenMultipleOwners_WhenRelinquish_ThenOwnerRemovedAndSubordinatesReassigned() {
            UUID founderId = UUID.randomUUID();
            UUID relinquishingOwnerId = UUID.randomUUID();
            UUID subordinateId = UUID.randomUUID();
            UUID otherOwnerId = UUID.randomUUID();
            String companyName = "TestCo";
            Company company = new Company(companyName, "desc", founderId);

            Member relinquishingOwner = new Member(relinquishingOwnerId, "owner1", "owner1@test.com", "pass");
            StaffAppointment ownerAppointment = new StaffAppointment(companyName, founderId, StaffAppointment.StaffRole.OWNER, Collections.emptySet(), Set.of(subordinateId));
            relinquishingOwner.addStaffAppointment(companyName, ownerAppointment);

            Member subordinate = new Member(subordinateId, "subordinate", "sub@test.com", "pass");
            StaffAppointment subAppointment = new StaffAppointment(companyName, relinquishingOwnerId, StaffAppointment.StaffRole.MANAGER, Set.of(ManagerPermission.INVENTORY_MGMT));
            subordinate.addStaffAppointment(companyName, subAppointment);

            Member founder = new Member(founderId, "founder", "founder@test.com", "pass");
            StaffAppointment founderAppointment = new StaffAppointment(companyName, founderId, StaffAppointment.StaffRole.OWNER, Collections.emptySet(), Set.of(relinquishingOwnerId));
            founder.addStaffAppointment(companyName, founderAppointment);

            Member otherOwner = new Member(otherOwnerId, "owner2", "owner2@test.com", "pass");
            StaffAppointment otherOwnerAppointment = new StaffAppointment(companyName, founderId, StaffAppointment.StaffRole.OWNER, Collections.emptySet());
            otherOwner.addStaffAppointment(companyName, otherOwnerAppointment);

            when(memberRepository.findById(relinquishingOwnerId)).thenReturn(Optional.of(relinquishingOwner));
            when(memberRepository.findById(founderId)).thenReturn(Optional.of(founder));
            when(memberRepository.findById(subordinateId)).thenReturn(Optional.of(subordinate));
            when(memberRepository.findByCompanyAppointment(companyName)).thenReturn(List.of(relinquishingOwner, otherOwner, founder));

            RelinquishOwnershipEvent event = new RelinquishOwnershipEvent(company, relinquishingOwnerId);
            handler.handle(event);

            assertNull(relinquishingOwner.getStaffAppointment(companyName));
            assertEquals(founderId, subordinate.getStaffAppointment(companyName).getAppointedByMemberId());
            verify(memberRepository, times(3)).save(any(Member.class));
            verify(notificationService).notify(eq(subordinateId.toString()), anyString());
            verify(notificationService).notify(eq(relinquishingOwnerId.toString()), anyString());
        }

        /**
         * Fulfills Acceptance Test: "Last owner cannot relinquish"
         */
        @Test
        public void GivenOnlyOneOwner_WhenRelinquish_ThenThrowsIllegalArgumentException() {
            UUID founderId = UUID.randomUUID();
            UUID ownerId = UUID.randomUUID();
            String companyName = "TestCo";
            Company company = new Company(companyName, "desc", founderId);

            Member owner = new Member(ownerId, "owner", "owner@test.com", "pass");
            StaffAppointment ownerAppointment = new StaffAppointment(companyName, founderId, StaffAppointment.StaffRole.OWNER, Collections.emptySet());
            owner.addStaffAppointment(companyName, ownerAppointment);

            when(memberRepository.findById(ownerId)).thenReturn(Optional.of(owner));
            when(memberRepository.findByCompanyAppointment(companyName)).thenReturn(List.of(owner));

            RelinquishOwnershipEvent event = new RelinquishOwnershipEvent(company, ownerId);

            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
                handler.handle(event);
            });
            assertEquals("Cannot relinquish ownership: you are the last owner. Add another owner first.", exception.getMessage());
        }

        /**
         * Fulfills Acceptance Test: "Founder relinquishment rejected -> directed to closure"
         */
        @Test
        public void GivenFounder_WhenRelinquish_ThenThrowsIllegalArgumentException() {
            UUID founderId = UUID.randomUUID();
            String companyName = "TestCo";
            Company company = new Company(companyName, "desc", founderId);

            Member founder = new Member(founderId, "founder", "founder@test.com", "pass");
            StaffAppointment founderAppointment = new StaffAppointment(companyName, founderId, StaffAppointment.StaffRole.OWNER, Collections.emptySet());
            founder.addStaffAppointment(companyName, founderAppointment);

            when(memberRepository.findById(founderId)).thenReturn(Optional.of(founder));

            RelinquishOwnershipEvent event = new RelinquishOwnershipEvent(company, founderId);

            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
                handler.handle(event);
            });
            assertEquals("Founder cannot relinquish ownership. Use company closure flow instead.", exception.getMessage());
        }

        /**
         * Fulfills Acceptance Test: "Manager cannot relinquish (must be revoked)"
         */
        @Test
        public void GivenManager_WhenRelinquish_ThenThrowsIllegalArgumentException() {
            UUID founderId = UUID.randomUUID();
            UUID managerId = UUID.randomUUID();
            String companyName = "TestCo";
            Company company = new Company(companyName, "desc", founderId);

            Member manager = new Member(managerId, "manager", "manager@test.com", "pass");
            StaffAppointment managerAppointment = new StaffAppointment(companyName, founderId, StaffAppointment.StaffRole.MANAGER, Set.of(ManagerPermission.INVENTORY_MGMT));
            manager.addStaffAppointment(companyName, managerAppointment);

            Member otherOwner = new Member(UUID.randomUUID(), "owner", "owner@test.com", "pass");

            when(memberRepository.findById(managerId)).thenReturn(Optional.of(manager));
            when(memberRepository.findByCompanyAppointment(companyName)).thenReturn(List.of(manager, otherOwner));

            RelinquishOwnershipEvent event = new RelinquishOwnershipEvent(company, managerId);

            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
                handler.handle(event);
            });
            assertEquals("Only owners can relinquish ownership. Managers must be revoked by their appointer.", exception.getMessage());
        }

        /**
         * Fulfills Acceptance Test: "Cross-company roles unaffected"
         */
        @Test
        public void GivenOwnerInMultipleCompanies_WhenRelinquishOne_ThenOtherCompanyRoleRemains() {
            UUID founderId = UUID.randomUUID();
            UUID ownerId = UUID.randomUUID();
            String companyName1 = "TestCo1";
            String companyName2 = "TestCo2";
            Company company1 = new Company(companyName1, "desc", founderId);

            Member owner = new Member(ownerId, "owner", "owner@test.com", "pass");
            StaffAppointment appointment1 = new StaffAppointment(companyName1, founderId, StaffAppointment.StaffRole.OWNER, Collections.emptySet());
            StaffAppointment appointment2 = new StaffAppointment(companyName2, UUID.randomUUID(), StaffAppointment.StaffRole.OWNER, Collections.emptySet());
            owner.addStaffAppointment(companyName1, appointment1);
            owner.addStaffAppointment(companyName2, appointment2);

            Member otherOwner = new Member(UUID.randomUUID(), "other", "other@test.com", "pass");
            StaffAppointment otherOwnerAppointment = new StaffAppointment(companyName1, founderId, StaffAppointment.StaffRole.OWNER, Collections.emptySet());
            otherOwner.addStaffAppointment(companyName1, otherOwnerAppointment);

            Member founder = new Member(founderId, "founder", "founder@test.com", "pass");
            StaffAppointment founderAppointment = new StaffAppointment(companyName1, founderId, StaffAppointment.StaffRole.OWNER, Collections.emptySet(), Set.of(ownerId, otherOwner.getId()));
            founder.addStaffAppointment(companyName1, founderAppointment);

            when(memberRepository.findById(ownerId)).thenReturn(Optional.of(owner));
            when(memberRepository.findById(founderId)).thenReturn(Optional.of(founder));
            when(memberRepository.findByCompanyAppointment(companyName1)).thenReturn(List.of(owner, otherOwner, founder));

            RelinquishOwnershipEvent event = new RelinquishOwnershipEvent(company1, ownerId);
            handler.handle(event);

            assertNull(owner.getStaffAppointment(companyName1));
            assertNotNull(owner.getStaffAppointment(companyName2));
        }
    }

    @Nested
    @DisplayName("Role offer requested")
    class RoleOfferRequested {

        private IMemberRepository memberRepository;
        private RoleAppointmentOfferRequestedHandler handler;
        private INotificationService notificationService;

        @BeforeEach
        void setUp() {
            memberRepository = mock(IMemberRepository.class);
            notificationService = mock(INotificationService.class);
            handler = new RoleAppointmentOfferRequestedHandler(memberRepository, notificationService);
        }

        @Test
        public void GivenAuthorizedAppointer_WhenHandleOfferRequest_ThenPendingOfferCreated() {
            UUID appointerId = UUID.randomUUID();
            UUID targetId = UUID.randomUUID();
            String companyName = "TestCo";

            Member appointer = createOwner(appointerId, companyName);
            Member target = new Member(targetId, "target", "t@t.com", "p");

            when(memberRepository.findById(appointerId)).thenReturn(java.util.Optional.of(appointer));
            when(memberRepository.findById(targetId)).thenReturn(java.util.Optional.of(target));

            RoleAppointmentOfferRequestedEvent event = new RoleAppointmentOfferRequestedEvent(
                appointerId, targetId, companyName, StaffAppointment.StaffRole.MANAGER, Collections.emptySet()
            );

            handler.handle(event);

            assertEquals(1, target.getPendingOffers().size());
            assertEquals(companyName, target.getPendingOffers().get(0).getCompanyName());
            verify(memberRepository).save(target);
        }

        @Test
        public void GivenUnauthorizedAppointer_WhenHandleOfferRequest_ThenPermissionDeniedException() {
            UUID appointerId = UUID.randomUUID();
            UUID targetId = UUID.randomUUID();
            String companyName = "TestCo";

            Member appointer = new Member(appointerId, "no-perm", "n@p.com", "p");
            Member target = new Member(targetId, "target", "t@t.com", "p");

            when(memberRepository.findById(appointerId)).thenReturn(java.util.Optional.of(appointer));

            RoleAppointmentOfferRequestedEvent event = new RoleAppointmentOfferRequestedEvent(
                appointerId, targetId, companyName, StaffAppointment.StaffRole.MANAGER, Collections.emptySet()
            );

            assertThrows(PermissionDeniedException.class, () -> handler.handle(event));
            verify(memberRepository, never()).save(any());
        }

        @Test
        public void GivenTargetAlreadyOwner_WhenHandleOfferRequest_ThenIllegalArgumentException() {
            UUID appointerId = UUID.randomUUID();
            UUID targetId = UUID.randomUUID();
            String companyName = "TestCo";

            Member appointer = createOwner(appointerId, companyName);
            Member target = createOwner(targetId, companyName);

            when(memberRepository.findById(appointerId)).thenReturn(java.util.Optional.of(appointer));
            when(memberRepository.findById(targetId)).thenReturn(java.util.Optional.of(target));

            RoleAppointmentOfferRequestedEvent event = new RoleAppointmentOfferRequestedEvent(
                appointerId, targetId, companyName, StaffAppointment.StaffRole.OWNER, Collections.emptySet()
            );

            assertThrows(IllegalArgumentException.class, () -> handler.handle(event));
        }

        private Member createOwner(UUID id, String companyName) {
            Member m = new Member(id, "owner", "o@o.com", "p");
            m.addStaffAppointment(companyName, new StaffAppointment(companyName, UUID.randomUUID(), StaffAppointment.StaffRole.OWNER, Collections.emptySet()));
            return m;
        }
    }

    @Nested
    @DisplayName("Role offer response")
    class RoleOfferResponse {

        private IMemberRepository memberRepository;
        private ICompanyRepository companyRepository;
        private INotificationService notificationService;
        private RoleAppointmentOfferResponseHandler handler;

        @BeforeEach
        public void setUp() {
            memberRepository = mock(IMemberRepository.class);
            companyRepository = mock(ICompanyRepository.class);
            notificationService = mock(INotificationService.class);
            handler = new RoleAppointmentOfferResponseHandler(memberRepository, companyRepository, notificationService);
        }

        @Test
        public void GivenAcceptedResponse_WhenHandle_ThenNotifiesAppointee() {
            UUID targetId = UUID.randomUUID();
            String companyName = "TestCo";
            PendingRoleOffer offer = new PendingRoleOffer(UUID.randomUUID(), companyName, StaffAppointment.StaffRole.MANAGER, java.util.Collections.emptySet(), java.time.LocalDateTime.now().plusDays(1));
            Member target = new Member(targetId, "target", "target@test.com", "password");
            target.addPendingOffer(offer);

            Member appointer = new Member(offer.getOfferedByMemberId(), "appointer", "appointer@test.com", "password");
            appointer.addStaffAppointment(companyName, new StaffAppointment(companyName, appointer.getId(), StaffAppointment.StaffRole.OWNER, java.util.Collections.emptySet()));
            when(memberRepository.findById(appointer.getId())).thenReturn(Optional.of(appointer));
            when(memberRepository.findById(targetId)).thenReturn(Optional.of(target));
            when(companyRepository.existsByName(companyName)).thenReturn(true);
            when(companyRepository.findByName(companyName)).thenReturn(Optional.of(new Company(companyName, "desc", UUID.randomUUID())));

            RoleAppointmentOfferResponseEvent event = new RoleAppointmentOfferResponseEvent(offer.getOfferId(), targetId, true);
            handler.handle(event);

            assertTrue(target.getPendingOffers().isEmpty());
            assertNotNull(target.getStaffAppointment(companyName));
            verify(memberRepository).save(target);
            verify(notificationService).notify(eq(offer.getOfferedByMemberId().toString()), contains("accepted the manager role offer"));
        }

        @Test
        public void GivenAcceptedResponse_WhenHandle_ThenAppointerAppointmentTracksTargetMember() {
            UUID targetId = UUID.randomUUID();
            UUID appointerId = UUID.randomUUID();
            String companyName = "TestCo";
            PendingRoleOffer offer = new PendingRoleOffer(appointerId, companyName, StaffAppointment.StaffRole.MANAGER, java.util.Collections.emptySet(), java.time.LocalDateTime.now().plusDays(1));
            Member target = new Member(targetId, "target", "target@test.com", "password");
            target.addPendingOffer(offer);

            Member appointer = new Member(appointerId, "appointer", "appointer@test.com", "password");
            appointer.addStaffAppointment(companyName, new StaffAppointment(companyName, appointerId, StaffAppointment.StaffRole.OWNER, java.util.Collections.emptySet()));

            when(memberRepository.findById(targetId)).thenReturn(Optional.of(target));
            when(memberRepository.findById(appointerId)).thenReturn(Optional.of(appointer));
            when(companyRepository.existsByName(companyName)).thenReturn(true);
            when(companyRepository.findByName(companyName)).thenReturn(Optional.of(new Company(companyName, "desc", UUID.randomUUID())));

            RoleAppointmentOfferResponseEvent event = new RoleAppointmentOfferResponseEvent(offer.getOfferId(), targetId, true);
            handler.handle(event);

            assertTrue(target.getPendingOffers().isEmpty());
            assertNotNull(target.getStaffAppointment(companyName));
            assertTrue(appointer.getStaffAppointment(companyName).getAppointedStaffMemberIds().contains(targetId));
            verify(memberRepository).save(target);
            verify(memberRepository).save(appointer);
            verify(notificationService).notify(eq(appointerId.toString()), contains("accepted the manager role offer"));
        }

        @Test
        public void GivenRejectedResponse_WhenHandle_ThenNotifiesAppointeeWithoutAppointment() {
            UUID targetId = UUID.randomUUID();
            String companyName = "TestCo";
            PendingRoleOffer offer = new PendingRoleOffer(UUID.randomUUID(), companyName, StaffAppointment.StaffRole.MANAGER, java.util.Collections.emptySet(), java.time.LocalDateTime.now().plusDays(1));
            Member target = new Member(targetId, "target", "target@test.com", "password");
            target.addPendingOffer(offer);

            Member appointer = new Member(offer.getOfferedByMemberId(), "appointer", "appointer@test.com", "password");
            appointer.addStaffAppointment(companyName, new StaffAppointment(companyName, appointer.getId(), StaffAppointment.StaffRole.OWNER, java.util.Collections.emptySet()));
            when(memberRepository.findById(appointer.getId())).thenReturn(Optional.of(appointer));
            when(memberRepository.findById(targetId)).thenReturn(Optional.of(target));
            when(companyRepository.existsByName(companyName)).thenReturn(true);
            when(companyRepository.findByName(companyName)).thenReturn(Optional.of(new Company(companyName, "desc", UUID.randomUUID())));

            RoleAppointmentOfferResponseEvent event = new RoleAppointmentOfferResponseEvent(offer.getOfferId(), targetId, false);
            handler.handle(event);

            assertTrue(target.getPendingOffers().isEmpty());
            assertNull(target.getStaffAppointment(companyName));
            verify(memberRepository).save(target);
            verify(notificationService).notify(eq(offer.getOfferedByMemberId().toString()), contains("declined the manager role offer"));
        }

        @Test
        public void GivenManagerPromotionAccepted_WhenHandle_ThenNotifiesAppointee() {
            UUID targetId = UUID.randomUUID();
            String companyName = "TestCo";
            UUID appointerId = UUID.randomUUID();
            PendingRoleOffer offer = new PendingRoleOffer(appointerId, companyName, StaffAppointment.StaffRole.OWNER, java.util.Collections.emptySet(), java.time.LocalDateTime.now().plusDays(1));
            Member target = new Member(targetId, "target", "target@test.com", "password");

            // Target is already a manager
            StaffAppointment existingAppointment = new StaffAppointment(companyName, appointerId, StaffAppointment.StaffRole.MANAGER, java.util.Collections.emptySet());
            target.addStaffAppointment(companyName, existingAppointment);
            target.addPendingOffer(offer);

            Member appointer = new Member(appointerId, "appointer", "appointer@test.com", "password");
            appointer.addStaffAppointment(companyName, new StaffAppointment(companyName, appointerId, StaffAppointment.StaffRole.OWNER, java.util.Collections.emptySet()));
            when(memberRepository.findById(appointerId)).thenReturn(Optional.of(appointer));
            // Mock the repositories to return the target member and company
            when(memberRepository.findById(targetId)).thenReturn(Optional.of(target));
            when(companyRepository.existsByName(companyName)).thenReturn(true);
            when(companyRepository.findByName(companyName)).thenReturn(Optional.of(new Company(companyName, "desc", UUID.randomUUID())));

            RoleAppointmentOfferResponseEvent event = new RoleAppointmentOfferResponseEvent(offer.getOfferId(), targetId, true);
            handler.handle(event);

            assertTrue(target.getPendingOffers().isEmpty());
            assertNotNull(target.getStaffAppointment(companyName));
            assertEquals(StaffAppointment.StaffRole.OWNER, target.getStaffAppointment(companyName).getRole());
            verify(memberRepository).save(target);
            verify(notificationService).notify(eq(appointerId.toString()), contains("promoted to owner"));
        }

        @Test
        public void GivenManagerPromotionRejected_WhenHandle_ThenNotifiesAppointee() {
            UUID targetId = UUID.randomUUID();
            String companyName = "TestCo";
            UUID appointerId = UUID.randomUUID();
            PendingRoleOffer offer = new PendingRoleOffer(appointerId, companyName, StaffAppointment.StaffRole.OWNER, java.util.Collections.emptySet(), java.time.LocalDateTime.now().plusDays(1));
            Member target = new Member(targetId, "target", "target@test.com", "password");
            // Target is already a manager
            StaffAppointment existingAppointment = new StaffAppointment(companyName, appointerId, StaffAppointment.StaffRole.MANAGER, java.util.Collections.emptySet());
            target.addStaffAppointment(companyName, existingAppointment);
            target.addPendingOffer(offer);

            Member appointer = new Member(appointerId, "appointer", "appointer@test.com", "password");
            appointer.addStaffAppointment(companyName, new StaffAppointment(companyName, appointerId, StaffAppointment.StaffRole.OWNER, java.util.Collections.emptySet()));
            when(memberRepository.findById(appointerId)).thenReturn(Optional.of(appointer));
            when(memberRepository.findById(targetId)).thenReturn(Optional.of(target));
            when(companyRepository.existsByName(companyName)).thenReturn(true);
            when(companyRepository.findByName(companyName)).thenReturn(Optional.of(new Company(companyName, "desc", UUID.randomUUID())));

            RoleAppointmentOfferResponseEvent event = new RoleAppointmentOfferResponseEvent(offer.getOfferId(), targetId, false);
            handler.handle(event);

            assertTrue(target.getPendingOffers().isEmpty());
            assertNotNull(target.getStaffAppointment(companyName));
            assertEquals(StaffAppointment.StaffRole.MANAGER, target.getStaffAppointment(companyName).getRole()); // Still manager
            verify(memberRepository).save(target);
            verify(notificationService).notify(eq(appointerId.toString()), contains("rejected the promotion to owner"));
        }

        @Test
        public void GivenExpiredOffer_WhenHandle_ThenThrowsIllegalArgumentException() {
            UUID targetId = UUID.randomUUID();
            String companyName = "TestCo";

            // Create the offer with a valid FUTURE date so the constructor doesn't complain
            java.time.LocalDateTime validDueDate = java.time.LocalDateTime.now().plusDays(1);
            PendingRoleOffer offer = new PendingRoleOffer(
                UUID.randomUUID(), 
                companyName, 
                StaffAppointment.StaffRole.MANAGER, 
                java.util.Collections.emptySet(), 
                validDueDate
            );

            Member target = new Member(targetId, "target", "target@test.com", "password");
            target.addPendingOffer(offer);

            when(memberRepository.findById(targetId)).thenReturn(Optional.of(target));

            RoleAppointmentOfferResponseEvent event = new RoleAppointmentOfferResponseEvent(offer.getOfferId(), targetId, true);

            // Fast-forward time to simulate the user waiting too long to respond
            try (org.mockito.MockedStatic<java.time.LocalDateTime> mockedTime = mockStatic(java.time.LocalDateTime.class)) {

                // Fast-forward to 1 day AFTER the due date
                mockedTime.when(java.time.LocalDateTime::now).thenReturn(validDueDate.plusDays(1));

                // Assert the handler catches the expired offer and throws the exception
                IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
                    handler.handle(event);
                });

                // Optional: Verify the exact message if you want to be thorough!
                assertEquals("Offer has expired", exception.getMessage());
            }
        }


        @Test
        public void GivenInactiveCompany_WhenHandle_ThenThrowsIllegalArgumentException() {
            UUID targetId = UUID.randomUUID();
            UUID appointerId = UUID.randomUUID();
            String companyName = "TestCo";

            // Create a valid offer
            java.time.LocalDateTime validDueDate = java.time.LocalDateTime.now().plusDays(1);
            PendingRoleOffer offer = new PendingRoleOffer(
                appointerId, 
                companyName, 
                StaffAppointment.StaffRole.MANAGER, 
                java.util.Collections.emptySet(), 
                validDueDate
            );

            Member target = new Member(targetId, "target", "target@test.com", "password");
            target.addPendingOffer(offer);

            // Create the Appointer
            Member appointer = new Member(appointerId, "appointer", "appointer@test.com", "password");

            when(memberRepository.findById(targetId)).thenReturn(Optional.of(target));
            when(memberRepository.findById(appointerId)).thenReturn(Optional.of(appointer)); // Mock the appointer lookup!
            when(companyRepository.existsByName(companyName)).thenReturn(true);

            // Create an INACTIVE company
            Company inactiveCompany = new Company(companyName, "desc", UUID.randomUUID());
            inactiveCompany.suspend(); 

            when(companyRepository.findByName(companyName)).thenReturn(Optional.of(inactiveCompany));

            RoleAppointmentOfferResponseEvent event = new RoleAppointmentOfferResponseEvent(offer.getOfferId(), targetId, true);

            // Assert the handler catches the inactive company 
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
                handler.handle(event);
            });

            // Check the exception message to ensure it's the expected reason for failure
            assertEquals("Cannot accept role appointment for inactive company", exception.getMessage());
        }
    }
}
