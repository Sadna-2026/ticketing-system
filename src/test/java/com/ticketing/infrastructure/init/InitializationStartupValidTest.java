package com.ticketing.infrastructure.init;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.ticketing.domain.company.ICompanyRepository;
import com.ticketing.domain.member.IMemberRepository;
import com.ticketing.domain.member.Member;
import com.ticketing.domain.member.PendingRoleOffer;
import com.ticketing.domain.member.StaffAppointment;

/**
 * V3-24 (§2.4b): a VALID configuration + initial-state file boots the application to the expected
 * state. Boots the full Spring context (the V3-25 test profile keeps it isolated) with
 * {@code ticketing.initial-state.file} pointing at a temp file of valid use-case operations, and
 * asserts the replayed state is present once startup completes. Seeding is disabled so the asserted
 * state comes solely from the initial-state file.
 */
@SpringBootTest(properties = {
        "ticketing.seed.enabled=false",
        "ticketing.startup.initialize-platform=false"
})
@ActiveProfiles("test")
@DisplayName("Valid config + initial-state file boots to the expected state (V3-24)")
class InitializationStartupValidTest {

    @DynamicPropertySource
    static void initialStateFile(DynamicPropertyRegistry registry) throws IOException {
        Path file = Files.createTempFile("v3-init-valid", ".txt");
        Files.writeString(file, """
                guest-registration(rina, rina@example.com, secret1, 050-000-0000, 1990-01-01);
                guest-registration(dana, dana@example.com, secret2);
                login(rina, secret1);
                open-production-company(rina_token, "Demo Co", "A demo company");
                appoint-manager(rina_token, "Demo Co", dana);
                """);
        file.toFile().deleteOnExit();
        registry.add("ticketing.initial-state.file", file::toString);
    }

    @Autowired
    private IMemberRepository memberRepository;
    @Autowired
    private ICompanyRepository companyRepository;

    @Test
    void GivenValidInitialStateFile_WhenAppBoots_ThenStateIsReplayed() {
        // Both members from the file were registered.
        Member rina = memberRepository.findByUsername("rina").orElseThrow();
        Member dana = memberRepository.findByUsername("dana").orElseThrow();
        assertThat(rina.getEmail()).isEqualTo("rina@example.com");

        // The company exists with rina as its owner.
        assertThat(companyRepository.existsByName("Demo Co")).isTrue();
        StaffAppointment rinaAppt = rina.getStaffAppointment("Demo Co");
        assertThat(rinaAppt).isNotNull();
        assertThat(rinaAppt.isOwner()).isTrue();

        // And dana has the pending manager-role offer the file appointed.
        List<PendingRoleOffer> offers = dana.getPendingOffers();
        assertThat(offers).hasSize(1);
        assertThat(offers.get(0).getRole()).isEqualTo(StaffAppointment.StaffRole.MANAGER);
    }
}
