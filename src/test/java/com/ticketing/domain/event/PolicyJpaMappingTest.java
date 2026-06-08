package com.ticketing.domain.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import com.ticketing.domain.order.ActiveOrder;
import com.ticketing.domain.order.OrderItem;

/**
 * V3-6 (#264): the purchase-policy, discount-policy and discount-condition hierarchies
 * persist and round-trip via H2 (JPA) using a SINGLE_TABLE inheritance strategy per
 * hierarchy. Composite trees (AndPolicy/OrPolicy, Sum/MaxCompositeDiscount) and the
 * ConditionalDiscount→condition relationship survive the round-trip with their structure,
 * scalar fields, and evaluation behaviour intact.
 *
 * <p>Uses @DataJpaTest (embedded H2) with ddl-auto=create-drop so the schema is built for
 * the test even though the app config sets ddl-auto=none. @DataJpaTest is transactional so
 * the policy trees can be traversed inside the test method.
 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@DisplayName("Policy JPA mapping")
class PolicyJpaMappingTest {

    @Autowired
    private TestEntityManager em;

    @Test
    void GivenNestedPurchaseComposite_WhenPersistedAndReloaded_ThenTreeAndLeavesRoundTrip() {
        // --- Given: AndPolicy(AgeRestriction(18), OrPolicy(MinQuantity(2), MaxQuantity(10)))
        AgeRestrictionPolicy age = new AgeRestrictionPolicy(18);
        MinQuantityPolicy min = new MinQuantityPolicy(2);
        MaxQuantityPolicy max = new MaxQuantityPolicy(10);
        OrPolicy orBranch = new OrPolicy(List.of(min, max));
        AndPolicy root = new AndPolicy(List.of(age, orBranch));

        // --- When: persisted, flushed, cleared, reloaded -------------------------
        em.persistAndFlush(root);
        Long rootId = root.getId();
        em.clear();
        AbstractPurchasePolicy reloadedBase = em.find(AbstractPurchasePolicy.class, rootId);

        // --- Then: the discriminator-driven concrete types survive ---------------
        assertThat(reloadedBase).isInstanceOf(AndPolicy.class);
        AndPolicy reloaded = (AndPolicy) reloadedBase;
        assertThat(reloaded.getPolicies()).hasSize(2);

        AgeRestrictionPolicy reloadedAge = reloaded.getPolicies().stream()
                .filter(p -> p instanceof AgeRestrictionPolicy)
                .map(p -> (AgeRestrictionPolicy) p)
                .findFirst().orElseThrow();
        assertThat(reloadedAge.getMinimumAge()).isEqualTo(18);

        OrPolicy reloadedOr = reloaded.getPolicies().stream()
                .filter(p -> p instanceof OrPolicy)
                .map(p -> (OrPolicy) p)
                .findFirst().orElseThrow();
        assertThat(reloadedOr.getPolicies()).hasSize(2);

        MinQuantityPolicy reloadedMin = reloadedOr.getPolicies().stream()
                .filter(p -> p instanceof MinQuantityPolicy)
                .map(p -> (MinQuantityPolicy) p)
                .findFirst().orElseThrow();
        assertThat(reloadedMin.getMinTickets()).isEqualTo(2);

        MaxQuantityPolicy reloadedMax = reloadedOr.getPolicies().stream()
                .filter(p -> p instanceof MaxQuantityPolicy)
                .map(p -> (MaxQuantityPolicy) p)
                .findFirst().orElseThrow();
        assertThat(reloadedMax.getMaxTickets()).isEqualTo(10);

        // --- Then (bonus): isAllowed(ctx) still evaluates correctly after reload --
        LocalDate adultDob = LocalDate.now().minusYears(25);
        LocalDate minorDob = LocalDate.now().minusYears(15);

        // adult buying 3 tickets -> age ok AND (min2 ok OR ...) -> allowed
        assertThat(reloaded.isAllowed(ctx(3, adultDob)).allowed()).isTrue();
        // adult buying 1 ticket -> age ok AND (min2 fails OR max10 ok) -> allowed
        assertThat(reloaded.isAllowed(ctx(1, adultDob)).allowed()).isTrue();
        // adult buying 11 tickets -> age ok AND (min2 ok) -> allowed (OR short-circuits)
        assertThat(reloaded.isAllowed(ctx(11, adultDob)).allowed()).isTrue();
        // minor buying 3 tickets -> age fails -> rejected
        PolicyResult minorResult = reloaded.isAllowed(ctx(3, minorDob));
        assertThat(minorResult.allowed()).isFalse();
        assertThat(minorResult.errorCode()).isEqualTo("AGE_RESTRICTED");
    }

    @Test
    void GivenDiscountCompositeWithCondition_WhenPersistedAndReloaded_ThenStructureAndPricingRoundTrip() {
        // --- Given: SumCompositeDiscount(SimpleDiscount(10%),
        //                                 ConditionalDiscount(coupon-style 20% if 2+ tickets))
        // truncate to micros: H2/Hibernate store Instant at microsecond precision,
        // so a nanosecond-precision Instant.now() would not round-trip exactly.
        Instant expiry = Instant.now().plus(30, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MICROS);
        SimpleDiscount simple = new SimpleDiscount(new BigDecimal("10"));
        MinQuantityCondition condition = new MinQuantityCondition(2);
        ConditionalDiscount conditional = new ConditionalDiscount(new BigDecimal("20"), condition);
        // also exercise CouponDiscount persistence in the same tree
        CouponDiscount coupon = new CouponDiscount(new BigDecimal("5"), "SAVE5", expiry);
        SumCompositeDiscount root = new SumCompositeDiscount(List.of(simple, conditional, coupon));

        // --- When -----------------------------------------------------------------
        em.persistAndFlush(root);
        Long rootId = root.getId();
        em.clear();
        AbstractDiscountPolicy reloadedBase = em.find(AbstractDiscountPolicy.class, rootId);

        // --- Then: structure survives ---------------------------------------------
        assertThat(reloadedBase).isInstanceOf(SumCompositeDiscount.class);
        SumCompositeDiscount reloaded = (SumCompositeDiscount) reloadedBase;
        assertThat(reloaded.getPolicies()).hasSize(3);

        SimpleDiscount reloadedSimple = reloaded.getPolicies().stream()
                .filter(p -> p instanceof SimpleDiscount)
                .map(p -> (SimpleDiscount) p)
                .findFirst().orElseThrow();
        assertThat(reloadedSimple.getPercentOff()).isEqualByComparingTo("10");

        CouponDiscount reloadedCoupon = reloaded.getPolicies().stream()
                .filter(p -> p instanceof CouponDiscount)
                .map(p -> (CouponDiscount) p)
                .findFirst().orElseThrow();
        assertThat(reloadedCoupon.getCouponCode()).isEqualTo("SAVE5");
        assertThat(reloadedCoupon.getPercentOff()).isEqualByComparingTo("5");
        assertThat(reloadedCoupon.getExpiresAt()).isEqualTo(expiry);

        ConditionalDiscount reloadedConditional = reloaded.getPolicies().stream()
                .filter(p -> p instanceof ConditionalDiscount)
                .map(p -> (ConditionalDiscount) p)
                .findFirst().orElseThrow();
        assertThat(reloadedConditional.getPercentOff()).isEqualByComparingTo("20");
        // the ConditionalDiscount -> condition @ManyToOne relationship round-trips
        assertThat(reloadedConditional.getCondition()).isInstanceOf(MinQuantityCondition.class);
        assertThat(((MinQuantityCondition) reloadedConditional.getCondition()).getMinTickets()).isEqualTo(2);

        // --- Then: priceAfterDiscount(...) still evaluates after reload -----------
        // order of 3 tickets @ 50 = 150 total, condition (2+) met.
        // simple: 10% off -> 15 discount; conditional: 20% off -> 30 discount; coupon w/o code -> 0.
        // SumComposite stacks the positive discounts: 15 + 30 = 45 -> 150 - 45 = 105.
        ActiveOrder order = orderWithItems(3, new BigDecimal("50.00"));
        BigDecimal afterDiscount = reloaded.priceAfterDiscount(order, null, Instant.now());
        assertThat(afterDiscount).isEqualByComparingTo("105.00");

        // with the right coupon code an additional 5% (7.50) stacks -> 150 - 52.50 = 97.50
        BigDecimal withCoupon = reloaded.priceAfterDiscount(order, "SAVE5", Instant.now());
        assertThat(withCoupon).isEqualByComparingTo("97.50");
    }

    // ---- helpers ----

    private static PurchaseContext ctx(int ticketCount, LocalDate dob) {
        return new PurchaseContext(orderWithItems(ticketCount, new BigDecimal("50.00")),
                UUID.randomUUID(), dob);
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
