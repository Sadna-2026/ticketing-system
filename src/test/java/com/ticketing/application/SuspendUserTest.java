package com.ticketing.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.ticketing.application.auth.ISessionTokenService;
import com.ticketing.application.services.AdminService;
import com.ticketing.domain.admin.Admin;
import com.ticketing.domain.admin.IAdminRepository;
import com.ticketing.domain.member.Member;
import com.ticketing.domain.member.Suspension;
import com.ticketing.domain.order.IOrderRepository;
import com.ticketing.infrastructure.InMemoryCompanyRepository;
import com.ticketing.infrastructure.InMemoryMemberRepository;

@DisplayName("UC-II.6.7 — System admin suspends a user")
class SuspendUserTest {

    private static final String ADMIN_TOKEN = "admin-token";
    private static final String USER_TOKEN = "user-token";

    private InMemoryMemberRepository memberRepository;
    private InMemoryCompanyRepository companyRepository;
    private ISessionTokenService sessionTokenService;
    private IAdminRepository adminRepository;
    private IOrderRepository orderRepository;
    private AdminService adminService;

    private UUID adminId;
    private UUID targetId;
    private Member target;

    @BeforeEach
    void setUp() {
        memberRepository = new InMemoryMemberRepository();
        companyRepository = new InMemoryCompanyRepository();
        sessionTokenService = mock(ISessionTokenService.class);
        adminRepository = mock(IAdminRepository.class);
        orderRepository = mock(IOrderRepository.class);

        adminService = new AdminService(memberRepository, companyRepository,
                sessionTokenService, adminRepository, orderRepository);

        adminId = UUID.randomUUID();
        when(sessionTokenService.isValid(ADMIN_TOKEN)).thenReturn(true);
        when(sessionTokenService.extractPermissions(ADMIN_TOKEN)).thenReturn(Set.of("SYSTEM_ADMIN"));
        when(sessionTokenService.extractMemberId(ADMIN_TOKEN)).thenReturn(adminId);

        targetId = UUID.randomUUID();
        target = new Member(targetId, "targetUser", "target@example.com", "encPw");
        memberRepository.saveIfUsernameAndEmailAvailable(target);
    }

    // ══════════════════════════════════════════════════════════════════
    //  Timed suspension
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Timed suspension")
    class TimedSuspension {

        @Test
        void GivenAdmin_WhenSuspendUserWithDuration_ThenSuspensionCreated() {
            Duration sevenDays = Duration.ofDays(7);

            Suspension result = adminService.suspendUser(ADMIN_TOKEN, targetId, sevenDays, "Spam");

            assertNotNull(result);
            assertNotNull(result.getSuspensionId());
            assertFalse(result.isPermanent());
            assertEquals(sevenDays, result.getDuration());
            assertEquals("Spam", result.getReason());
        }

        @Test
        void GivenTimedSuspension_WhenCheckMember_ThenMemberIsSuspended() {
            adminService.suspendUser(ADMIN_TOKEN, targetId, Duration.ofDays(7), "Policy violation");

            Member saved = memberRepository.findById(targetId).orElseThrow();
            assertTrue(saved.isSuspended(Instant.now()));
        }

