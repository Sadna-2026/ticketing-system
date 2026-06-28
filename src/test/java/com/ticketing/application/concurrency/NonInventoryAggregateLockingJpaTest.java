package com.ticketing.application.concurrency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import com.ticketing.domain.company.Company;
import com.ticketing.domain.company.ICompanyRepository;
import com.ticketing.domain.event.AgeRestrictionPolicy;
import com.ticketing.domain.event.AlwaysAllowPolicy;
import com.ticketing.domain.exception.OptimisticLockException;
import com.ticketing.domain.member.IMemberRepository;
import com.ticketing.domain.member.ManagerPermission;
import com.ticketing.domain.member.Member;
import com.ticketing.domain.member.StaffAppointment;

/**
 * #509: DB-level optimistic locking on writable aggregates beyond seats/zones.
 *
 * <p>SeatReservationLockingJpaTest already proves the inventory hot-path (Seat /
 * InventoryZone @Version) prevents double-sell. This suite extends that proof to the
 * other writable aggregates the system mutates concurrently in real workflows:
 * Member profile edits, Company policy edits, and StaffAppointment changes.
 *
 * <p>Each scenario follows the same shape borrowed from SeatReservationLockingJpaTest:
 * two transactions load independent detached snapshots, the first commits and bumps
 * the row version, and the second commit is rejected by the {@code @Version} guard at
 * flush time. The repositories translate the Hibernate / JPA exception into the domain
 * {@link OptimisticLockException}, so callers see a stable type they can react to.
 *
 * <p>Together with the {@code @Version} additions in #510 and the inventory tests in
 * V3-11 (#269), this closes the must-have requirement that no writable aggregate is
 * left to silent last-write-wins under concurrency.
 */
@org.junit.jupiter.api.Tag("slow")
@SpringBootTest(
        properties = {
                "ticketing.persistence=jpa",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "ticketing.seed.enabled=false",
                "ticketing.startup.initialize-platform=false"
        })
@DisplayName("DB-level optimistic locking on non-inventory writable aggregates (#509)")
class NonInventoryAggregateLockingJpaTest {

    @Autowired
    private IMemberRepository memberRepository;
    @Autowired
    private ICompanyRepository companyRepository;
    @Autowired
    private TransactionTemplate transactionTemplate;

    // ── Member profile race: two transactions edit the same member's profile ──────

