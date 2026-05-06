package com.ticketing.domain.queue;

import java.util.Objects;

/**
 * Value Object representing the configuration of a Virtual Queue.
 * Immutable by design.
 */
public final class QueueConfig {

    private final int threshold;
    private final int flowRate;

    public QueueConfig(int threshold, int flowRate) {
        if (threshold <= 0) throw new IllegalArgumentException("Threshold must be positive");
        if (flowRate <= 0) throw new IllegalArgumentException("Flow rate must be positive");
        this.threshold = threshold;
        this.flowRate = flowRate;
    }

    public int getThreshold() { return threshold; }
    public int getFlowRate() { return flowRate; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        QueueConfig that = (QueueConfig) o;
        return threshold == that.threshold && flowRate == that.flowRate;
    }

    @Override
    public int hashCode() {
        return Objects.hash(threshold, flowRate);
    }
}
