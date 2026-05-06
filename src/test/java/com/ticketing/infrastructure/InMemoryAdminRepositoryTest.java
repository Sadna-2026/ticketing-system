package com.ticketing.infrastructure;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ticketing.domain.admin.Admin;

/**
 * CRUD smoke test for the in-memory repository pattern. Other in-memory
 * repositories (queue, lottery, etc.) follow the same shape.
 */
public class InMemoryAdminRepositoryTest {

    private InMemoryAdminRepository repo;

    @BeforeEach
    public void setUp() {
        repo = new InMemoryAdminRepository();
    }

    @Test
    public void GivenNewAdmin_WhenSave_ThenFindByIdReturnsIt() {
        UUID id = UUID.randomUUID();
        Admin a = new Admin(id, "root", "root@example.com");

        repo.save(a);

        assertEquals(a, repo.findById(id).orElseThrow());
    }

    @Test
    public void GivenSavedAdmin_WhenFindByUsername_ThenReturnsIt() {
        UUID id = UUID.randomUUID();
        repo.save(new Admin(id, "root", "root@example.com"));

        assertTrue(repo.findByUsername("root").isPresent());
        assertTrue(repo.findByUsername("ROOT").isPresent(), "case-insensitive lookup");
        assertEquals(id, repo.findByUsername("root").orElseThrow().getId());
    }

    @Test
    public void GivenMultipleAdmins_WhenFindAll_ThenReturnsAll() {
        repo.save(new Admin(UUID.randomUUID(), "alpha", "a@x.com"));
        repo.save(new Admin(UUID.randomUUID(), "bravo", "b@x.com"));
        repo.save(new Admin(UUID.randomUUID(), "charlie", "c@x.com"));

        assertEquals(3, repo.findAll().size());
    }

    @Test
    public void GivenSavedAdmin_WhenDelete_ThenIsGone() {
        UUID id = UUID.randomUUID();
        repo.save(new Admin(id, "doomed", "d@x.com"));

        repo.delete(id);

        assertTrue(repo.findById(id).isEmpty());
        assertFalse(repo.existsByUsername("doomed"));
    }

    @Test
    public void GivenAdmin_WhenSavedTwice_ThenLatestWins() {
        UUID id = UUID.randomUUID();
        repo.save(new Admin(id, "first", "a@x.com"));
        repo.save(new Admin(id, "second", "b@x.com"));

        Admin found = repo.findById(id).orElseThrow();
        assertEquals("second", found.getUsername());
        // old username index should be cleared
        assertFalse(repo.existsByUsername("first"));
        assertTrue(repo.existsByUsername("second"));
    }

    @Test
    public void GivenUnknownId_WhenFindById_ThenEmpty() {
        assertTrue(repo.findById(UUID.randomUUID()).isEmpty());
    }

    @Test
    public void GivenNullArgs_WhenLookup_ThenSafeEmpty() {
        assertTrue(repo.findById(null).isEmpty());
        assertTrue(repo.findByUsername(null).isEmpty());
        assertFalse(repo.existsByUsername(null));
    }

    @Test
    public void GivenNullAdmin_WhenSave_ThenThrowIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> repo.save(null));
    }
}
