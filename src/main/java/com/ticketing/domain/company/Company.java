package com.ticketing.domain.company;

import java.util.Objects;
import java.util.UUID;

public class Company {
    private String name; // also the unique identifier for the company
    private String description;
    private final UUID founderId;
    private CompanyStatus status;

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
    }

    // --- Getters ---
    public String getName() { return name; }
    public String getDescription() { return description; }
    public UUID getFounderId() { return founderId; }
    public CompanyStatus getStatus() { return status; }

    public boolean isActive() { return status == CompanyStatus.ACTIVE; }

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
