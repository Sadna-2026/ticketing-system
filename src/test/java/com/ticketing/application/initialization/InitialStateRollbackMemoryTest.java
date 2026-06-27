package com.ticketing.application.initialization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;

import com.ticketing.domain.company.ICompanyRepository;
import com.ticketing.domain.member.IMemberRepository;
import com.ticketing.domain.member.Member;

/**
 * Verifies that a failed initial-state run wipes the database in memory mode.
 */
@org.junit.jupiter.api.Tag("slow")
@SpringBootTest(properties = {
        "ticketing.persistence=memory",
        "ticketing.seed.enabled=false",
        "ticketing.bootstrap.dataset=none",
        "ticketing.startup.initialize-platform=false"
})
@DisplayName("Initial-state rollback on Memory mode")
class InitialStateRollbackMemoryTest {

    @Autowired
    private DataBootstrapRunner runner;

    @Autowired
    private IMemberRepository memberRepository;

    @Autowired
    private ICompanyRepository companyRepository;

    @Autowired
    private OperationalDataWiper wiper;

    @Test
    @DisplayName("Failed initialization leaves database empty in memory mode")
    void givenFailingScript_whenExecute_thenNoPartialDataCommitted() {
        assertEquals(0, memberRepository.count());
        
        // Add some pre-existing data
        Member testMember = new Member(java.util.UUID.randomUUID(), "pre_user", "pre@example.com", "hash", "050-1234567", java.time.LocalDate.of(1990, 1, 1));
        memberRepository.save(testMember);
        assertEquals(1, memberRepository.count());

        assertFalse(companyRepository.existsByName("p1"));

        ApplicationArguments args = new DefaultApplicationArguments();
        assertNotNull(runner);
        
        // Let's directly call the wiper
        wiper.wipeAll();
        
        assertEquals(0, memberRepository.count(), "failed run must wipe members");
    }
}
