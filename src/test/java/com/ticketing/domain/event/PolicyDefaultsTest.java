package com.ticketing.domain.event;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * UC-C.2 (PARTIAL — defaults only). V1 ships abstractions + defaults wired
 * to every Event. The full edit API for policies is deferred to V2 — see
 * CONTRIBUTING.md "Policy Editing Architecture (V1 vs V2)".
 */
public class PolicyDefaultsTest {

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
    public void GivenTwoEvents_WhenInspectPolicies_ThenEachHasOwnDefaultInstance() {
        // Sanity: defaults are independent — event A's purchase policy isn't
        // accidentally shared by reference with event B (would matter if a future
        // policy holds state).
        Event a = newEvent();
        Event b = newEvent();
        assertNotNull(a.getPurchasePolicy());
        assertNotNull(b.getPurchasePolicy());
        // not the same instance
        assertTrue(a.getPurchasePolicy() != b.getPurchasePolicy());
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
