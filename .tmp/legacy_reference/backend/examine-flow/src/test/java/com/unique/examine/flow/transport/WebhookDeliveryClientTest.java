package com.unique.examine.flow.transport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.api.OutboundHttpTransport;
import com.unique.examine.core.api.SecretResolverFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebhookDeliveryClientTest {
    private static final Instant NOW =
            Instant.parse("2026-07-31T06:00:00Z");

    private CapturingTransport transport;
    private SecretResolverFacade.ResolvedSecret resolvedSecret;
    private WebhookDeliveryClient client;

    @BeforeEach
    void setUp() throws Exception {
        transport = new CapturingTransport();
        resolvedSecret = SecretResolverFacade.ResolvedSecret.utf8(
                "top-secret");
        var targets = new WebhookTargetPolicy(host -> new InetAddress[]{
                InetAddress.getByAddress(
                        host, new byte[]{93, (byte) 184, (byte) 216, 34})
        });
        client = new WebhookDeliveryClient(
                request -> Optional.of(resolvedSecret),
                transport,
                targets,
                new WebhookPayloadEncoder(new ObjectMapper()),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void signsCanonicalPayloadAndNeverIncludesSensitiveDecisionContent()
            throws Exception {
        transport.responses.add(new OutboundHttpTransport.Response(
                204,
                "accepted".getBytes(StandardCharsets.UTF_8),
                Duration.ofMillis(25)
        ));
        var result = client.deliver(
                10,
                20,
                configuration(3),
                facts(),
                1
        );

        assertThat(result.outcome())
                .isEqualTo(WebhookDeliveryClient.Outcome.SUCCEEDED);
        assertThat(result.httpStatus()).isEqualTo(204);
        assertThat(result.durationMillis()).isEqualTo(25);
        assertThat(result.responseSha256())
                .matches("^[0-9a-f]{64}$");
        assertThat(transport.requests).singleElement().satisfies(request -> {
            var body = new String(
                    request.body(), StandardCharsets.UTF_8);
            assertThat(body)
                    .contains("\"version\":1")
                    .contains("\"evidenceId\":\"700\"")
                    .doesNotContain(
                            "typedSignature",
                            "comment",
                            "storage",
                            "mutable");
            assertThat(request.headers())
                    .containsEntry("Content-Type", "application/json")
                    .containsEntry("X-Examine-Delivery-Id", "900")
                    .containsEntry(
                            "X-Examine-Timestamp",
                            Long.toString(NOW.getEpochSecond()))
                    .containsEntry(
                            "X-Examine-Signature",
                            "v1=" + expectedSignature(
                                    "top-secret",
                                    Long.toString(NOW.getEpochSecond()),
                                    request.body()));
        });
        assertThatThrownBy(resolvedSecret::copyBytes)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void classifiesRetryableThenSuccessfulResponsesWithBoundedBackoff() {
        transport.responses.add(new OutboundHttpTransport.Response(
                503, new byte[0], Duration.ofMillis(5)));
        transport.responses.add(new OutboundHttpTransport.Response(
                200, new byte[0], Duration.ofMillis(3)));

        var first = client.deliver(
                10, 20, configuration(3), facts(), 2);
        resolvedSecret = SecretResolverFacade.ResolvedSecret.utf8(
                "top-secret");
        var second = client.deliver(
                10, 20, configuration(3), facts(), 3);

        assertThat(first.outcome())
                .isEqualTo(
                        WebhookDeliveryClient.Outcome.RETRYABLE_FAILURE);
        assertThat(first.failureCode())
                .isEqualTo("WEBHOOK_HTTP_RETRYABLE");
        assertThat(first.backoffSeconds()).isEqualTo(20);
        assertThat(second.outcome())
                .isEqualTo(WebhookDeliveryClient.Outcome.SUCCEEDED);
        assertThat(second.backoffSeconds()).isZero();
    }

    @Test
    void rejectsPrivateResolutionAndUnresolvedSecretsDuringPreflight()
            throws Exception {
        var privateTargets = new WebhookTargetPolicy(host ->
                new InetAddress[]{InetAddress.getByName("127.0.0.1")});
        assertThatThrownBy(() ->
                privateTargets.requireSafe("https://localhost/hook"))
                .isInstanceOf(
                        WebhookTargetPolicy
                                .UnsafeWebhookTargetException.class)
                .hasMessageContaining("non-public");

        var publicTargets = new WebhookTargetPolicy(host ->
                new InetAddress[]{InetAddress.getByAddress(
                        new byte[]{93, (byte) 184, (byte) 216, 34})});
        var unresolved = new WebhookDeliveryClient(
                request -> Optional.empty(),
                transport,
                publicTargets,
                new WebhookPayloadEncoder(new ObjectMapper()),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        assertThatThrownBy(() -> unresolved.validateConfiguration(
                10, 20, configuration(3)))
                .isInstanceOf(
                        WebhookDeliveryClient
                                .InvalidWebhookConfigurationException.class)
                .hasMessageContaining("cannot be resolved");
    }

    @Test
    void timeoutIsSanitizedAndStopsAtMaximumAttempts() {
        transport.failure =
                new OutboundHttpTransport.TransportException(
                        OutboundHttpTransport.TransportException.Kind.TIMEOUT,
                        "internal socket address must not escape");

        var retrying = client.deliver(
                10, 20, configuration(3), facts(), 1);
        resolvedSecret = SecretResolverFacade.ResolvedSecret.utf8(
                "top-secret");
        var terminal = client.deliver(
                10, 20, configuration(3), facts(), 3);

        assertThat(retrying.outcome())
                .isEqualTo(
                        WebhookDeliveryClient.Outcome.RETRYABLE_FAILURE);
        assertThat(retrying.failureCode()).isEqualTo("WEBHOOK_TIMEOUT");
        assertThat(retrying.failureMessage())
                .doesNotContain("socket", "address");
        assertThat(terminal.outcome())
                .isEqualTo(
                        WebhookDeliveryClient.Outcome.TERMINAL_FAILURE);
        assertThat(terminal.backoffSeconds()).isZero();
    }

    @Test
    void exponentialBackoffIsCapped() {
        assertThat(WebhookDeliveryClient.backoffSeconds(300, 1))
                .isEqualTo(300);
        assertThat(WebhookDeliveryClient.backoffSeconds(300, 10))
                .isEqualTo(3600);
    }

    private static WebhookDeliveryClient.WebhookConfiguration configuration(
            int maximumAttempts
    ) {
        return new WebhookDeliveryClient.WebhookConfiguration(
                "https://example.com/hooks/approval",
                "vault://flow/webhook",
                10,
                maximumAttempts,
                10
        );
    }

    private static WebhookPayloadEncoder.DeliveryFacts facts() {
        return new WebhookPayloadEncoder.DeliveryFacts(
                900,
                10,
                20,
                300,
                400,
                2,
                1,
                "notify",
                "Notify ERP",
                "approval-001",
                500,
                new WebhookPayloadEncoder.TriggerReference(
                        "purchase_order", "RECORD_CREATED"),
                new WebhookPayloadEncoder.RecordReference(
                        "purchase_order", 600),
                List.of(new WebhookPayloadEncoder.DecisionReference(
                        2, "APPROVED", 700L))
        );
    }

    private static String expectedSignature(
            String secret,
            String timestamp,
            byte[] body
    ) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"));
            mac.update(timestamp.getBytes(StandardCharsets.UTF_8));
            mac.update((byte) '.');
            return HexFormat.of().formatHex(mac.doFinal(body));
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static final class CapturingTransport
            implements OutboundHttpTransport {
        private final List<Request> requests = new java.util.ArrayList<>();
        private final ArrayDeque<Response> responses = new ArrayDeque<>();
        private TransportException failure;

        @Override
        public Response post(Request request) throws TransportException {
            requests.add(request);
            if (failure != null) {
                throw failure;
            }
            return responses.removeFirst();
        }
    }
}
