package com.ticketing.domain.company;

import java.util.Objects;
import java.util.UUID;

import com.ticketing.domain.event.AbstractDiscountPolicy;
import com.ticketing.domain.event.AbstractPurchasePolicy;
import com.ticketing.domain.event.AlwaysAllowPolicy;
import com.ticketing.domain.event.IDiscountPolicy;
import com.ticketing.domain.event.IPurchasePolicy;
import com.ticketing.domain.event.NoDiscountPolicy;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "companies")
public class Company {
    @Id
    @Column(name = "name")
    private String name; // also the unique identifier for the company
    @Column(name = "description")
    private String description;
    @Column(name = "founder_id")
    private UUID founderId;
    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private CompanyStatus status;
    @ManyToOne(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @JoinColumn(name = "purchase_policy_id")
    private AbstractPurchasePolicy purchasePolicy;

    @ManyToOne(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @JoinColumn(name = "discount_policy_id")
    private AbstractDiscountPolicy discountPolicy;
    @Column(name = "allow_discount_stacking", nullable = false)
    private boolean allowDiscountStacking;  // true = company + event discounts both apply
    @Version
    @Column(name = "version")
    private int version;

    /** JPA-only no-arg constructor. */
    protected Company() {
        this.status = CompanyStatus.ACTIVE;
        this.purchasePolicy = new AlwaysAllowPolicy();
        this.discountPolicy = new NoDiscountPolicy();
        this.allowDiscountStacking = false;
    }

    public Company(String name, String description, UUID founderId) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Company name is required");
        }
        if (founderId == null) {
            throw new IllegalArgumentException("Founder ID is required");
        }

        this.name = name;
        this.description = description;
        this.founderId = founderId;
        this.status = CompanyStatus.ACTIVE;
        this.purchasePolicy = new AlwaysAllowPolicy();
        this.discountPolicy = new NoDiscountPolicy();
        this.allowDiscountStacking = false;
    }

    // --- Getters ---
    public String getName() { return name; }
    public String getDescription() { return description; }
    public UUID getFounderId() { return founderId; }
    public CompanyStatus getStatus() { return status; }

    public boolean isActive() { return status == CompanyStatus.ACTIVE; }

    public IPurchasePolicy getPurchasePolicy() { return purchasePolicy; }
    public IDiscountPolicy getDiscountPolicy() { return discountPolicy; }

    public boolean isAllowDiscountStacking() { return allowDiscountStacking; }

    public void setAllowDiscountStacking(boolean allow) {
        this.allowDiscountStacking = allow;
    }

    public void setPurchasePolicy(IPurchasePolicy policy) {
        if (policy == null) throw new IllegalArgumentException("Purchase policy cannot be null");
        this.purchasePolicy = requirePersistentPurchasePolicy(policy);
    }

    public void setDiscountPolicy(IDiscountPolicy policy) {
        if (policy == null) throw new IllegalArgumentException("Discount policy cannot be null");
        this.discountPolicy = requirePersistentDiscountPolicy(policy);
    }

    private static AbstractPurchasePolicy requirePersistentPurchasePolicy(IPurchasePolicy policy) {
        if (policy instanceof AbstractPurchasePolicy persistent) {
            return persistent;
        }
        throw new IllegalArgumentException(
                "Purchase policy must be a persistent entity type, got: " + policy.getClass().getName());
    }

    private static AbstractDiscountPolicy requirePersistentDiscountPolicy(IDiscountPolicy policy) {
        if (policy instanceof AbstractDiscountPolicy persistent) {
            return persistent;
        }
        throw new IllegalArgumentException(
                "Discount policy must be a persistent entity type, got: " + policy.getClass().getName());
    }

    public int getVersion() { return version; }

    public void incrementVersion() { this.version++; }

    /**
     * Returns a detached copy with the same field values and version.
     * Callers may mutate the copy without affecting the repository's stored instance.
     */
    public Company detachedCopy() {
        Company copy = new Company(name, description, founderId);
        copy.status = this.status;
        copy.purchasePolicy = this.purchasePolicy;
        copy.discountPolicy = this.discountPolicy;
        copy.allowDiscountStacking = this.allowDiscountStacking;
        copy.version = this.version;
        return copy;
    }

    // --- Setters ---
    public void setName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Name cannot be blank");
        }
        this.name = name;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    // --- Lifecycle ---

    public void suspend() {
        if (status != CompanyStatus.ACTIVE) {
            throw new IllegalStateException("Can only suspend an ACTIVE company. Current: " + status);
        }
        this.status = CompanyStatus.SUSPENDED;
    }

    public void reopen() {
        if (status != CompanyStatus.SUSPENDED) {
            throw new IllegalStateException("Can only reopen a SUSPENDED company. Current: " + status);
        }
        this.status = CompanyStatus.ACTIVE;
    }

    public void close() {
        if (status == CompanyStatus.CLOSED) {
            throw new IllegalStateException("Company is already closed");
        }
        this.status = CompanyStatus.CLOSED;
    }

    public void markPendingClosure() {
        if (status == CompanyStatus.CLOSED || status == CompanyStatus.PENDING_CLOSURE) {
            throw new IllegalStateException("Cannot mark pending closure from status: " + status);
        }
        this.status = CompanyStatus.PENDING_CLOSURE;
    }

    public void completeClosure() {
        if (status != CompanyStatus.PENDING_CLOSURE) {
            throw new IllegalStateException("completeClosure requires PENDING_CLOSURE. Current: " + status);
        }
        this.status = CompanyStatus.CLOSED;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Company other = (Company)o;
        return Objects.equals(name, other.name); // Equality based on unique name
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }
}
