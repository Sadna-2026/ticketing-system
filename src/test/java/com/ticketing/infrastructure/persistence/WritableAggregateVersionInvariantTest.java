package com.ticketing.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import jakarta.persistence.Entity;
import jakarta.persistence.Version;

/**
 * Regression guard for #510: every writable JPA entity in {@code com.ticketing.domain.*}
 * must declare {@code @Version} so a concurrent edit is rejected at the DB layer instead of
 * silently overwriting. Entities that are append-once or pure immutable value objects
 * (replaced wholesale rather than mutated in place) are explicitly allowlisted with a
 * one-line rationale; adding a new entity without {@code @Version} forces the contributor
 * to either annotate it or extend the allowlist with a documented reason.
 *
 * <p>Linked to ADR 0001 (locking strategy) under {@code docs/adr/0001-locking-strategy.md}.
 */
class WritableAggregateVersionInvariantTest {

    /**
     * Entities exempt from {@code @Version}. Each entry is one of:
     * <ul>
     *   <li><b>Immutable snapshot</b> — no public mutators; the row is written once and never updated
     *       (e.g. {@code CompletedPurchase}, {@code PendingRoleOffer}).</li>
     *   <li><b>Wholesale-replaced value object</b> — never mutated in place; "editing" creates a new
     *       row and the owning aggregate's root version catches the swap. Applies to the entire
     *       discount / purchase-policy / discount-condition hierarchies (SINGLE_TABLE inheritance —
     *       only the abstract roots are listed).</li>
     * </ul>
     */
    private static final Set<Class<?>> ALLOWLIST = Set.of(
            com.ticketing.domain.order.CompletedPurchase.class,    // immutable snapshot (receipt)
            com.ticketing.domain.member.PendingRoleOffer.class,    // immutable; no public mutators
            com.ticketing.domain.event.AbstractDiscountPolicy.class,   // value object, replaced wholesale
            com.ticketing.domain.event.AbstractPurchasePolicy.class,   // value object, replaced wholesale
            com.ticketing.domain.event.AbstractDiscountCondition.class // value object, replaced wholesale
    );

    @Test
    void everyMutableDomainEntity_hasVersionAnnotation_orIsAllowlisted() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));

        List<String> offenders = new ArrayList<>();
        List<Class<?>> scanned = new ArrayList<>();

        scanner.findCandidateComponents("com.ticketing.domain").forEach(bd -> {
            Class<?> entity;
            try {
                entity = Class.forName(bd.getBeanClassName());
            } catch (ClassNotFoundException e) {
                throw new AssertionError("Failed to load scanned entity: " + bd.getBeanClassName(), e);
            }
            scanned.add(entity);

            if (isAllowlisted(entity)) {
                return;
            }
            if (!hasVersionField(entity)) {
                offenders.add(entity.getName());
            }
        });

        assertThat(scanned)
                .as("classpath scan should discover the domain entities (sanity check)")
                .isNotEmpty();

        assertThat(offenders)
                .as("Entities missing @Version (add @Version or extend ALLOWLIST with rationale). "
                        + "See ADR 0001 (docs/adr/0001-locking-strategy.md) for the locking policy.")
                .isEmpty();
    }

    private static boolean isAllowlisted(Class<?> entity) {
        for (Class<?> allowed : ALLOWLIST) {
            if (allowed.isAssignableFrom(entity)) {
                // Covers SINGLE_TABLE subclasses of AbstractDiscountPolicy etc. by inheritance.
                return true;
            }
        }
        return false;
    }

    /**
     * Walks the class hierarchy looking for any non-static field carrying {@code @Version}.
     * JPA permits the annotation on either field- or property-access, but the codebase
     * consistently uses field access on a {@code version} field, so a field-based scan
     * matches the convention used by every existing root aggregate.
     */
    private static boolean hasVersionField(Class<?> type) {
        Class<?> cursor = type;
        while (cursor != null && cursor != Object.class) {
            for (Field f : cursor.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) {
                    continue;
                }
                if (f.isAnnotationPresent(Version.class)) {
                    return true;
                }
            }
            cursor = cursor.getSuperclass();
        }
        return false;
    }
}
