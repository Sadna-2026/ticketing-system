package com.ticketing.domain.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Tracks a pending refund for a checkout that failed after the payment was successfully captured.
 * This occurs when ticket supply generation fails, and the subsequent auto-refund attempt also fails.
 */
@Entity
@Table(name = "failed_checkout_refunds")
public class FailedCheckoutRefund {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "event_id")
    private UUID eventId;

    @Column(name = "member_id")
    private UUID memberId;

    @Column(name = "transaction_id")
    private String transactionId;

    @Column(name = "amount")
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private RefundStatus status;

    @Column(name = "created_at")
    private Instant createdAt;

    // Required by JPA
    protected FailedCheckoutRefund() {}

    public FailedCheckoutRefund(UUID id, UUID eventId, UUID memberId, String transactionId, BigDecimal amount, Instant createdAt) {
        if (id == null) throw new IllegalArgumentException("id is required");
        if (eventId == null) throw new IllegalArgumentException("eventId is required");
        // memberId can be null for guests
        if (transactionId == null || transactionId.isBlank()) throw new IllegalArgumentException("transactionId is required");
        if (amount == null || amount.signum() < 0) throw new IllegalArgumentException("amount must be non-negative");
        if (createdAt == null) throw new IllegalArgumentException("createdAt is required");

        this.id = id;
        this.eventId = eventId;
        this.memberId = memberId;
        this.transactionId = transactionId;
        this.amount = amount;
        this.status = RefundStatus.PENDING;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getEventId() { return eventId; }
    public UUID getMemberId() { return memberId; }
    public String getTransactionId() { return transactionId; }
    public BigDecimal getAmount() { return amount; }
    public RefundStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }

    public boolean isPending() {
        return status == RefundStatus.PENDING;
    }

    public void markRefunded() {
        this.status = RefundStatus.REFUNDED;
    }

    public void markFailed() {
        this.status = RefundStatus.FAILED;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FailedCheckoutRefund that = (FailedCheckoutRefund) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
