package com.ticketing.infrastructure.persistence.parity;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import com.ticketing.domain.company.ICompanyRepository;
import com.ticketing.domain.member.IMemberRepository;
import com.ticketing.infrastructure.persistence.JpaCompanyRepository;
import com.ticketing.infrastructure.persistence.JpaMemberRepository;

/**
 * JPA-mode side of {@link MemberAndCompanyParityScenarios}. Uses {@code @DataJpaTest}
 * (a lightweight slice — JPA + H2 + the two repo adapters) with
 * {@code ticketing.persistence=jpa} so the conditional adapter beans wire, and
 * {@code ddl-auto=create-drop} so the schema is built per test class.
 *
 * <p>This mirrors the wiring of {@link com.ticketing.infrastructure.persistence
 * .JpaMemberCompanyRepositoryTest JpaMemberCompanyRepositoryTest} — but the
 * scenarios are the parity-contract ones, not JPA-specific behaviour.
 */
@DataJpaTest(properties = {
        "ticketing.persistence=jpa",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({ JpaMemberRepository.class, JpaCompanyRepository.class })
@DisplayName("Repository parity — JPA side (#492)")
class JpaRepositoryParityTest implements MemberAndCompanyParityScenarios {

    @Autowired
    private IMemberRepository memberRepo;

    @Autowired
    private ICompanyRepository companyRepo;

    /**
     * Explicit cleanup around every test method. {@code @DataJpaTest} normally wraps
     * each test in a transaction with auto-rollback, but that machinery doesn't
     * reliably apply to test methods inherited as {@code default} methods on a Java
     * interface — Spring's {@code TransactionalTestExecutionListener} resolves
     * {@code @Transactional} from the test method's declared element (the interface)
     * and misses the class-level annotation contributed by {@code @DataJpaTest}.
     *
     * <p>Without this cleanup the rows committed by these parity scenarios leak into
     * the cached H2 instance, and any other {@code @DataJpaTest} class sharing the
     * same context cache key (notably {@code JpaMemberCompanyRepositoryTest}, which
     * asserts {@code count() == 1L}) sees those leftovers and fails in CI.
     *
     * <p>Both {@code @BeforeEach} and {@code @AfterEach} run the truncation to
     * defend against (a) prior pollution from another test class and (b) leaving
     * pollution for the next.
     */
    @BeforeEach
    @AfterEach
    void truncateMemberAndCompany() {
        memberRepo.deleteAll();
        companyRepo.deleteAll();
    }

    @Override
    public IMemberRepository memberRepo() {
        return memberRepo;
    }

    @Override
    public ICompanyRepository companyRepo() {
        return companyRepo;
    }
}
