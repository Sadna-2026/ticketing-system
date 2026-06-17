package com.ticketing.domain.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import com.ticketing.domain.member.StaffAppointment.StaffRole;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class MemberJpaMappingTest {

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void GivenMemberWithOwnedTypes_WhenPersistedAndReloaded_ThenAllScalarFieldsSurvive() {
        UUID memberId = UUID.randomUUID();
        LocalDate dateOfBirth = LocalDate.of(1995, 6, 15);
        Member member = new Member(memberId, "tamar", "tamar@example.com", "encrypted-pw",
                "0500000000", dateOfBirth);

        entityManager.persistAndFlush(member);
        entityManager.clear();

        Member reloaded = entityManager.find(Member.class, memberId);

        assertThat(reloaded).isNotNull();
        assertThat(reloaded.getId()).isEqualTo(memberId);
        assertThat(reloaded.getUsername()).isEqualTo("tamar");
        assertThat(reloaded.getEmail()).isEqualTo("tamar@example.com");
        assertThat(reloaded.getEncryptedPassword()).isEqualTo("encrypted-pw");
        assertThat(reloaded.getPhoneNumber()).isEqualTo("0500000000");
        assertThat(reloaded.getDateOfBirth()).isEqualTo(dateOfBirth);
    }

    @Test
    void GivenMemberWithSuspension_WhenPersistedAndReloaded_ThenSuspensionRoundTrips() {
        UUID memberId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        Instant start = Instant.parse("2026-01-01T10:00:00Z");
        Member member = new Member(memberId, "suspended", "suspended@example.com", "pw");
        Suspension suspension = new Suspension(adminId, start, Duration.ofHours(2), "spamming");
        member.addSuspension(suspension);

        entityManager.persistAndFlush(member);
        entityManager.clear();

        Member reloaded = entityManager.find(Member.class, memberId);

        assertThat(reloaded.getSuspensions()).hasSize(1);
        Suspension reloadedSuspension = reloaded.getSuspensions().get(0);
        assertThat(reloadedSuspension.getSuspensionId()).isEqualTo(suspension.getSuspensionId());
        assertThat(reloadedSuspension.getImposedByAdminId()).isEqualTo(adminId);
        assertThat(reloadedSuspension.getStartTime()).isEqualTo(start);
        assertThat(reloadedSuspension.getDuration()).isEqualTo(Duration.ofHours(2));
        assertThat(reloadedSuspension.getReason()).isEqualTo("spamming");
        assertThat(reloadedSuspension.isCancelled()).isFalse();
        assertThat(reloadedSuspension.isPermanent()).isFalse();
    }

    @Test
    void GivenMemberWithStaffAppointment_WhenPersistedAndReloaded_ThenAppointmentAndItsCollectionsRoundTrip() {
        UUID memberId = UUID.randomUUID();
        UUID appointer = UUID.randomUUID();
        UUID appointedStaff = UUID.randomUUID();
        Member member = new Member(memberId, "owner", "owner@example.com", "pw");
        StaffAppointment appointment = new StaffAppointment(
                "Acme",
                appointer,
                StaffRole.MANAGER,
                Set.of(ManagerPermission.POLICY_MODIFICATION, ManagerPermission.VIEW_REPORTS),
                Set.of(appointedStaff));
        member.addStaffAppointment("acme", appointment);

        entityManager.persistAndFlush(member);
        entityManager.clear();

        Member reloaded = entityManager.find(Member.class, memberId);

        StaffAppointment reloadedAppointment = reloaded.getStaffAppointment("acme");
        assertThat(reloadedAppointment).isNotNull();
        assertThat(reloadedAppointment.getCompanyId()).isEqualTo("acme");
        assertThat(reloadedAppointment.getAppointedByMemberId()).isEqualTo(appointer);
        assertThat(reloadedAppointment.getRole()).isEqualTo(StaffRole.MANAGER);
        assertThat(reloadedAppointment.isRevoked()).isFalse();
        assertThat(reloadedAppointment.getPermissions())
                .containsExactlyInAnyOrder(
                        ManagerPermission.POLICY_MODIFICATION,
                        ManagerPermission.VIEW_REPORTS);
        assertThat(reloadedAppointment.getAppointedStaffMemberIds())
                .containsExactly(appointedStaff);
    }

    @Test
    void GivenMemberWithPendingRoleOffer_WhenPersistedAndReloaded_ThenOfferAndPermissionsRoundTrip() {
        UUID memberId = UUID.randomUUID();
        UUID offeredBy = UUID.randomUUID();
        LocalDateTime dueDate = LocalDateTime.of(2026, 12, 31, 23, 59);
        Member member = new Member(memberId, "offeree", "offeree@example.com", "pw");
        PendingRoleOffer offer = new PendingRoleOffer(
                offeredBy,
                "Globex",
                StaffRole.MANAGER,
                Set.of(ManagerPermission.INVENTORY_MGMT),
                dueDate);
        member.addPendingOffer(offer);

        entityManager.persistAndFlush(member);
        entityManager.clear();

        Member reloaded = entityManager.find(Member.class, memberId);

        assertThat(reloaded.getPendingOffers()).hasSize(1);
        PendingRoleOffer reloadedOffer = reloaded.findPendingOffer(offer.getOfferId()).orElseThrow();
        assertThat(reloadedOffer.getOfferId()).isEqualTo(offer.getOfferId());
        assertThat(reloadedOffer.getOfferedByMemberId()).isEqualTo(offeredBy);
        assertThat(reloadedOffer.getCompanyName()).isEqualTo("Globex");
        assertThat(reloadedOffer.getRole()).isEqualTo(StaffRole.MANAGER);
        // createdAt is generated with nanosecond precision but the timestamp column
        // stores microseconds, so the value is rounded on round-trip; allow a tolerance.
        assertThat(reloadedOffer.getCreatedAt())
                .isCloseTo(offer.getCreatedAt(), within(1, ChronoUnit.MILLIS));
        assertThat(reloadedOffer.getDueDate()).isEqualTo(dueDate);
        assertThat(reloadedOffer.getPermissions())
                .containsExactly(ManagerPermission.INVENTORY_MGMT);
    }

    @Test
    void GivenFullyPopulatedMember_WhenPersistedAndReloaded_ThenEveryOwnedTypeSurvivesTogether() {
        UUID memberId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        UUID appointer = UUID.randomUUID();
        UUID appointedStaff = UUID.randomUUID();
        UUID offeredBy = UUID.randomUUID();
        LocalDate dateOfBirth = LocalDate.of(1990, 3, 20);
        Instant suspensionStart = Instant.parse("2026-02-01T08:00:00Z");
        LocalDateTime dueDate = LocalDateTime.of(2026, 11, 30, 12, 0);

        Member member = new Member(memberId, "complete", "complete@example.com", "encrypted",
                "0521234567", dateOfBirth);

        Suspension suspension = new Suspension(adminId, suspensionStart, null, "permanent ban");
        member.addSuspension(suspension);

        StaffAppointment appointment = new StaffAppointment(
                "Initech",
                appointer,
                StaffRole.OWNER,
                Set.of(ManagerPermission.PERSONNEL_MGMT),
                Set.of(appointedStaff));
        member.addStaffAppointment("initech", appointment);

        PendingRoleOffer offer = new PendingRoleOffer(
                offeredBy,
                "Initech",
                StaffRole.MANAGER,
                Set.of(ManagerPermission.HANDLE_INQUIRIES, ManagerPermission.EVENT_LIFECYCLE),
                dueDate);
        member.addPendingOffer(offer);

        entityManager.persistAndFlush(member);
        entityManager.clear();

        Member reloaded = entityManager.find(Member.class, memberId);

        // Scalar / contact info
        assertThat(reloaded.getUsername()).isEqualTo("complete");
        assertThat(reloaded.getEmail()).isEqualTo("complete@example.com");
        assertThat(reloaded.getEncryptedPassword()).isEqualTo("encrypted");
        assertThat(reloaded.getPhoneNumber()).isEqualTo("0521234567");
        assertThat(reloaded.getDateOfBirth()).isEqualTo(dateOfBirth);

        // Suspension (permanent)
        assertThat(reloaded.getSuspensions()).hasSize(1);
        Suspension reloadedSuspension = reloaded.getSuspensions().get(0);
        assertThat(reloadedSuspension.getSuspensionId()).isEqualTo(suspension.getSuspensionId());
        assertThat(reloadedSuspension.getImposedByAdminId()).isEqualTo(adminId);
        assertThat(reloadedSuspension.getStartTime()).isEqualTo(suspensionStart);
        assertThat(reloadedSuspension.getDuration()).isNull();
        assertThat(reloadedSuspension.isPermanent()).isTrue();
        assertThat(reloadedSuspension.getReason()).isEqualTo("permanent ban");

        // Staff appointment + its nested collections
        StaffAppointment reloadedAppointment = reloaded.getStaffAppointment("initech");
        assertThat(reloadedAppointment).isNotNull();
        assertThat(reloadedAppointment.getCompanyId()).isEqualTo("initech");
        assertThat(reloadedAppointment.getAppointedByMemberId()).isEqualTo(appointer);
        assertThat(reloadedAppointment.getRole()).isEqualTo(StaffRole.OWNER);
        assertThat(reloadedAppointment.getPermissions())
                .containsExactly(ManagerPermission.PERSONNEL_MGMT);
        assertThat(reloadedAppointment.getAppointedStaffMemberIds())
                .containsExactly(appointedStaff);

        // Pending role offer + its permission collection
        assertThat(reloaded.getPendingOffers()).hasSize(1);
        PendingRoleOffer reloadedOffer = reloaded.findPendingOffer(offer.getOfferId()).orElseThrow();
        assertThat(reloadedOffer.getOfferedByMemberId()).isEqualTo(offeredBy);
        assertThat(reloadedOffer.getCompanyName()).isEqualTo("Initech");
        assertThat(reloadedOffer.getRole()).isEqualTo(StaffRole.MANAGER);
        assertThat(reloadedOffer.getDueDate()).isEqualTo(dueDate);
        assertThat(reloadedOffer.getPermissions())
                .containsExactlyInAnyOrder(
                        ManagerPermission.HANDLE_INQUIRIES,
                        ManagerPermission.EVENT_LIFECYCLE);
    }
}
