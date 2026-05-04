package com.ticketing.domain.event;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ticketing.domain.member.User;
import com.ticketing.domain.order.ActiveOrder;
import com.ticketing.infrastructure.Interface.IDiscountPolicy;
import com.ticketing.infrastructure.Interface.IPurchasePolicy;

class PolicyFrameworkTest {

    private final Currency testCurrency = Currency.getInstance("ILS");

    @Test
    void testNoDiscountPolicyReturnsZeroOrGreater() {
        IDiscountPolicy policy = new NoDiscountPolicy(testCurrency);
        ActiveOrder mockOrder = mock(ActiveOrder.class);
        User mockUser = mock(User.class);
        
        Money discount = policy.applyTo(mockOrder, mockUser);
        
        assertTrue(discount.amount().compareTo(BigDecimal.ZERO) >= 0);
        assertEquals(BigDecimal.ZERO, discount.amount());
        assertEquals(testCurrency, discount.currency());
    }

    @Test
    void testAlwaysAllowPolicy() {
        IPurchasePolicy policy = new AlwaysAllowPolicy();
        ActiveOrder mockOrder = mock(ActiveOrder.class);
        User mockUser = mock(User.class);
        
        PolicyResult result = policy.isAllowed(mockOrder, mockUser);
        
        assertTrue(result.allowed());
        assertNull(result.errorCode());
    }

    @Test
    void testAndPolicyCombinations() {
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
    void testOrPolicyCombinations() {
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