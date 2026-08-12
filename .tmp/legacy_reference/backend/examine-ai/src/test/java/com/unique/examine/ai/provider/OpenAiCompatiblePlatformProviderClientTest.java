package com.unique.examine.ai.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.unique.examine.ai.domain.PlatformAiProvider;
import com.unique.examine.core.api.PlatformSecretResolverFacade;
import com.unique.examine.core.api.SecretResolverFacade;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiCompatiblePlatformProviderClientTest {
    private static final Instant NOW = Instant.parse("2026-08-04T00:00:00Z");

    @Test
    void resolvesAPlatformSecretWithoutForgedTenantIdentifiers() throws Exception {
        var authorization = new AtomicReference<String>();
        var requestBody = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestBody.set(new String(
                    exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            var response = new ObjectMapper().writeValueAsBytes(Map.of(
                    "choices", List.of(Map.of("message", Map.of(
                            "content", "{\"operation\":\"AUTHORIZED_SYSTEMS_QUERY\"}"))),
                    "usage", Map.of(
                            "prompt_tokens", 12, "completion_tokens", 4)));
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        var resolved = SecretResolverFacade.ResolvedSecret.utf8(
                "platform-secret-value");
        PlatformSecretResolverFacade secrets = request -> {
            assertThat(request.reference()).isEqualTo("env://PLATFORM_AI_KEY");
            return Optional.of(resolved);
        };
        try {
            var client = new OpenAiCompatiblePlatformProviderClient(
                    secrets, new ObjectMapper());

            var completion = client.complete(provider(server),
                    new AiProviderClient.Request(
                            AiProviderClient.Phase.PLAN, "platform-only prompt",
                            "list systems", 100));

            assertThat(completion.content())
                    .isEqualTo("{\"operation\":\"AUTHORIZED_SYSTEMS_QUERY\"}");
            assertThat(authorization.get())
                    .isEqualTo("Bearer platform-secret-value");
            assertThat(requestBody.get())
                    .contains("\"model\":\"gpt-platform\"")
                    .doesNotContain("platform-secret-value")
                    .doesNotContain("env://PLATFORM_AI_KEY");
            assertThatThrownBy(resolved::copyBytes)
                    .isInstanceOf(IllegalStateException.class);
        } finally {
            server.stop(0);
        }
    }

    private static PlatformAiProvider provider(HttpServer server) {
        return new PlatformAiProvider(
                80, "openai", "OpenAI",
                "http://127.0.0.1:" + server.getAddress().getPort() + "/v1",
                "gpt-platform", "env://PLATFORM_AI_KEY", 10, true, 3,
                NOW, 7, NOW, 7);
    }
}
