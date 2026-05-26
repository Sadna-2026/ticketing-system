package com.ticketing.domain.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.ticketing.domain.order.ActiveOrder;
import com.ticketing.domain.order.OrderItem;

@DisplayName("Event policy")
class EventPolicyTest {

    @Nested
    @DisplayName("Defaults")
    class Defaults {

        @Test
        public void GivenNewEvent_WhenInspectPolicies_ThenDefaultsAreWired() {
            Event e = newEvent();

            assertNotNull(e.getPurchasePolicy(), "every Event must have a purchase policy by default");
            assertNotNull(e.getDiscountPolicy(), "every Event must have a discount policy by default");
            assertTrue(e.getPurchasePolicy() instanceof AlwaysAllowPolicy,
                    "default purchase policy must be AlwaysAllowPolicy");
            assertTrue(e.getDiscountPolicy() instanceof NoDiscountPolicy,
                    "default discount policy must be NoDiscountPolicy");
        }

        @Test
        public void GivenDefaultCurrencyConstant_WhenInspect_ThenIsExposedAtClassLevel() {
            assertNotNull(Event.DEFAULT_CURRENCY, "Event.DEFAULT_CURRENCY must be defined");
        }

        private static Event newEvent() {
            UUID id = UUID.randomUUID();
            Instant start = Instant.now().plus(30, ChronoUnit.DAYS);
            return new Event(id, "Acme Productions", "Concert", "desc", EventCategory.CONCERT,
                    new EventSchedule(start, start.plus(2, ChronoUnit.HOURS), start.minus(1, ChronoUnit.HOURS)),
                    new LockTimerDuration(Duration.ofMinutes(15)));
        }

    }

    @Nested
    @DisplayName("Framework")
    class Framework {

        @Test
        void GivenAlwaysAllowPolicy_WhenEvaluate_ThenAllowed() {
            IPurchasePolicy policy = new AlwaysAllowPolicy();
            PurchaseContext ctx = new PurchaseContext(null, UUID.randomUUID(), null);

            PolicyResult result = policy.isAllowed(ctx);

            assertTrue(result.allowed());
            assertNull(result.errorCode());
        }

        @Test
        void GivenAndPolicy_WhenEvaluateCombinations_ThenExpectedResults() {
            IPurchasePolicy truePolicy = mock(IPurchasePolicy.class);
            when(truePolicy.isAllowed(any())).thenReturn(PolicyResult.success());

            IPurchasePolicy falsePolicy = mock(IPurchasePolicy.class);
            when(falsePolicy.isAllowed(any())).thenReturn(PolicyResult.failure("TEST_ERROR", "Failed"));

            IPurchasePolicy bothTrue = new AndPolicy(List.of(truePolicy, truePolicy));
            assertTrue(bothTrue.isAllowed(dummyCtx()).allowed());

            IPurchasePolicy oneFalse = new AndPolicy(List.of(truePolicy, falsePolicy));
            PolicyResult result = oneFalse.isAllowed(dummyCtx());
            assertFalse(result.allowed());
            assertEquals("TEST_ERROR", result.errorCode());
        }

        @Test
        void GivenOrPolicy_WhenEvaluateCombinations_ThenExpectedResults() {
            IPurchasePolicy truePolicy = mock(IPurchasePolicy.class);
            when(truePolicy.isAllowed(any())).thenReturn(PolicyResult.success());

            IPurchasePolicy falsePolicy = mock(IPurchasePolicy.class);
            when(falsePolicy.isAllowed(any())).thenReturn(PolicyResult.failure("TEST_ERROR", "Failed"));

            IPurchasePolicy oneTrue = new OrPolicy(List.of(falsePolicy, truePolicy));
            assertTrue(oneTrue.isAllowed(dummyCtx()).allowed());

            IPurchasePolicy bothFalse = new OrPolicy(List.of(falsePolicy, falsePolicy));
            PolicyResult result = bothFalse.isAllowed(dummyCtx());
            assertFalse(result.allowed());
            assertEquals("ALL_OR_CONDITIONS_FAILED", result.errorCode());
        }
    }

