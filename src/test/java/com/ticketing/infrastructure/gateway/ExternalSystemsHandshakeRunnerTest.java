package com.ticketing.infrastructure.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ticketing.domain.gateway.IExternalSystemsClient;

/**
 * Verifies the startup handshake runner gating (V3-16): off when no base URL is configured,
 * verifies availability when configured, and halts startup on a failed handshake.
 */
class ExternalSystemsHandshakeRunnerTest {

    /** Records whether handshake() was invoked and returns a canned result. */
    private static final class RecordingClient implements IExternalSystemsClient {
        private final boolean handshakeResult;
        private boolean handshakeCalled;

        RecordingClient(boolean handshakeResult) {
            this.handshakeResult = handshakeResult;
        }

        @Override
        public String send(Map<String, String> params) {
            throw new UnsupportedOperationException("not used in handshake runner test");
        }

        @Override
        public boolean handshake() {
            handshakeCalled = true;
            return handshakeResult;
        }
    }

    @Test
    void GivenNoBaseUrl_WhenRun_ThenSkipsHandshakeAndDoesNotCallClient() {
        RecordingClient client = new RecordingClient(true);
        ExternalSystemsHandshakeRunner runner = new ExternalSystemsHandshakeRunner("", client);

        assertThatCode(() -> runner.run(null)).doesNotThrowAnyException();
        assertThat(client.handshakeCalled).isFalse();
    }

    @Test
    void GivenConfiguredUrlAndReachable_WhenRun_ThenHandshakeSucceeds() {
        RecordingClient client = new RecordingClient(true);
        ExternalSystemsHandshakeRunner runner =
                new ExternalSystemsHandshakeRunner("http://external.test/api/", client);

        assertThatCode(() -> runner.run(null)).doesNotThrowAnyException();
        assertThat(client.handshakeCalled).isTrue();
    }

    @Test
    void GivenConfiguredUrlButUnreachable_WhenRun_ThenHaltsStartup() {
        RecordingClient client = new RecordingClient(false);
        ExternalSystemsHandshakeRunner runner =
                new ExternalSystemsHandshakeRunner("http://external.test/api/", client);

        assertThatThrownBy(() -> runner.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("payment and ticket-issuance");
        assertThat(client.handshakeCalled).isTrue();
    }
}
