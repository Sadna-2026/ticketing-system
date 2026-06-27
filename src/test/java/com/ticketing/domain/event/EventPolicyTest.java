package com.ticketing.domain.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import java.util.Set;
import java.util.UUID;

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

        @Test
        public void GivenEvent_WhenInspectClass_ThenPolicySettersExist() throws Exception {
            // V2: policy editing is now supported (UC-II.4.3).
            boolean hasPurchaseSetter = false;
            boolean hasDiscountSetter = false;
            for (var m : Event.class.getMethods()) {
                String name = m.getName();
                if (name.equals("setPurchasePolicy")) hasPurchaseSetter = true;
                if (name.equals("setDiscountPolicy")) hasDiscountSetter = true;
            }
            assertTrue(hasPurchaseSetter, "Event must expose setPurchasePolicy in V2");
            assertTrue(hasDiscountSetter, "Event must expose setDiscountPolicy in V2");
        }

        @Test
        public void GivenEvent_WhenSetPurchasePolicy_ThenPolicyUpdated() {
            Event e = newEvent();
            IPurchasePolicy custom = new AndPolicy(List.of(new AlwaysAllowPolicy()));
            e.setPurchasePolicy(custom);
            assertEquals(custom, e.getPurchasePolicy());
        }

        @Test
        public void GivenEvent_WhenSetDiscountPolicy_ThenPolicyUpdated() {
            Event e = newEvent();
            IDiscountPolicy custom = new NoDiscountPolicy();
            e.setDiscountPolicy(custom);
            assertEquals(custom, e.getDiscountPolicy());
        }

        @Test
        public void GivenCancelledEvent_WhenSetPurchasePolicy_ThenThrows() {
            Event e = newEvent();
            e.cancel();
            assertThrows(IllegalStateException.class,
                    () -> e.setPurchasePolicy(new AlwaysAllowPolicy()));
        }

        @Test
        public void GivenNullPolicy_WhenSetPurchasePolicy_ThenThrows() {
            Event e = newEvent();
            assertThrows(IllegalArgumentException.class,
                    () -> e.setPurchasePolicy(null));
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
            assertEquals("Failed\nFailed", result.reason());
        }

        @Test
        void GivenOrPolicy_WhenAllBranchesFail_ThenCollectViolationsReturnsEachBranchReason() {
            IPurchasePolicy orPolicy = new OrPolicy(List.of(
                    new MinQuantityPolicy(3),
                    new AgeRestrictionPolicy(21)));

            List<String> violations = orPolicy.collectViolations(ctxWithTickets(1));

            assertEquals(2, violations.size());
            assertTrue(violations.stream().anyMatch(v -> v.contains("at least 3 tickets")));
            assertTrue(violations.stream().anyMatch(v -> v.contains("Date of birth is required")));
        }
    }

    @Nested
    @DisplayName("NoOrphanSeatPolicy")
    class NoOrphanSeatPolicyTests {

        @Test
        void GivenSelectionLeavesMultipleOrphans_ThenErrorListsEveryOrphanSeat() {
            NoOrphanSeatPolicy policy = new NoOrphanSeatPolicy();
            UUID zoneId = UUID.randomUUID();
            UUID b1 = UUID.randomUUID();
            UUID b2 = UUID.randomUUID();
            UUID b3 = UUID.randomUUID();
            UUID b4 = UUID.randomUUID();
            UUID b5 = UUID.randomUUID();

            Event event = new Event(UUID.randomUUID(), "Acme", "Orphan Demo", "desc", EventCategory.CONCERT,
                    new EventSchedule(Instant.now().plus(30, ChronoUnit.DAYS),
                            Instant.now().plus(31, ChronoUnit.DAYS),
                            Instant.now().plus(29, ChronoUnit.DAYS)),
                    new LockTimerDuration(Duration.ofMinutes(15)),
                    policy,
                    new NoDiscountPolicy());

            InventoryZone zone = InventoryZone.createAssigned(zoneId, "Main Hall", new BigDecimal("50.00"));
            for (int i = 1; i <= 5; i++) {
                Seat sold = new Seat(UUID.randomUUID(), "A", String.valueOf(i));
                sold.lock();
                sold.sell();
                zone.addSeat(sold);
            }
            zone.addSeat(new Seat(b1, "B", "1"));
            zone.addSeat(new Seat(b2, "B", "2"));
            zone.addSeat(new Seat(b3, "B", "3"));
            zone.addSeat(new Seat(b4, "B", "4"));
            zone.addSeat(new Seat(b5, "B", "5"));
            event.addZone(zone);

            PurchaseContext context = new PurchaseContext(
                    null, null, null, event, Set.of(b1, b3, b5), Set.of());

            PolicyResult result = policy.isAllowed(context);

            assertFalse(result.allowed());
            assertTrue(result.reason().contains("B-2"));
            assertTrue(result.reason().contains("B-4"));
            assertTrue(result.reason().contains("seats B-2, B-4"));
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
