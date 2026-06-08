package com.ticketing.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import com.ticketing.domain.company.Company;
import com.ticketing.domain.company.ICompanyRepository;
import com.ticketing.domain.exception.OptimisticLockException;
import com.ticketing.domain.member.IMemberRepository;
import com.ticketing.domain.member.Member;

/**
 * V3-7: exercises the JPA-backed repositories against embedded H2.
 *
 * <p>Uses {@code @DataJpaTest} (slices in the Spring Data JPA repos + an H2
 * EntityManager) with {@code ticketing.persistence=jpa} so the adapter beans'
 * {@link ConditionalOnProperty} matches, and {@code ddl-auto=create-drop} so the
 * schema is built for the test (the app config uses {@code ddl-auto=none}). The
 * two adapters are explicitly {@code @Import}ed because they live outside the
 * default {@code @DataJpaTest} scan.
 */
@DataJpaTest(properties = {
        "ticketing.persistence=jpa",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({ JpaMemberRepository.class, JpaCompanyRepository.class })
@DisplayName("JPA member & company repositories on H2")
class JpaMemberCompanyRepositoryTest {

    @Autowired
    private IMemberRepository memberRepository;

    @Autowired
    private ICompanyRepository companyRepository;

    // ── Member ─────────────────────────────────────────────────────────────

    @Test
    void GivenMember_WhenSavedAndFetchedById_ThenRoundTrips() {
        UUID memberId = UUID.randomUUID();
        Member member = new Member(memberId, "alice", "alice@example.com", "encrypted-pw");

        memberRepository.save(member);
        var found = memberRepository.findById(memberId);

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(memberId);
        assertThat(found.get().getUsername()).isEqualTo("alice");
        assertThat(found.get().getEmail()).isEqualTo("alice@example.com");
    }

    @Test
    void GivenMemberSavedViaUniqueness_WhenDuplicateUsername_ThenRejected() {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        boolean firstSaved = memberRepository.saveIfUsernameAndEmailAvailable(
                new Member(firstId, "bob", "bob@example.com", "pw"));
        boolean secondSaved = memberRepository.saveIfUsernameAndEmailAvailable(
                new Member(secondId, "bob", "different@example.com", "pw"));

        assertThat(firstSaved).isTrue();
        assertThat(secondSaved).isFalse();
        assertThat(memberRepository.count()).isEqualTo(1L);
    }

    @Test
    void GivenTwoMemberSnapshots_WhenSecondSaveIsStale_ThenDomainOptimisticLockException() {
        UUID memberId = UUID.randomUUID();
        memberRepository.save(new Member(memberId, "carol", "carol@example.com", "pw"));

        Member first = memberRepository.findById(memberId).orElseThrow();
        Member second = memberRepository.findById(memberId).orElseThrow();

        first.updatePhoneNumber("0501111111");
        memberRepository.save(first);

        second.updatePhoneNumber("0502222222");
        assertThatExceptionOfType(OptimisticLockException.class)
                .isThrownBy(() -> memberRepository.save(second));
    }

    // ── Company ────────────────────────────────────────────────────────────

    @Test
    void GivenCompany_WhenSavedAndFetchedById_ThenRoundTrips() {
        UUID founderId = UUID.randomUUID();
        Company company = new Company("Acme Productions", "demo", founderId);

        companyRepository.save(company);
        var found = companyRepository.findById("Acme Productions");

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Acme Productions");
        assertThat(found.get().getFounderId()).isEqualTo(founderId);
        assertThat(companyRepository.findByName("Acme Productions")).isPresent();
        assertThat(companyRepository.existsByName("Acme Productions")).isTrue();
    }

    @Test
    void GivenExistingCompanyName_WhenSavingFreshCompanyWithSameName_ThenRejected() {
        companyRepository.save(new Company("Globex", "first", UUID.randomUUID()));

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> companyRepository.save(
                        new Company("Globex", "second", UUID.randomUUID())));
    }

    @Test
    void GivenTwoCompanySnapshots_WhenSecondSaveIsStale_ThenDomainOptimisticLockException() {
        companyRepository.save(new Company("Initech", "desc", UUID.randomUUID()));

        Company first = companyRepository.findByName("Initech").orElseThrow();
        Company second = companyRepository.findByName("Initech").orElseThrow();

        first.setDescription("updated by first");
        companyRepository.save(first);

        second.setDescription("updated by second");
        assertThatExceptionOfType(OptimisticLockException.class)
                .isThrownBy(() -> companyRepository.save(second));
    }
}
