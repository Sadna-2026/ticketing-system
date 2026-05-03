package com.ticketing.domain.event;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

public record Money(BigDecimal amount, Currency currency) {
    public Money {
        Objects.requireNonNull(currency, "Currency cannot be null");
        Objects.requireNonNull(amount, "Amount cannot be null");
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Amount cannot be negative");
        }
    }

    public static Money zero(Currency currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    public Money add(Money other) {
        requireSameCurrency(other);
        return new Money(this.amount.add(other.amount()), this.currency);
    }

    public Money subtract(Money other) {
        requireSameCurrency(other);
        // The compact constructor will automatically throw an exception if the result
        // is negative
        return new Money(this.amount.subtract(other.amount()), this.currency);
    }

    public Money exchange(Currency targetCurrency, BigDecimal exchangeRate) {
        Objects.requireNonNull(targetCurrency, "Target currency cannot be null");
        Objects.requireNonNull(exchangeRate, "Exchange rate cannot be null");
        if (exchangeRate.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Exchange rate must be positive");
        }

        // Multiply and round to 2 decimal places (standard for most currencies)
        BigDecimal convertedAmount = this.amount.multiply(exchangeRate)
                .setScale(2, RoundingMode.HALF_UP);
        return new Money(convertedAmount, targetCurrency);
    }

    private void requireSameCurrency(Money other) {
        if (!this.currency.equals(other.currency())) {
            throw new IllegalArgumentException(
                    String.format("Currency mismatch: Cannot perform arithmetic between %s and %s",
                            this.currency.getCurrencyCode(),
                            other.currency().getCurrencyCode()));
        }
    }
}