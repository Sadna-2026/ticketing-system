package com.ticketing.infrastructure.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import com.ticketing.domain.gateway.ExternalSystemsUnavailableException;

/**
 * Verifies the external systems HTTP client against a stubbed endpoint (MockRestServiceServer) —
 * never the live WSEP endpoint (V3-16 acceptance criterion).
 */
class HttpExternalSystemsClientTest {

    private static final String BASE_URL = "http://external.test/api/";

    private HttpExternalSystemsClient clientFor(RestTemplate restTemplate) {
        return new HttpExternalSystemsClient(restTemplate, BASE_URL);
    }

    @Test
    void GivenEndpointRespondsOk_WhenHandshake_ThenPostsHandshakeActionAndReturnsTrue() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        server.expect(requestTo(BASE_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().string("action_type=handshake"))
                .andRespond(withSuccess("OK", MediaType.TEXT_PLAIN));

        boolean reachable = clientFor(restTemplate).handshake();

        assertThat(reachable).isTrue();
        server.verify();
    }

    @Test
    void GivenEndpointRespondsNonOk_WhenHandshake_ThenReturnsFalse() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        server.expect(requestTo(BASE_URL))
                .andRespond(withSuccess("ERROR", MediaType.TEXT_PLAIN));

        assertThat(clientFor(restTemplate).handshake()).isFalse();
        server.verify();
    }

    @Test
    void GivenEndpointUnavailable_WhenHandshake_ThenReturnsFalse() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        server.expect(requestTo(BASE_URL)).andRespond(withServerError());

        assertThat(clientFor(restTemplate).handshake()).isFalse();
        server.verify();
    }

    @Test
    void GivenEndpointRespondsOk_WhenSend_ThenReturnsBodyAndSendsAllParams() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        server.expect(requestTo(BASE_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string("action_type=pay"))
                .andRespond(withSuccess("APPROVED 1234", MediaType.TEXT_PLAIN));

        String body = clientFor(restTemplate).send(Map.of("action_type", "pay"));

        assertThat(body).isEqualTo("APPROVED 1234");
        server.verify();
    }

    @Test
    void GivenServerError_WhenSend_ThenThrowsExternalSystemsUnavailable() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        server.expect(requestTo(BASE_URL)).andRespond(withServerError());

        assertThatThrownBy(() -> clientFor(restTemplate).send(Map.of("action_type", "pay")))
                .isInstanceOf(ExternalSystemsUnavailableException.class);
        server.verify();
    }

    @Test
    void GivenBlankBaseUrl_WhenSend_ThenThrowsExternalSystemsUnavailable() {
        HttpExternalSystemsClient client = new HttpExternalSystemsClient(new RestTemplate(), "");

        assertThatThrownBy(() -> client.send(Map.of("action_type", "pay")))
                .isInstanceOf(ExternalSystemsUnavailableException.class)
                .hasMessageContaining("not configured");
    }
}
