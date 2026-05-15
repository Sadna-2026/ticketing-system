package com.ticketing.domain.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.ticketing.infrastructure.Interface.IPurchasePolicy;

@DisplayName("Event policy")
class EventPolicyTest {

    @Nested
    @DisplayName("Defaults")
    class Defaults {

        @Test
        public void GivenNewEvent_WhenInspectPolicies_ThenDefaultsAreWired() {
            Event e = newEvent();

            assertNotNull(e.getPurchasePolicy(), "every Event must have a purchase policy by default");
            assertNotNull(e.getDiscountPolicy(), "every Event must have a discount policy by default");
            assertTrue(e.getPurchasePolicy() instanceof AlwaysAllowPolicy,
                    "default purchase policy must be AlwaysAllowPolicy");
            assertTrue(e.getDiscountPolicy() instanceof NoDiscountPolicy,
                    "default discount policy must be NoDiscountPolicy");
        }

        @Test
        public void GivenDefaultCurrencyConstant_WhenInspect_ThenIsExposedAtClassLevel() {
            // The currency choice for the no-discount default lives on Event as a
            // constant rather than being buried in the constructor. This test pins
            // the constant's existence + visibility so the override path is clear.
            assertNotNull(Event.DEFAULT_CURRENCY, "Event.DEFAULT_CURRENCY must be defined");
        }

        @Test
        public void GivenEvent_WhenInspectClass_ThenNoPolicySetterExists() throws Exception {
            // Pin the V1 invariant: there must be no public mutator for the policies
            // on Event. If someone adds setPurchasePolicy/setDiscountPolicy in V1 by
            // mistake, this test fails — flagging that the deferral was broken.
            for (var m : Event.class.getMethods()) {
                String name = m.getName().toLowerCase();
                assertTrue(!name.equals("setpurchasepolicy"),
                        "Event must not expose setPurchasePolicy in V1");
                assertTrue(!name.equals("setdiscountpolicy"),
                        "Event must not expose setDiscountPolicy in V1");
            }
        }

        // --- V0 acceptance tests for UC-C.2, deferred to V2 per V1 spec ---

        @Test
        @Disabled("V1 spec defers UC-II.4.3 — full policy edit lands in V2")
        public void GivenOwner_WhenEditPolicy_ThenPolicyUpdated() {
            // V0 acceptance: SuccessfulDefaultPolicyEdit
        }

        @Test
        @Disabled("V1 spec defers UC-II.4.3 — full policy edit lands in V2")
        public void GivenManagerWithPolicyMod_WhenEditPolicy_ThenPolicyUpdated() {
            // V0 acceptance: ManagerWithPolicyModificationCanEdit
        }

        @Test
        @Disabled("V1 spec defers UC-II.4.3 — full policy edit lands in V2")
        public void GivenManagerWithoutPolicyMod_WhenEditPolicy_ThenDenied() {
            // V0 acceptance: ManagerWithoutPolicyModificationDenied
        }

        @Test
        @Disabled("V1 spec defers UC-II.4.3 — full policy edit lands in V2")
        public void GivenInvalidPolicyExpression_WhenEditPolicy_ThenRejected() {
            // V0 acceptance: InvalidPolicyExpressionRejected
        }

        // --- helpers ---

        private static Event newEvent() {
            UUID id = UUID.randomUUID();
            Instant start = Instant.now().plus(30, ChronoUnit.DAYS);
            return new Event(id, "Acme Productions", "Concert", "desc", EventCategory.CONCERT,
                    new EventSchedule(start, start.plus(2, ChronoUnit.HOURS), start.minus(1, ChronoUnit.HOURS)),
                    new LockTimerDuration(Duration.ofMinutes(15)));
        }

    }

    @Nested
    @DisplayName("Framework")
    class Framework {

        @Test
        void GivenAlwaysAllowPolicy_WhenEvaluate_ThenAllowed() {
            IPurchasePolicy policy = new AlwaysAllowPolicy();
            UUID memberId = UUID.randomUUID();

            PolicyResult result = policy.isAllowed(null, memberId);

            assertTrue(result.allowed());
            assertNull(result.errorCode());
        }

        @Test
        void GivenAndPolicy_WhenEvaluateCombinations_ThenExpectedResults() {
            IPurchasePolicy truePolicy = mock(IPurchasePolicy.class);
            when(truePolicy.isAllowed(any(), any())).thenReturn(PolicyResult.success());

            IPurchasePolicy falsePolicy = mock(IPurchasePolicy.class);
            when(falsePolicy.isAllowed(any(), any())).thenReturn(PolicyResult.failure("TEST_ERROR", "Failed"));

            IPurchasePolicy bothTrue = new AndPolicy(List.of(truePolicy, truePolicy));
            assertTrue(bothTrue.isAllowed(null, null).allowed());

            IPurchasePolicy oneFalse = new AndPolicy(List.of(truePolicy, falsePolicy));
            PolicyResult result = oneFalse.isAllowed(null, null);
            assertFalse(result.allowed());
            assertEquals("TEST_ERROR", result.errorCode());
        }

        @Test
        void GivenOrPolicy_WhenEvaluateCombinations_ThenExpectedResults() {
            IPurchasePolicy truePolicy = mock(IPurchasePolicy.class);
            when(truePolicy.isAllowed(any(), any())).thenReturn(PolicyResult.success());

            IPurchasePolicy falsePolicy = mock(IPurchasePolicy.class);
            when(falsePolicy.isAllowed(any(), any())).thenReturn(PolicyResult.failure("TEST_ERROR", "Failed"));

            IPurchasePolicy oneTrue = new OrPolicy(List.of(falsePolicy, truePolicy));
            assertTrue(oneTrue.isAllowed(null, null).allowed());

            IPurchasePolicy bothFalse = new OrPolicy(List.of(falsePolicy, falsePolicy));
            PolicyResult result = bothFalse.isAllowed(null, null);
            assertFalse(result.allowed());
            assertEquals("ALL_OR_CONDITIONS_FAILED", result.errorCode());
        }
    }
}
