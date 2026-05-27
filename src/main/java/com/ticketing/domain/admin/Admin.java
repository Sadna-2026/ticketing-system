package com.ticketing.domain.admin;

import java.util.Objects;
import java.util.UUID;

/**
 * System-administrator aggregate. Distinct from {@code Member} so that
 * platform-wide privileged operations (force-close company, etc.) have a
 * dedicated identity that doesn't accidentally inherit member-side concerns.
 */
public class Admin {

    private final UUID id;
    private String username;
    private String email;
    private int version;
    private String encryptedPassword;

    public Admin(UUID id, String username, String email, String encryptedPassword) {
        if (id == null) throw new IllegalArgumentException("id is required");
        if (username == null || username.isBlank()) throw new IllegalArgumentException("username is required");
        if (email == null || email.isBlank()) throw new IllegalArgumentException("email is required");
        this.id = id;
        this.username = username;
        this.email = email;
        this.encryptedPassword = encryptedPassword;
    }

    public String getEncryptedPassword() { return encryptedPassword; }

    public UUID getId() { return id; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public int getVersion() { return version; }
    public void incrementVersion() { this.version++; }

    public Admin detachedCopy() {
        Admin copy = new Admin(id, username, email, encryptedPassword);
        copy.version = this.version;
        return copy;
    }

    public void setUsername(String username) {
        if (username == null || username.isBlank()) throw new IllegalArgumentException("username cannot be blank");
        this.username = username;
    }

    public void setEmail(String email) {
        if (email == null || email.isBlank()) throw new IllegalArgumentException("email cannot be blank");
        this.email = email;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Admin a)) return false;
        return Objects.equals(id, a.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }
}
