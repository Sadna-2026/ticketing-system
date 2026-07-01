package com.ticketing.infrastructure.persistence.parity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.ticketing.domain.company.Company;
import com.ticketing.domain.company.ICompanyRepository;
import com.ticketing.domain.member.IMemberRepository;
import com.ticketing.domain.member.Member;

/**
 * #492 (JPA ↔ memory parity): observable-behaviour contract that BOTH repository
 * implementations of {@link IMemberRepository} and {@link ICompanyRepository} must
 * satisfy identically. Implementing test classes pick one mode (memory or jpa) and
 * inherit every scenario as a {@code @Test}.
 *
 * <p>The point is to catch divergence early: a query that returns {@code null} in
 * one impl and {@link java.util.Optional#empty()} in the other; a normalisation
 * (case / trim) applied by one and not the other; a duplicate-check honoured by
 * one and not the other. {@code application.yml} flips between modes with a single
 * property — those flips must be invisible to the application services.
 *
 * <p>Default methods on a JUnit 5 interface are picked up as tests on the
 * implementing class, so the same body runs once per implementation. The bean
 * uniqueness side of the acceptance criterion ("exactly one active impl per
 * interface per mode") lives in {@link PersistenceModeUniqueImplTest}.
 */
interface MemberAndCompanyParityScenarios {

    /** The {@link IMemberRepository} this run is exercising. Fresh per test class instance. */
    IMemberRepository memberRepo();

    /** The {@link ICompanyRepository} this run is exercising. Fresh per test class instance. */
    ICompanyRepository companyRepo();

    // ── Member: round-trip persistence ─────────────────────────────────────────

    @Test
    default void saveMemberThenFindById_returnsEquivalentSnapshot() {
        UUID id = UUID.randomUUID();
        Member original = new Member(id, "parity-user-" + id, id + "@parity.example", "encryptedPw");

        memberRepo().save(original);
        var found = memberRepo().findById(id);

        assertThat(found).as("findById should return a present Optional after save").isPresent();
        assertThat(found.get().getId()).isEqualTo(id);
        assertThat(found.get().getUsername()).isEqualTo("parity-user-" + id);
        assertThat(found.get().getEmail()).isEqualTo(id + "@parity.example");
    }

    @Test
    default void findByIdOnAbsentMember_returnsEmptyOptional_neverNull() {
        // Both impls MUST return Optional.empty() — never null. NPEs from callers
        // that assumed Optional in one mode and got null in the other have been a
        // source of confusion historically; the parity contract pins this.
        var found = memberRepo().findById(UUID.randomUUID());
        assertThat(found)
                .as("absent lookup returns an Optional, not null")
                .isNotNull()
                .isEmpty();
    }

    @Test
    default void findByUsernameOnAbsentMember_returnsEmptyOptional() {
        assertThat(memberRepo().findByUsername("does-not-exist-" + UUID.randomUUID()))
                .as("absent username lookup returns Optional.empty(), not null")
                .isNotNull()
                .isEmpty();
    }

    @Test
    default void findByEmailOnAbsentMember_returnsEmptyOptional() {
        assertThat(memberRepo().findByEmail("missing-" + UUID.randomUUID() + "@parity.example"))
                .as("absent email lookup returns Optional.empty(), not null")
                .isNotNull()
                .isEmpty();
    }

    // ── Member: uniqueness checks and update guards ───────────────────────────

    @Test
    default void saveIfUsernameAndEmailAvailable_acceptsFirstAndRejectsDuplicateUsername() {
        String uniqueSuffix = UUID.randomUUID().toString();
        String username = "first-" + uniqueSuffix;
        Member first = new Member(UUID.randomUUID(), username, "first-" + uniqueSuffix + "@parity.example", "pw");
        Member dup = new Member(UUID.randomUUID(), username, "second-" + uniqueSuffix + "@parity.example", "pw");

        assertThat(memberRepo().saveIfUsernameAndEmailAvailable(first))
                .as("first save with a free username should succeed")
                .isTrue();
        assertThat(memberRepo().saveIfUsernameAndEmailAvailable(dup))
                .as("second save with the same username should be rejected")
                .isFalse();
    }

