package com.ticketing.application;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.ticketing.application.auth.ISessionTokenService;
import com.ticketing.application.services.AdminService;
import com.ticketing.domain.admin.IAdminRepository;
import com.ticketing.domain.member.Member;
import com.ticketing.domain.member.Suspension;
import com.ticketing.domain.order.IOrderRepository;
import com.ticketing.infrastructure.InMemoryCompanyRepository;
import com.ticketing.infrastructure.InMemoryMemberRepository;

@DisplayName("UC-II.6.8 — System admin cancels a user suspension")
class CancelSuspensionTest {

    private static final String ADMIN_TOKEN = "admin-token";
    private static final String USER_TOKEN = "user-token";

    private InMemoryMemberRepository memberRepository;
    private ISessionTokenService sessionTokenService;
    private IAdminRepository adminRepository;
    private AdminService adminService;

    private UUID adminId;
    private UUID targetId;

    @BeforeEach
    void setUp() {
        memberRepository = new InMemoryMemberRepository();
        InMemoryCompanyRepository companyRepository = new InMemoryCompanyRepository();
        sessionTokenService = mock(ISessionTokenService.class);
        adminRepository = mock(IAdminRepository.class);
        IOrderRepository orderRepository = mock(IOrderRepository.class);

        adminService = new AdminService(memberRepository, companyRepository,
                sessionTokenService, adminRepository, orderRepository);

        adminId = UUID.randomUUID();
        when(sessionTokenService.isValid(ADMIN_TOKEN)).thenReturn(true);
        when(sessionTokenService.extractPermissions(ADMIN_TOKEN)).thenReturn(Set.of("SYSTEM_ADMIN"));
        when(sessionTokenService.extractMemberId(ADMIN_TOKEN)).thenReturn(adminId);

        targetId = UUID.randomUUID();
        Member target = new Member(targetId, "targetUser", "target@example.com", "encPw");
        memberRepository.saveIfUsernameAndEmailAvailable(target);
    }

    // ══════════════════════════════════════════════════════════════════
    //  Cancel timed suspension
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Cancel timed suspension")
    class CancelTimedSuspension {

        @Test
        void GivenTimedSuspension_WhenAdminCancels_ThenSuspensionCancelled() {
            Suspension suspension = adminService.suspendUser(
                    ADMIN_TOKEN, targetId, Duration.ofDays(7), "Temp ban");
            UUID suspensionId = suspension.getSuspensionId();

            adminService.cancelSuspension(ADMIN_TOKEN, targetId, suspensionId);

            Member saved = memberRepository.findById(targetId).orElseThrow();
            assertFalse(saved.isSuspended(Instant.now()));
        }

