package com.ticketing.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.ticketing.domain.admin.Admin;
import com.ticketing.domain.company.Company;
import com.ticketing.domain.exception.OptimisticLockException;

@DisplayName("In-memory repositories")
class InMemoryRepositoryTest {

    @Nested
    @DisplayName("Admin repository")
    class AdminRepository {

        private InMemoryAdminRepository repo;

        @BeforeEach
        public void setUp() {
            repo = new InMemoryAdminRepository();
        }

        @Test
        public void GivenNewAdmin_WhenSave_ThenFindByIdReturnsIt() {
            UUID id = UUID.randomUUID();
            Admin a = new Admin(id, "root", "root@example.com", "encPw");

            repo.save(a);

            assertEquals(a, repo.findById(id).orElseThrow());
        }

        @Test
        public void GivenSavedAdmin_WhenFindByUsername_ThenReturnsIt() {
            UUID id = UUID.randomUUID();
            repo.save(new Admin(id, "root", "root@example.com", "encPw"));

            assertTrue(repo.findByUsername("root").isPresent());
            assertTrue(repo.findByUsername("ROOT").isPresent(), "case-insensitive lookup");
            assertEquals(id, repo.findByUsername("root").orElseThrow().getId());
        }

        @Test
        public void GivenMultipleAdmins_WhenFindAll_ThenReturnsAll() {
            repo.save(new Admin(UUID.randomUUID(), "alpha", "a@x.com", "encPw"));
            repo.save(new Admin(UUID.randomUUID(), "bravo", "b@x.com", "encPw"));
            repo.save(new Admin(UUID.randomUUID(), "charlie", "c@x.com", "encPw"));

            assertEquals(3, repo.findAll().size());
        }

        @Test
        public void GivenSavedAdmin_WhenDelete_ThenIsGone() {
            UUID id = UUID.randomUUID();
            repo.save(new Admin(id, "doomed", "d@x.com", "encPw"));

            repo.delete(id);

            assertTrue(repo.findById(id).isEmpty());
            assertFalse(repo.existsByUsername("doomed"));
        }