    @Test
    default void saveIfUsernameAndEmailAvailable_acceptsFirstAndRejectsDuplicateEmail() {
        String uniqueSuffix = UUID.randomUUID().toString();
        String email = "shared-" + uniqueSuffix + "@parity.example";
        Member first = new Member(UUID.randomUUID(), "user-a-" + uniqueSuffix, email, "pw");
        Member dup = new Member(UUID.randomUUID(), "user-b-" + uniqueSuffix, email, "pw");

        assertThat(memberRepo().saveIfUsernameAndEmailAvailable(first)).isTrue();
        assertThat(memberRepo().saveIfUsernameAndEmailAvailable(dup))
                .as("second save with the same email should be rejected")
                .isFalse();
    }

    @Test
    default void existsByUsername_isTrueAfterSave_andFalseOtherwise() {
        String username = "exists-" + UUID.randomUUID();
        assertThat(memberRepo().existsByUsername(username)).isFalse();

        memberRepo().saveIfUsernameAndEmailAvailable(
                new Member(UUID.randomUUID(), username, username + "@parity.example", "pw"));

        assertThat(memberRepo().existsByUsername(username)).isTrue();
    }

    @Test
    default void countReflectsAllSavedMembers() {
        long before = memberRepo().count();

        memberRepo().saveIfUsernameAndEmailAvailable(
                new Member(UUID.randomUUID(), "count-a-" + UUID.randomUUID(),
                        "count-a-" + UUID.randomUUID() + "@parity.example", "pw"));
        memberRepo().saveIfUsernameAndEmailAvailable(
                new Member(UUID.randomUUID(), "count-b-" + UUID.randomUUID(),
                        "count-b-" + UUID.randomUUID() + "@parity.example", "pw"));

        assertThat(memberRepo().count())
                .as("count should increase by exactly 2 after two saves")
                .isEqualTo(before + 2);
    }

    // ── Company: round-trip persistence ───────────────────────────────────────

    @Test
    default void saveCompanyThenFindByName_returnsEquivalentSnapshot() {
        String name = "ParityCorp-" + UUID.randomUUID();
        UUID founderId = UUID.randomUUID();

        companyRepo().save(new Company(name, "parity description", founderId));
        var found = companyRepo().findByName(name);

        assertThat(found).as("findByName should return a present Optional after save").isPresent();
        assertThat(found.get().getName()).isEqualToIgnoringCase(name);
        assertThat(found.get().getFounderId()).isEqualTo(founderId);
    }

    @Test
    default void findByNameOnAbsentCompany_returnsEmptyOptional_neverNull() {
        assertThat(companyRepo().findByName("never-saved-" + UUID.randomUUID()))
                .as("absent company lookup returns Optional.empty(), not null")
                .isNotNull()
                .isEmpty();
    }

    @Test
    default void existsByName_isTrueAfterSave() {
        String name = "ExistsCorp-" + UUID.randomUUID();
        assertThat(companyRepo().existsByName(name)).isFalse();

        companyRepo().save(new Company(name, "desc", UUID.randomUUID()));

        assertThat(companyRepo().existsByName(name)).isTrue();
    }

    @Test
    default void findActiveCompanies_excludesSuspendedAndClosed() {
        String activeName = "active-" + UUID.randomUUID();
        String suspendedName = "suspended-" + UUID.randomUUID();
        String closedName = "closed-" + UUID.randomUUID();

        Company active = new Company(activeName, "still here", UUID.randomUUID());
        Company suspended = new Company(suspendedName, "on hold", UUID.randomUUID());
        suspended.suspend();
        Company closed = new Company(closedName, "gone", UUID.randomUUID());
        closed.close();

        companyRepo().save(active);
        companyRepo().save(suspended);
        companyRepo().save(closed);

        var actives = companyRepo().findActiveCompanies("");
        var activeNames = actives.stream().map(Company::getName).toList();

        assertThat(activeNames)
                .as("active query should include the active company")
                .anyMatch(n -> n.equalsIgnoreCase(activeName));
        assertThat(activeNames)
                .as("active query should exclude suspended and closed companies")
                .noneMatch(n -> n.equalsIgnoreCase(suspendedName))
                .noneMatch(n -> n.equalsIgnoreCase(closedName));
    }
}
