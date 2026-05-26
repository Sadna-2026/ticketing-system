package com.ticketing.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.ticketing.application.auth.ISessionTokenService;
import com.ticketing.application.services.PolicyService;
import com.ticketing.domain.company.Company;
import com.ticketing.domain.company.CompanyStatus;
import com.ticketing.domain.company.ICompanyRepository;
import com.ticketing.domain.event.AlwaysAllowPolicy;
import com.ticketing.domain.event.AndPolicy;
import com.ticketing.domain.event.Event;
import com.ticketing.domain.event.EventCategory;
import com.ticketing.domain.event.EventSchedule;
import com.ticketing.domain.event.IDiscountPolicy;
import com.ticketing.domain.event.IEventRepository;
import com.ticketing.domain.event.IPurchasePolicy;
import com.ticketing.domain.event.LockTimerDuration;
import com.ticketing.domain.event.NoDiscountPolicy;
import com.ticketing.domain.event.OrPolicy;
import com.ticketing.domain.event.PolicyResult;
import com.ticketing.domain.member.IMemberRepository;
import com.ticketing.domain.member.ManagerPermission;
import com.ticketing.domain.member.Member;
import com.ticketing.domain.member.StaffAppointment;
import com.ticketing.domain.order.ActiveOrder;
import com.ticketing.infrastructure.InMemoryCompanyRepository;
import com.ticketing.infrastructure.InMemoryEventRepository;
import com.ticketing.infrastructure.InMemoryMemberRepository;

@DisplayName("PolicyService")
class PolicyServiceTest {

    private static final String COMPANY_NAME = "Acme Productions";
    private static final String VALID_TOKEN = "valid-token";

    private InMemoryEventRepository eventRepository;
    private InMemoryCompanyRepository companyRepository;
    private InMemoryMemberRepository memberRepository;
    private ISessionTokenService sessionTokenService;
    private PolicyService policyService;

    private UUID memberId;
    private Member member;
    private Company company;

    @BeforeEach
    void setUp() {
        eventRepository = new InMemoryEventRepository();
        companyRepository = new InMemoryCompanyRepository();
        memberRepository = new InMemoryMemberRepository();
        sessionTokenService = mock(ISessionTokenService.class);

        policyService = new PolicyService(
                eventRepository, companyRepository, memberRepository, sessionTokenService);

        memberId = UUID.randomUUID();
        member = new Member(memberId, "policyAdmin", "admin@example.com", "encryptedPw");
        memberRepository.save(member);

        company = new Company(COMPANY_NAME, "desc", memberId);
        companyRepository.save(company);

        when(sessionTokenService.isValid(VALID_TOKEN)).thenReturn(true);
        when(sessionTokenService.extractMemberId(VALID_TOKEN)).thenReturn(memberId);
    }

    // ── helpers ──────────────────────────────────────────────────────

    private void appointAs(StaffAppointment.StaffRole role, Set<ManagerPermission> perms) {
        StaffAppointment appt = new StaffAppointment(COMPANY_NAME, memberId, role, perms);
        member.addStaffAppointment(COMPANY_NAME, appt);
        memberRepository.save(member);
    }

    private UUID createDraftEvent() {
        Instant start = Instant.now().plus(30, ChronoUnit.DAYS);
        Event event = new Event(UUID.randomUUID(), COMPANY_NAME, "Concert", "desc",
                EventCategory.CONCERT,
                new EventSchedule(start, start.plus(2, ChronoUnit.HOURS),
                        start.minus(1, ChronoUnit.HOURS)),
                new LockTimerDuration(Duration.ofMinutes(15)));
        eventRepository.save(event);
        return event.getId();
    }

    private Company stubCompany(String name, CompanyStatus status) {
        Company c = mock(Company.class);
        when(c.getName()).thenReturn(name);
        when(c.isActive()).thenReturn(status == CompanyStatus.ACTIVE);
        when(c.getStatus()).thenReturn(status);
        return c;
    }

    /** Custom purchase policy for testing — rejects everything. */
    private static IPurchasePolicy rejectAllPolicy() {
        return (order, mid) -> PolicyResult.failure("REJECTED", "test rejection");
    }