        @Test
        public void GivenFetchedAdmin_WhenSavedTwice_ThenLatestWins() {
            UUID id = UUID.randomUUID();
            repo.save(new Admin(id, "first", "a@x.com", "encPw"));
            Admin admin = repo.findById(id).orElseThrow();
            admin.setUsername("second");
            admin.setEmail("b@x.com");
            repo.save(admin);

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

    @Nested
    @DisplayName("Company repository")
    class CompanyRepository {

        private InMemoryCompanyRepository repository;

        @BeforeEach
        public void setUp() {
            repository = new InMemoryCompanyRepository();
        }

        // ===== CRUD Tests =====

        @Test
        public void GivenNewCompany_WhenSaveAndFindById_ThenFound() {
            Company company = new Company("TechCorp", "A tech company", java.util.UUID.randomUUID());

            repository.save(company);

            Optional<Company> found = repository.findById("TechCorp");
            assertTrue(found.isPresent());
            assertEquals("TechCorp", found.get().getName());
        }

        @Test
        public void GivenUnknownId_WhenFindById_ThenEmpty() {
            Optional<Company> found = repository.findById("NonExistent");
            assertFalse(found.isPresent());
        }

        @Test
        public void GivenNewCompany_WhenSaveAndFindByName_ThenFound() {
            Company company = new Company("TechCorp", "A tech company", java.util.UUID.randomUUID());

            repository.save(company);

            Optional<Company> found = repository.findByName("TechCorp");
            assertTrue(found.isPresent());
            assertEquals("TechCorp", found.get().getName());
        }

        @Test
        public void GivenUnknownName_WhenFindByName_ThenEmpty() {
            Optional<Company> found = repository.findByName("NonExistent");
            assertFalse(found.isPresent());
        }

        @Test
        public void GivenSavedCompany_WhenExistsById_ThenTrueForKnownFalseForUnknown() {
            Company company = new Company("TechCorp", "A tech company", java.util.UUID.randomUUID());

            repository.save(company);

            assertTrue(repository.existsById("TechCorp"));
            assertFalse(repository.existsById("NonExistent"));
        }

        @Test
        public void GivenSavedCompany_WhenExistsByName_ThenTrueForKnownFalseForUnknown() {
            Company company = new Company("TechCorp", "A tech company", java.util.UUID.randomUUID());

            repository.save(company);

            assertTrue(repository.existsByName("TechCorp"));
            assertFalse(repository.existsByName("NonExistent"));
        }

        @Test
        public void GivenMultipleCompanies_WhenGetAll_ThenAllReturned() {
            Company company1 = new Company("TechCorp", "A tech company", java.util.UUID.randomUUID());
            Company company2 = new Company("FinServ", "A financial services company", java.util.UUID.randomUUID());

            repository.save(company1);
            repository.save(company2);

            List<Company> companies = repository.getAll();
            assertEquals(2, companies.size());
        }

        @Test
        public void GivenRepository_WhenDelete_ThenUnsupportedOperationException() {
            assertThrows(UnsupportedOperationException.class, () -> {
                repository.delete("anyId");
            });
        }

        // ===== Normalization Tests =====

        @Test
        public void GivenSavedCompany_WhenFindByIdWithDifferentCase_ThenFound() {
            Company company = new Company("TechCorp", "A tech company", java.util.UUID.randomUUID());

            repository.save(company);

            // Should find with different case
            Optional<Company> found = repository.findById("techcorp");
            assertTrue(found.isPresent());

            found = repository.findById("TECHCORP");
            assertTrue(found.isPresent());

            found = repository.findById("TeChCoRp");
            assertTrue(found.isPresent());
        }

        @Test
        public void GivenSavedCompany_WhenFindByIdWithWhitespace_ThenFound() {
            Company company = new Company("TechCorp", "A tech company", java.util.UUID.randomUUID());

            repository.save(company);

            // Should find with leading/trailing whitespace
            Optional<Company> found = repository.findById("  TechCorp  ");
            assertTrue(found.isPresent());
        }

        @Test
        public void GivenSavedCompany_WhenFindByIdWithCaseAndWhitespace_ThenFound() {
            Company company = new Company("TechCorp", "A tech company", java.util.UUID.randomUUID());

            repository.save(company);

            // Should find with case difference and whitespace
            Optional<Company> found = repository.findById("  TECHCORP  ");
            assertTrue(found.isPresent());
        }

        @Test
        public void GivenSavedCompany_WhenUpdateDescription_ThenPersisted() {
            Company company = new Company("TechCorp", "Old description", java.util.UUID.randomUUID());

            repository.save(company);
            company.setDescription("New description");
            repository.save(company);

            Optional<Company> found = repository.findById("TechCorp");
            assertTrue(found.isPresent());
            assertEquals("New description", found.get().getDescription());
        }

        // ===== Thread Safety Tests =====

        @Test
        public void GivenConcurrentThreads_WhenSaveDifferentCompanies_ThenAllPersisted() throws InterruptedException {
            int threadCount = 10;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);

            for (int i = 0; i < threadCount; i++) {
                final int threadId = i;
                executor.submit(() -> {
                    try {
                        Company company = new Company("Company" + threadId, "Description " + threadId, java.util.UUID.randomUUID());
                        repository.save(company);
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await();
            executor.shutdown();

            // All 10 companies should be saved
            List<Company> companies = repository.getAll();
            assertEquals(10, companies.size());
        }

        @Test
        public void GivenSavedCompany_WhenConcurrentReads_ThenAllFindCompany() throws InterruptedException {
            Company company = new Company("TechCorp", "A tech company", java.util.UUID.randomUUID());
            repository.save(company);

            int threadCount = 10;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger foundCount = new AtomicInteger(0);

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        Optional<Company> found = repository.findById("TechCorp");
                        if (found.isPresent()) {
                            foundCount.incrementAndGet();
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await();
            executor.shutdown();

            // All threads should find the company
            assertEquals(10, foundCount.get());
        }

        @Test
        public void GivenConcurrentReadersAndWriters_WhenMixedOperations_ThenConsistentState() throws InterruptedException {
            int threadCount = 20;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);

            // Save some initial companies
            for (int i = 0; i < 5; i++) {
                Company company = new Company("Company" + i, "Description " + i, java.util.UUID.randomUUID());
                repository.save(company);
            }

            // Half threads read, half write
            for (int i = 0; i < threadCount; i++) {
                final int threadId = i;
                executor.submit(() -> {
                    try {
                        if (threadId % 2 == 0) {
                            // Read operation
                            repository.findById("Company0");
                        } else {
                            // Write operation
                            Company company = new Company("Company" + (5 + threadId), "Description " + threadId, java.util.UUID.randomUUID());
                            repository.save(company);
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await();
            executor.shutdown();

            // Should have initial 5 + added companies
            List<Company> companies = repository.getAll();
            assertNotNull(companies);
            assertTrue(companies.size() >= 5);
        }

        @Test
        public void GivenConcurrentThreads_WhenDuplicateInsertSameName_ThenOnlyOnePersists()
                throws InterruptedException {
            int threadCount = 10;
            String companyName = "DuplicateCorp";
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger duplicateFailures = new AtomicInteger(0);

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        Company company = new Company(
                                companyName, "desc", UUID.randomUUID());
                        repository.save(company);
                        successCount.incrementAndGet();
                    } catch (IllegalArgumentException ex) {
                        if (ex.getMessage() != null
                                && ex.getMessage().contains("already exists")) {
                            duplicateFailures.incrementAndGet();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            doneLatch.await();
            executor.shutdown();

            assertEquals(1, successCount.get());
            assertEquals(threadCount - 1, duplicateFailures.get());
            assertEquals(1, repository.getAll().size());
        }

        @Test
        public void GivenTwoDetachedSnapshots_WhenSecondSaveIsStale_ThenOptimisticLockException() {
            Company company = new Company("TechCorp", "desc", UUID.randomUUID());
            repository.save(company);

            Company firstSnapshot = repository.findByName("TechCorp").orElseThrow();
            Company secondSnapshot = repository.findByName("TechCorp").orElseThrow();

            firstSnapshot.setDescription("First update");
            repository.save(firstSnapshot);

            secondSnapshot.setDescription("Stale update");
            assertThrows(OptimisticLockException.class, () -> repository.save(secondSnapshot));
        }

        @Test
        public void GivenSavedCompany_WhenMutateReturnedCopyWithoutSave_ThenStoredStateUnchanged() {
            Company company = new Company("TechCorp", "desc", UUID.randomUUID());
            repository.save(company);

            Company copy = repository.findByName("TechCorp").orElseThrow();
            copy.setDescription("Mutated outside save");

            Company stored = repository.findByName("TechCorp").orElseThrow();
            assertEquals("desc", stored.getDescription());
        }
    }
}
