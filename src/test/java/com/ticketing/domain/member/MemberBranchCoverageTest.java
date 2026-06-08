package com.ticketing.domain.member;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class MemberBranchCoverageTest {

    @Test
    void GivenInvalidContactInfo_WhenConstructed_ThenValidationRejectsBadRequiredFields() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ContactInfo(null, "Tamar", "Iluz", "050", LocalDate.of(2000, 1, 1))),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ContactInfo(" ", "Tamar", "Iluz", "050", LocalDate.of(2000, 1, 1))),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ContactInfo("t@example.com", null, "Iluz", "050", LocalDate.of(2000, 1, 1))),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ContactInfo("t@example.com", " ", "Iluz", "050", LocalDate.of(2000, 1, 1))),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ContactInfo("t@example.com", "Tamar", null, "050", LocalDate.of(2000, 1, 1))),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ContactInfo("t@example.com", "Tamar", " ", "050", LocalDate.of(2000, 1, 1)))
        );
    }

    @Test
    void GivenContactInfo_WhenComparingAndCalculatingAge_ThenBranchesAreCovered() {
        ContactInfo withoutBirthDate = new ContactInfo("a@example.com", "A", "B", null, null);
        ContactInfo first = new ContactInfo("a@example.com", "A", "B", "050", LocalDate.of(2000, 1, 1));
        ContactInfo same = new ContactInfo("a@example.com", "A", "B", "050", LocalDate.of(2000, 1, 1));
        ContactInfo different = new ContactInfo("b@example.com", "A", "B", "050", LocalDate.of(2000, 1, 1));

        assertAll(
                () -> assertEquals(-1, withoutBirthDate.getAgeAsOf(LocalDate.of(2026, 1, 1))),
                () -> assertEquals(26, first.getAgeAsOf(LocalDate.of(2026, 1, 2))),
                () -> assertEquals(first, first),
                () -> assertEquals(first, same),
                () -> assertNotEquals(first, different),
                () -> assertNotEquals(first, null),
                () -> assertNotEquals(first, "not contact info"),
                () -> assertTrue(first.toString().contains("a@example.com"))
        );
    }

    @Test
    void GivenInvalidMemberInputs_WhenConstructOrUpdate_ThenValidationRejectsThem() {
        UUID id = UUID.randomUUID();
        Member member = validMember();

        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new Member(null, "user", "u@example.com", "enc")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new Member(id, null, "u@example.com", "enc")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new Member(id, " ", "u@example.com", "enc")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new Member(id, "user", null, "enc")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new Member(id, "user", " ", "enc")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new Member(id, "user", "u@example.com", null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new Member(id, "user", "u@example.com", " ")),
                () -> assertThrows(IllegalArgumentException.class, () -> member.updateUsername(null)),
                () -> assertThrows(IllegalArgumentException.class, () -> member.updateUsername(" ")),
                () -> assertThrows(IllegalArgumentException.class, () -> member.updateEmail(null)),
                () -> assertThrows(IllegalArgumentException.class, () -> member.updateEmail(" ")),
                () -> assertThrows(IllegalArgumentException.class, () -> member.updatePhoneNumber(null)),
                () -> assertThrows(IllegalArgumentException.class, () -> member.updatePhoneNumber(" ")),
                () -> assertThrows(IllegalArgumentException.class, () -> member.updateDateOfBirth(null)),
                () -> assertThrows(IllegalArgumentException.class, () -> member.updateEncryptedPassword(null)),
                () -> assertThrows(IllegalArgumentException.class, () -> member.updateEncryptedPassword(" "))
        );
    }

    @Test
    void GivenMember_WhenUpdatingValidFieldsAndVersion_ThenStateChanges() {
        Member member = validMember();

        member.updateUsername("newUser");
        member.updateEmail("new@example.com");
        member.updatePhoneNumber("052");
        member.updateDateOfBirth(LocalDate.of(2001, 2, 3));
        member.updateEncryptedPassword("newEnc");
        member.incrementVersion();

        assertAll(
                () -> assertEquals("newUser", member.getUsername()),
                () -> assertEquals("new@example.com", member.getEmail()),
                () -> assertEquals("052", member.getPhoneNumber()),
                () -> assertEquals(LocalDate.of(2001, 2, 3), member.getDateOfBirth()),
                () -> assertEquals("newEnc", member.getEncryptedPassword()),
                () -> assertEquals(1, member.getVersion())
        );
    }

    @Test
    void GivenStaffAppointments_WhenManagingAndAuthorizing_ThenAllowedAndDeniedBranchesAreCovered() {
        Member member = validMember();
        String company = "Acme";
        StaffAppointment managerWithoutPolicy = new StaffAppointment(
                company,
                UUID.randomUUID(),
                StaffAppointment.StaffRole.MANAGER,
                Set.of(ManagerPermission.VIEW_REPORTS));
        StaffAppointment managerWithPolicy = new StaffAppointment(
                company,
                UUID.randomUUID(),
                StaffAppointment.StaffRole.MANAGER,
                Set.of(ManagerPermission.POLICY_MODIFICATION));
        StaffAppointment owner = new StaffAppointment(
                company,
                UUID.randomUUID(),
                StaffAppointment.StaffRole.OWNER,
                Set.of());

        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> member.addStaffAppointment(null, owner)),
                () -> assertThrows(IllegalArgumentException.class, () -> member.addStaffAppointment(" ", owner)),
                () -> assertThrows(IllegalArgumentException.class, () -> member.addStaffAppointment(company, null)),
                () -> assertThrows(IllegalArgumentException.class, () -> member.getStaffAppointment(null)),
                () -> assertThrows(IllegalArgumentException.class, () -> member.removeStaffAppointment(" ")),
                () -> assertThrows(SecurityException.class, () -> member.authorizePolicyModification(company))
        );

        member.addStaffAppointment(company, managerWithoutPolicy);
        assertFalse(member.hasStaffAppointment(company, StaffAppointment.StaffRole.OWNER));
        assertThrows(SecurityException.class, () -> member.authorizePolicyModification(company));

        member.addStaffAppointment(company, managerWithPolicy);
        assertTrue(member.hasStaffAppointment(company, StaffAppointment.StaffRole.MANAGER));
        member.authorizePolicyModification(company);

        member.addStaffAppointment(company, owner);
        member.authorizePolicyModification(company);

        member.removeStaffAppointment(company);
        assertFalse(member.hasStaffAppointment(company, StaffAppointment.StaffRole.MANAGER));
        member.clearStaffAppointments();
        assertTrue(member.getPendingOffers().isEmpty());
    }

    @Test
    void GivenPendingOffers_WhenAddingFindingRemovingAndCopying_ThenBranchesAreCovered() {
        Member member = validMember();
        PendingRoleOffer offer = new PendingRoleOffer(
                UUID.randomUUID(),
                "Acme",
                StaffAppointment.StaffRole.MANAGER,
                Set.of(ManagerPermission.VIEW_REPORTS),
                LocalDateTime.now().plusDays(1));

        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> member.findPendingOffer(null)),
                () -> assertThrows(IllegalArgumentException.class, () -> member.removePendingOffer(null)),
                () -> assertThrows(IllegalArgumentException.class, () -> member.addPendingOffer(null)),
                () -> assertTrue(member.findPendingOffer(UUID.randomUUID()).isEmpty())
        );

        member.addPendingOffer(offer);
        assertTrue(member.findPendingOffer(offer.getOfferId()).isPresent());
        assertThrows(UnsupportedOperationException.class, () -> member.getPendingOffers().add(offer));

        Member copy = member.detachedCopy();
        assertTrue(copy.findPendingOffer(offer.getOfferId()).isPresent());

        member.removePendingOffer(offer.getOfferId());
        assertTrue(member.findPendingOffer(offer.getOfferId()).isEmpty());
    }

    @Test
    void GivenSuspensions_WhenCheckingActiveAndRejecting_ThenPermanentAndTimedBranchesAreCovered() {
        Member member = validMember();
        Instant base = Instant.parse("2026-01-01T10:00:00Z");
        Suspension shortSuspension = new Suspension(UUID.randomUUID(), base.minusSeconds(60), Duration.ofMinutes(5), "short");
        Suspension longSuspension = new Suspension(UUID.randomUUID(), base.minusSeconds(60), Duration.ofHours(2), "long");
        Suspension permanentSuspension = new Suspension(UUID.randomUUID(), base.minusSeconds(60), null, null);
        Suspension futureSuspension = new Suspension(UUID.randomUUID(), base.plusSeconds(60), Duration.ofMinutes(5), "future");

        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> member.suspend(null, Duration.ofMinutes(1), "bad")),
                () -> assertThrows(IllegalArgumentException.class, () -> member.cancelSuspension(null)),
                () -> assertThrows(IllegalArgumentException.class, () -> member.cancelSuspension(UUID.randomUUID())),
                () -> assertThrows(IllegalArgumentException.class, () -> member.addSuspension(null))
        );

        member.addSuspension(futureSuspension);
        assertFalse(member.isSuspended(base));
        assertNull(member.getActiveSuspension(base));

        member.addSuspension(shortSuspension);
        member.addSuspension(longSuspension);
        assertEquals(longSuspension.getSuspensionId(), member.getActiveSuspension(base).getSuspensionId());

        member.addSuspension(permanentSuspension);
        assertEquals(permanentSuspension.getSuspensionId(), member.getActiveSuspension(base).getSuspensionId());
        assertTrue(member.isSuspended(base));
        IllegalStateException permanentError = assertThrows(IllegalStateException.class,
                () -> member.rejectIfSuspended(base));
        assertTrue(permanentError.getMessage().contains("permanently"));

        Member timedMember = validMember();
        timedMember.addSuspension(longSuspension);
        IllegalStateException timedError = assertThrows(IllegalStateException.class,
                () -> timedMember.rejectIfSuspended(base));
        assertTrue(timedError.getMessage().contains("Reason: long"));

        Suspension created = timedMember.suspend(UUID.randomUUID(), Duration.ofMinutes(10), "created");
        assertNotNull(created.getSuspensionId());
        timedMember.cancelSuspension(created.getSuspensionId());
        assertTrue(timedMember.getSuspensions().stream().anyMatch(Suspension::isCancelled));
    }

    @Test
    void GivenSuspensionValueObject_WhenConstructedAndCancelled_ThenAllStateBranchesAreCovered() {
        Instant start = Instant.parse("2026-01-01T10:00:00Z");
        Suspension timed = new Suspension(UUID.randomUUID(), start, Duration.ofMinutes(30), "reason");
        Suspension permanent = new Suspension(UUID.randomUUID(), start, null, null);

        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new Suspension(null, start, Duration.ofMinutes(1), "bad")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new Suspension(UUID.randomUUID(), null, Duration.ofMinutes(1), "bad")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new Suspension(UUID.randomUUID(), start, Duration.ZERO, "bad")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new Suspension(UUID.randomUUID(), start, Duration.ofSeconds(-1), "bad")),
                () -> assertFalse(timed.isActive(start.minusSeconds(1))),
                () -> assertTrue(timed.isActive(start.plusSeconds(1))),
                () -> assertFalse(timed.isActive(start.plus(Duration.ofHours(1)))),
                () -> assertTrue(permanent.isPermanent()),
                () -> assertNull(permanent.getEndTime()),
                () -> assertTrue(permanent.isActive(start.plus(Duration.ofDays(365))))
        );

        timed.cancel();
        assertFalse(timed.isActive(start.plusSeconds(1)));
        assertThrows(IllegalStateException.class, timed::cancel);

        Suspension copy = timed.detachedCopy();
        assertEquals(timed.getSuspensionId(), copy.getSuspensionId());
        assertTrue(copy.isCancelled());
    }

    @Test
    void GivenStaffAppointment_WhenMutatingAndRevoking_ThenPermissionBranchesAreCovered() {
        UUID appointer = UUID.randomUUID();
        UUID staffA = UUID.randomUUID();
        UUID staffB = UUID.randomUUID();
        StaffAppointment manager = new StaffAppointment(
                " Acme ",
                appointer,
                StaffAppointment.StaffRole.MANAGER,
                Set.of(ManagerPermission.VIEW_REPORTS),
                Set.of(staffA));
        StaffAppointment owner = new StaffAppointment(
                "Acme",
                appointer,
                StaffAppointment.StaffRole.OWNER,
                Set.of(ManagerPermission.POLICY_MODIFICATION));

        Set<ManagerPermission> permissionsWithNull = new HashSet<>();
        permissionsWithNull.add(null);

        assertAll(
                () -> assertEquals("acme", manager.getCompanyId()),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new StaffAppointment(null, appointer, StaffAppointment.StaffRole.MANAGER, Set.of())),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new StaffAppointment(" ", appointer, StaffAppointment.StaffRole.MANAGER, Set.of())),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new StaffAppointment("Acme", appointer, null, Set.of())),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new StaffAppointment("Acme", appointer, StaffAppointment.StaffRole.MANAGER,
                                permissionsWithNull)),
                () -> assertThrows(IllegalArgumentException.class, () -> manager.addAppointedStaffMember(null)),
                () -> assertThrows(IllegalArgumentException.class, () -> manager.addAppointedStaffMemberGroup(null)),
                () -> assertThrows(IllegalArgumentException.class, () -> manager.removeAppointedStaffMember(null))
        );

        manager.addAppointedStaffMember(staffB);
        manager.addAppointedStaffMemberGroup(Set.of(staffA, staffB));
        assertTrue(manager.getAppointedStaffMemberIds().contains(staffA));
        assertTrue(manager.getAppointedStaffMemberIds().contains(staffB));
        manager.removeAppointedStaffMember(staffA);
        assertFalse(manager.getAppointedStaffMemberIds().contains(staffA));

        assertFalse(manager.hasPermission(null));
        assertTrue(manager.hasPermission(ManagerPermission.VIEW_REPORTS));
        assertFalse(manager.hasPermission(ManagerPermission.POLICY_MODIFICATION));
        manager.updateManagerPermissions(Set.of(ManagerPermission.POLICY_MODIFICATION));
        assertTrue(manager.hasPermission(ManagerPermission.POLICY_MODIFICATION));

        assertTrue(owner.isOwner());
        assertTrue(owner.hasPermission(ManagerPermission.HANDLE_INQUIRIES));
        assertThrows(IllegalStateException.class, () -> owner.updateManagerPermissions(Set.of()));
        owner.revoke();
        assertFalse(owner.isActive());
        assertThrows(IllegalStateException.class, owner::revoke);

        manager.updateAppointedBy(UUID.randomUUID());
        StaffAppointment copy = manager.detachedCopy();
        assertEquals(manager.getCompanyId(), copy.getCompanyId());

        manager.revoke();
        assertFalse(manager.isActive());
        assertFalse(manager.hasPermission(ManagerPermission.POLICY_MODIFICATION));
        assertThrows(IllegalStateException.class, manager::revoke);
    }

    @Test
    void GivenProducerAndRoles_WhenConstructing_ThenRoleBranchesAreCovered() {
        UUID member = UUID.randomUUID();
        UUID appointer = UUID.randomUUID();
        Producer founder = new Producer("Acme", member, null, RoleFactory.createFounder());
        Producer owner = new Producer("Acme", member, appointer, RoleFactory.createOwner());
        Producer manager = new Producer("Acme", member, appointer,
                RoleFactory.createManager(List.of(ManagerPermission.MAP_DEFINITION, ManagerPermission.VIEW_REPORTS)));

        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new Producer(null, member, null, RoleFactory.createFounder())),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new Producer("", member, null, RoleFactory.createFounder())),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new Producer("Acme", null, null, RoleFactory.createFounder())),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new Producer("Acme", member, null, null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new Producer("Acme", member, member, RoleFactory.createOwner())),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new Producer("Acme", member, appointer, RoleFactory.createFounder())),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new Producer("Acme", member, null, RoleFactory.createOwner()))
        );

        assertAll(
                () -> assertEquals("Founder", founder.getRoleName()),
                () -> assertTrue(founder.canAppoint()),
                () -> assertTrue(owner.canManageInventory()),
                () -> assertTrue(owner.canHandleEventLifecycle()),
                () -> assertEquals("Manager", manager.getRoleName()),
                () -> assertFalse(manager.canAppoint()),
                () -> assertTrue(manager.canDefineMap()),
                () -> assertFalse(manager.canManageInventory()),
                () -> assertFalse(manager.canModifyPolicy()),
                () -> assertFalse(manager.canManagePersonnel()),
                () -> assertTrue(manager.canViewReports()),
                () -> assertFalse(manager.canHandleInquiries()),
                () -> assertFalse(manager.canHandleEventLifecycle())
        );

        manager.setAppointerId(null);
        manager.setRole(RoleFactory.createOwner());
        assertTrue(manager.canModifyPolicy());
    }

    private static Member validMember() {
        return new Member(UUID.randomUUID(), "user", "user@example.com", "encrypted", "050", LocalDate.of(2000, 1, 1));
    }
}
