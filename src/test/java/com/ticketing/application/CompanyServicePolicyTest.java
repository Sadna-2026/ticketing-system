package com.ticketing.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.ticketing.application.auth.ISessionTokenService;
import com.ticketing.application.services.CompanyService;
import com.ticketing.domain.company.Company;
import com.ticketing.domain.company.ICompanyRepository;
import com.ticketing.domain.event.AlwaysAllowPolicy;
import com.ticketing.domain.event.IDiscountPolicy;
import com.ticketing.domain.event.IEventPublisher;
import com.ticketing.domain.event.IPurchasePolicy;
import com.ticketing.domain.event.NoDiscountPolicy;
import com.ticketing.domain.event.PolicyResult;
import com.ticketing.domain.event.PurchaseContext;
import com.ticketing.domain.member.IMemberRepository;
import com.ticketing.domain.member.ManagerPermission;
import com.ticketing.domain.member.Member;
import com.ticketing.domain.member.StaffAppointment;
import com.ticketing.infrastructure.InMemoryCompanyRepository;
import com.ticketing.infrastructure.InMemoryEventPublisher;
import com.ticketing.infrastructure.InMemoryMemberRepository;

@DisplayName("CompanyService — Policy management")
class CompanyServicePolicyTest {

    private static final String COMPANY_NAME = "Acme Productions";
    private static final String VALID_TOKEN = "valid-token";

    private InMemoryCompanyRepository companyRepository;
    private InMemoryMemberRepository memberRepository;
    private ISessionTokenService sessionTokenService;
    private CompanyService companyService;

    private UUID memberId;
    private Member member;
    private Company company;

    @BeforeEach
    void setUp() {
        companyRepository = new InMemoryCompanyRepository();
        memberRepository = new InMemoryMemberRepository();
        sessionTokenService = mock(ISessionTokenService.class);

        companyService = new CompanyService(
                companyRepository, mock(IEventPublisher.class),
                sessionTokenService, memberRepository);

        memberId = UUID.randomUUID();
        member = new Member(memberId, "policyAdmin", "admin@example.com", "encryptedPw");
        memberRepository.save(member);

        company = new Company(COMPANY_NAME, "desc", memberId);
        companyRepository.save(company);

        when(sessionTokenService.isValid(VALID_TOKEN)).thenReturn(true);
        when(sessionTokenService.extractMemberId(VALID_TOKEN)).thenReturn(memberId);
    }

    // ── helpers ──────────────────────────────────────────────────────

    private void appointAs(StaffAppointment.StaffRole role, Set<ManagerPermission> perms) {
        StaffAppointment appt = new StaffAppointment(COMPANY_NAME, memberId, role, perms);
        member.addStaffAppointment(COMPANY_NAME, appt);
        memberRepository.save(member);
    }

    /** Custom purchase policy for testing — rejects everything. */
    private static IPurchasePolicy rejectAllPolicy() {
        return (ctx) -> PolicyResult.failure("REJECTED", "test rejection");
    }

    /** Custom discount policy for testing — 50% off. */
    private static IDiscountPolicy halfPricePolicy() {
        return (order, couponCode, clock) -> {
            BigDecimal total = order.getTotalPrice();
            return total.divide(BigDecimal.valueOf(2), 2, java.math.RoundingMode.HALF_UP);
        };
    }

    // ══════════════════════════════════════════════════════════════════
    //  Company purchase policy
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Company purchase policy")
    class CompanyPurchasePolicyTests {

        @Test
        void GivenOwner_WhenSetCompanyPurchasePolicy_ThenPolicyUpdated() {
            appointAs(StaffAppointment.StaffRole.OWNER, Set.of());

            companyService.setCompanyPurchasePolicy(VALID_TOKEN, COMPANY_NAME, rejectAllPolicy());

            Company saved = companyRepository.findByName(COMPANY_NAME).orElseThrow();
            PolicyResult result = saved.getPurchasePolicy().isAllowed(new PurchaseContext(null, null, null));
            assertEquals("REJECTED", result.errorCode());
        }

