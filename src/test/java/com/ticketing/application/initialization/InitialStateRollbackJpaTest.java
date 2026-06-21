package com.ticketing.application.initialization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.ticketing.domain.company.ICompanyRepository;
import com.ticketing.domain.member.IMemberRepository;

/**
 * Verifies that a failed initial-state run does not leave partial data in the database (JPA mode).
 */
@SpringBootTest(properties = {
        "ticketing.persistence=jpa",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "ticketing.seed.enabled=false",
        "ticketing.bootstrap.dataset=none",
        "ticketing.startup.initialize-platform=false"
})
@DisplayName("Initial-state rollback on JPA")
class InitialStateRollbackJpaTest {

    @Autowired
    private InitialStateExecutor executor;

    @Autowired
    private IMemberRepository memberRepository;

    @Autowired
    private ICompanyRepository companyRepository;

    private final InitialStateParser parser = new InitialStateParser();

    @Test
    @DisplayName("Failed initialization leaves database empty")
    void givenFailingScript_whenExecute_thenNoPartialDataCommitted() {
        assertEquals(0, memberRepository.count());
        assertFalse(companyRepository.existsByName("p1"));

        List<InitialStateOperation> ops = parser.parse("""
                guest-registration(u1, u1@example.com, secret1);
                login(u1, secret1);
                open-production-company(u1_token, p1);
                set-invalid-operation(u1_token);
                """, "test.txt");

        InitialStateExecutionException ex =
                assertThrows(InitialStateExecutionException.class, () -> executor.execute(ops));

        assertTrue(ex.getMessage().contains("set-invalid-operation"), ex.getMessage());
        assertEquals(0, memberRepository.count(), "failed run must not persist members");
        assertFalse(companyRepository.existsByName("p1"), "failed run must not persist company");
    }
}
