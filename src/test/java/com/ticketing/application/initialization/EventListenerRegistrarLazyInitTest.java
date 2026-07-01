package com.ticketing.application.initialization;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.ticketing.application.auth.ISessionTokenService;
import com.ticketing.application.services.CompanyService;
import com.ticketing.application.services.MemberService;
import com.ticketing.domain.member.IMemberRepository;
import com.ticketing.domain.member.Member;
import com.ticketing.domain.member.request.RegisterRequest;
import com.ticketing.domain.member.response.RegisterResponse;

/**
 * Regression test for the founder-owner event wiring under {@code spring.main.lazy-initialization=true}
 * (the production default).
 *
 * <p>{@code EventListenerRegistrar} is a {@code @Component} that nothing else injects, so under global
 * lazy init it is only created — and its {@code @PostConstruct} that subscribes the domain event
 * listeners only runs — if it is forced eager ({@code @Lazy(false)}). Without that, publishing
 * {@code CompanyOpenedEvent} has no subscriber, the founder is never assigned the OWNER role, and the
 * first {@code create-event} in the V3 final scenario fails with
 * "Caller is not a staff member of company". This test reproduces that wiring end-to-end against the
 * real application context.
 */
@SpringBootTest(properties = {
        "ticketing.persistence=memory",
        "spring.main.lazy-initialization=true",
        "ticketing.startup.initialize-platform=false",
        "ticketing.bootstrap.dataset=none",
        "ticketing.seed.enabled=false",
        "ticketing.external.base-url="
})
@ActiveProfiles("test")
@DisplayName("EventListenerRegistrar under lazy init")
class EventListenerRegistrarLazyInitTest {

    private static final String COMPANY = "LazyInit QA Co";

    @Autowired
    private MemberService memberService;

    @Autowired
    private CompanyService companyService;

    @Autowired
    private ISessionTokenService sessionTokenService;

    @Autowired
    private IMemberRepository memberRepository;

    @Test
    @DisplayName("Founder opening a company is assigned OWNER via the CompanyOpened listener")
    void givenLazyInit_whenFounderOpensCompany_thenOwnerAppointmentAssigned() {
        String guestToken = sessionTokenService.generateGuestToken();
        RegisterResponse registration = memberService.register(
                new RegisterRequest("founder1", "founder1@test.com", "secret1", null, (LocalDate) null),
                guestToken);
        assertThat(registration.success()).isTrue();

        // Publishes CompanyOpenedEvent — only handled if EventListenerRegistrar ran at startup.
        companyService.openProductionCompany(registration.sessionToken(), COMPANY, "desc");

        Member founder = memberRepository.findByUsername("founder1").orElseThrow();
        assertThat(founder.getStaffAppointment(COMPANY)).isNotNull();
        assertThat(founder.getStaffAppointment(COMPANY).isOwner()).isTrue();
    }
}
