package com.unique.examine.ai.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.unique.examine.ai.domain.AiProvider;
import com.unique.examine.core.api.SecretResolverFacade;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiCompatibleProviderClientTest {
    private static final Instant NOW = Instant.parse("2026-08-04T00:00:00Z");

    @Test
    void resolvesTenantScopedSecretForOneBoundedOpenAiCompatibleRequest() throws Exception {
        var authorization = new AtomicReference<String>();
        var requestBody = new AtomicReference<String>();
        var server = server(exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestBody.set(new String(
                    exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            var response = """
                    {"choices":[{"message":{"content":"{\\\"operation\\\":\\\"RECORD_QUERY\\\"}"}}],
                     "usage":{"prompt_tokens":12,"completion_tokens":4}}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        var resolved = SecretResolverFacade.ResolvedSecret.utf8("tenant-key-value");
        SecretResolverFacade secrets = request -> {
            assertThat(request.systemId()).isEqualTo(1);
            assertThat(request.tenantId()).isEqualTo(2);
            assertThat(request.reference()).isEqualTo("vault://tenant/openai");
            return Optional.of(resolved);
        };
        try {
            var client = new OpenAiCompatibleProviderClient(secrets, new ObjectMapper());

            var completion = client.complete(provider(server),
                    new AiProviderClient.Request(
                            AiProviderClient.Phase.PLAN, "system prompt",
                            "question", 100));

            assertThat(completion.content()).isEqualTo("{\"operation\":\"RECORD_QUERY\"}");
            assertThat(completion.promptTokens()).isEqualTo(12);
            assertThat(completion.completionTokens()).isEqualTo(4);
            assertThat(authorization.get()).isEqualTo("Bearer tenant-key-value");
            assertThat(requestBody.get())
                    .contains("\"model\":\"gpt-read\"")
                    .contains("\"response_format\":{\"type\":\"json_object\"}")
                    .doesNotContain("tenant-key-value")
                    .doesNotContain("vault://tenant/openai");
            assertThatThrownBy(resolved::copyBytes)
                    .isInstanceOf(IllegalStateException.class);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rejectsOversizedProviderResponseWithoutExposingItsBody() throws Exception {
        var server = server(exchange -> {
            var response = new byte[OpenAiCompatibleProviderClient.MAXIMUM_RESPONSE_BYTES + 1];
            java.util.Arrays.fill(response, (byte) 'x');
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        try {
            var client = new OpenAiCompatibleProviderClient(
                    request -> Optional.of(
                            SecretResolverFacade.ResolvedSecret.utf8("never-print-this")),
                    new ObjectMapper());

            assertThatThrownBy(() -> client.complete(provider(server),
                    new AiProviderClient.Request(
                            AiProviderClient.Phase.SUMMARY,
                            "system prompt", "bounded tool result", 100)))
                    .isInstanceOf(AiProviderClient.ProviderFailure.class)
                    .satisfies(failure -> {
                        var providerFailure = (AiProviderClient.ProviderFailure) failure;
                        assertThat(providerFailure.code())
                                .isEqualTo("AI_PROVIDER_RESPONSE_TOO_LARGE");
                        assertThat(providerFailure.retryable()).isFalse();
                        assertThat(providerFailure.toString())
                                .doesNotContain("never-print-this")
                                .doesNotContain("xxxx");
                    });
        } finally {
            server.stop(0);
        }
    }

    private static HttpServer server(Handler handler) throws IOException {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> handler.handle(exchange));
        server.start();
        return server;
    }

    private static AiProvider provider(HttpServer server) {
        return new AiProvider(
                80, 1, 2, "openai", "OpenAI",
                "http://127.0.0.1:" + server.getAddress().getPort() + "/v1",
                "gpt-read", "vault://tenant/openai", 10, true, 3,
                NOW, 7, NOW, 7);
    }

    @FunctionalInterface
    private interface Handler {
        void handle(com.sun.net.httpserver.HttpExchange exchange) throws IOException;
    }
}
