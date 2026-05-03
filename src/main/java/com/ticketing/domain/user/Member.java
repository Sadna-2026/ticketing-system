package com.ticketing.domain.user;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class Member {
    private final UUID id;
    private ContactInfo contactInfo;
    private String passwordHash;
    private boolean banned;
    private final Instant registeredAt;
    private final Map<String, Producer> companyRoles; // key: company name, value: Producer role in that company

    public Member(UUID id, ContactInfo contactInfo, String passwordHash, Instant registeredAt) {
        this.id = id;
        this.contactInfo = contactInfo;
        this.passwordHash = passwordHash;
        this.registeredAt = registeredAt;
        this.banned = false;
        this.companyRoles = new HashMap<>();
    }

    public String getName() {
        return contactInfo.getFirstName() + " " + contactInfo.getLastName();
    }

    public String getEmail() {
        return contactInfo.getEmail();
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Producer getCompanyRole(String companyName) {
        return this.companyRoles.get(companyName);
    }
    
    public List<Producer> getCompanyRoles() {
        return companyRoles.values().stream().toList();
    }

    public void addCompanyRole(Producer role) {
        this.companyRoles.put(role.getCompanyName(), role);
    }

    public void removeCompanyRole(String companyName) {
        this.companyRoles.remove(companyName);
    }

    public boolean isBanned() {
        return banned;
    }

    public void setBanned(boolean banned) {
        this.banned = banned;
    }
}