        @Test
        void GivenTimedSuspension_WhenCheckAfterExpiry_ThenNoLongerSuspended() {
            // Create a suspension that started 2 days ago, lasting 1 day (already expired)
            Member m = memberRepository.findById(targetId).orElseThrow();
            Suspension expired = new Suspension(adminId,
                    Instant.now().minus(2, ChronoUnit.DAYS),
                    Duration.ofDays(1), "Short ban");
            m.addSuspension(expired);
            memberRepository.save(m);

            Member saved = memberRepository.findById(targetId).orElseThrow();
            assertFalse(saved.isSuspended(Instant.now()));
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  Permanent suspension
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Permanent suspension")
    class PermanentSuspension {

        @Test
        void GivenAdmin_WhenSuspendUserPermanently_ThenSuspensionIsPermanent() {
            Suspension result = adminService.suspendUser(ADMIN_TOKEN, targetId, null, "Fraud");

            assertTrue(result.isPermanent());
            assertTrue(result.isActive(Instant.now()));
            assertNotNull(result.getStartTime());
        }

        @Test
        void GivenPermanentSuspension_WhenCheckFarFuture_ThenStillSuspended() {
            adminService.suspendUser(ADMIN_TOKEN, targetId, null, "Fraud");

            Member saved = memberRepository.findById(targetId).orElseThrow();
            Instant farFuture = Instant.now().plus(365 * 10, ChronoUnit.DAYS);
            assertTrue(saved.isSuspended(farFuture));
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  Permitted views (read-only access for suspended users)
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Suspended user read access")
    class SuspendedReadAccess {

        @Test
        void GivenSuspendedMember_WhenAccessReadOnlyFields_ThenAllowed() {
            adminService.suspendUser(ADMIN_TOKEN, targetId, Duration.ofDays(7), "Temp ban");

            Member saved = memberRepository.findById(targetId).orElseThrow();
            // Read operations should not throw
            assertNotNull(saved.getUsername());
            assertNotNull(saved.getEmail());
            assertNotNull(saved.getId());
            assertNotNull(saved.getSuspensions());
        }

        @Test
        void GivenSuspendedMember_WhenRejectIfSuspended_ThenThrowsWithMessage() {
            adminService.suspendUser(ADMIN_TOKEN, targetId, Duration.ofDays(7), "Temp ban");

            Member saved = memberRepository.findById(targetId).orElseThrow();
            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> saved.rejectIfSuspended(Instant.now()));
            assertTrue(ex.getMessage().contains("suspended"));
            assertTrue(ex.getMessage().contains("Temp ban"));
        }

        @Test
        void GivenPermanentSuspension_WhenRejectIfSuspended_ThenMessageSaysPermanent() {
            adminService.suspendUser(ADMIN_TOKEN, targetId, null, "Fraud");

            Member saved = memberRepository.findById(targetId).orElseThrow();
            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> saved.rejectIfSuspended(Instant.now()));
            assertTrue(ex.getMessage().contains("permanently suspended"));
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  Authorization
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Permission checks")
    class PermissionChecks {

        @Test
        void GivenNonAdmin_WhenSuspendUser_ThenThrowsSecurityException() {
            when(sessionTokenService.isValid(USER_TOKEN)).thenReturn(true);
            when(sessionTokenService.extractPermissions(USER_TOKEN)).thenReturn(Collections.emptySet());

            SecurityException ex = assertThrows(SecurityException.class,
                    () -> adminService.suspendUser(USER_TOKEN, targetId, Duration.ofDays(1), "test"));
            assertTrue(ex.getMessage().contains("System admin permission required"));
        }

        @Test
        void GivenInvalidToken_WhenSuspendUser_ThenThrowsSecurityException() {
            String badToken = "bad";
            when(sessionTokenService.isValid(badToken)).thenReturn(false);

            assertThrows(SecurityException.class,
                    () -> adminService.suspendUser(badToken, targetId, Duration.ofDays(1), "test"));
        }

        @Test
        void GivenAdmin_WhenSuspendNonexistentUser_ThenThrowsIllegalArgument() {
            UUID ghostId = UUID.randomUUID();

            assertThrows(IllegalArgumentException.class,
                    () -> adminService.suspendUser(ADMIN_TOKEN, ghostId, Duration.ofDays(1), "test"));
        }

        @Test
        void GivenSoleAdmin_WhenSuspendSelf_ThenThrowsIllegalState() {
            Admin soleAdmin = new Admin(targetId, "targetUser", "target@example.com", "encPw");
            when(adminRepository.findById(targetId)).thenReturn(Optional.of(soleAdmin));
            when(adminRepository.findAll()).thenReturn(java.util.List.of(soleAdmin));

            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> adminService.suspendUser(ADMIN_TOKEN, targetId, null, "test"));
            assertTrue(ex.getMessage().contains("Cannot suspend the last system admin"));
        }

        @Test
        void GivenNullTargetId_WhenSuspendUser_ThenThrowsIllegalArgument() {
            assertThrows(IllegalArgumentException.class,
                    () -> adminService.suspendUser(ADMIN_TOKEN, null, Duration.ofDays(1), "test"));
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  Domain-level enforcement
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Domain enforcement")
    class DomainEnforcement {

        @Test
        void GivenActiveSuspension_WhenRejectIfSuspended_ThenBlocked() {
            Member m = memberRepository.findById(targetId).orElseThrow();
            m.addSuspension(new Suspension(adminId, Instant.now(), Duration.ofDays(7), "test"));
            memberRepository.save(m);

            Member saved = memberRepository.findById(targetId).orElseThrow();
            assertThrows(IllegalStateException.class,
                    () -> saved.rejectIfSuspended(Instant.now()));
        }

        @Test
        void GivenExpiredSuspension_WhenRejectIfSuspended_ThenAllowed() {
            Member m = memberRepository.findById(targetId).orElseThrow();
            Suspension expired = new Suspension(adminId,
                    Instant.now().minus(2, ChronoUnit.DAYS),
                    Duration.ofDays(1), "expired");
            m.addSuspension(expired);
            memberRepository.save(m);

            Member saved = memberRepository.findById(targetId).orElseThrow();
            // Should not throw — suspension expired
            saved.rejectIfSuspended(Instant.now());
        }

        @Test
        void GivenCancelledSuspension_WhenRejectIfSuspended_ThenAllowed() {
            Member m = memberRepository.findById(targetId).orElseThrow();
            Suspension s = new Suspension(adminId, Instant.now(), Duration.ofDays(7), "cancelled");
            s.cancel();
            m.addSuspension(s);
            memberRepository.save(m);

            Member saved = memberRepository.findById(targetId).orElseThrow();
            // Should not throw — suspension was cancelled
            saved.rejectIfSuspended(Instant.now());
        }

        @Test
        void GivenSuspensionHistory_WhenGetSuspensions_ThenAllReturned() {
            adminService.suspendUser(ADMIN_TOKEN, targetId, Duration.ofDays(1), "first");
            adminService.suspendUser(ADMIN_TOKEN, targetId, Duration.ofDays(7), "second");

            Member saved = memberRepository.findById(targetId).orElseThrow();
            assertEquals(2, saved.getSuspensions().size());
        }
    }
}
