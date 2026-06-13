package com.ticketing.infrastructure.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.web.client.RestTemplateAutoConfiguration;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import com.ticketing.domain.gateway.IPaymentGateway;

/**
 * V3-17: the active {@link IPaymentGateway} is selected by {@code ticketing.external.base-url}, and
 * exactly one bean is active in each mode — {@link StubPaymentGateway} when the URL is blank (local
 * dev / tests), {@link HttpPaymentGateway} when it is configured (production). Mirrors
 * {@code PersistenceProfileSelectionTest}: an {@link ApplicationContextRunner} so the full app does
 * not boot. Proves the stub and the real gateway are mutually exclusive (the stub can never mask a
 * real decline in {@code OrderService}'s gateway failover).
 */
@DisplayName("Payment gateway selection by ticketing.external.base-url")
class PaymentGatewaySelectionTest {

    @Configuration
    @Import({
            HttpExternalSystemsClient.class,
            StubPaymentGateway.class,
            HttpPaymentGateway.class
    })
    static class GatewayWiringConfig {
    }

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RestTemplateAutoConfiguration.class))
            .withUserConfiguration(GatewayWiringConfig.class);

    @Test
    void GivenNoExternalUrl_WhenContextStarts_ThenStubIsTheSingleActiveGateway() {
        contextRunner.run(PaymentGatewaySelectionTest::assertStubIsSingleActiveGateway);
    }

    @Test
    void GivenBlankExternalUrl_WhenContextStarts_ThenStubIsTheSingleActiveGateway() {
        contextRunner.withPropertyValues("ticketing.external.base-url=")
                .run(PaymentGatewaySelectionTest::assertStubIsSingleActiveGateway);
    }

    @Test
    void GivenExternalUrlConfigured_WhenContextStarts_ThenHttpGatewayIsTheSingleActiveGateway() {
        contextRunner.withPropertyValues("ticketing.external.base-url=http://wsep.test/api/")
                .run(context -> {
                    assertThat(context).hasSingleBean(IPaymentGateway.class);
                    assertThat(context.getBean(IPaymentGateway.class))
                            .isInstanceOf(HttpPaymentGateway.class);
                    assertThat(context).doesNotHaveBean(StubPaymentGateway.class);
                });
    }

    private static void assertStubIsSingleActiveGateway(AssertableApplicationContext context) {
        assertThat(context).hasSingleBean(IPaymentGateway.class);
        assertThat(context.getBean(IPaymentGateway.class)).isInstanceOf(StubPaymentGateway.class);
        assertThat(context).doesNotHaveBean(HttpPaymentGateway.class);
    }
}
