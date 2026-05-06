package com.ticketing.domain.member;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Value object wrapping a set of manager permissions.
 */
public class ManagerPermissions {
    private final Set<ManagerPermission> permissions;

    public ManagerPermissions(Set<ManagerPermission> permissions) {
        if (permissions == null) {
            this.permissions = Collections.emptySet();
        } else {
            for (ManagerPermission p : permissions) {
                if (p == null) {
                    throw new IllegalArgumentException("Permissions set cannot contain null values.");
                }
            }
            this.permissions = Collections.unmodifiableSet(new HashSet<>(permissions));
        }
    }

    public static ManagerPermissions empty() {
        return new ManagerPermissions(Collections.emptySet());
    }

    public Set<ManagerPermission> getPermissions() {
        return permissions;
    }

    public boolean hasPermission(ManagerPermission permission) {
        return permissions.contains(permission);
    }

    public boolean isEmpty() {
        return permissions.isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ManagerPermissions that = (ManagerPermissions) o;
        return Objects.equals(permissions, that.permissions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(permissions);
    }

    @Override
    public String toString() {
        return "ManagerPermissions{" + permissions + "}";
    }
}
