package com.ticketing.domain.admin;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class AdminBranchCoverageTest {

    @Test
    void GivenAdmin_WhenConstructingUpdatingCopyingAndComparing_ThenBranchesAreCovered() {
        UUID id = UUID.randomUUID();
        Admin admin = new Admin(id, "root", "root@example.com", "enc");
        Admin sameId = new Admin(id, "other", "other@example.com", "otherEnc");
        Admin different = new Admin(UUID.randomUUID(), "root", "root@example.com", "enc");

        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> new Admin(null, "root", "root@example.com", "enc")),
                () -> assertThrows(IllegalArgumentException.class, () -> new Admin(id, null, "root@example.com", "enc")),
                () -> assertThrows(IllegalArgumentException.class, () -> new Admin(id, " ", "root@example.com", "enc")),
                () -> assertThrows(IllegalArgumentException.class, () -> new Admin(id, "root", null, "enc")),
                () -> assertThrows(IllegalArgumentException.class, () -> new Admin(id, "root", " ", "enc")),
                () -> assertEquals("enc", admin.getEncryptedPassword()),
                () -> assertEquals(admin, admin),
                () -> assertEquals(admin, sameId),
                () -> assertNotEquals(admin, different),
                () -> assertNotEquals(admin, null),
                () -> assertNotEquals(admin, "admin"),
                () -> assertEquals(admin.hashCode(), sameId.hashCode())
        );

        admin.setUsername("newRoot");
        admin.setEmail("new@example.com");
        admin.incrementVersion();
        Admin copy = admin.detachedCopy();

        assertAll(
                () -> assertEquals("newRoot", admin.getUsername()),
                () -> assertEquals("new@example.com", admin.getEmail()),
                () -> assertEquals(1, admin.getVersion()),
                () -> assertEquals(admin.getId(), copy.getId()),
                () -> assertEquals(admin.getUsername(), copy.getUsername()),
                () -> assertEquals(admin.getEmail(), copy.getEmail()),
                () -> assertEquals(admin.getVersion(), copy.getVersion()),
                () -> assertThrows(IllegalArgumentException.class, () -> admin.setUsername(null)),
                () -> assertThrows(IllegalArgumentException.class, () -> admin.setUsername(" ")),
                () -> assertThrows(IllegalArgumentException.class, () -> admin.setEmail(null)),
                () -> assertThrows(IllegalArgumentException.class, () -> admin.setEmail(" "))
        );
    }
}
