package com.unique.examine.event.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.unique.examine.core.api.OutboundHttpTransport;
import com.unique.examine.core.api.SecretResolverFacade;
import com.unique.examine.event.config.WebhookTransportProperties;
import com.unique.examine.event.port.EventChannelTargetDirectory;
import com.unique.examine.event.port.EventChannelTransport;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SignedWebhookTransportTest {
    private static final Instant NOW = Instant.parse("2030-01-01T00:00:00Z");
    private static final String SECRET = "webhook-signing-secret-value";

    @Test
    void sendsCanonicalSignedJsonOverARealLocalHttpProtocol() throws Exception {
        var capture = new AtomicReference<CapturedRequest>();
        try (var endpoint = LocalHttpServer.start(exchange -> {
            capture.set(capture(exchange));
            respond(exchange, 202, "provider-sensitive-response-body");
        })) {
            var material = SecretResolverFacade.ResolvedSecret.utf8(SECRET);
            var transport = transport(endpoint.uri("/events"), 1_000,
                    request -> Optional.of(material));

            var result = transport.deliver(command());

            assertThat(result.status()).isEqualTo(EventChannelTransport.Status.SENT);
            assertThat(result.failureCode()).isNull();
            assertThat(result.maskedDestination())
                    .isEqualTo("http://127.0.0.1:" + endpoint.port() + "/[redacted]");
            assertThat(result.traceId()).isEqualTo("trace-webhook-1");
            assertThat(result.toString())
                    .doesNotContain("provider-sensitive-response-body")
                    .doesNotContain(SECRET)
                    .doesNotContain("/events");
            assertThatThrownBy(material::copyBytes).isInstanceOf(IllegalStateException.class);

            var request = capture.get();
            assertThat(request.deliveryId()).isEqualTo("101");
            assertThat(request.dedupeKey()).isEqualTo("task:42:assigned");
            assertThat(request.timestamp()).isEqualTo("1893456000");
            assertThat(request.traceId()).isEqualTo("trace-webhook-1");
            assertThat(request.signature()).isEqualTo("v1=" + hmac(
                    SECRET, request.timestamp() + ".101.task:42:assigned.", request.body()));

            var payload = new ObjectMapper().readTree(request.body());
            assertThat(payload.path("schemaVersion").asText()).isEqualTo("1");
            assertThat(payload.path("deliveryId").asLong()).isEqualTo(101);
            assertThat(payload.path("dedupeKey").asText()).isEqualTo("task:42:assigned");
            assertThat(payload.path("timestamp").asLong()).isEqualTo(1_893_456_000L);
            assertThat(payload.at("/event/templateCode").asText()).isEqualTo("TASK_ASSIGNED");
            assertThat(payload.at("/event/variables/taskName").asText()).isEqualTo("季度复盘");
        }
    }

    @Test
    void neverFollowsRedirectsOrPersistsTheResponseBody() throws Exception {
        var redirectedHits = new AtomicInteger();
        try (var redirected = LocalHttpServer.start(exchange -> {
            redirectedHits.incrementAndGet();
            respond(exchange, 200, "should-never-be-read");
        }); var origin = LocalHttpServer.start(exchange -> {
            exchange.getResponseHeaders().set("Location", redirected.uri("/stolen").toString());
            respond(exchange, 302, "redirect-sensitive-body");
        })) {
            var result = transport(origin.uri("/events"), 1_000, secrets()).deliver(command());

            assertThat(result.status()).isEqualTo(EventChannelTransport.Status.PERMANENT_FAILURE);
            assertThat(result.failureCode()).isEqualTo("WEBHOOK_REDIRECT_REJECTED");
            assertThat(redirectedHits).hasValue(0);
            assertThat(result.toString()).doesNotContain("redirect-sensitive-body");
        }
    }

    @Test
    void persistedTimeoutOverridesTheLongerDeploymentDefault() throws Exception {
        try (var endpoint = LocalHttpServer.start(exchange -> {
            try {
                Thread.sleep(250);
                respond(exchange, 200, "late");
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } catch (IOException disconnected) {
                // The client is expected to close after its bounded timeout.
            }
        })) {
            var result = transport(endpoint.uri("/slow"), 40, secrets()).deliver(command());

            assertThat(result.status()).isEqualTo(EventChannelTransport.Status.TEMPORARY_FAILURE);
            assertThat(result.failureCode()).isEqualTo("WEBHOOK_TIMEOUT");
        }
    }

    @Test
    void classifiesProviderFailuresWithoutReturningProviderContent() throws Exception {
        try (var temporary = LocalHttpServer.start(exchange ->
                respond(exchange, 429, "secret temporary explanation"));
             var permanent = LocalHttpServer.start(exchange ->
                     respond(exchange, 400, "secret permanent explanation"))) {
            var temporaryResult = transport(temporary.uri("/events"), 1_000, secrets())
                    .deliver(command());
            var permanentResult = transport(permanent.uri("/events"), 1_000, secrets())
                    .deliver(command());

            assertThat(temporaryResult.status())
                    .isEqualTo(EventChannelTransport.Status.TEMPORARY_FAILURE);
            assertThat(temporaryResult.failureCode()).isEqualTo("WEBHOOK_PROVIDER_TEMPORARY");
            assertThat(permanentResult.status())
                    .isEqualTo(EventChannelTransport.Status.PERMANENT_FAILURE);
            assertThat(permanentResult.failureCode()).isEqualTo("WEBHOOK_PROVIDER_REJECTED");
            assertThat(temporaryResult.toString()).doesNotContain("explanation");
            assertThat(permanentResult.toString()).doesNotContain("explanation");
        }
    }

    @Test
    void rejectsPrivateLoopbackHttpAndUnsafeUriFormsOutsideExplicitTestMode() throws Exception {
        var production = properties(false);
        var privateAddress = InetAddress.getByAddress(new byte[]{10, 1, 2, 3});
        var privateTransport = direct(production,
                URI.create("https://internal.example.test/hooks"), 1_000, secrets(),
                host -> new InetAddress[]{privateAddress});

        var privateResult = privateTransport.deliver(command());
        assertThat(privateResult.status()).isEqualTo(EventChannelTransport.Status.PERMANENT_FAILURE);
        assertThat(privateResult.failureCode()).isEqualTo("WEBHOOK_TARGET_FORBIDDEN");

        var loopbackTransport = direct(production,
                URI.create("http://127.0.0.1:8080/hooks"), 1_000, secrets(),
                host -> new InetAddress[]{InetAddress.getLoopbackAddress()});
        assertThat(loopbackTransport.deliver(command()).failureCode())
                .isEqualTo("WEBHOOK_HTTPS_REQUIRED");

        var queryTransport = direct(properties(true),
                URI.create("http://127.0.0.1:8080/hooks?token=must-not-leak"),
                1_000, secrets(), host -> new InetAddress[]{InetAddress.getLoopbackAddress()});
        var queryResult = queryTransport.deliver(command());
        assertThat(queryResult.failureCode()).isEqualTo("WEBHOOK_ENDPOINT_INVALID");
        assertThat(queryResult.toString()).doesNotContain("must-not-leak").doesNotContain("hooks");
    }

    @Test
    void reportsDnsSecretAndTimeoutConfigurationFailuresWithStableSafeCodes() {
        var dnsFailure = direct(properties(false), URI.create("https://missing.example.test/hook"),
                1_000, secrets(), host -> {
                    throw new UnknownHostException("sensitive resolver detail");
                });
        var dnsResult = dnsFailure.deliver(command());
        assertThat(dnsResult.status()).isEqualTo(EventChannelTransport.Status.TEMPORARY_FAILURE);
        assertThat(dnsResult.failureCode()).isEqualTo("WEBHOOK_DNS_UNAVAILABLE");
        assertThat(dnsResult.toString()).doesNotContain("sensitive");

        var missingSecret = direct(properties(true), URI.create("http://127.0.0.1:8080/hook"),
                1_000, request -> Optional.empty(),
                host -> new InetAddress[]{InetAddress.getLoopbackAddress()});
        assertThat(missingSecret.deliver(command()).failureCode())
                .isEqualTo("WEBHOOK_SECRET_UNAVAILABLE");

        var invalidTimeout = direct(properties(true), URI.create("http://127.0.0.1:8080/hook"),
                30_001, secrets(), host -> new InetAddress[]{InetAddress.getLoopbackAddress()});
        assertThat(invalidTimeout.deliver(command()).failureCode())
                .isEqualTo("WEBHOOK_TIMEOUT_INVALID");
    }

    @Test
    void delegatesPublicHttpsToTheSharedHardenedOutboundBoundary() throws Exception {
        var captured = new AtomicReference<OutboundHttpTransport.Request>();
        OutboundHttpTransport outbound = request -> {
            captured.set(request);
            return new OutboundHttpTransport.Response(
                    204, "bounded-sensitive-response".getBytes(StandardCharsets.UTF_8),
                    Duration.ofMillis(12));
        };
        var properties = properties(false);
        var endpoint = URI.create("https://webhook.example.test/events");
        EventChannelTargetDirectory targets = (channel, system, tenant, member) -> Optional.of(
                new EventChannelTargetDirectory.Target(null, endpoint,
                        "env://EXAMINE_EVENT_WEBHOOK_SECRET", 750));
        var transport = new SignedWebhookTransport(properties, targets, secrets(), outbound,
                new ObjectMapper(), Clock.fixed(NOW, ZoneOffset.UTC),
                HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build(),
                host -> new InetAddress[]{InetAddress.getByAddress(
                        new byte[]{8, 8, 8, 8})}, new Ticker(), () -> "trace-shared-http");

        var result = transport.deliver(command());

        assertThat(result.status()).isEqualTo(EventChannelTransport.Status.SENT);
        assertThat(result.toString()).doesNotContain("bounded-sensitive-response");
        assertThat(captured.get().uri()).isEqualTo(endpoint);
        assertThat(captured.get().timeout()).isEqualTo(Duration.ofMillis(750));
        assertThat(captured.get().headers())
                .containsEntry(SignedWebhookTransport.HEADER_DELIVERY_ID, "101")
                .containsEntry(SignedWebhookTransport.HEADER_TRACE_ID, "trace-shared-http");
    }

    private static SignedWebhookTransport transport(
            URI endpoint, int timeoutMs, SecretResolverFacade secrets
    ) {
        return direct(properties(true), endpoint, timeoutMs, secrets,
                InetAddress::getAllByName);
    }

    private static SignedWebhookTransport direct(
            WebhookTransportProperties properties,
            URI endpoint,
            int timeoutMs,
            SecretResolverFacade secrets,
            SignedWebhookTransport.HostnameResolver resolver
    ) {
        EventChannelTargetDirectory targets = (channel, system, tenant, member) -> Optional.of(
                new EventChannelTargetDirectory.Target(null, endpoint,
                        "env://EXAMINE_EVENT_WEBHOOK_SECRET", timeoutMs));
        return new SignedWebhookTransport(properties, targets, secrets,
                request -> {
                    throw new AssertionError("Rejected/local targets must not use production HTTP");
                }, new ObjectMapper(), Clock.fixed(NOW, ZoneOffset.UTC),
                HttpClient.newBuilder()
                        .connectTimeout(properties.getConnectTimeout())
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build(), resolver, new Ticker(), () -> "trace-webhook-1");
    }

    private static WebhookTransportProperties properties(boolean allowLoopback) {
        var properties = new WebhookTransportProperties();
        properties.setEnabled(true);
        properties.setConnectTimeout(Duration.ofMillis(250));
        properties.setRequestTimeout(Duration.ofSeconds(2));
        properties.setAllowLoopbackHttpForTesting(allowLoopback);
        return properties;
    }

    private static SecretResolverFacade secrets() {
        return request -> Optional.of(SecretResolverFacade.ResolvedSecret.utf8(SECRET));
    }

    private static EventChannelTransport.DeliveryCommand command() {
        return new EventChannelTransport.DeliveryCommand(
                101, 7, 9, 11, "TASK_ASSIGNED", "task:42:assigned",
                "任务已分派", "任务 42 已分派给您。", "TASK", "42", "/tasks/42",
                Map.of("taskName", "季度复盘", "actor", "成员***"));
    }

    private static CapturedRequest capture(HttpExchange exchange) throws IOException {
        return new CapturedRequest(
                exchange.getRequestHeaders().getFirst(SignedWebhookTransport.HEADER_DELIVERY_ID),
                exchange.getRequestHeaders().getFirst(SignedWebhookTransport.HEADER_DEDUPE_KEY),
                exchange.getRequestHeaders().getFirst(SignedWebhookTransport.HEADER_TIMESTAMP),
                exchange.getRequestHeaders().getFirst(SignedWebhookTransport.HEADER_SIGNATURE),
                exchange.getRequestHeaders().getFirst(SignedWebhookTransport.HEADER_TRACE_ID),
                exchange.getRequestBody().readAllBytes());
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        var bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (var response = exchange.getResponseBody()) {
            response.write(bytes);
        }
    }

    private static String hmac(String secret, String prefix, byte[] body) throws Exception {
        var mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        mac.update(prefix.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(mac.doFinal(body));
    }

    private record CapturedRequest(
            String deliveryId,
            String dedupeKey,
            String timestamp,
            String signature,
            String traceId,
            byte[] body
    ) {
    }

    @FunctionalInterface
    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws IOException;
    }

    private static final class LocalHttpServer implements AutoCloseable {
        private final HttpServer server;

        private LocalHttpServer(HttpServer server) {
            this.server = server;
        }

        private static LocalHttpServer start(ExchangeHandler handler) throws IOException {
            var server = HttpServer.create(new InetSocketAddress(
                    InetAddress.getLoopbackAddress(), 0), 0);
            server.createContext("/", exchange -> handler.handle(exchange));
            server.start();
            return new LocalHttpServer(server);
        }

        private int port() {
            return server.getAddress().getPort();
        }

        private URI uri(String path) {
            return URI.create("http://127.0.0.1:" + port() + path);
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }

    private static final class Ticker implements java.util.function.LongSupplier {
        private long value;

        @Override
        public long getAsLong() {
            value += 1_000_000;
            return value;
        }
    }
}