    /** Custom discount policy for testing — 50 % off. */
    private static IDiscountPolicy halfPricePolicy() {
        return (order, couponCode, clock) -> {
            BigDecimal total = order.getTotalPrice();
            return total.divide(BigDecimal.valueOf(2), 2, java.math.RoundingMode.HALF_UP);
        };
    }

    // ══════════════════════════════════════════════════════════════════
    //  Event-scope tests
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Event purchase policy")
    class EventPurchasePolicy {

        @Test
        void GivenOwner_WhenSetEventPurchasePolicy_ThenPolicyUpdated() {
            appointAs(StaffAppointment.StaffRole.OWNER, Set.of());
            UUID eventId = createDraftEvent();

            IPurchasePolicy custom = rejectAllPolicy();
            policyService.setEventPurchasePolicy(VALID_TOKEN, eventId, custom);

            Event saved = eventRepository.findById(eventId).orElseThrow();
            PolicyResult result = saved.getEventPurchasePolicy().isAllowed(null, null);
            assertEquals("REJECTED", result.errorCode());
        }

        @Test
        void GivenManagerWithPolicyMod_WhenSetEventPurchasePolicy_ThenSuccess() {
            appointAs(StaffAppointment.StaffRole.MANAGER,
                    Set.of(ManagerPermission.POLICY_MODIFICATION));
            UUID eventId = createDraftEvent();

            policyService.setEventPurchasePolicy(VALID_TOKEN, eventId, rejectAllPolicy());

            Event saved = eventRepository.findById(eventId).orElseThrow();
            assertNotNull(saved.getEventPurchasePolicy());
        }

        @Test
        void GivenManagerWithoutPolicyMod_WhenSetEventPurchasePolicy_ThenDenied() {
            appointAs(StaffAppointment.StaffRole.MANAGER,
                    Set.of(ManagerPermission.INVENTORY_MGMT));
            UUID eventId = createDraftEvent();

            assertThrows(SecurityException.class,
                    () -> policyService.setEventPurchasePolicy(VALID_TOKEN, eventId, rejectAllPolicy()));
        }

        @Test
        void GivenOwner_WhenRemoveEventPurchasePolicy_ThenResetToDefault() {
            appointAs(StaffAppointment.StaffRole.OWNER, Set.of());
            UUID eventId = createDraftEvent();

            // first set a custom policy
            policyService.setEventPurchasePolicy(VALID_TOKEN, eventId, rejectAllPolicy());
            // now remove it
            policyService.removeEventPurchasePolicy(VALID_TOKEN, eventId);

            Event saved = eventRepository.findById(eventId).orElseThrow();
            assertTrue(saved.getEventPurchasePolicy() instanceof AlwaysAllowPolicy);
        }

        @Test
        void GivenCompositePolicy_WhenSetEventPurchasePolicy_ThenCompositeStored() {
            appointAs(StaffAppointment.StaffRole.OWNER, Set.of());
            UUID eventId = createDraftEvent();

            IPurchasePolicy composite = new AndPolicy(List.of(
                    new AlwaysAllowPolicy(), new OrPolicy(List.of(rejectAllPolicy()))));
            policyService.setEventPurchasePolicy(VALID_TOKEN, eventId, composite);

            Event saved = eventRepository.findById(eventId).orElseThrow();
            assertTrue(saved.getEventPurchasePolicy() instanceof AndPolicy);
        }
    }

    @Nested
    @DisplayName("Event discount policy")
    class EventDiscountPolicy {

        @Test
        void GivenOwner_WhenSetEventDiscountPolicy_ThenPolicyUpdated() {
            appointAs(StaffAppointment.StaffRole.OWNER, Set.of());
            UUID eventId = createDraftEvent();

            policyService.setEventDiscountPolicy(VALID_TOKEN, eventId, halfPricePolicy());

            Event saved = eventRepository.findById(eventId).orElseThrow();
            // verify it's no longer the default NoDiscountPolicy
            assertTrue(!(saved.getEventDiscountPolicy() instanceof NoDiscountPolicy));
        }

