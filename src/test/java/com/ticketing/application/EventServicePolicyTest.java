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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.ticketing.application.auth.ISessionTokenService;
import com.ticketing.application.services.EventService;
import com.ticketing.domain.company.Company;
import com.ticketing.domain.event.AgeRestrictionPolicy;
import com.ticketing.domain.event.AlwaysAllowPolicy;
import com.ticketing.domain.event.AndPolicy;
import com.ticketing.domain.event.Event;
import com.ticketing.domain.event.EventCategory;
import com.ticketing.domain.event.EventSchedule;
import com.ticketing.domain.event.IPurchasePolicy;
import com.ticketing.domain.event.LockTimerDuration;
import com.ticketing.domain.event.MaxCompositeDiscount;
import com.ticketing.domain.event.MaxQuantityPolicy;
import com.ticketing.domain.event.NoDiscountPolicy;
import com.ticketing.domain.event.OrPolicy;
import com.ticketing.domain.event.PolicyResult;
import com.ticketing.domain.event.PurchaseContext;
import com.ticketing.domain.event.SimpleDiscount;
import com.ticketing.domain.event.SumCompositeDiscount;
import com.ticketing.domain.member.ManagerPermission;
import com.ticketing.domain.member.Member;
import com.ticketing.domain.member.StaffAppointment;
import com.ticketing.domain.order.IOrderRepository;
import com.ticketing.infrastructure.InMemoryCompanyRepository;
import com.ticketing.infrastructure.InMemoryEventRepository;
import com.ticketing.infrastructure.InMemoryMemberRepository;
import com.ticketing.testsupport.RejectAllPurchasePolicy;

@DisplayName("EventService — Policy management")
class EventServicePolicyTest {

    private static final String COMPANY_NAME = "Acme Productions";
    private static final String VALID_TOKEN = "valid-token";

    private InMemoryEventRepository eventRepository;
    private InMemoryCompanyRepository companyRepository;
    private InMemoryMemberRepository memberRepository;
    private ISessionTokenService sessionTokenService;
    private EventService eventService;

    private UUID memberId;
    private Member member;
    private Company company;