    @Nested
    @DisplayName("AgeRestrictionPolicy")
    class AgeRestriction {

        @Test
        void GivenBuyerOverMinAge_WhenEvaluate_ThenAllowed() {
            AgeRestrictionPolicy policy = new AgeRestrictionPolicy(18);
            LocalDate twentyYearsAgo = LocalDate.now().minusYears(20);
            PurchaseContext ctx = new PurchaseContext(null, UUID.randomUUID(), twentyYearsAgo);

            assertTrue(policy.isAllowed(ctx).allowed());
        }

        @Test
        void GivenBuyerExactlyMinAge_WhenEvaluate_ThenAllowed() {
            AgeRestrictionPolicy policy = new AgeRestrictionPolicy(18);
            LocalDate exactlyEighteen = LocalDate.now().minusYears(18);
            PurchaseContext ctx = new PurchaseContext(null, UUID.randomUUID(), exactlyEighteen);

            assertTrue(policy.isAllowed(ctx).allowed());
        }

        @Test
        void GivenBuyerUnderMinAge_WhenEvaluate_ThenRejectedWithMessage() {
            AgeRestrictionPolicy policy = new AgeRestrictionPolicy(18);
            LocalDate sixteenYearsAgo = LocalDate.now().minusYears(16);
            PurchaseContext ctx = new PurchaseContext(null, UUID.randomUUID(), sixteenYearsAgo);

            PolicyResult result = policy.isAllowed(ctx);
            assertFalse(result.allowed());
            assertEquals("AGE_RESTRICTED", result.errorCode());
            assertTrue(result.reason().contains("18"));
        }

        @Test
        void GivenNullDateOfBirth_WhenEvaluate_ThenRejected() {
            AgeRestrictionPolicy policy = new AgeRestrictionPolicy(18);
            PurchaseContext ctx = new PurchaseContext(null, UUID.randomUUID(), null);

            PolicyResult result = policy.isAllowed(ctx);
            assertFalse(result.allowed());
            assertEquals("AGE_UNKNOWN", result.errorCode());
        }
    }

    @Nested
    @DisplayName("QuantityPolicies")
    class QuantityPolicies {

        @Test
        void GivenTicketCountBelowMax_WhenEvaluateMaxPolicy_ThenAllowed() {
            MaxQuantityPolicy policy = new MaxQuantityPolicy(5);
            PurchaseContext ctx = ctxWithTickets(3);

            assertTrue(policy.isAllowed(ctx).allowed());
        }

        @Test
        void GivenTicketCountExactlyMax_WhenEvaluateMaxPolicy_ThenAllowed() {
            MaxQuantityPolicy policy = new MaxQuantityPolicy(5);
            PurchaseContext ctx = ctxWithTickets(5);

            assertTrue(policy.isAllowed(ctx).allowed());
        }

        @Test
        void GivenTicketCountAboveMax_WhenEvaluateMaxPolicy_ThenRejectedWithMessage() {
            MaxQuantityPolicy policy = new MaxQuantityPolicy(5);
            PurchaseContext ctx = ctxWithTickets(6);

            PolicyResult result = policy.isAllowed(ctx);
            assertFalse(result.allowed());
            assertEquals("MAX_QUANTITY_EXCEEDED", result.errorCode());
            assertTrue(result.reason().contains("5"));
        }

        @Test
        void GivenTicketCountAboveMin_WhenEvaluateMinPolicy_ThenAllowed() {
            MinQuantityPolicy policy = new MinQuantityPolicy(2);
            PurchaseContext ctx = ctxWithTickets(3);

            assertTrue(policy.isAllowed(ctx).allowed());
        }

        @Test
        void GivenTicketCountExactlyMin_WhenEvaluateMinPolicy_ThenAllowed() {
            MinQuantityPolicy policy = new MinQuantityPolicy(2);
            PurchaseContext ctx = ctxWithTickets(2);

            assertTrue(policy.isAllowed(ctx).allowed());
        }

