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
 * Persistent base for the discount-policy hierarchy (V3-6 / #264).
 *
 * <p>The domain composes discounts through the {@link IDiscountPolicy} interface
 * (including ad-hoc lambdas in tests), so the interface is kept. This abstract class
 * {@code implements IDiscountPolicy} and is the JPA root the concrete leaves/composites
 * {@code extends}, giving every persistent discount a generated surrogate id without
 * changing any public API or pricing behaviour.
 *
 * <p>Mapped {@code SINGLE_TABLE}: one {@code discount_policies} table with a
 * {@code discount_type} discriminator. Subtypes carry only a few nullable scalar columns
 * (percent off, coupon code/expiry) and the whole composite tree is fetched without
 * joins — preferred over JOINED for these small, read-mostly trees.
 */
@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "discount_type", discriminatorType = DiscriminatorType.STRING)
@Table(name = "discount_policies")
public abstract class AbstractDiscountPolicy implements IDiscountPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    protected AbstractDiscountPolicy() {
    }

    public Long getId() {
        return id;
    }
}