        @Test
        void GivenManagerWithPolicyMod_WhenSetCompanyPurchasePolicy_ThenSuccess() {
            appointAs(StaffAppointment.StaffRole.MANAGER,
                    Set.of(ManagerPermission.POLICY_MODIFICATION));

            companyService.setCompanyPurchasePolicy(VALID_TOKEN, COMPANY_NAME, rejectAllPolicy());

            Company saved = companyRepository.findByName(COMPANY_NAME).orElseThrow();
            PolicyResult result = saved.getPurchasePolicy().isAllowed(new PurchaseContext(null, null, null));
            assertEquals("REJECTED", result.errorCode());
        }

        @Test
        void GivenManagerWithoutPolicyMod_WhenSetCompanyPurchasePolicy_ThenDenied() {
            appointAs(StaffAppointment.StaffRole.MANAGER,
                    Set.of(ManagerPermission.INVENTORY_MGMT));

            assertThrows(SecurityException.class,
                    () -> companyService.setCompanyPurchasePolicy(VALID_TOKEN, COMPANY_NAME, rejectAllPolicy()));
        }

        @Test
        void GivenOwner_WhenRemoveCompanyPurchasePolicy_ThenResetToDefault() {
            appointAs(StaffAppointment.StaffRole.OWNER, Set.of());

            companyService.setCompanyPurchasePolicy(VALID_TOKEN, COMPANY_NAME, rejectAllPolicy());
            companyService.removeCompanyPurchasePolicy(VALID_TOKEN, COMPANY_NAME);

            Company saved = companyRepository.findByName(COMPANY_NAME).orElseThrow();
            assertTrue(saved.getPurchasePolicy() instanceof AlwaysAllowPolicy);
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  Company discount policy
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Company discount policy")
    class CompanyDiscountPolicyTests {

        @Test
        void GivenOwner_WhenSetCompanyDiscountPolicy_ThenPolicyUpdated() {
            appointAs(StaffAppointment.StaffRole.OWNER, Set.of());

            companyService.setCompanyDiscountPolicy(VALID_TOKEN, COMPANY_NAME, halfPricePolicy());

            Company saved = companyRepository.findByName(COMPANY_NAME).orElseThrow();
            assertTrue(!(saved.getDiscountPolicy() instanceof NoDiscountPolicy));
        }

        @Test
        void GivenOwner_WhenRemoveCompanyDiscountPolicy_ThenResetToDefault() {
            appointAs(StaffAppointment.StaffRole.OWNER, Set.of());

            companyService.setCompanyDiscountPolicy(VALID_TOKEN, COMPANY_NAME, halfPricePolicy());
            companyService.removeCompanyDiscountPolicy(VALID_TOKEN, COMPANY_NAME);

            Company saved = companyRepository.findByName(COMPANY_NAME).orElseThrow();
            assertTrue(saved.getDiscountPolicy() instanceof NoDiscountPolicy);
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  Authorization & validation
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Authorization and validation")
    class AuthorizationAndValidation {

        @Test
        void GivenGuestToken_WhenSetCompanyPolicy_ThenThrowsSecurityException() {
            String guestToken = "guest-token";
            when(sessionTokenService.isValid(guestToken)).thenReturn(true);
            when(sessionTokenService.extractMemberId(guestToken)).thenReturn(null);

            assertThrows(SecurityException.class,
                    () -> companyService.setCompanyPurchasePolicy(guestToken, COMPANY_NAME, rejectAllPolicy()));
        }

        @Test
        void GivenInvalidToken_WhenSetCompanyPolicy_ThenThrowsIllegalArgument() {
            String badToken = "bad-token";
            when(sessionTokenService.isValid(badToken)).thenReturn(false);

            assertThrows(IllegalArgumentException.class,
                    () -> companyService.setCompanyPurchasePolicy(badToken, COMPANY_NAME, rejectAllPolicy()));
        }

        @Test
        void GivenSuspendedCompany_WhenSetCompanyPolicy_ThenThrowsIllegalState() {
            appointAs(StaffAppointment.StaffRole.OWNER, Set.of());

            Company c = companyRepository.findByName(COMPANY_NAME).orElseThrow();
            c.suspend();
            companyRepository.save(c);

            assertThrows(IllegalStateException.class,
                    () -> companyService.setCompanyPurchasePolicy(VALID_TOKEN, COMPANY_NAME, rejectAllPolicy()));
        }

        @Test
        void GivenStaffOfOtherCompany_WhenSetCompanyPolicy_ThenThrowsSecurityException() {
            StaffAppointment otherAppt = new StaffAppointment(
                    "Other Co", memberId, StaffAppointment.StaffRole.OWNER, Set.of());
            member.addStaffAppointment("other co", otherAppt);
            memberRepository.save(member);

            assertThrows(SecurityException.class,
                    () -> companyService.setCompanyPurchasePolicy(VALID_TOKEN, COMPANY_NAME, rejectAllPolicy()));
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  Policy retrieval
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Policy retrieval")
    class PolicyRetrieval {

        @Test
        void GivenCompanyWithDefaultPolicy_WhenGetPurchasePolicy_ThenReturnsDefault() {
            IPurchasePolicy policy = companyService.getCompanyPurchasePolicy(VALID_TOKEN, COMPANY_NAME);

            assertTrue(policy instanceof AlwaysAllowPolicy);
        }

        @Test
        void GivenCompanyWithCustomPolicy_WhenGetDiscountPolicy_ThenReturnsCustom() {
            appointAs(StaffAppointment.StaffRole.OWNER, Set.of());

            companyService.setCompanyDiscountPolicy(VALID_TOKEN, COMPANY_NAME, halfPricePolicy());

            IDiscountPolicy policy = companyService.getCompanyDiscountPolicy(VALID_TOKEN, COMPANY_NAME);
            assertTrue(!(policy instanceof NoDiscountPolicy));
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  Discount stacking
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Discount stacking")
    class DiscountStacking {

        @Test
        void GivenNewCompany_WhenCheckStacking_ThenDefaultIsFalse() {
            assertFalse(companyService.isDiscountStackingAllowed(VALID_TOKEN, COMPANY_NAME));
        }

        @Test
        void GivenOwner_WhenEnableStacking_ThenStackingIsTrue() {
            appointAs(StaffAppointment.StaffRole.OWNER, Set.of());

            companyService.setDiscountStacking(VALID_TOKEN, COMPANY_NAME, true);

            assertTrue(companyService.isDiscountStackingAllowed(VALID_TOKEN, COMPANY_NAME));
        }

        @Test
        void GivenOwner_WhenDisableStacking_ThenStackingIsFalse() {
            appointAs(StaffAppointment.StaffRole.OWNER, Set.of());

            companyService.setDiscountStacking(VALID_TOKEN, COMPANY_NAME, true);
            companyService.setDiscountStacking(VALID_TOKEN, COMPANY_NAME, false);

            assertFalse(companyService.isDiscountStackingAllowed(VALID_TOKEN, COMPANY_NAME));
        }

        @Test
        void GivenManagerWithPolicyMod_WhenSetStacking_ThenSuccess() {
            appointAs(StaffAppointment.StaffRole.MANAGER,
                    Set.of(ManagerPermission.POLICY_MODIFICATION));

            companyService.setDiscountStacking(VALID_TOKEN, COMPANY_NAME, true);

            assertTrue(companyService.isDiscountStackingAllowed(VALID_TOKEN, COMPANY_NAME));
        }

        @Test
        void GivenManagerWithoutPolicyMod_WhenSetStacking_ThenDenied() {
            appointAs(StaffAppointment.StaffRole.MANAGER,
                    Set.of(ManagerPermission.INVENTORY_MGMT));

            assertThrows(SecurityException.class,
                    () -> companyService.setDiscountStacking(VALID_TOKEN, COMPANY_NAME, true));
        }
    }
}
