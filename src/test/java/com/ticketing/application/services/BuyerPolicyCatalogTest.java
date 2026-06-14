package com.ticketing.application.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ticketing.application.dto.EventPolicyBadgeDTO;
import com.ticketing.application.dto.EventPolicyBadgeDTO.Kind;
import com.ticketing.domain.event.AgeRestrictionPolicy;
import com.ticketing.domain.event.AlwaysAllowPolicy;
import com.ticketing.domain.event.AndPolicy;
import com.ticketing.domain.event.ConditionalDiscount;
import com.ticketing.domain.event.CouponDiscount;
import com.ticketing.domain.event.Event;
import com.ticketing.domain.event.EventCategory;
import com.ticketing.domain.event.EventSchedule;
import com.ticketing.domain.event.LockTimerDuration;
import com.ticketing.domain.event.MaxQuantityPolicy;
import com.ticketing.domain.event.MinQuantityCondition;
import com.ticketing.domain.event.MinQuantityPolicy;
import com.ticketing.domain.event.NoDiscountPolicy;
import com.ticketing.domain.event.OrPolicy;
import com.ticketing.domain.event.SimpleDiscount;

@DisplayName("BuyerPolicyCatalog")
class BuyerPolicyCatalogTest {

    @Test
    void GivenAlwaysAllowAndNoDiscount_WhenBuildingBadges_ThenListsAreEmpty() {
        Event event = event(new AlwaysAllowPolicy(), new NoDiscountPolicy());

        assertTrue(BuyerPolicyCatalog.purchaseRestrictions(event).isEmpty());
        assertTrue(BuyerPolicyCatalog.visibleDiscounts(event).isEmpty());
    }

    @Test
    void GivenCouponDiscount_WhenBuildingBadges_ThenDiscountIsHidden() {
        Event event = event(new AlwaysAllowPolicy(),
                new CouponDiscount(new BigDecimal("20"), "SECRET", Instant.now().plus(Duration.ofDays(1))));

        assertTrue(BuyerPolicyCatalog.visibleDiscounts(event).isEmpty());
    }

    @Test
    void GivenCompositePolicies_WhenBuildingBadges_ThenStructuredItemsAreReturned() {
        Event event = event(
                new AndPolicy(List.of(new AgeRestrictionPolicy(18), new MaxQuantityPolicy(2))),
                new ConditionalDiscount(new BigDecimal("15"), new MinQuantityCondition(3)));

        List<EventPolicyBadgeDTO> restrictions = BuyerPolicyCatalog.purchaseRestrictions(event);
        assertEquals(2, restrictions.size());
        assertEquals(Kind.RESTRICTION, restrictions.get(0).kind());
        assertEquals("Age requirement", restrictions.get(0).title());
        assertEquals("Ticket limit", restrictions.get(1).title());

        List<EventPolicyBadgeDTO> discounts = BuyerPolicyCatalog.visibleDiscounts(event);
        assertEquals(1, discounts.size());
        assertEquals(Kind.DISCOUNT, discounts.get(0).kind());
        assertTrue(discounts.get(0).detail().contains("15% off"));
        assertTrue(discounts.get(0).detail().contains("3 or more"));
    }

    @Test
    void GivenOrPolicy_WhenBuildingBadges_ThenFlexibleRuleIsSummarized() {
        Event event = event(
                new OrPolicy(List.of(new MinQuantityPolicy(3), new AgeRestrictionPolicy(21))),
                new SimpleDiscount(new BigDecimal("10")));

        List<EventPolicyBadgeDTO> restrictions = BuyerPolicyCatalog.purchaseRestrictions(event);
        assertEquals(1, restrictions.size());
        assertEquals("Flexible purchase rule", restrictions.get(0).title());
        assertTrue(restrictions.get(0).detail().contains("Meet any one of"));

        assertEquals(1, BuyerPolicyCatalog.visibleDiscounts(event).size());
    }

    private static Event event(
            com.ticketing.domain.event.IPurchasePolicy purchasePolicy,
            com.ticketing.domain.event.IDiscountPolicy discountPolicy
    ) {
        Instant start = Instant.now().plus(Duration.ofDays(10));
        return new Event(java.util.UUID.randomUUID(), "Co", "Event", "desc",
                EventCategory.CONCERT,
                new EventSchedule(start, start.plus(Duration.ofHours(2)), start.minus(Duration.ofHours(1))),
                new LockTimerDuration(Duration.ofMinutes(10)),
                purchasePolicy,
                discountPolicy);
    }
}
