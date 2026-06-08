package com.ticketing.domain.event;

import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.Table;

/**
 * Persistent base for the discount-condition hierarchy (V3-6 / #264).
 *
 * <p>Conditions are still expressed through the {@link IDiscountCondition} functional
 * interface (a {@link ConditionalDiscount} can be built from an ad-hoc lambda in tests),
 * so the interface is kept. This abstract class {@code implements IDiscountCondition} and
 * is the JPA root the concrete conditions {@code extends}, giving each a generated
 * surrogate id without changing the public API or {@code isMet} behaviour.
 *
 * <p>Mapped {@code SINGLE_TABLE}: one {@code discount_conditions} table with a
 * {@code condition_type} discriminator and a few nullable scalar columns (date range,
 * quantity threshold).
 */
@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "condition_type", discriminatorType = DiscriminatorType.STRING)
@Table(name = "discount_conditions")
public abstract class AbstractDiscountCondition implements IDiscountCondition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    protected AbstractDiscountCondition() {
    }

    public Long getId() {
        return id;
    }
}
