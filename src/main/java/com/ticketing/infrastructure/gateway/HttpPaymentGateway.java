package com.ticketing.infrastructure.gateway;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import com.ticketing.domain.gateway.ExternalSystemsUnavailableException;
import com.ticketing.domain.gateway.IExternalSystemsClient;
import com.ticketing.domain.gateway.IPaymentGateway;
import com.ticketing.domain.gateway.PaymentDetails;
import com.ticketing.domain.gateway.PaymentResult;
import com.ticketing.domain.gateway.RefundResult;

/**
 * The real {@link IPaymentGateway} (V3-17): delegates {@code charge}/{@code refund} to the WSEP
 * external payment system via {@link IExternalSystemsClient} ({@code action_type=pay} /
 * {@code action_type=refund}, {@code application/x-www-form-urlencoded}).
 *
 * <p>{@code pay} returns a transaction id in {@code [10000, 100000]} on approval or {@code -1} on
 * decline; {@code refund} returns {@code 1} on success or {@code -1} on failure. Anything outside
 * those success ranges (including non-numeric bodies and an unreachable endpoint) is mapped to a
 * failed result so the purchase flow can react without seeing exceptions.
 *
 * <p>Registered only when {@code ticketing.external.base-url} is configured — it then <b>replaces</b>
 * {@link StubPaymentGateway} as the active production bean (the stub carries the inverse condition),
 * so the two are mutually exclusive and the always-approving stub can never mask a real decline.
 *
 * <p>The WSEP {@code pay} action also requires card details (number/expiry/holder/cvv/id) and a
 * currency. The purchase flow does not capture card data and the {@link IPaymentGateway} contract is
 * fixed (amount + {@link PaymentDetails}), so these are sourced from configuration
 * ({@code ticketing.external.payment.*}) with sandbox-friendly defaults. Capturing real cardholder
 * input is intentionally out of scope for V3-17 and left to a future ticket.
 */
@Component
@ConditionalOnExpression("'${ticketing.external.base-url:}'.trim() != ''")
public class HttpPaymentGateway implements IPaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(HttpPaymentGateway.class);

    private static final String ACTION_PAY = "pay";
    private static final String ACTION_REFUND = "refund";
    private static final int MIN_TRANSACTION_ID = 10000;
    private static final int MAX_TRANSACTION_ID = 100000;
    private static final String REFUND_OK = "1";

    private final IExternalSystemsClient client;
    private final CardConfig card;

    @Autowired
    public HttpPaymentGateway(
            IExternalSystemsClient client,
            @Value("${ticketing.external.payment.currency:USD}") String currency,
            @Value("${ticketing.external.payment.card-number:2222333344445555}") String cardNumber,
            @Value("${ticketing.external.payment.card-month:12}") String cardMonth,
            @Value("${ticketing.external.payment.card-year:2030}") String cardYear,
            @Value("${ticketing.external.payment.card-holder:Ticketing System}") String cardHolder,
            @Value("${ticketing.external.payment.card-cvv:123}") String cardCvv,
            @Value("${ticketing.external.payment.card-id:000000000}") String cardId) {
        this(client, new CardConfig(currency, cardNumber, cardMonth, cardYear, cardHolder, cardCvv, cardId));
    }

    /** Visible for testing — lets a test supply a fixed card config and a mock-backed client. */
    HttpPaymentGateway(IExternalSystemsClient client, CardConfig card) {
        this.client = client;
        this.card = card;
    }

    @Override
    public PaymentResult charge(BigDecimal finalAmount, PaymentDetails details) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("action_type", ACTION_PAY);
        params.put("amount", finalAmount.toPlainString());
        params.put("currency", card.currency());
        params.put("card_number", card.cardNumber());
        params.put("month", card.cardMonth());
        params.put("year", card.cardYear());
        params.put("holder", card.cardHolder());
        params.put("cvv", card.cardCvv());
        params.put("id", card.cardId());

        try {
            String body = client.send(params).trim();
            Integer transactionId = parseIntOrNull(body);
            if (transactionId != null
                    && transactionId >= MIN_TRANSACTION_ID
                    && transactionId <= MAX_TRANSACTION_ID) {
                // Return the endpoint's own response verbatim (refund echoes it back), rather than a
                // re-stringified int, so the transaction id is preserved exactly as issued.
                return PaymentResult.successful(body);
            }
            log.warn("External payment declined or returned an unexpected response: '{}'", body);
            return PaymentResult.failed("Payment was declined by the external payment system.");
        } catch (ExternalSystemsUnavailableException ex) {
            log.error("External payment system unavailable during charge: {}", ex.getMessage());
            return PaymentResult.failed("Payment system is currently unavailable.");
        }
    }

    @Override
    public RefundResult refund(String transactionId, double amount) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("action_type", ACTION_REFUND);
        params.put("transaction_id", transactionId);

        try {
            String body = client.send(params).trim();
            if (REFUND_OK.equals(body)) {
                return RefundResult.successful(transactionId);
            }
            log.warn("External refund failed or returned an unexpected response: '{}'", body);
            return RefundResult.failed("Refund was rejected by the external payment system.");
        } catch (ExternalSystemsUnavailableException ex) {
            log.error("External payment system unavailable during refund: {}", ex.getMessage());
            return RefundResult.failed("Payment system is currently unavailable.");
        }
    }

    private static Integer parseIntOrNull(String value) {
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * Configuration-sourced card + currency sent with the WSEP {@code pay} action. The purchase flow
     * does not collect cardholder input (see class javadoc); defaults are sandbox placeholders.
     */
    record CardConfig(
            String currency,
            String cardNumber,
            String cardMonth,
            String cardYear,
            String cardHolder,
            String cardCvv,
            String cardId) {
    }
}
