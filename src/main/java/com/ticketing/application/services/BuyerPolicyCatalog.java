package com.ticketing.application.services;

import java.util.ArrayList;
import java.util.List;

import com.ticketing.application.dto.EventPolicyBadgeDTO;
import com.ticketing.application.dto.EventPolicyBadgeDTO.Kind;
import com.ticketing.domain.event.AgeRestrictionPolicy;
import com.ticketing.domain.event.AlwaysAllowPolicy;
import com.ticketing.domain.event.AndPolicy;
import com.ticketing.domain.event.ConditionalDiscount;
import com.ticketing.domain.event.CouponDiscount;
import com.ticketing.domain.event.DateRangeCondition;
import com.ticketing.domain.event.DiscountPolicyText;
import com.ticketing.domain.event.Event;
import com.ticketing.domain.event.IDiscountCondition;
import com.ticketing.domain.event.IDiscountPolicy;
import com.ticketing.domain.event.IPurchasePolicy;
import com.ticketing.domain.event.MaxCompositeDiscount;
import com.ticketing.domain.event.MaxQuantityCondition;
import com.ticketing.domain.event.MaxQuantityPolicy;
import com.ticketing.domain.event.MinQuantityCondition;
import com.ticketing.domain.event.MinQuantityPolicy;
import com.ticketing.domain.event.NoDiscountPolicy;
import com.ticketing.domain.event.NoOrphanSeatPolicy;
import com.ticketing.domain.event.OrPolicy;
import com.ticketing.domain.event.PurchasePolicyText;
import com.ticketing.domain.event.SimpleDiscount;
import com.ticketing.domain.event.SumCompositeDiscount;

/**
 * Builds structured buyer-facing policy badges from domain policies.
 * Hides coupon discounts and omits sections when there are no real rules or offers.
 */
public final class BuyerPolicyCatalog {

    private BuyerPolicyCatalog() {
    }

    public static List<EventPolicyBadgeDTO> purchaseRestrictions(Event event) {
        if (event == null) {
            return List.of();
        }
        List<EventPolicyBadgeDTO> badges = new ArrayList<>();
        collectPurchaseRestrictions(event.getEventPurchasePolicy(), badges);
        return List.copyOf(badges);
    }

    public static List<EventPolicyBadgeDTO> visibleDiscounts(Event event) {
        if (event == null) {
            return List.of();
        }
        List<EventPolicyBadgeDTO> badges = new ArrayList<>();
        collectVisibleDiscounts(event.getEventDiscountPolicy(), badges);
        return List.copyOf(badges);
    }

    private static void collectPurchaseRestrictions(IPurchasePolicy policy, List<EventPolicyBadgeDTO> badges) {
        if (policy == null || policy instanceof AlwaysAllowPolicy) {
            return;
        }
        if (policy instanceof AgeRestrictionPolicy age) {
            badges.add(new EventPolicyBadgeDTO(
                    Kind.RESTRICTION,
                    "Age requirement",
                    "You must be at least " + age.getMinimumAge() + " years old to purchase"));
            return;
        }
        if (policy instanceof MinQuantityPolicy min) {
            badges.add(new EventPolicyBadgeDTO(
                    Kind.RESTRICTION,
                    "Minimum purchase",
                    "At least " + min.getMinTickets() + " tickets per order"));
            return;
        }
        if (policy instanceof MaxQuantityPolicy max) {
            badges.add(new EventPolicyBadgeDTO(
                    Kind.RESTRICTION,
                    "Ticket limit",
                    "Up to " + max.getMaxTickets() + " tickets per order"));
            return;
        }
        if (policy instanceof NoOrphanSeatPolicy) {
            badges.add(new EventPolicyBadgeDTO(
                    Kind.RESTRICTION,
                    "Seat selection rule",
                    "Do not leave a single isolated seat between your picks and sold seats"));
            return;
        }
        if (policy instanceof AndPolicy and) {
            badges.add(new EventPolicyBadgeDTO(
                    Kind.RESTRICTION,
                    "AND purchase policy",
                    "Purchase allowed when: " + PurchasePolicyText.describeRequirement(and)));
            return;
        }
        if (policy instanceof OrPolicy or) {
            badges.add(new EventPolicyBadgeDTO(
                    Kind.RESTRICTION,
                    "OR purchase policy",
                    "Purchase allowed when: " + PurchasePolicyText.describeRequirement(or)));
        }
    }

    private static void collectVisibleDiscounts(IDiscountPolicy policy, List<EventPolicyBadgeDTO> badges) {
        if (policy == null || policy instanceof NoDiscountPolicy || policy instanceof CouponDiscount) {
            return;
        }
        if (policy instanceof SimpleDiscount simple) {
            badges.add(new EventPolicyBadgeDTO(
                    Kind.DISCOUNT,
                    "Discount",
                    simple.getPercentOff().stripTrailingZeros().toPlainString() + "% off all tickets"));
            return;
        }
        if (policy instanceof ConditionalDiscount conditional) {
            badges.add(new EventPolicyBadgeDTO(
                    Kind.DISCOUNT,
                    "Conditional discount",
                    conditional.getPercentOff().stripTrailingZeros().toPlainString() + "% off when "
                            + describeCondition(conditional.getCondition())));
            return;
        }
        if (policy instanceof MaxCompositeDiscount max) {
            addCompositeDiscountBadge("Best available discount", max, badges);
            return;
        }
        if (policy instanceof SumCompositeDiscount sum) {
            addCompositeDiscountBadge("Stacked discounts", sum, badges);
        }
    }

    private static void addCompositeDiscountBadge(
            String title,
            IDiscountPolicy policy,
            List<EventPolicyBadgeDTO> badges
    ) {
        String description = DiscountPolicyText.describeVisibleDiscount(policy);
        if (description != null && !description.isBlank()) {
            badges.add(new EventPolicyBadgeDTO(Kind.DISCOUNT, title, description));
        }
    }

    private static String describeCondition(IDiscountCondition condition) {
        if (condition instanceof MinQuantityCondition min) {
            return "buying " + min.getMinTickets() + " or more tickets";
        }
        if (condition instanceof MaxQuantityCondition max) {
            return "buying up to " + max.getMaxTickets() + " tickets";
        }
        if (condition instanceof DateRangeCondition range) {
            String from = range.getFrom() == null ? "now" : range.getFrom().toString();
            String to = range.getTo() == null ? "event day" : range.getTo().toString();
            return "purchasing between " + from + " and " + to;
        }
        return "a qualifying purchase is made";
    }
}
