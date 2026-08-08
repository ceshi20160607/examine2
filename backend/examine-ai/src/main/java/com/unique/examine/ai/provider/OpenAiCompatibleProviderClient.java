package com.unique.examine.ai.provider;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.unique.examine.ai.AiSupport;
import com.unique.examine.ai.domain.AiProvider;
import com.unique.examine.core.api.SecretResolverFacade;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;

@Component
public class OpenAiCompatibleProviderClient implements AiProviderClient {
    static final int MAXIMUM_REQUEST_BYTES = 256 * 1024;
    static final int MAXIMUM_RESPONSE_BYTES = 1024 * 1024;

    private final SecretResolverFacade secrets;
    private final ObjectMapper json;
    private final HttpClient http;

    @Autowired
    public OpenAiCompatibleProviderClient(
            SecretResolverFacade secrets,
            ObjectMapper json
    ) {
        this(secrets, json, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build());
    }

    OpenAiCompatibleProviderClient(
            SecretResolverFacade secrets,
            ObjectMapper json,
            HttpClient http
    ) {
        this.secrets = Objects.requireNonNull(secrets, "secrets");
        this.json = Objects.requireNonNull(json, "json");
        this.http = Objects.requireNonNull(http, "http");
    }

    @Override
    public Completion complete(AiProvider provider, Request request) {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(request, "request");
        if (!provider.enabled()) {
            throw failure("AI_PROVIDER_DISABLED", false);
        }
        var body = body(provider, request);
        if (body.length > MAXIMUM_REQUEST_BYTES) {
            throw failure("AI_PROVIDER_REQUEST_TOO_LARGE", false);
        }
        final SecretResolverFacade.ResolvedSecret resolved;
        try {
            resolved = secrets.resolve(new SecretResolverFacade.SecretRequest(
                    provider.systemId(), provider.tenantId(), provider.secretRef()))
                    .orElseThrow(() -> failure("AI_PROVIDER_SECRET_UNAVAILABLE", false));
        } catch (ProviderFailure expected) {
            throw expected;
        } catch (RuntimeException unavailable) {
            throw failure("AI_PROVIDER_SECRET_UNAVAILABLE", false);
        }
        var key = resolved.copyBytes();
        try (resolved) {
            var authorization = "Bearer " + new String(key, StandardCharsets.UTF_8);
            var httpRequest = HttpRequest.newBuilder(endpoint(provider.baseUrl()))
                    .timeout(Duration.ofSeconds(provider.timeoutSeconds()))
                    .header("Authorization", authorization)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            var started = System.nanoTime();
            final HttpResponse<java.io.InputStream> response;
            try {
                response = http.send(
                        httpRequest, HttpResponse.BodyHandlers.ofInputStream());
            } catch (java.net.http.HttpTimeoutException timeout) {
                throw failure("AI_PROVIDER_TIMEOUT", true);
            } catch (IOException io) {
                throw failure("AI_PROVIDER_IO_FAILURE", true);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw failure("AI_PROVIDER_INTERRUPTED", true);
            }
            var latency = Math.max(
                    0, (System.nanoTime() - started) / 1_000_000);
            final byte[] responseBody;
            try (var input = response.body()) {
                responseBody = input.readNBytes(MAXIMUM_RESPONSE_BYTES + 1);
            } catch (IOException io) {
                throw failure("AI_PROVIDER_IO_FAILURE", true);
            }
            if (responseBody.length > MAXIMUM_RESPONSE_BYTES) {
                throw failure("AI_PROVIDER_RESPONSE_TOO_LARGE", false);
            }
            var status = response.statusCode();
            if (status < 200 || status > 299) {
                throw failure("AI_PROVIDER_HTTP_FAILURE",
                        status == 408 || status == 425 || status == 429
                                || status >= 500);
            }
            return completion(responseBody, latency);
        } finally {
            Arrays.fill(key, (byte) 0);
        }
    }

    private byte[] body(AiProvider provider, Request request) {
        var root = JsonNodeFactory.instance.objectNode();
        root.put("model", provider.model());
        root.put("temperature", 0);
        root.put("max_tokens", request.maxOutputTokens());
        var messages = root.putArray("messages");
        messages.addObject()
                .put("role", "system")
                .put("content", request.systemPrompt());
        messages.addObject()
                .put("role", "user")
                .put("content", request.userContent());
        if (request.phase() == Phase.PLAN) {
            root.putObject("response_format").put("type", "json_object");
        }
        try {
            return json.writeValueAsBytes(root);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Cannot encode AI provider request", failure);
        }
    }

    private Completion completion(byte[] body, long latency) {
        final JsonNode root;
        try {
            root = json.readTree(body);
        } catch (IOException failure) {
            throw failure("AI_PROVIDER_RESPONSE_INVALID", false);
        }
        var choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            throw failure("AI_PROVIDER_RESPONSE_INVALID", false);
        }
        var content = choices.get(0).path("message").path("content");
        if (!content.isTextual() || content.textValue().isBlank()
                || content.textValue().length() > 128_000) {
            throw failure("AI_PROVIDER_RESPONSE_INVALID", false);
        }
        var usage = root.path("usage");
        var promptTokens = token(usage.path("prompt_tokens"));
        var completionTokens = token(usage.path("completion_tokens"));
        return new Completion(
                content.textValue(), promptTokens, completionTokens, latency,
                AiSupport.sha256(body));
    }

    private static int token(JsonNode node) {
        return node.isIntegralNumber() && node.canConvertToInt()
                && node.intValue() >= 0 ? node.intValue() : 0;
    }

    private static URI endpoint(String baseUrl) {
        return URI.create(baseUrl + "/chat/completions");
    }

    private static ProviderFailure failure(String code, boolean retryable) {
        return new ProviderFailure(code, retryable);
    }
}
