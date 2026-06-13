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

import com.ticketing.domain.gateway.ITicketSupplyGateway;

/**
 * V3-18: the active {@link ITicketSupplyGateway} is selected by {@code ticketing.external.base-url},
 * and exactly one bean is active in each mode — {@link StubTicketSupplyGateway} when the URL is blank
 * (local dev / tests), {@link HttpTicketSupplyGateway} when it is configured (production). Mirrors
 * {@code PaymentGatewaySelectionTest}: an {@link ApplicationContextRunner} so the full app does not
 * boot. Proves the stub and the real gateway are mutually exclusive (the always-succeeding stub can
 * never mask a real supply failure in {@code OrderService}'s gateway failover).
 */
@DisplayName("Ticket supply gateway selection by ticketing.external.base-url")
class TicketSupplyGatewaySelectionTest {

    @Configuration
    @Import({
            HttpExternalSystemsClient.class,
            StubTicketSupplyGateway.class,
            HttpTicketSupplyGateway.class
    })
    static class GatewayWiringConfig {
    }

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RestTemplateAutoConfiguration.class))
            .withUserConfiguration(GatewayWiringConfig.class);

    @Test
    void GivenNoExternalUrl_WhenContextStarts_ThenStubIsTheSingleActiveGateway() {
        contextRunner.run(TicketSupplyGatewaySelectionTest::assertStubIsSingleActiveGateway);
    }

    @Test
    void GivenBlankExternalUrl_WhenContextStarts_ThenStubIsTheSingleActiveGateway() {
        contextRunner.withPropertyValues("ticketing.external.base-url=")
                .run(TicketSupplyGatewaySelectionTest::assertStubIsSingleActiveGateway);
    }

    @Test
    void GivenExternalUrlConfigured_WhenContextStarts_ThenHttpGatewayIsTheSingleActiveGateway() {
        contextRunner.withPropertyValues("ticketing.external.base-url=http://wsep.test/api/")
                .run(context -> {
                    assertThat(context).hasSingleBean(ITicketSupplyGateway.class);
                    assertThat(context.getBean(ITicketSupplyGateway.class))
                            .isInstanceOf(HttpTicketSupplyGateway.class);
                    assertThat(context).doesNotHaveBean(StubTicketSupplyGateway.class);
                });
    }

    private static void assertStubIsSingleActiveGateway(AssertableApplicationContext context) {
        assertThat(context).hasSingleBean(ITicketSupplyGateway.class);
        assertThat(context.getBean(ITicketSupplyGateway.class)).isInstanceOf(StubTicketSupplyGateway.class);
        assertThat(context).doesNotHaveBean(HttpTicketSupplyGateway.class);
    }
}
