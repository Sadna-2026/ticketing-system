package com.ticketing.domain.company;

import java.util.Objects;
import java.util.UUID;

import com.ticketing.domain.event.IPurchasePolicy;

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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Company other = (Company)o;
        return Objects.equals(name, other.name); // Equality based on unique name
    }

}
