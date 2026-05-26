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
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import com.ticketing.domain.order.ActiveOrder;
import com.ticketing.domain.order.OrderItem;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

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
            // The currency choice for the no-discount default lives on Event as a
            // constant rather than being buried in the constructor. This test pins
            // the constant's existence + visibility so the override path is clear.
            assertNotNull(Event.DEFAULT_CURRENCY, "Event.DEFAULT_CURRENCY must be defined");
        }

        @Test
        public void GivenEvent_WhenInspectClass_ThenNoPolicySetterExists() throws Exception {
            // Pin the V1 invariant: there must be no public mutator for the policies
            // on Event. If someone adds setPurchasePolicy/setDiscountPolicy in V1 by
            // mistake, this test fails — flagging that the deferral was broken.
            for (var m : Event.class.getMethods()) {
                String name = m.getName().toLowerCase();
                assertTrue(!name.equals("setpurchasepolicy"),
                        "Event must not expose setPurchasePolicy in V1");
                assertTrue(!name.equals("setdiscountpolicy"),
                        "Event must not expose setDiscountPolicy in V1");
            }
        }

        // --- V0 acceptance tests for UC-C.2, deferred to V2 per V1 spec ---

        @Test
        @Disabled("V1 spec defers UC-II.4.3 — full policy edit lands in V2")
        public void GivenOwner_WhenEditPolicy_ThenPolicyUpdated() {
            // V0 acceptance: SuccessfulDefaultPolicyEdit
        }

        @Test
        @Disabled("V1 spec defers UC-II.4.3 — full policy edit lands in V2")
        public void GivenManagerWithPolicyMod_WhenEditPolicy_ThenPolicyUpdated() {
            // V0 acceptance: ManagerWithPolicyModificationCanEdit
        }

        @Test
        @Disabled("V1 spec defers UC-II.4.3 — full policy edit lands in V2")
        public void GivenManagerWithoutPolicyMod_WhenEditPolicy_ThenDenied() {
            // V0 acceptance: ManagerWithoutPolicyModificationDenied
        }

        @Test
        @Disabled("V1 spec defers UC-II.4.3 — full policy edit lands in V2")
        public void GivenInvalidPolicyExpression_WhenEditPolicy_ThenRejected() {
            // V0 acceptance: InvalidPolicyExpressionRejected
        }

        // --- helpers ---

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
            UUID memberId = UUID.randomUUID();

            PolicyResult result = policy.isAllowed(null, memberId);

            assertTrue(result.allowed());
            assertNull(result.errorCode());
        }

        @Test
        void GivenAndPolicy_WhenEvaluateCombinations_ThenExpectedResults() {
            IPurchasePolicy truePolicy = mock(IPurchasePolicy.class);
            when(truePolicy.isAllowed(any(), any())).thenReturn(PolicyResult.success());

            IPurchasePolicy falsePolicy = mock(IPurchasePolicy.class);
            when(falsePolicy.isAllowed(any(), any())).thenReturn(PolicyResult.failure("TEST_ERROR", "Failed"));

            IPurchasePolicy bothTrue = new AndPolicy(List.of(truePolicy, truePolicy));
            assertTrue(bothTrue.isAllowed(null, null).allowed());

            IPurchasePolicy oneFalse = new AndPolicy(List.of(truePolicy, falsePolicy));
            PolicyResult result = oneFalse.isAllowed(null, null);
            assertFalse(result.allowed());
            assertEquals("TEST_ERROR", result.errorCode());
        }

        @Test
        void GivenOrPolicy_WhenEvaluateCombinations_ThenExpectedResults() {
            IPurchasePolicy truePolicy = mock(IPurchasePolicy.class);
            when(truePolicy.isAllowed(any(), any())).thenReturn(PolicyResult.success());

            IPurchasePolicy falsePolicy = mock(IPurchasePolicy.class);
            when(falsePolicy.isAllowed(any(), any())).thenReturn(PolicyResult.failure("TEST_ERROR", "Failed"));

            IPurchasePolicy oneTrue = new OrPolicy(List.of(falsePolicy, truePolicy));
            assertTrue(oneTrue.isAllowed(null, null).allowed());

            IPurchasePolicy bothFalse = new OrPolicy(List.of(falsePolicy, falsePolicy));
            PolicyResult result = bothFalse.isAllowed(null, null);
            assertFalse(result.allowed());
            assertEquals("ALL_OR_CONDITIONS_FAILED", result.errorCode());
        }
    }

    // -------- Discount policy tests (V2-UC-2 / #148) --------

    @Nested
    @DisplayName("SimpleDiscount")
    class SimpleDiscountTests {

        @Test
        void GivenSimpleDiscount_WhenApply_ThenPriceReducedByPercentage() {
            SimpleDiscount discount = new SimpleDiscount(new BigDecimal("10"));
            ActiveOrder order = orderWithTotal(new BigDecimal("100.00"));

            BigDecimal result = discount.priceAfterDiscount(order, null, Instant.now());

            assertEquals(new BigDecimal("90.00"), result);
        }

        @Test
        void GivenSimpleDiscount50Percent_WhenApply_ThenHalfPrice() {
            SimpleDiscount discount = new SimpleDiscount(new BigDecimal("50"));
            ActiveOrder order = orderWithTotal(new BigDecimal("200.00"));

            assertEquals(new BigDecimal("100.00"), discount.priceAfterDiscount(order, null, Instant.now()));
        }
    }

    @Nested
    @DisplayName("ConditionalDiscount")
    class ConditionalDiscountTests {

        @Test
        void GivenMinQuantityConditionMet_WhenApply_ThenDiscountApplied() {
            ConditionalDiscount discount = new ConditionalDiscount(
                    new BigDecimal("10"), new MinQuantityCondition(2));
            ActiveOrder order = orderWithItems(3, new BigDecimal("50.00"));

            BigDecimal result = discount.priceAfterDiscount(order, null, Instant.now());

            // 3 * 50 = 150, 10% off = 135
            assertEquals(new BigDecimal("135.00"), result);
        }

        @Test
        void GivenMinQuantityConditionNotMet_WhenApply_ThenOriginalPrice() {
            ConditionalDiscount discount = new ConditionalDiscount(
                    new BigDecimal("10"), new MinQuantityCondition(5));
            ActiveOrder order = orderWithItems(2, new BigDecimal("50.00"));

            BigDecimal result = discount.priceAfterDiscount(order, null, Instant.now());

            assertEquals(new BigDecimal("100.00"), result);
        }

        @Test
        void GivenDateRangeConditionMet_WhenApply_ThenDiscountApplied() {
            Instant now = Instant.now();
            DateRangeCondition inRange = new DateRangeCondition(
                    now.minus(1, ChronoUnit.DAYS), now.plus(1, ChronoUnit.DAYS));
            ConditionalDiscount discount = new ConditionalDiscount(new BigDecimal("15"), inRange);
            ActiveOrder order = orderWithTotal(new BigDecimal("100.00"));

            assertEquals(new BigDecimal("85.00"), discount.priceAfterDiscount(order, null, now));
        }

        @Test
        void GivenDateRangeConditionExpired_WhenApply_ThenOriginalPrice() {
            Instant now = Instant.now();
            DateRangeCondition expired = new DateRangeCondition(
                    now.minus(10, ChronoUnit.DAYS), now.minus(1, ChronoUnit.DAYS));
            ConditionalDiscount discount = new ConditionalDiscount(new BigDecimal("15"), expired);
            ActiveOrder order = orderWithTotal(new BigDecimal("100.00"));

            assertEquals(new BigDecimal("100.00"), discount.priceAfterDiscount(order, null, now));
        }

        @Test
        void GivenNewConditionType_WhenAddToConditional_ThenNoChangesToExistingCode() {
            // demonstrates extensibility: custom condition plugs in without changing anything
            IDiscountCondition weekendOnly = (order, now) -> true; // always met for test
            ConditionalDiscount discount = new ConditionalDiscount(new BigDecimal("5"), weekendOnly);
            ActiveOrder order = orderWithTotal(new BigDecimal("100.00"));

            assertEquals(new BigDecimal("95.00"), discount.priceAfterDiscount(order, null, Instant.now()));
        }
    }

    @Nested
    @DisplayName("CouponDiscount")
    class CouponDiscountTests {

        @Test
        void GivenCorrectCodeBeforeExpiry_WhenApply_ThenDiscountApplied() {
            Instant expiry = Instant.now().plus(30, ChronoUnit.DAYS);
            CouponDiscount discount = new CouponDiscount(new BigDecimal("20"), "SAVE20", expiry);
            ActiveOrder order = orderWithTotal(new BigDecimal("100.00"));

            assertEquals(new BigDecimal("80.00"),
                    discount.priceAfterDiscount(order, "SAVE20", Instant.now()));
        }

        @Test
        void GivenWrongCode_WhenApply_ThenOriginalPrice() {
            Instant expiry = Instant.now().plus(30, ChronoUnit.DAYS);
            CouponDiscount discount = new CouponDiscount(new BigDecimal("20"), "SAVE20", expiry);
            ActiveOrder order = orderWithTotal(new BigDecimal("100.00"));

            assertEquals(new BigDecimal("100.00"),
                    discount.priceAfterDiscount(order, "WRONGCODE", Instant.now()));
        }

        @Test
        void GivenNullCode_WhenApply_ThenOriginalPrice() {
            Instant expiry = Instant.now().plus(30, ChronoUnit.DAYS);
            CouponDiscount discount = new CouponDiscount(new BigDecimal("20"), "SAVE20", expiry);
            ActiveOrder order = orderWithTotal(new BigDecimal("100.00"));

            assertEquals(new BigDecimal("100.00"),
                    discount.priceAfterDiscount(order, null, Instant.now()));
        }

        @Test
        void GivenExpiredCoupon_WhenApply_ThenOriginalPrice() {
            Instant expiry = Instant.now().minus(1, ChronoUnit.DAYS);
            CouponDiscount discount = new CouponDiscount(new BigDecimal("20"), "SAVE20", expiry);
            ActiveOrder order = orderWithTotal(new BigDecimal("100.00"));

            assertEquals(new BigDecimal("100.00"),
                    discount.priceAfterDiscount(order, "SAVE20", Instant.now()));
        }

        @Test
        void GivenCodeCaseInsensitive_WhenApply_ThenDiscountApplied() {
            Instant expiry = Instant.now().plus(30, ChronoUnit.DAYS);
            CouponDiscount discount = new CouponDiscount(new BigDecimal("20"), "SAVE20", expiry);
            ActiveOrder order = orderWithTotal(new BigDecimal("100.00"));

            assertEquals(new BigDecimal("80.00"),
                    discount.priceAfterDiscount(order, "save20", Instant.now()));
        }
    }

    @Nested
    @DisplayName("DiscountComposition")
    class DiscountCompositionTests {

        @Test
        void GivenMaxComposite_WhenMultipleDiscounts_ThenBiggestDiscountWins() {
            IDiscountPolicy tenOff = new SimpleDiscount(new BigDecimal("10"));
            IDiscountPolicy twentyOff = new SimpleDiscount(new BigDecimal("20"));
            MaxCompositeDiscount max = new MaxCompositeDiscount(List.of(tenOff, twentyOff));

            ActiveOrder order = orderWithTotal(new BigDecimal("100.00"));
            // 20% is bigger discount → final price 80
            assertEquals(new BigDecimal("80.00"),
                    max.priceAfterDiscount(order, null, Instant.now()));
        }

        @Test
        void GivenSumComposite_WhenMultipleDiscounts_ThenDiscountsStack() {
            IDiscountPolicy tenOff = new SimpleDiscount(new BigDecimal("10"));
            IDiscountPolicy twentyOff = new SimpleDiscount(new BigDecimal("20"));
            SumCompositeDiscount sum = new SumCompositeDiscount(List.of(tenOff, twentyOff));

            ActiveOrder order = orderWithTotal(new BigDecimal("100.00"));
            // 10% + 20% = 30% off → final price 70
            assertEquals(new BigDecimal("70.00"),
                    sum.priceAfterDiscount(order, null, Instant.now()));
        }

        @Test
        void GivenSumComposite_WhenDiscountsExceed100Percent_ThenClampedToZero() {
            IDiscountPolicy sixtyOff = new SimpleDiscount(new BigDecimal("60"));
            IDiscountPolicy fiftyOff = new SimpleDiscount(new BigDecimal("50"));
            SumCompositeDiscount sum = new SumCompositeDiscount(List.of(sixtyOff, fiftyOff));

            ActiveOrder order = orderWithTotal(new BigDecimal("100.00"));
            // 60% + 50% = 110% off → clamped to 0
            assertEquals(new BigDecimal("0.00"),
                    sum.priceAfterDiscount(order, null, Instant.now()).setScale(2));
        }

        @Test
        void GivenMaxComposite_WhenConditionalNotMet_ThenOnlyMetDiscountApplied() {
            IDiscountPolicy tenOff = new SimpleDiscount(new BigDecimal("10"));
            // 30% off only if 5+ tickets — won't be met
            IDiscountPolicy conditional = new ConditionalDiscount(
                    new BigDecimal("30"), new MinQuantityCondition(5));
            MaxCompositeDiscount max = new MaxCompositeDiscount(List.of(tenOff, conditional));

            ActiveOrder order = orderWithItems(2, new BigDecimal("50.00"));
            // only 10% applies → 100 * 0.9 = 90
            assertEquals(new BigDecimal("90.00"),
                    max.priceAfterDiscount(order, null, Instant.now()));
        }

        @Test
        void GivenNestedComposition_WhenEvaluate_ThenWorksAtArbitraryDepth() {
            // sum(10% simple, max(20% simple, 5% simple))
            // inner max: 20% wins → discount 20
            // outer sum: 10 + 20 = 30 discount → price 70
            IDiscountPolicy tenOff = new SimpleDiscount(new BigDecimal("10"));
            IDiscountPolicy twentyOff = new SimpleDiscount(new BigDecimal("20"));
            IDiscountPolicy fiveOff = new SimpleDiscount(new BigDecimal("5"));

            IDiscountPolicy innerMax = new MaxCompositeDiscount(List.of(twentyOff, fiveOff));
            IDiscountPolicy outerSum = new SumCompositeDiscount(List.of(tenOff, innerMax));

            ActiveOrder order = orderWithTotal(new BigDecimal("100.00"));
            assertEquals(new BigDecimal("70.00"),
                    outerSum.priceAfterDiscount(order, null, Instant.now()));
        }
    }

    // ---- helpers ----

    private static ActiveOrder orderWithTotal(BigDecimal total) {
        ActiveOrder order = new ActiveOrder(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), Instant.now());
        // single GA item with the desired total
        order.addItem(OrderItem.forGA(UUID.randomUUID(), UUID.randomUUID(), 1, total));
        return order;
    }

    private static ActiveOrder orderWithItems(int count, BigDecimal priceEach) {
        ActiveOrder order = new ActiveOrder(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), Instant.now());
        UUID zoneId = UUID.randomUUID();
        for (int i = 0; i < count; i++) {
            order.addItem(OrderItem.forSeat(UUID.randomUUID(), zoneId,
                    UUID.randomUUID(), priceEach));
        }
        return order;
    }
}