        @Test
        void GivenTicketCountBelowMin_WhenEvaluateMinPolicy_ThenRejectedWithMessage() {
            MinQuantityPolicy policy = new MinQuantityPolicy(2);
            PurchaseContext ctx = ctxWithTickets(1);

            PolicyResult result = policy.isAllowed(ctx);
            assertFalse(result.allowed());
            assertEquals("MIN_QUANTITY_NOT_MET", result.errorCode());
            assertTrue(result.reason().contains("2"));
        }
    }

    @Nested
    @DisplayName("Composition")
    class Composition {

        @Test
        void GivenNestedAndOrComposition_WhenEvaluate_ThenArbitraryDepthWorks() {
            // "from age 18 AND (at most 2 OR at least 100 tickets)"
            IPurchasePolicy agePolicy = new AgeRestrictionPolicy(18);
            IPurchasePolicy max2 = new MaxQuantityPolicy(2);
            IPurchasePolicy min100 = new MinQuantityPolicy(100);

            IPurchasePolicy orBranch = new OrPolicy(List.of(max2, min100));
            IPurchasePolicy composed = new AndPolicy(List.of(agePolicy, orBranch));

            LocalDate adultDob = LocalDate.now().minusYears(25);

            // adult buying 1 ticket -> age ok, max2 ok -> allowed
            assertTrue(composed.isAllowed(ctxWithAge(1, adultDob)).allowed());

            // adult buying 2 tickets -> age ok, max2 ok -> allowed
            assertTrue(composed.isAllowed(ctxWithAge(2, adultDob)).allowed());

            // adult buying 50 tickets -> age ok, max2 fails AND min100 fails -> rejected
            PolicyResult result = composed.isAllowed(ctxWithAge(50, adultDob));
            assertFalse(result.allowed());

            // adult buying 100 tickets -> age ok, max2 fails but min100 ok -> allowed
            assertTrue(composed.isAllowed(ctxWithAge(100, adultDob)).allowed());

            // minor buying 1 ticket -> age fails immediately
            LocalDate minorDob = LocalDate.now().minusYears(15);
            PolicyResult minorResult = composed.isAllowed(ctxWithAge(1, minorDob));
            assertFalse(minorResult.allowed());
            assertEquals("AGE_RESTRICTED", minorResult.errorCode());
        }

        @Test
        void GivenNewRuleImplementation_WhenAddToComposite_ThenNoChangesToExistingCode() {
            // demonstrates Open/Closed: a brand-new rule class composes with
            // existing AndPolicy/OrPolicy without modifying them
            IPurchasePolicy customRule = ctx -> {
                if (ctx.memberId() == null) {
                    return PolicyResult.failure("NO_GUEST", "Guests cannot purchase");
                }
                return PolicyResult.success();
            };

            IPurchasePolicy composed = new AndPolicy(List.of(new AlwaysAllowPolicy(), customRule));

            // member -> allowed
            assertTrue(composed.isAllowed(new PurchaseContext(null, UUID.randomUUID(), null)).allowed());

            // guest -> rejected by custom rule
            PolicyResult result = composed.isAllowed(new PurchaseContext(null, null, null));
            assertFalse(result.allowed());
            assertEquals("NO_GUEST", result.errorCode());
        }
    }

    // --- helpers ---

    private static PurchaseContext dummyCtx() {
        return new PurchaseContext(null, null, null);
    }

    private static PurchaseContext ctxWithTickets(int count) {
        ActiveOrder order = new ActiveOrder(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), Instant.now());
        UUID zoneId = UUID.randomUUID();
        for (int i = 0; i < count; i++) {
            order.addItem(OrderItem.forSeat(UUID.randomUUID(), zoneId,
                    UUID.randomUUID(), new BigDecimal("50.00")));
        }
        return new PurchaseContext(order, UUID.randomUUID(), null);
    }

    private static PurchaseContext ctxWithAge(int ticketCount, LocalDate dob) {
        ActiveOrder order = new ActiveOrder(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), Instant.now());
        UUID zoneId = UUID.randomUUID();
        for (int i = 0; i < ticketCount; i++) {
            order.addItem(OrderItem.forSeat(UUID.randomUUID(), zoneId,
                    UUID.randomUUID(), new BigDecimal("50.00")));
        }
        return new PurchaseContext(order, UUID.randomUUID(), dob);
    }
}
