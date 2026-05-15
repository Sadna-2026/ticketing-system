package com.ticketing.domain.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.ticketing.infrastructure.Interface.IPurchasePolicy;

class PolicyFrameworkTest {

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
