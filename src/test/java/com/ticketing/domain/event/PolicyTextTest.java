package com.ticketing.domain.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.ticketing.domain.order.ActiveOrder;
import com.ticketing.domain.order.OrderItem;

@DisplayName("Policy text helpers")
class PolicyTextTest {

    @Nested
    @DisplayName("PurchasePolicyText")
    class PurchasePolicyTextTests {

        @Test
        void GivenNullOrAlwaysAllow_WhenDescribeRequirement_ThenNoRestrictions() {
            assertEquals("no purchase restrictions", PurchasePolicyText.describeRequirement(null));
            assertEquals("no purchase restrictions", PurchasePolicyText.describeRequirement(new AlwaysAllowPolicy()));
        }

        @Test
        void GivenNoOrphanSeatPolicy_WhenDescribeRequirement_ThenIsolatedSeatRuleShown() {
            assertEquals("no isolated single seats",
                    PurchasePolicyText.describeRequirement(new NoOrphanSeatPolicy()));
        }

        @Test
        void GivenEmptyOrBranches_WhenDescribeOrFailure_ThenReturnsDefaultMessage() {
            assertEquals("No policies in the OR condition passed.",
                    PurchasePolicyText.describeOrFailure(null, dummyCtx()));
            assertEquals("No policies in the OR condition passed.",
                    PurchasePolicyText.describeOrFailure(List.of(), dummyCtx()));
        }

        @Test
        void GivenFailedBranchesWithReasons_WhenDescribeOrFailure_ThenIncludesFailureSuffix() {
            String message = PurchasePolicyText.describeOrFailure(
                    List.of(new MinQuantityPolicy(3), new AgeRestrictionPolicy(21)),
                    ctxWithTickets(1));

            assertTrue(message.contains("failed because"));
            assertTrue(message.contains("Option 1 (at least 3 tickets)"));
            assertTrue(message.contains("Option 2 (minimum age 21)"));
        }

        @Test
        void GivenNestedOrWithPassingInnerBranch_WhenDescribeOrFailure_ThenInnerOrIsSkipped() {
            IPurchasePolicy nested = new OrPolicy(List.of(
                    new MinQuantityPolicy(2),
                    new AlwaysAllowPolicy()));
            String message = PurchasePolicyText.describeOrFailure(
                    List.of(new AndPolicy(List.of(nested, new AgeRestrictionPolicy(21)))),
                    ctxWithTickets(1));

            assertTrue(message.contains("Option 1"));
            assertTrue(message.contains("minimum age 21"));
        }

        @Test
        void GivenCompositeWithSkippableChildren_WhenDescribeRequirement_ThenFiltersAlwaysAllow() {
            IPurchasePolicy composite = new AndPolicy(List.of(
                    new AlwaysAllowPolicy(),
                    new MaxQuantityPolicy(4)));
            assertEquals("at most 4 tickets", PurchasePolicyText.describeRequirement(composite));
        }

        @Test
        void GivenCompositeWithOnlySkippableChildren_WhenDescribeRequirement_ThenNoRestrictions() {
            IPurchasePolicy composite = new AndPolicy(List.of(new AlwaysAllowPolicy()));
            assertEquals("no purchase restrictions", PurchasePolicyText.describeRequirement(composite));
        }

        @Test
        void GivenSingleNonTrivialChild_WhenDescribeRequirement_ThenNoCompositeWrapper() {
            IPurchasePolicy composite = new AndPolicy(List.of(new MinQuantityPolicy(2)));
            assertEquals("at least 2 tickets", PurchasePolicyText.describeRequirement(composite));
        }
    }

    @Nested
    @DisplayName("DiscountPolicyText")
    class DiscountPolicyTextTests {

        @Test
        void GivenHiddenDiscountTypes_WhenDescribeVisible_ThenReturnsEmpty() {
            assertEquals("", DiscountPolicyText.describeVisibleDiscount(null));
            assertEquals("", DiscountPolicyText.describeVisibleDiscount(new NoDiscountPolicy()));
            assertEquals("", DiscountPolicyText.describeVisibleDiscount(
                    new CouponDiscount(new BigDecimal("10"), "SAVE", Instant.now().plus(1, ChronoUnit.DAYS))));
        }

        @Test
        void GivenNoDiscountOrNull_WhenDescribeManagement_ThenNoDiscountLabel() {
            assertEquals("No discount", DiscountPolicyText.describeManagementDiscount(null));
            assertEquals("No discount", DiscountPolicyText.describeManagementDiscount(new NoDiscountPolicy()));
        }

        @Test
        void GivenCoupon_WhenDescribeManagement_ThenShowsCodeAndExpiry() {
            Instant expiry = Instant.parse("2026-12-31T00:00:00Z");
            String description = DiscountPolicyText.describeManagementDiscount(
                    new CouponDiscount(new BigDecimal("15"), "EARLYBIRD", expiry));

            assertTrue(description.contains("15% coupon 'EARLYBIRD'"));
            assertTrue(description.contains("2026-12-31"));
        }

        @Test
        void GivenMaxComposite_WhenDescribeVisible_ThenBestOfLabelUsed() {
            IDiscountPolicy policy = new MaxCompositeDiscount(List.of(
                    new SimpleDiscount(new BigDecimal("10")),
                    new SimpleDiscount(new BigDecimal("20"))));

            assertEquals("best of: (10% off all tickets OR 20% off all tickets)",
                    DiscountPolicyText.describeVisibleDiscount(policy));
        }

        @Test
        void GivenCompositeWithOnlyHiddenChildren_WhenDescribeVisible_ThenReturnsEmpty() {
            IDiscountPolicy policy = new SumCompositeDiscount(List.of(
                    new NoDiscountPolicy(),
                    new CouponDiscount(new BigDecimal("5"), "HIDDEN", Instant.now().plus(1, ChronoUnit.DAYS))));

            assertEquals("", DiscountPolicyText.describeVisibleDiscount(policy));
        }

        @Test
        void GivenSingleVisibleChildInComposite_WhenDescribeVisible_ThenNoCompositeLabel() {
            IDiscountPolicy policy = new SumCompositeDiscount(List.of(new SimpleDiscount(new BigDecimal("5"))));
            assertEquals("5% off all tickets", DiscountPolicyText.describeVisibleDiscount(policy));
        }

        @Test
        void GivenMaxQuantityCondition_WhenDescribeVisible_ThenUsesMaxQuantityWording() {
            IDiscountPolicy policy = new ConditionalDiscount(
                    new BigDecimal("10"), new MaxQuantityCondition(2));

            assertTrue(DiscountPolicyText.describeVisibleDiscount(policy)
                    .contains("buying at most 2 tickets"));
        }

        @Test
        void GivenDateRangeCondition_WhenDescribeVisible_ThenHandlesBounds() {
            Instant from = Instant.parse("2026-01-01T00:00:00Z");
            Instant to = Instant.parse("2026-06-01T00:00:00Z");
            IDiscountPolicy bounded = new ConditionalDiscount(
                    new BigDecimal("10"), new DateRangeCondition(from, to));
            assertTrue(DiscountPolicyText.describeVisibleDiscount(bounded)
                    .contains("purchasing between " + from + " and " + to));

            IDiscountPolicy openEnded = new ConditionalDiscount(
                    new BigDecimal("10"), new DateRangeCondition(null, null));
            assertTrue(DiscountPolicyText.describeVisibleDiscount(openEnded)
                    .contains("purchasing between now and event day"));
        }
    }

    private static PurchaseContext dummyCtx() {
        return new PurchaseContext(null, UUID.randomUUID(), null);
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
}
