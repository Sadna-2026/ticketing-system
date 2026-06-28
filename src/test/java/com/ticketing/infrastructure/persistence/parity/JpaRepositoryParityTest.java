package com.ticketing.infrastructure.persistence.parity;

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

    @Override
    public IMemberRepository memberRepo() {
        return memberRepo;
    }

    @Override
    public ICompanyRepository companyRepo() {
        return companyRepo;
    }
}