        @Test
        void GivenTimedSuspension_WhenCancelled_ThenUserCanActAgain() {
            Suspension suspension = adminService.suspendUser(
                    ADMIN_TOKEN, targetId, Duration.ofDays(7), "Temp ban");

            adminService.cancelSuspension(ADMIN_TOKEN, targetId, suspension.getSuspensionId());

            Member saved = memberRepository.findById(targetId).orElseThrow();
            assertDoesNotThrow(() -> saved.rejectIfSuspended(Instant.now()));
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  Cancel permanent suspension
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Cancel permanent suspension")
    class CancelPermanentSuspension {

        @Test
        void GivenPermanentSuspension_WhenAdminCancels_ThenSuspensionLifted() {
            Suspension suspension = adminService.suspendUser(
                    ADMIN_TOKEN, targetId, null, "Fraud");

            adminService.cancelSuspension(ADMIN_TOKEN, targetId, suspension.getSuspensionId());

            Member saved = memberRepository.findById(targetId).orElseThrow();
            assertFalse(saved.isSuspended(Instant.now()));
        }

        @Test
        void GivenPermanentSuspension_WhenCancelled_ThenNotSuspendedInFarFuture() {
            Suspension suspension = adminService.suspendUser(
                    ADMIN_TOKEN, targetId, null, "Fraud");

            adminService.cancelSuspension(ADMIN_TOKEN, targetId, suspension.getSuspensionId());

            Member saved = memberRepository.findById(targetId).orElseThrow();
            Instant farFuture = Instant.now().plus(365 * 10, ChronoUnit.DAYS);
            assertFalse(saved.isSuspended(farFuture));
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  Multiple suspensions — cancel one
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Selective cancellation")
    class SelectiveCancellation {

        @Test
        void GivenTwoSuspensions_WhenCancelOne_ThenOtherRemains() {
            Suspension first = adminService.suspendUser(
                    ADMIN_TOKEN, targetId, Duration.ofDays(3), "Warning");
            Suspension second = adminService.suspendUser(
                    ADMIN_TOKEN, targetId, Duration.ofDays(30), "Serious");

            adminService.cancelSuspension(ADMIN_TOKEN, targetId, first.getSuspensionId());

            Member saved = memberRepository.findById(targetId).orElseThrow();
            // Still suspended because second is still active
            assertTrue(saved.isSuspended(Instant.now()));
        }

        @Test
        void GivenTwoSuspensions_WhenCancelBoth_ThenFullyRestored() {
            Suspension first = adminService.suspendUser(
                    ADMIN_TOKEN, targetId, Duration.ofDays(3), "Warning");
            Suspension second = adminService.suspendUser(
                    ADMIN_TOKEN, targetId, Duration.ofDays(30), "Serious");

            adminService.cancelSuspension(ADMIN_TOKEN, targetId, first.getSuspensionId());
            adminService.cancelSuspension(ADMIN_TOKEN, targetId, second.getSuspensionId());

            Member saved = memberRepository.findById(targetId).orElseThrow();
            assertFalse(saved.isSuspended(Instant.now()));
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  Authorization
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Permission checks")
    class PermissionChecks {

        @Test
        void GivenNonAdmin_WhenCancelSuspension_ThenThrowsSecurityException() {
            Suspension suspension = adminService.suspendUser(
                    ADMIN_TOKEN, targetId, Duration.ofDays(7), "Ban");

            when(sessionTokenService.isValid(USER_TOKEN)).thenReturn(true);
            when(sessionTokenService.extractPermissions(USER_TOKEN)).thenReturn(Collections.emptySet());

            assertThrows(SecurityException.class,
                    () -> adminService.cancelSuspension(USER_TOKEN, targetId, suspension.getSuspensionId()));
        }

        @Test
        void GivenInvalidToken_WhenCancelSuspension_ThenThrowsSecurityException() {
            Suspension suspension = adminService.suspendUser(
                    ADMIN_TOKEN, targetId, Duration.ofDays(7), "Ban");

            String badToken = "bad-token";
            when(sessionTokenService.isValid(badToken)).thenReturn(false);

            assertThrows(SecurityException.class,
                    () -> adminService.cancelSuspension(badToken, targetId, suspension.getSuspensionId()));
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  Validation errors
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Validation")
    class Validation {

        @Test
        void GivenAdmin_WhenCancelWithNullTargetId_ThenThrowsIllegalArgument() {
            assertThrows(IllegalArgumentException.class,
                    () -> adminService.cancelSuspension(ADMIN_TOKEN, null, UUID.randomUUID()));
        }

        @Test
        void GivenAdmin_WhenCancelWithNullSuspensionId_ThenThrowsIllegalArgument() {
            assertThrows(IllegalArgumentException.class,
                    () -> adminService.cancelSuspension(ADMIN_TOKEN, targetId, null));
        }

        @Test
        void GivenAdmin_WhenCancelForNonexistentMember_ThenThrowsIllegalArgument() {
            UUID ghostId = UUID.randomUUID();
            assertThrows(IllegalArgumentException.class,
                    () -> adminService.cancelSuspension(ADMIN_TOKEN, ghostId, UUID.randomUUID()));
        }

        @Test
        void GivenAdmin_WhenCancelNonexistentSuspension_ThenThrowsIllegalArgument() {
            UUID fakeSuspensionId = UUID.randomUUID();
            assertThrows(IllegalArgumentException.class,
                    () -> adminService.cancelSuspension(ADMIN_TOKEN, targetId, fakeSuspensionId));
        }

        @Test
        void GivenAlreadyCancelled_WhenCancelAgain_ThenThrowsIllegalState() {
            Suspension suspension = adminService.suspendUser(
                    ADMIN_TOKEN, targetId, Duration.ofDays(7), "Ban");
            adminService.cancelSuspension(ADMIN_TOKEN, targetId, suspension.getSuspensionId());

            // Trying to cancel the same suspension again — it's no longer active
            assertThrows(IllegalStateException.class,
                    () -> adminService.cancelSuspension(ADMIN_TOKEN, targetId, suspension.getSuspensionId()));
        }

        @Test
        void GivenExpiredSuspension_WhenCancel_ThenThrowsIllegalState() {
            // Manually add an already-expired suspension
            Member m = memberRepository.findById(targetId).orElseThrow();
            Suspension expired = new Suspension(adminId,
                    Instant.now().minus(2, ChronoUnit.DAYS),
                    Duration.ofDays(1), "Old ban");
            m.addSuspension(expired);
            memberRepository.save(m);

            assertThrows(IllegalStateException.class,
                    () -> adminService.cancelSuspension(ADMIN_TOKEN, targetId, expired.getSuspensionId()));
        }
    }
}
