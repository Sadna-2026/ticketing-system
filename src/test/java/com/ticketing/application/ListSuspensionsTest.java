package com.ticketing.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.ticketing.application.auth.ISessionTokenService;
import com.ticketing.application.dto.SuspensionDTO;
import com.ticketing.application.services.AdminService;
import com.ticketing.domain.admin.IAdminRepository;
import com.ticketing.domain.member.Member;
import com.ticketing.domain.member.Suspension;
import com.ticketing.domain.order.IOrderRepository;
import com.ticketing.infrastructure.InMemoryCompanyRepository;
import com.ticketing.infrastructure.InMemoryMemberRepository;

@DisplayName("UC-II.6.9 — System admin views user suspensions")
class ListSuspensionsTest {

    private static final String ADMIN_TOKEN = "admin-token";
    private static final String USER_TOKEN = "user-token";

    private InMemoryMemberRepository memberRepository;
    private InMemoryCompanyRepository companyRepository;
    private ISessionTokenService sessionTokenService;
    private IAdminRepository adminRepository;
    private IOrderRepository orderRepository;
    private AdminService adminService;

    private UUID adminId;

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
    }

    private Member createMember(String username) {
        UUID id = UUID.randomUUID();
        Member m = new Member(id, username, username + "@example.com", "encPw");
        memberRepository.saveIfUsernameAndEmailAvailable(m);
        return m;
    }

    // ══════════════════════════════════════════════════════════════════
    //  Active suspensions
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Active suspensions")
    class ActiveSuspensions {

        @Test
        void GivenMemberWithTimedSuspension_WhenListActive_ThenReturnsOne() {
            Member target = createMember("user1");
            adminService.suspendUser(ADMIN_TOKEN, target.getId(), Duration.ofDays(7), "Spam");

            List<SuspensionDTO> result = adminService.listSuspensions(ADMIN_TOKEN, true);

            assertEquals(1, result.size());
            SuspensionDTO dto = result.get(0);
            assertEquals(target.getId(), dto.memberId());
            assertEquals("user1", dto.memberUsername());
            assertTrue(dto.active());
            assertFalse(dto.permanent());
            assertEquals("Spam", dto.reason());
        }

        @Test
        void GivenMemberWithPermanentSuspension_WhenListActive_ThenReturnsPermanentEntry() {
            Member target = createMember("user2");
            adminService.suspendUser(ADMIN_TOKEN, target.getId(), null, "Ban");

            List<SuspensionDTO> result = adminService.listSuspensions(ADMIN_TOKEN, true);

            assertEquals(1, result.size());
            SuspensionDTO dto = result.get(0);
            assertTrue(dto.permanent());
            assertTrue(dto.active());
            assertEquals(target.getId(), dto.memberId());
            assertEquals("Ban", dto.reason());
        }

        @Test
        void GivenMultipleSuspendedMembers_WhenListActive_ThenReturnsAll() {
            Member user1 = createMember("alpha");
            Member user2 = createMember("beta");
            adminService.suspendUser(ADMIN_TOKEN, user1.getId(), Duration.ofDays(3), "Reason A");
            adminService.suspendUser(ADMIN_TOKEN, user2.getId(), null, "Reason B");

            List<SuspensionDTO> result = adminService.listSuspensions(ADMIN_TOKEN, true);

            assertEquals(2, result.size());
        }

        @Test
        void GivenNoSuspensions_WhenListActive_ThenReturnsEmpty() {
            createMember("cleanUser");

            List<SuspensionDTO> result = adminService.listSuspensions(ADMIN_TOKEN, true);

            assertTrue(result.isEmpty());
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  All suspensions (including cancelled/expired)
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("All suspensions (activeOnly=false)")
    class AllSuspensions {

        @Test
        void GivenCancelledSuspension_WhenListAll_ThenIncludesCancelled() {
            Member target = createMember("user3");
            Suspension suspension = adminService.suspendUser(ADMIN_TOKEN, target.getId(),
                    Duration.ofDays(30), "Temp ban");
            adminService.cancelSuspension(ADMIN_TOKEN, target.getId(),
                    suspension.getSuspensionId());

            List<SuspensionDTO> all = adminService.listSuspensions(ADMIN_TOKEN, false);
            List<SuspensionDTO> activeOnly = adminService.listSuspensions(ADMIN_TOKEN, true);

            assertEquals(1, all.size());
            assertTrue(all.get(0).cancelled());
            assertFalse(all.get(0).active());
            assertTrue(activeOnly.isEmpty());
        }

        @Test
        void GivenMixedSuspensions_WhenListAll_ThenReturnsEverything() {
            Member target = createMember("user4");
            // First suspension: active
            adminService.suspendUser(ADMIN_TOKEN, target.getId(), null, "Permanent");
            // Second suspension: will be cancelled
            Suspension toCancel = adminService.suspendUser(ADMIN_TOKEN, target.getId(),
                    Duration.ofDays(1), "Short");
            adminService.cancelSuspension(ADMIN_TOKEN, target.getId(),
                    toCancel.getSuspensionId());

            List<SuspensionDTO> all = adminService.listSuspensions(ADMIN_TOKEN, false);
            List<SuspensionDTO> active = adminService.listSuspensions(ADMIN_TOKEN, true);

            assertEquals(2, all.size());
            assertEquals(1, active.size());
            assertTrue(active.get(0).permanent());
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  DTO content
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("DTO content verification")
    class DtoContent {

        @Test
        void GivenTimedSuspension_WhenList_ThenDtoHasCorrectFields() {
            Member target = createMember("dtoUser");
            Duration sevenDays = Duration.ofDays(7);
            adminService.suspendUser(ADMIN_TOKEN, target.getId(), sevenDays, "Testing");

            List<SuspensionDTO> result = adminService.listSuspensions(ADMIN_TOKEN, false);

            assertEquals(1, result.size());
            SuspensionDTO dto = result.get(0);
            assertEquals(target.getId(), dto.memberId());
            assertEquals("dtoUser", dto.memberUsername());
            assertEquals(adminId, dto.imposedByAdminId());
            assertEquals(sevenDays, dto.duration());
            assertFalse(dto.permanent());
            assertTrue(dto.active());
            assertFalse(dto.cancelled());
            assertEquals("Testing", dto.reason());
            // endTime should be approximately startTime + 7 days
            assertTrue(dto.endTime() != null);
            assertTrue(dto.startTime() != null);
        }

        @Test
        void GivenPermanentSuspension_WhenList_ThenEndTimeIsNull() {
            Member target = createMember("permUser");
            adminService.suspendUser(ADMIN_TOKEN, target.getId(), null, "Permanent ban");

            List<SuspensionDTO> result = adminService.listSuspensions(ADMIN_TOKEN, false);

            SuspensionDTO dto = result.get(0);
            assertTrue(dto.permanent());
            assertEquals(null, dto.endTime());
            assertEquals(null, dto.duration());
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  Authorization
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Authorization")
    class Authorization {

        @Test
        void GivenNonAdmin_WhenListSuspensions_ThenThrowsSecurityException() {
            when(sessionTokenService.isValid(USER_TOKEN)).thenReturn(true);
            when(sessionTokenService.extractPermissions(USER_TOKEN)).thenReturn(Set.of());

            assertThrows(SecurityException.class,
                    () -> adminService.listSuspensions(USER_TOKEN, false));
        }

        @Test
        void GivenInvalidToken_WhenListSuspensions_ThenThrowsSecurityException() {
            String badToken = "invalid";
            when(sessionTokenService.isValid(badToken)).thenReturn(false);

            assertThrows(SecurityException.class,
                    () -> adminService.listSuspensions(badToken, true));
        }

        @Test
        void GivenNullToken_WhenListSuspensions_ThenThrowsSecurityException() {
            assertThrows(SecurityException.class,
                    () -> adminService.listSuspensions(null, false));
        }
    }
}
