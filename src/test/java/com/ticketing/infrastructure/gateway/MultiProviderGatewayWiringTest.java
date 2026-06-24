package com.ticketing.infrastructure.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

import com.ticketing.domain.gateway.CancelResult;
import com.ticketing.domain.gateway.CustomerInfo;
import com.ticketing.domain.gateway.IPaymentGateway;
import com.ticketing.domain.gateway.ITicketSupplyGateway;
import com.ticketing.domain.gateway.PaymentDetails;
import com.ticketing.domain.gateway.PaymentResult;
import com.ticketing.domain.gateway.RefundResult;
import com.ticketing.domain.gateway.SupplyResult;
import com.ticketing.domain.gateway.TicketRequest;

/**
 * Reqs I.3/I.4: the platform must support <b>more than one</b> clearing (payment) service and
 * <b>more than one</b> ticket-supply service, registered via config/DI. {@code OrderService}
 * consumes them as {@code List<IPaymentGateway>} / {@code List<ITicketSupplyGateway>} and fails over
 * across the list (proven behaviourally in {@code OrderServiceTest}). This test proves the wiring
 * side: when two providers of each kind are registered as beans, Spring injects <b>both</b>, in
 * {@link Order} order, into the exact list injection point the service uses.
 */
@DisplayName("Multi-provider gateway wiring (reqs I.3/I.4)")
class MultiProviderGatewayWiringTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TwoProviderConfig.class);

    @Test
    void GivenTwoPaymentProvidersRegistered_WhenContextStarts_ThenBothAreInjectedInOrder() {
        contextRunner.run(context -> {
            assertThat(context.getBeansOfType(IPaymentGateway.class)).hasSize(2);

            @SuppressWarnings("unchecked")
            List<IPaymentGateway> injected = context.getBean("paymentGateways", List.class);
            assertThat(injected).hasSize(2);
            assertThat(injected.get(0)).isInstanceOf(PrimaryPaymentGateway.class);
            assertThat(injected.get(1)).isInstanceOf(SecondaryPaymentGateway.class);
        });
    }

    @Test
    void GivenTwoSupplyProvidersRegistered_WhenContextStarts_ThenBothAreInjectedInOrder() {
        contextRunner.run(context -> {
            assertThat(context.getBeansOfType(ITicketSupplyGateway.class)).hasSize(2);

            @SuppressWarnings("unchecked")
            List<ITicketSupplyGateway> injected = context.getBean("supplyGateways", List.class);
            assertThat(injected).hasSize(2);
            assertThat(injected.get(0)).isInstanceOf(PrimarySupplyGateway.class);
            assertThat(injected.get(1)).isInstanceOf(SecondarySupplyGateway.class);
        });
    }

    /** Two payment + two supply providers, plus list beans mirroring OrderService's injection points. */
    @Configuration
    static class TwoProviderConfig {
        @Bean @Order(1) IPaymentGateway primaryPayment() { return new PrimaryPaymentGateway(); }
        @Bean @Order(2) IPaymentGateway secondaryPayment() { return new SecondaryPaymentGateway(); }
        @Bean @Order(1) ITicketSupplyGateway primarySupply() { return new PrimarySupplyGateway(); }
        @Bean @Order(2) ITicketSupplyGateway secondarySupply() { return new SecondarySupplyGateway(); }

        // Spring injects all matching beans, ordered by @Order — exactly as OrderService receives them.
        @Bean List<IPaymentGateway> paymentGateways(List<IPaymentGateway> gateways) { return gateways; }
        @Bean List<ITicketSupplyGateway> supplyGateways(List<ITicketSupplyGateway> gateways) { return gateways; }
    }

    private static class PrimaryPaymentGateway implements IPaymentGateway {
        @Override public PaymentResult charge(BigDecimal amount, PaymentDetails details) {
            return PaymentResult.failed("primary down");
        }
        @Override public RefundResult refund(String transactionId, double amount) {
            return RefundResult.failed("primary down");
        }
    }

    private static class SecondaryPaymentGateway implements IPaymentGateway {
        @Override public PaymentResult charge(BigDecimal amount, PaymentDetails details) {
            return PaymentResult.successful("txn-2");
        }
        @Override public RefundResult refund(String transactionId, double amount) {
            return RefundResult.successful("refund-2");
        }
    }

    private static class PrimarySupplyGateway implements ITicketSupplyGateway {
        @Override public SupplyResult issueTickets(List<TicketRequest> tickets, CustomerInfo customer) {
            return SupplyResult.failed("primary down");
        }
        @Override public CancelResult cancelTickets(List<String> ticketCodes) {
            return CancelResult.successful();
        }
    }

    private static class SecondarySupplyGateway implements ITicketSupplyGateway {
        @Override public SupplyResult issueTickets(List<TicketRequest> tickets, CustomerInfo customer) {
            return SupplyResult.successful(List.of("TKT-2"));
        }
        @Override public CancelResult cancelTickets(List<String> ticketCodes) {
            return CancelResult.successful();
        }
    }
}
