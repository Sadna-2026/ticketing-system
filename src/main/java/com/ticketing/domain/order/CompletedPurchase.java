package com.ticketing.domain.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A frozen record of a completed purchase. Snapshot fields (eventName, companyName,
 * amount, purchasedAt) are captured at checkout time and never reflect later changes
 * to the underlying Event.
 *
 * <p>JPA mapping: an immutable {@code @Entity} (field access) whose snapshot fields are
 * stored as PLAIN COPIED COLUMNS. There is deliberately NO {@code @ManyToOne} to
 * {@link com.ticketing.domain.event.Event} or {@code Company}: that is what guarantees
 * the history integrity requirement — a stored purchase keeps the {@code eventName}/
 * {@code companyName}/{@code amount} as they were at checkout, so later edits to the
 * Event can never mutate recorded history.
 *
 * <p>Was a {@code record} in V2; converted to a class because JPA cannot instantiate a
 * record (no no-arg constructor / final components). The record-style accessors
 * ({@link #purchaseId()}, {@link #eventId()}, {@link #eventName()}, {@link #companyName()},
 * {@link #memberId()}, {@link #transactionId()}, {@link #amount()}, {@link #purchasedAt()})
 * plus {@code equals}/{@code hashCode} are preserved so all call sites compile unchanged
 * and the public API is identical.
 *
 * NOTE for the checkout-flow caller (UC-II.12): pass {@code ISystemClock.now()}
 * for {@code purchasedAt} rather than {@link java.time.Instant#now()} directly,
 * so tests can use TestClock and pin timestamps deterministically.
 */
@Entity
@Table(name = "completed_purchases")
public class CompletedPurchase {

    @Id
    @Column(name = "purchase_id")
    private UUID purchaseId;
    @Column(name = "event_id")
    private UUID eventId;
    @Column(name = "event_name")
    private String eventName;
    @Column(name = "company_name")
    private String companyName;
    @Column(name = "member_id")
    private UUID memberId;
    @Column(name = "transaction_id")
    private String transactionId;
    @Column(name = "amount")
    private BigDecimal amount;
    @Column(name = "purchased_at")
    private Instant purchasedAt;

    // Required by JPA; do not use directly.
    protected CompletedPurchase() {
    }

    public CompletedPurchase(
            UUID purchaseId,
            UUID eventId,
            String eventName,
            String companyName,
            UUID memberId,
            String transactionId,
            BigDecimal amount,
            Instant purchasedAt) {
        if (purchaseId == null) throw new IllegalArgumentException("purchaseId is required");
        if (eventId == null) throw new IllegalArgumentException("eventId is required");
        if (eventName == null || eventName.isBlank()) {
            throw new IllegalArgumentException("eventName is required");
        }
        if (companyName == null || companyName.isBlank()) {
            throw new IllegalArgumentException("companyName is required");
        }
        if (transactionId == null || transactionId.isBlank()) {
            throw new IllegalArgumentException("transactionId is required");
        }
        if (amount == null || amount.signum() < 0) {
            throw new IllegalArgumentException("amount must be non-negative");
        }
        if (purchasedAt == null) {
            throw new IllegalArgumentException("purchasedAt is required");
        }
        this.purchaseId = purchaseId;
        this.eventId = eventId;
        this.eventName = eventName;
        this.companyName = companyName;
        this.memberId = memberId;
        this.transactionId = transactionId;
        this.amount = amount;
        this.purchasedAt = purchasedAt;
    }

    // Record-style accessors, preserved so all existing call sites compile unchanged.
    public UUID purchaseId() { return purchaseId; }
    public UUID eventId() { return eventId; }
    public String eventName() { return eventName; }
    public String companyName() { return companyName; }
    public UUID memberId() { return memberId; }
    public String transactionId() { return transactionId; }
    public BigDecimal amount() { return amount; }
    public Instant purchasedAt() { return purchasedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CompletedPurchase that = (CompletedPurchase) o;
        return Objects.equals(purchaseId, that.purchaseId)
                && Objects.equals(eventId, that.eventId)
                && Objects.equals(eventName, that.eventName)
                && Objects.equals(companyName, that.companyName)
                && Objects.equals(memberId, that.memberId)
                && Objects.equals(transactionId, that.transactionId)
                && Objects.equals(amount, that.amount)
                && Objects.equals(purchasedAt, that.purchasedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(purchaseId, eventId, eventName, companyName, memberId,
                transactionId, amount, purchasedAt);
    }

    @Override
    public String toString() {
        return "CompletedPurchase[purchaseId=" + purchaseId
                + ", eventId=" + eventId
                + ", eventName=" + eventName
                + ", companyName=" + companyName
                + ", memberId=" + memberId
                + ", transactionId=" + transactionId
                + ", amount=" + amount
                + ", purchasedAt=" + purchasedAt + "]";
    }
}