        @Test
        void GivenOwner_WhenRemoveEventDiscountPolicy_ThenResetToDefault() {
            appointAs(StaffAppointment.StaffRole.OWNER, Set.of());
            UUID eventId = createDraftEvent();

            policyService.setEventDiscountPolicy(VALID_TOKEN, eventId, halfPricePolicy());
            policyService.removeEventDiscountPolicy(VALID_TOKEN, eventId);

            Event saved = eventRepository.findById(eventId).orElseThrow();
            assertTrue(saved.getEventDiscountPolicy() instanceof NoDiscountPolicy);
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  Company-scope tests
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Company purchase policy")
    class CompanyPurchasePolicyTests {

        @Test
        void GivenOwner_WhenSetCompanyPurchasePolicy_ThenPolicyUpdated() {
            appointAs(StaffAppointment.StaffRole.OWNER, Set.of());

            policyService.setCompanyPurchasePolicy(VALID_TOKEN, COMPANY_NAME, rejectAllPolicy());

            Company saved = companyRepository.findByName(COMPANY_NAME).orElseThrow();
            PolicyResult result = saved.getPurchasePolicy().isAllowed(null, null);
            assertEquals("REJECTED", result.errorCode());
        }

        @Test
        void GivenOwner_WhenRemoveCompanyPurchasePolicy_ThenResetToDefault() {
            appointAs(StaffAppointment.StaffRole.OWNER, Set.of());

            policyService.setCompanyPurchasePolicy(VALID_TOKEN, COMPANY_NAME, rejectAllPolicy());
            policyService.removeCompanyPurchasePolicy(VALID_TOKEN, COMPANY_NAME);

            Company saved = companyRepository.findByName(COMPANY_NAME).orElseThrow();
            assertTrue(saved.getPurchasePolicy() instanceof AlwaysAllowPolicy);
        }
    }

    @Nested
    @DisplayName("Company discount policy")
    class CompanyDiscountPolicyTests {

        @Test
        void GivenOwner_WhenSetCompanyDiscountPolicy_ThenPolicyUpdated() {
            appointAs(StaffAppointment.StaffRole.OWNER, Set.of());

            policyService.setCompanyDiscountPolicy(VALID_TOKEN, COMPANY_NAME, halfPricePolicy());

            Company saved = companyRepository.findByName(COMPANY_NAME).orElseThrow();
            assertTrue(!(saved.getDiscountPolicy() instanceof NoDiscountPolicy));
        }

        @Test
        void GivenOwner_WhenRemoveCompanyDiscountPolicy_ThenResetToDefault() {
            appointAs(StaffAppointment.StaffRole.OWNER, Set.of());

            policyService.setCompanyDiscountPolicy(VALID_TOKEN, COMPANY_NAME, halfPricePolicy());
            policyService.removeCompanyDiscountPolicy(VALID_TOKEN, COMPANY_NAME);

            Company saved = companyRepository.findByName(COMPANY_NAME).orElseThrow();
            assertTrue(saved.getDiscountPolicy() instanceof NoDiscountPolicy);
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  Authorization & validation edge cases
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Authorization and validation")
    class AuthorizationAndValidation {

        @Test
        void GivenGuestToken_WhenSetPolicy_ThenThrowsSecurityException() {
            String guestToken = "guest-token";
            when(sessionTokenService.isValid(guestToken)).thenReturn(true);
            when(sessionTokenService.extractMemberId(guestToken)).thenReturn(null);

            UUID eventId = createDraftEvent();
            assertThrows(SecurityException.class,
                    () -> policyService.setEventPurchasePolicy(guestToken, eventId, rejectAllPolicy()));
        }

        @Test
        void GivenInvalidToken_WhenSetPolicy_ThenThrowsIllegalArgument() {
            String badToken = "bad-token";
            when(sessionTokenService.isValid(badToken)).thenReturn(false);

            UUID eventId = createDraftEvent();
            assertThrows(IllegalArgumentException.class,
                    () -> policyService.setEventPurchasePolicy(badToken, eventId, rejectAllPolicy()));
        }

        @Test
        void GivenStaffOfOtherCompany_WhenSetPolicy_ThenThrowsSecurityException() {
            // member is staff of "Other Co", not "Acme Productions"
            StaffAppointment otherAppt = new StaffAppointment(
                    "Other Co", memberId, StaffAppointment.StaffRole.OWNER, Set.of());
            member.addStaffAppointment("other co", otherAppt);
            memberRepository.save(member);

            UUID eventId = createDraftEvent();
            assertThrows(SecurityException.class,
                    () -> policyService.setEventPurchasePolicy(VALID_TOKEN, eventId, rejectAllPolicy()));
        }

        @Test
        void GivenSuspendedCompany_WhenSetEventPolicy_ThenThrowsIllegalState() {
            appointAs(StaffAppointment.StaffRole.OWNER, Set.of());
            UUID eventId = createDraftEvent();

            // suspend the company
            Company c = companyRepository.findByName(COMPANY_NAME).orElseThrow();
            c.suspend();
            companyRepository.save(c);

            assertThrows(IllegalStateException.class,
                    () -> policyService.setEventPurchasePolicy(VALID_TOKEN, eventId, rejectAllPolicy()));
        }

        @Test
        void GivenSuspendedCompany_WhenSetCompanyPolicy_ThenThrowsIllegalState() {
            appointAs(StaffAppointment.StaffRole.OWNER, Set.of());

            Company c = companyRepository.findByName(COMPANY_NAME).orElseThrow();
            c.suspend();
            companyRepository.save(c);

            assertThrows(IllegalStateException.class,
                    () -> policyService.setCompanyPurchasePolicy(VALID_TOKEN, COMPANY_NAME, rejectAllPolicy()));
        }

        @Test
        void GivenCancelledEvent_WhenSetPurchasePolicy_ThenThrowsIllegalState() {
            appointAs(StaffAppointment.StaffRole.OWNER, Set.of());
            UUID eventId = createDraftEvent();

            Event event = eventRepository.findById(eventId).orElseThrow();
            event.cancel();
            eventRepository.save(event);

            assertThrows(IllegalStateException.class,
                    () -> policyService.setEventPurchasePolicy(VALID_TOKEN, eventId, rejectAllPolicy()));
        }

        @Test
        void GivenNullPolicy_WhenSetEventPurchasePolicy_ThenThrowsIllegalArgument() {
            appointAs(StaffAppointment.StaffRole.OWNER, Set.of());
            UUID eventId = createDraftEvent();

            assertThrows(IllegalArgumentException.class,
                    () -> policyService.setEventPurchasePolicy(VALID_TOKEN, eventId, null));
        }

        @Test
        void GivenNonexistentEvent_WhenSetPolicy_ThenThrowsIllegalArgument() {
            appointAs(StaffAppointment.StaffRole.OWNER, Set.of());

            assertThrows(IllegalArgumentException.class,
                    () -> policyService.setEventPurchasePolicy(VALID_TOKEN, UUID.randomUUID(), rejectAllPolicy()));
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  Read/query tests
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Policy retrieval")
    class PolicyRetrieval {

        @Test
        void GivenEventWithDefaultPolicy_WhenGetPurchasePolicy_ThenReturnsDefault() {
            UUID eventId = createDraftEvent();

            IPurchasePolicy policy = policyService.getEventPurchasePolicy(VALID_TOKEN, eventId);

            assertTrue(policy instanceof AlwaysAllowPolicy);
        }

        @Test
        void GivenCompanyWithDefaultPolicy_WhenGetPurchasePolicy_ThenReturnsDefault() {
            IPurchasePolicy policy = policyService.getCompanyPurchasePolicy(VALID_TOKEN, COMPANY_NAME);

            assertTrue(policy instanceof AlwaysAllowPolicy);
        }

        @Test
        void GivenEventWithCustomPolicy_WhenGetPurchasePolicy_ThenReturnsCustom() {
            appointAs(StaffAppointment.StaffRole.OWNER, Set.of());
            UUID eventId = createDraftEvent();

            policyService.setEventPurchasePolicy(VALID_TOKEN, eventId, rejectAllPolicy());

            IPurchasePolicy policy = policyService.getEventPurchasePolicy(VALID_TOKEN, eventId);
            PolicyResult result = policy.isAllowed(null, null);
            assertEquals("REJECTED", result.errorCode());
        }
    }
}