    @Test
    @DisplayName("Given two transactions updating the same member profile, When both commit, Then exactly one succeeds")
    void GivenTwoTransactionsUpdatingSameMemberProfile_WhenBothCommit_ThenExactlyOneSucceeds() {
        UUID memberId = UUID.randomUUID();
        Member seed = new Member(memberId, "racer-" + memberId, memberId + "@t.example", "encryptedPw");
        memberRepository.saveIfUsernameAndEmailAvailable(seed);

        // Two transactions load independent snapshots at the same row version.
        Member snapshotA = memberRepository.findById(memberId).orElseThrow();
        Member snapshotB = memberRepository.findById(memberId).orElseThrow();

        // Transaction A flips the phone number first and commits → bumps Member.@Version.
        transactionTemplate.executeWithoutResult(status -> {
            snapshotA.updatePhoneNumber("+972-50-1111111");
            memberRepository.save(snapshotA);
        });

        // Transaction B, holding the now-stale version, is rejected by the @Version guard.
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            snapshotB.updatePhoneNumber("+972-50-2222222");
            memberRepository.save(snapshotB);
        })).isInstanceOf(OptimisticLockException.class);

        // DB ends consistent: the winning write is the one that actually persisted.
        Member persisted = memberRepository.findById(memberId).orElseThrow();
        assertThat(persisted.getPhoneNumber())
                .as("only one writer's phone number survives")
                .isEqualTo("+972-50-1111111");
    }

    @Test
    @DisplayName("Given two transactions updating different scalar fields on same member, When both commit, Then the second still conflicts on @Version")
    void GivenTwoTransactionsUpdatingDifferentScalarFieldsOnSameMember_WhenBothCommit_ThenSecondConflicts() {
        // Optimistic locking on aggregate roots is row-level, not field-level: two writers
        // mutating different scalars on the same Member still conflict. The test guards
        // against accidentally narrowing the version check to per-field tracking later.
        UUID memberId = UUID.randomUUID();
        Member seed = new Member(memberId, "split-" + memberId, memberId + "@t.example", "encryptedPw");
        memberRepository.saveIfUsernameAndEmailAvailable(seed);

        Member snapshotA = memberRepository.findById(memberId).orElseThrow();
        Member snapshotB = memberRepository.findById(memberId).orElseThrow();

        transactionTemplate.executeWithoutResult(status -> {
            snapshotA.updateDateOfBirth(LocalDate.of(1990, 1, 1));
            memberRepository.save(snapshotA);
        });

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            snapshotB.updatePhoneNumber("+972-50-9999999");
            memberRepository.save(snapshotB);
        })).isInstanceOf(OptimisticLockException.class);
    }

    // ── Company policy race: two transactions edit the same company's purchase policy ──

    @Test
    @DisplayName("Given two transactions editing the same company purchase policy, When both commit, Then exactly one succeeds")
    void GivenTwoTransactionsEditingSameCompanyPurchasePolicy_WhenBothCommit_ThenExactlyOneSucceeds() {
        UUID founderId = UUID.randomUUID();
        String companyName = "RaceCorp-" + UUID.randomUUID();
        companyRepository.save(new Company(companyName, "race-suite seed", founderId));

        Company snapshotA = companyRepository.findByName(companyName).orElseThrow();
        Company snapshotB = companyRepository.findByName(companyName).orElseThrow();

        transactionTemplate.executeWithoutResult(status -> {
            snapshotA.setPurchasePolicy(new AgeRestrictionPolicy(18));
            companyRepository.save(snapshotA);
        });

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            snapshotB.setPurchasePolicy(new AlwaysAllowPolicy());
            companyRepository.save(snapshotB);
        })).isInstanceOf(OptimisticLockException.class);

        Company persisted = companyRepository.findByName(companyName).orElseThrow();
        assertThat(persisted.getPurchasePolicy())
                .as("only the first writer's policy survives")
                .isInstanceOf(AgeRestrictionPolicy.class);
    }

    @Test
    @DisplayName("Given two transactions flipping the same company discount-stacking flag, When both commit, Then exactly one succeeds")
    void GivenTwoTransactionsFlippingSameCompanyDiscountStacking_WhenBothCommit_ThenExactlyOneSucceeds() {
        UUID founderId = UUID.randomUUID();
        String companyName = "FlipCorp-" + UUID.randomUUID();
        Company seed = new Company(companyName, "stacking seed", founderId);
        seed.setAllowDiscountStacking(false);
        companyRepository.save(seed);

        Company snapshotA = companyRepository.findByName(companyName).orElseThrow();
        Company snapshotB = companyRepository.findByName(companyName).orElseThrow();

        transactionTemplate.executeWithoutResult(status -> {
            snapshotA.setAllowDiscountStacking(true);
            companyRepository.save(snapshotA);
        });

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            snapshotB.setAllowDiscountStacking(true); // same intended end-state, still conflicts
            companyRepository.save(snapshotB);
        })).isInstanceOf(OptimisticLockException.class);
    }

    // ── Manager-appointment race: concurrent revoke vs. permissions change ────────

    @Test
    @DisplayName("Given concurrent revoke and permissions change on the same appointment, When both commit, Then exactly one succeeds")
    void GivenConcurrentRevokeAndPermissionsChangeOnSameAppointment_WhenBothCommit_ThenExactlyOneSucceeds() {
        UUID founderId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();
        String companyName = "appoint-corp-" + UUID.randomUUID();

        // Founder owns the company; manager has a MANAGER appointment with one permission.
        memberRepository.saveIfUsernameAndEmailAvailable(
                new Member(founderId, "founder-" + founderId, founderId + "@t.example", "p"));
        Member managerSeed = new Member(managerId, "mgr-" + managerId, managerId + "@t.example", "p");
        managerSeed.addStaffAppointment(companyName, new StaffAppointment(
                companyName, founderId, StaffAppointment.StaffRole.MANAGER,
                Set.of(ManagerPermission.INVENTORY_MGMT)));
        memberRepository.saveIfUsernameAndEmailAvailable(managerSeed);
        companyRepository.save(new Company(companyName, "appoint-corp seed", founderId));

        // Two independent snapshots of the manager Member, each holding a detached copy
        // of the appointment at the same Member.@Version and StaffAppointment.@Version.
        Member snapshotA = memberRepository.findById(managerId).orElseThrow();
        Member snapshotB = memberRepository.findById(managerId).orElseThrow();

        // Transaction A revokes the appointment and commits → bumps versions.
        transactionTemplate.executeWithoutResult(status -> {
            snapshotA.getStaffAppointment(companyName).revoke();
            memberRepository.save(snapshotA);
        });

        // Transaction B, built on the stale snapshot, tries to tweak permissions instead.
        // Either Member.@Version or StaffAppointment.@Version (or the cascade thereof) catches it.
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            snapshotB.getStaffAppointment(companyName)
                    .updateManagerPermissions(Collections.emptySet());
            memberRepository.save(snapshotB);
        })).isInstanceOf(OptimisticLockException.class);

        // DB ends consistent: the revoke is what survived, with no permissions overlay
        // from the losing snapshot.
        Member persisted = memberRepository.findById(managerId).orElseThrow();
        StaffAppointment finalAppt = persisted.getStaffAppointment(companyName);
        assertThat(finalAppt.isRevoked())
                .as("the revoke from transaction A is the one that survived")
                .isTrue();
    }
}
