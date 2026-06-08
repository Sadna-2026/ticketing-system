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
 * Persistent base for the purchase-policy hierarchy (V3-6 / #264).
 *
 * <p>The domain composes purchase rules through the {@link IPurchasePolicy} interface
 * (including ad-hoc lambdas in tests), so the interface is kept. This abstract class
 * {@code implements IPurchasePolicy} and is the JPA root the concrete leaves/composites
 * {@code extends}, giving every persistent policy a generated surrogate id without
 * changing any public API or evaluation behaviour.
 *
 * <p>Mapped {@code SINGLE_TABLE}: one {@code purchase_policies} table with a
 * {@code policy_type} discriminator. The subtypes carry only a handful of nullable
 * scalar columns (min age / quantity thresholds) and the whole composite tree is fetched
 * without joins, which suits the small, read-mostly policy trees far better than the
 * extra join-per-level a JOINED strategy would impose.
 */
@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "policy_type", discriminatorType = DiscriminatorType.STRING)
@Table(name = "purchase_policies")
public abstract class AbstractPurchasePolicy implements IPurchasePolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    protected AbstractPurchasePolicy() {
    }

    public Long getId() {
        return id;
    }
}
