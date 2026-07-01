package com.ticketing.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

import com.ticketing.domain.gateway.IPaymentGateway;
import com.ticketing.domain.gateway.ITicketSupplyGateway;
import com.ticketing.infrastructure.gateway.StubPaymentGateway;
import com.ticketing.infrastructure.gateway.StubTicketSupplyGateway;

/**
 * V3-25 (§2.4c): proves the dedicated test configuration isolates the suite from the real external
 * systems and from any real / working database. The {@code test} profile is activated for every run
 * by the Surefire plugin (pom.xml), so this asserts the running context — not just the file content:
 *
 * <ul>
 *   <li>the {@code test} profile is active;</li>
 *   <li>the external base-url is blank, so the <b>stub</b> payment/ticket gateways are wired (never
 *       the {@code Http*} gateways that would call the real WSEP endpoint);</li>
 *   <li>the datasource is a throwaway in-memory H2 ({@code ticketing-test}), never a remote/working DB.</li>
 * </ul>
 *
 * <p>Seeding and platform initialization are disabled to keep the context lean; they are orthogonal
 * to the isolation guarantees asserted here.
 */
@org.junit.jupiter.api.Tag("slow")
@SpringBootTest(properties = {
        "ticketing.seed.enabled=false",
        "ticketing.startup.initialize-platform=false"
})
@DisplayName("Dedicated test configuration isolates externals + DB (V3-25)")
class TestConfigurationIsolationTest {

    @Autowired
    private Environment environment;
    @Autowired
    private IPaymentGateway paymentGateway;
    @Autowired
    private ITicketSupplyGateway ticketSupplyGateway;

    @Test
    void GivenTestRun_WhenContextStarts_ThenTestProfileIsActive() {
        assertThat(environment.getActiveProfiles()).contains("test");
    }

    @Test
    void GivenTestRun_WhenContextStarts_ThenExternalsAreStubbedNotReal() {
        String baseUrl = environment.getProperty("ticketing.external.base-url");
        assertThat(baseUrl == null || baseUrl.isBlank())
                .as("external base-url must be blank so no real WSEP endpoint is contacted")
                .isTrue();
        assertThat(paymentGateway).isInstanceOf(StubPaymentGateway.class);
        assertThat(ticketSupplyGateway).isInstanceOf(StubTicketSupplyGateway.class);
    }

    @Test
    void GivenTestRun_WhenContextStarts_ThenDatasourceIsThrowawayInMemoryH2() {
        String url = environment.getProperty("spring.datasource.url");
        assertThat(url).isNotNull();
        assertThat(url)
                .as("tests must use a throwaway in-memory H2, never a remote/working DB")
                .contains("jdbc:h2:mem:")
                .contains("ticketing-test");
    }
}
