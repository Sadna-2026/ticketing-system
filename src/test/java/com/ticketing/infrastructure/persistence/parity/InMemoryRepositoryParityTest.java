package com.ticketing.infrastructure.persistence.parity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;

import com.ticketing.domain.company.ICompanyRepository;
import com.ticketing.domain.member.IMemberRepository;
import com.ticketing.infrastructure.InMemoryCompanyRepository;
import com.ticketing.infrastructure.InMemoryMemberRepository;

/**
 * Memory-mode side of {@link MemberAndCompanyParityScenarios}. No Spring context —
 * just plain JUnit with fresh in-memory repos per test, which keeps the run fast
 * and guarantees test isolation.
 */
@DisplayName("Repository parity — in-memory side (#492)")
class InMemoryRepositoryParityTest implements MemberAndCompanyParityScenarios {

    private InMemoryMemberRepository memberRepo;
    private InMemoryCompanyRepository companyRepo;

    @BeforeEach
    void freshRepos() {
        // Each test gets pristine repositories. Without this, tests that count rows
        // or assert empties would see leftovers from earlier methods.
        this.memberRepo = new InMemoryMemberRepository();
        this.companyRepo = new InMemoryCompanyRepository();
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
