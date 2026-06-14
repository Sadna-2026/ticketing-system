package com.ticketing.infrastructure.gateway;

import java.time.Duration;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.ticketing.domain.gateway.ExternalSystemsUnavailableException;
import com.ticketing.domain.gateway.IExternalSystemsClient;

/**
 * HTTP client for the WSEP external systems endpoint (V3-16): a single {@code POST} endpoint keyed
 * by {@code action_type}, sent {@code application/x-www-form-urlencoded}. The base URL is
 * configuration-driven ({@code ticketing.external.base-url}); no network call is made at construction.
 */
@Component
public class HttpExternalSystemsClient implements IExternalSystemsClient {

    private static final Logger log = LoggerFactory.getLogger(HttpExternalSystemsClient.class);
    private static final String HANDSHAKE_OK = "OK";

    private final RestTemplate restTemplate;
    private final String baseUrl;

    @Autowired
    public HttpExternalSystemsClient(
            RestTemplateBuilder builder,
            @Value("${ticketing.external.base-url:}") String baseUrl,
            @Value("${ticketing.external.connect-timeout-ms:5000}") long connectTimeoutMs,
            @Value("${ticketing.external.read-timeout-ms:5000}") long readTimeoutMs) {
        this(builder
                .setConnectTimeout(Duration.ofMillis(connectTimeoutMs))
                .setReadTimeout(Duration.ofMillis(readTimeoutMs))
                .build(), baseUrl);
    }

    /** Visible for testing — lets a test bind {@code MockRestServiceServer} to the {@link RestTemplate}. */
    HttpExternalSystemsClient(RestTemplate restTemplate, String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
    }

    @Override
    public String send(Map<String, String> params) {
        if (baseUrl.isBlank()) {
            throw new ExternalSystemsUnavailableException(
                    "External systems base URL is not configured (set ticketing.external.base-url).");
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        if (params != null) {
            params.forEach(form::add);
        }
        try {
            String body = restTemplate.execute(baseUrl, HttpMethod.POST, request -> {
                request.getHeaders().setContentType(MediaType.APPLICATION_FORM_URLENCODED);
                new FormHttpMessageConverter().write(form, MediaType.APPLICATION_FORM_URLENCODED, request);
            }, response -> {
                try (java.io.InputStream is = response.getBody()) {
                    byte[] data = is.readNBytes(2049);
                    if (data.length > 2048) {
                        throw new RestClientException("External system response exceeded maximum allowed length (2048 bytes).");
                    }
                    return new String(data, java.nio.charset.StandardCharsets.UTF_8);
                }
            });
            return body == null ? "" : body;
        } catch (RestClientException ex) {
            throw new ExternalSystemsUnavailableException(
                    "External systems call " + params + " failed: " + ex.getMessage(), ex);
        }
    }

    @Override
    public boolean handshake() {
        try {
            return HANDSHAKE_OK.equals(send(Map.of("action_type", HANDSHAKE_ACTION)).trim());
        } catch (RuntimeException ex) {
            log.warn("External systems handshake failed: {}", ex.getMessage());
            return false;
        }
    }
}