    @BeforeEach
    void setUp() {
        eventRepository = new InMemoryEventRepository();
        companyRepository = new InMemoryCompanyRepository();
        memberRepository = new InMemoryMemberRepository();
        sessionTokenService = mock(ISessionTokenService.class);

        eventService = new EventService(
                eventRepository, companyRepository, memberRepository,
                mock(IOrderRepository.class), sessionTokenService);

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

    private static RejectAllPurchasePolicy rejectAllPolicy() {
        return new RejectAllPurchasePolicy("REJECTED", "test rejection");
    }

    private static SimpleDiscount halfPricePolicy() {
        return new SimpleDiscount(new BigDecimal("50"));
    }

    // ══════════════════════════════════════════════════════════════════
    //  Event purchase policy
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Event purchase policy")
    class EventPurchasePolicy {

        @Test
        void GivenOwner_WhenSetEventPurchasePolicy_ThenPolicyUpdated() {
            appointAs(StaffAppointment.StaffRole.OWNER, Set.of());
            UUID eventId = createDraftEvent();

            IPurchasePolicy custom = rejectAllPolicy();
            eventService.setEventPurchasePolicy(VALID_TOKEN, eventId, custom);

            Event saved = eventRepository.findById(eventId).orElseThrow();
            PolicyResult result = saved.getEventPurchasePolicy().isAllowed(new PurchaseContext(null, null, null));
            assertEquals("REJECTED", result.errorCode());
        }

        @Test
        void GivenManagerWithPolicyMod_WhenSetEventPurchasePolicy_ThenSuccess() {
            appointAs(StaffAppointment.StaffRole.MANAGER,
                    Set.of(ManagerPermission.POLICY_MODIFICATION));
            UUID eventId = createDraftEvent();

            eventService.setEventPurchasePolicy(VALID_TOKEN, eventId, rejectAllPolicy());

            Event saved = eventRepository.findById(eventId).orElseThrow();
            assertNotNull(saved.getEventPurchasePolicy());
        }

        @Test
        void GivenManagerWithoutPolicyMod_WhenSetEventPurchasePolicy_ThenDenied() {
            appointAs(StaffAppointment.StaffRole.MANAGER,
                    Set.of(ManagerPermission.INVENTORY_MGMT));
            UUID eventId = createDraftEvent();

            assertThrows(SecurityException.class,
                    () -> eventService.setEventPurchasePolicy(VALID_TOKEN, eventId, rejectAllPolicy()));
        }

        @Test
        void GivenOwner_WhenRemoveEventPurchasePolicy_ThenResetToDefault() {
            appointAs(StaffAppointment.StaffRole.OWNER, Set.of());
            UUID eventId = createDraftEvent();

            eventService.setEventPurchasePolicy(VALID_TOKEN, eventId, rejectAllPolicy());
            eventService.removeEventPurchasePolicy(VALID_TOKEN, eventId);

            Event saved = eventRepository.findById(eventId).orElseThrow();
            assertTrue(saved.getEventPurchasePolicy() instanceof AlwaysAllowPolicy);
        }

        @Test
        void GivenCompositePolicy_WhenSetEventPurchasePolicy_ThenCompositeStored() {
            appointAs(StaffAppointment.StaffRole.OWNER, Set.of());
            UUID eventId = createDraftEvent();

            IPurchasePolicy composite = new AndPolicy(List.of(
                    new AlwaysAllowPolicy(), new OrPolicy(List.of(rejectAllPolicy()))));
            eventService.setEventPurchasePolicy(VALID_TOKEN, eventId, composite);

            Event saved = eventRepository.findById(eventId).orElseThrow();
            assertTrue(saved.getEventPurchasePolicy() instanceof AndPolicy);
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  Event discount policy
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Event discount policy")
    class EventDiscountPolicy {

        @Test
        void GivenOwner_WhenSetEventDiscountPolicy_ThenPolicyUpdated() {
            appointAs(StaffAppointment.StaffRole.OWNER, Set.of());
            UUID eventId = createDraftEvent();

            eventService.setEventDiscountPolicy(VALID_TOKEN, eventId, halfPricePolicy());

            Event saved = eventRepository.findById(eventId).orElseThrow();
            assertTrue(!(saved.getEventDiscountPolicy() instanceof NoDiscountPolicy));
        }

        @Test
        void GivenOwner_WhenRemoveEventDiscountPolicy_ThenResetToDefault() {
            appointAs(StaffAppointment.StaffRole.OWNER, Set.of());
            UUID eventId = createDraftEvent();

            eventService.setEventDiscountPolicy(VALID_TOKEN, eventId, halfPricePolicy());
            eventService.removeEventDiscountPolicy(VALID_TOKEN, eventId);

            Event saved = eventRepository.findById(eventId).orElseThrow();
            assertTrue(saved.getEventDiscountPolicy() instanceof NoDiscountPolicy);
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  Authorization & validation
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
                    () -> eventService.setEventPurchasePolicy(guestToken, eventId, rejectAllPolicy()));
        }

        @Test
        void GivenInvalidToken_WhenSetPolicy_ThenThrowsIllegalArgument() {
            String badToken = "bad-token";
            when(sessionTokenService.isValid(badToken)).thenReturn(false);

            UUID eventId = createDraftEvent();
            assertThrows(IllegalArgumentException.class,
                    () -> eventService.setEventPurchasePolicy(badToken, eventId, rejectAllPolicy()));
        }

        @Test
        void GivenStaffOfOtherCompany_WhenSetPolicy_ThenThrowsSecurityException() {
            StaffAppointment otherAppt = new StaffAppointment(
                    "Other Co", memberId, StaffAppointment.StaffRole.OWNER, Set.of());
            member.addStaffAppointment("other co", otherAppt);
            memberRepository.save(member);

            UUID eventId = createDraftEvent();
            assertThrows(SecurityException.class,
                    () -> eventService.setEventPurchasePolicy(VALID_TOKEN, eventId, rejectAllPolicy()));
        }

        @Test
        void GivenSuspendedCompany_WhenSetEventPolicy_ThenThrowsIllegalState() {
            appointAs(StaffAppointment.StaffRole.OWNER, Set.of());
            UUID eventId = createDraftEvent();

            Company c = companyRepository.findByName(COMPANY_NAME).orElseThrow();
            c.suspend();
            companyRepository.save(c);

            assertThrows(IllegalStateException.class,
                    () -> eventService.setEventPurchasePolicy(VALID_TOKEN, eventId, rejectAllPolicy()));
        }

        @Test
        void GivenCancelledEvent_WhenSetPurchasePolicy_ThenThrowsIllegalState() {
            appointAs(StaffAppointment.StaffRole.OWNER, Set.of());
            UUID eventId = createDraftEvent();

            Event event = eventRepository.findById(eventId).orElseThrow();
            event.cancel();
            eventRepository.save(event);

            assertThrows(IllegalStateException.class,
                    () -> eventService.setEventPurchasePolicy(VALID_TOKEN, eventId, rejectAllPolicy()));
        }

        @Test
        void GivenNullPolicy_WhenSetEventPurchasePolicy_ThenThrowsIllegalArgument() {
            appointAs(StaffAppointment.StaffRole.OWNER, Set.of());
            UUID eventId = createDraftEvent();

            assertThrows(IllegalArgumentException.class,
                    () -> eventService.setEventPurchasePolicy(VALID_TOKEN, eventId, null));
        }

        @Test
        void GivenNonexistentEvent_WhenSetPolicy_ThenThrowsIllegalArgument() {
            appointAs(StaffAppointment.StaffRole.OWNER, Set.of());

            assertThrows(IllegalArgumentException.class,
                    () -> eventService.setEventPurchasePolicy(VALID_TOKEN, UUID.randomUUID(), rejectAllPolicy()));
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  Policy retrieval
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Policy retrieval")
    class PolicyRetrieval {

        @Test
        void GivenEventWithDefaultPolicy_WhenGetPurchasePolicy_ThenReturnsDefault() {
            UUID eventId = createDraftEvent();

            IPurchasePolicy policy = eventService.getEventPurchasePolicy(VALID_TOKEN, eventId);

            assertTrue(policy instanceof AlwaysAllowPolicy);
        }

        @Test
        void GivenEventWithCustomPolicy_WhenGetPurchasePolicy_ThenReturnsCustom() {
            appointAs(StaffAppointment.StaffRole.OWNER, Set.of());
            UUID eventId = createDraftEvent();

            eventService.setEventPurchasePolicy(VALID_TOKEN, eventId, rejectAllPolicy());

            IPurchasePolicy policy = eventService.getEventPurchasePolicy(VALID_TOKEN, eventId);
            PolicyResult result = policy.isAllowed(new PurchaseContext(null, null, null));
            assertEquals("REJECTED", result.errorCode());
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  Company default policy inheritance
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Company default policy inheritance")
    class CompanyDefaultPolicyInheritance {

        @Test
        void GivenCompanyWithAgePolicy_WhenCreateEvent_ThenEventInheritsCompanyPurchasePolicy() {
            appointAs(StaffAppointment.StaffRole.OWNER, Set.of());
            Company c = companyRepository.findByName(COMPANY_NAME).orElseThrow();
            c.setPurchasePolicy(new AgeRestrictionPolicy(18));
            companyRepository.save(c);

            UUID eventId = eventService.createEvent(VALID_TOKEN, validCreateRequest());

            Event saved = eventRepository.findById(eventId).orElseThrow();
            assertTrue(saved.getEventPurchasePolicy() instanceof AgeRestrictionPolicy);
            assertEquals(18, ((AgeRestrictionPolicy) saved.getEventPurchasePolicy()).getMinimumAge());
        }

        @Test
        void GivenCompanyWithDiscountPolicy_WhenCreateEvent_ThenEventInheritsCompanyDiscountPolicy() {
            appointAs(StaffAppointment.StaffRole.OWNER, Set.of());
            Company c = companyRepository.findByName(COMPANY_NAME).orElseThrow();
            c.setDiscountPolicy(halfPricePolicy());
            companyRepository.save(c);

            UUID eventId = eventService.createEvent(VALID_TOKEN, validCreateRequest());

            Event saved = eventRepository.findById(eventId).orElseThrow();
            assertNotNull(saved.getEventDiscountPolicy());
            assertTrue(!(saved.getEventDiscountPolicy() instanceof NoDiscountPolicy));
        }

        @Test
        void GivenCompanyWithCustomPolicy_WhenRemoveEventPurchasePolicy_ThenResetsToCompanyDefault() {
            appointAs(StaffAppointment.StaffRole.OWNER, Set.of());
            Company c = companyRepository.findByName(COMPANY_NAME).orElseThrow();
            c.setPurchasePolicy(new AgeRestrictionPolicy(21));
            companyRepository.save(c);

            UUID eventId = createDraftEvent();

            eventService.setEventPurchasePolicy(VALID_TOKEN, eventId, rejectAllPolicy());
            eventService.removeEventPurchasePolicy(VALID_TOKEN, eventId);

            Event saved = eventRepository.findById(eventId).orElseThrow();
            assertTrue(saved.getEventPurchasePolicy() instanceof AgeRestrictionPolicy);
            assertEquals(21, ((AgeRestrictionPolicy) saved.getEventPurchasePolicy()).getMinimumAge());
        }

        @Test
        void GivenCompanyWithCustomDiscount_WhenRemoveEventDiscountPolicy_ThenResetsToCompanyDefault() {
            appointAs(StaffAppointment.StaffRole.OWNER, Set.of());
            Company c = companyRepository.findByName(COMPANY_NAME).orElseThrow();
            c.setDiscountPolicy(halfPricePolicy());
            companyRepository.save(c);

            UUID eventId = createDraftEvent();

            eventService.setEventDiscountPolicy(VALID_TOKEN, eventId, halfPricePolicy());
            eventService.removeEventDiscountPolicy(VALID_TOKEN, eventId);

            Event saved = eventRepository.findById(eventId).orElseThrow();
            assertTrue(!(saved.getEventDiscountPolicy() instanceof NoDiscountPolicy));
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  Add (compose) policy
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Add (compose) policy")
    class AddComposePolicy {

        @Test
        void GivenOwner_WhenAddPurchasePolicyWithAnd_ThenComposedAsAndPolicy() {
            appointAs(StaffAppointment.StaffRole.OWNER, Set.of());
            UUID eventId = createDraftEvent();

            eventService.addEventPurchasePolicy(VALID_TOKEN, eventId, new MaxQuantityPolicy(4), false);

            Event saved = eventRepository.findById(eventId).orElseThrow();
            assertTrue(saved.getEventPurchasePolicy() instanceof AndPolicy);
        }

        @Test
        void GivenOwner_WhenAddPurchasePolicyWithOr_ThenComposedAsOrPolicy() {
            appointAs(StaffAppointment.StaffRole.OWNER, Set.of());
            UUID eventId = createDraftEvent();

            eventService.addEventPurchasePolicy(VALID_TOKEN, eventId, rejectAllPolicy(), true);

            Event saved = eventRepository.findById(eventId).orElseThrow();
            assertTrue(saved.getEventPurchasePolicy() instanceof OrPolicy);
        }

        @Test
        void GivenOwner_WhenAddDiscountPolicyWithStacking_ThenComposedAsSumComposite() {
            appointAs(StaffAppointment.StaffRole.OWNER, Set.of());
            UUID eventId = createDraftEvent();

            eventService.addEventDiscountPolicy(VALID_TOKEN, eventId, halfPricePolicy(), true);

            Event saved = eventRepository.findById(eventId).orElseThrow();
            assertTrue(saved.getEventDiscountPolicy() instanceof SumCompositeDiscount);
        }

        @Test
        void GivenOwner_WhenAddDiscountPolicyWithoutStacking_ThenComposedAsMaxComposite() {
            appointAs(StaffAppointment.StaffRole.OWNER, Set.of());
            UUID eventId = createDraftEvent();

            eventService.addEventDiscountPolicy(VALID_TOKEN, eventId, halfPricePolicy(), false);

            Event saved = eventRepository.findById(eventId).orElseThrow();
            assertTrue(saved.getEventDiscountPolicy() instanceof MaxCompositeDiscount);
        }

        @Test
        void GivenManagerWithoutPolicyMod_WhenAddPurchasePolicy_ThenDenied() {
            appointAs(StaffAppointment.StaffRole.MANAGER,
                    Set.of(ManagerPermission.INVENTORY_MGMT));
            UUID eventId = createDraftEvent();

            assertThrows(SecurityException.class,
                    () -> eventService.addEventPurchasePolicy(VALID_TOKEN, eventId, new MaxQuantityPolicy(4), false));
        }

        @Test
        void GivenNullPolicy_WhenAddPurchasePolicy_ThenThrowsIllegalArgument() {
            appointAs(StaffAppointment.StaffRole.OWNER, Set.of());
            UUID eventId = createDraftEvent();

            assertThrows(IllegalArgumentException.class,
                    () -> eventService.addEventPurchasePolicy(VALID_TOKEN, eventId, null, false));
        }
    }

    // ── helpers for service-level event creation ────────────────────

    private CreateEventRequest validCreateRequest() {
        Instant start = Instant.now().plus(30, java.time.temporal.ChronoUnit.DAYS);
        Instant end = start.plus(3, java.time.temporal.ChronoUnit.HOURS);
        Instant doors = start.minus(1, java.time.temporal.ChronoUnit.HOURS);

        List<CreateEventRequest.ZoneSpec> zones = List.of(
                new CreateEventRequest.GAZoneSpec("Floor", new BigDecimal("50.00"), 500));

        Map<String, String> sectionMap = new LinkedHashMap<>();
        sectionMap.put("Section A", "Floor");

        return new CreateEventRequest(COMPANY_NAME, "Test Concert", "desc",
                EventCategory.CONCERT,
                new EventSchedule(start, end, doors),
                new LockTimerDuration(Duration.ofMinutes(15)),
                zones, sectionMap);
    }
}
