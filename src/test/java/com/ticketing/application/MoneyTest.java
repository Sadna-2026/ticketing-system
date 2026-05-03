package com.ticketing.application;

import org.junit.jupiter.api.Test;

import com.ticketing.domain.event.Money;

import java.math.BigDecimal;
import java.util.Currency;

import static org.junit.jupiter.api.Assertions.*;

class MoneyTest {

    private final Currency ILS = Currency.getInstance("ILS");
    private final Currency USD = Currency.getInstance("USD");
    private final Currency EUR = Currency.getInstance("EUR");

    @Test
    void testValidCreation() {
        Money m = new Money(new BigDecimal("150.50"), ILS);
        assertEquals(new BigDecimal("150.50"), m.amount());
        assertEquals(ILS, m.currency());
    }

    @Test
    void testNegativeAmountThrowsException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            new Money(new BigDecimal("-10.00"), ILS);
        });
        assertEquals("Amount cannot be negative", exception.getMessage());
    }

    @Test
    void testNullCurrencyThrowsException() {
        assertThrows(NullPointerException.class, () -> {
            new Money(BigDecimal.TEN, null);
        });
    }

    @Test
    void testAdditionWithSameCurrency() {
        Money m1 = new Money(new BigDecimal("100.00"), ILS);
        Money m2 = new Money(new BigDecimal("50.25"), ILS);
        
        Money result = m1.add(m2);
        
        assertEquals(new BigDecimal("150.25"), result.amount());
        assertEquals(ILS, result.currency());
    }

    @Test
    void testAdditionWithDifferentCurrenciesThrowsException() {
        Money m1 = new Money(new BigDecimal("100.00"), ILS);
        Money m2 = new Money(new BigDecimal("50.00"), USD);
        
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            m1.add(m2);
        });
        assertTrue(exception.getMessage().contains("Currency mismatch"));
    }

    @Test
    void testSubtractionWithSameCurrency() {
        Money m1 = new Money(new BigDecimal("100.00"), ILS);
        Money m2 = new Money(new BigDecimal("40.00"), ILS);
        
        Money result = m1.subtract(m2);
        
        assertEquals(new BigDecimal("60.00"), result.amount());
        assertEquals(ILS, result.currency());
    }

    @Test
    void testSubtractionResultingInNegativeThrowsException() {
        Money m1 = new Money(new BigDecimal("50.00"), ILS);
        Money m2 = new Money(new BigDecimal("60.00"), ILS);
        
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            m1.subtract(m2);
        });
        assertEquals("Amount cannot be negative", exception.getMessage());
    }

    @Test
    void testExchangeToTargetCurrency() {
        Money usdMoney = new Money(new BigDecimal("100.00"), USD);
        BigDecimal exchangeRateToEur = new BigDecimal("0.92"); // 1 USD = 0.92 EUR
        
        Money eurMoney = usdMoney.exchange(EUR, exchangeRateToEur);
        
        assertEquals(new BigDecimal("92.00"), eurMoney.amount());
        assertEquals(EUR, eurMoney.currency());
    }

    @Test
    void testExchangeWithNegativeRateThrowsException() {
        Money m = new Money(new BigDecimal("100.00"), ILS);
        
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            m.exchange(USD, new BigDecimal("-3.5"));
        });
        assertEquals("Exchange rate must be positive", exception.getMessage());
    }
}