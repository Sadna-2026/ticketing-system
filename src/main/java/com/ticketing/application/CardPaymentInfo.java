package com.ticketing.application;

/**
 * Buyer-entered card details captured by the checkout dialog and forwarded to the external
 * payment system's {@code pay} action. Carries no persistence — it lives only for the duration
 * of one checkout call and is never stored.
 */
public record CardPaymentInfo(
        String currency,
        String cardNumber,
        String month,
        String year,
        String holder,
        String cvv,
        String cardId) {
}
