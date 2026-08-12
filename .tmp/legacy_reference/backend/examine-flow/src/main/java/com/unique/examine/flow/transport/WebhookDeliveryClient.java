package com.unique.examine.flow.transport;

import com.unique.examine.core.api.OutboundHttpTransport;
import com.unique.examine.core.api.SecretResolverFacade;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Objects;

/**
 * Stateless Webhook protocol adapter. Transactions and leases are owned by
 * the worker; this class performs only one network attempt.
 */
public final class WebhookDeliveryClient {
    private static final long MAXIMUM_BACKOFF_SECONDS = 3600;

    private final SecretResolverFacade secrets;
    private final OutboundHttpTransport transport;
    private final WebhookTargetPolicy targets;
    private final WebhookPayloadEncoder payloads;
    private final Clock clock;

    public WebhookDeliveryClient(
            SecretResolverFacade secrets,
            OutboundHttpTransport transport,
            WebhookTargetPolicy targets,
            WebhookPayloadEncoder payloads,
            Clock clock
    ) {
        this.secrets = Objects.requireNonNull(secrets, "secrets");
        this.transport = Objects.requireNonNull(transport, "transport");
        this.targets = Objects.requireNonNull(targets, "targets");
        this.payloads = Objects.requireNonNull(payloads, "payloads");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public void validateConfiguration(
            long systemId,
            long tenantId,
            WebhookConfiguration configuration
    ) {
        Objects.requireNonNull(configuration, "configuration");
        targets.requireSafe(configuration.url());
        if (configuration.secretRef() == null) {
            return;
        }
        try (var ignored = secrets.resolve(
                new SecretResolverFacade.SecretRequest(
                        systemId, tenantId, configuration.secretRef())
        ).orElseThrow(() -> new InvalidWebhookConfigurationException(
                "Webhook secret reference cannot be resolved"))) {
            // Resolution is the validation. Plaintext is cleared on close.
        }
    }

    public DeliveryResult deliver(
            long systemId,
            long tenantId,
            WebhookConfiguration configuration,
            WebhookPayloadEncoder.DeliveryFacts facts,
            int attemptNumber
    ) {
        Objects.requireNonNull(configuration, "configuration");
        if (attemptNumber < 1
                || attemptNumber > configuration.maxAttempts()) {
            throw new IllegalArgumentException(
                    "Webhook attempt number is invalid");
        }
        final byte[] body;
        final java.net.URI uri;
        try {
            uri = targets.requireSafe(configuration.url());
            body = payloads.encode(facts);
        } catch (RuntimeException failure) {
            return terminal(
                    attemptNumber,
                    "WEBHOOK_CONFIGURATION_INVALID",
                    "Webhook delivery configuration is invalid"
            );
        }
        return deliverPrepared(
                systemId,
                tenantId,
                configuration,
                facts.deliveryId(),
                body,
                uri,
                attemptNumber
        );
    }

    /**
     * Sends the immutable canonical payload persisted with an execution.
     */
    public DeliveryResult deliverCanonicalPayload(
            long systemId,
            long tenantId,
            WebhookConfiguration configuration,
            long deliveryId,
            byte[] canonicalPayload,
            int attemptNumber
    ) {
        Objects.requireNonNull(configuration, "configuration");
        if (deliveryId <= 0
                || canonicalPayload == null
                || canonicalPayload.length == 0
                || canonicalPayload.length
                        > WebhookPayloadEncoder.MAXIMUM_PAYLOAD_BYTES
                || attemptNumber < 1
                || attemptNumber > configuration.maxAttempts()) {
            throw new IllegalArgumentException(
                    "Canonical webhook delivery is invalid");
        }
        final java.net.URI uri;
        try {
            uri = targets.requireSafe(configuration.url());
        } catch (RuntimeException failure) {
            return terminal(
                    attemptNumber,
                    "WEBHOOK_CONFIGURATION_INVALID",
                    "Webhook delivery configuration is invalid"
            );
        }
        return deliverPrepared(
                systemId,
                tenantId,
                configuration,
                deliveryId,
                canonicalPayload.clone(),
                uri,
                attemptNumber
        );
    }

    private DeliveryResult deliverPrepared(
            long systemId,
            long tenantId,
            WebhookConfiguration configuration,
            long deliveryId,
            byte[] body,
            java.net.URI uri,
            int attemptNumber
    ) {
        var timestamp = Long.toString(clock.instant().getEpochSecond());
        var headers = new LinkedHashMap<String, String>();
        headers.put("Content-Type", "application/json");
        headers.put(
                "X-Examine-Delivery-Id",
                Long.toString(deliveryId));
        headers.put("X-Examine-Timestamp", timestamp);
        if (configuration.secretRef() != null) {
            final SecretResolverFacade.ResolvedSecret secret;
            try {
                secret = secrets.resolve(
                        new SecretResolverFacade.SecretRequest(
                                systemId,
                                tenantId,
                                configuration.secretRef()
                        )
                ).orElse(null);
            } catch (RuntimeException failure) {
                return terminal(
                        attemptNumber,
                        "WEBHOOK_SECRET_UNAVAILABLE",
                        "Webhook signing secret is unavailable");
            }
            if (secret == null) {
                return terminal(
                        attemptNumber,
                        "WEBHOOK_SECRET_UNAVAILABLE",
                        "Webhook signing secret is unavailable");
            }
            try (secret) {
                headers.put(
                        "X-Examine-Signature",
                        "v1=" + signature(
                                secret, timestamp, body));
            }
        }
        final OutboundHttpTransport.Response response;
        try {
            response = transport.post(
                    new OutboundHttpTransport.Request(
                            uri,
                            headers,
                            body,
                            Duration.ofSeconds(
                                    configuration.timeoutSeconds())
                    )
            );
        } catch (OutboundHttpTransport.TransportException failure) {
            return transportFailure(
                    attemptNumber, configuration, failure.kind());
        } catch (RuntimeException failure) {
            return retryable(
                    attemptNumber,
                    configuration,
                    null,
                    null,
                    null,
                    "WEBHOOK_IO_FAILURE",
                    "Webhook delivery failed"
            );
        }
        var responseHash = sha256(response.body());
        if (response.statusCode() >= 200
                && response.statusCode() <= 299) {
            return new DeliveryResult(
                    Outcome.SUCCEEDED,
                    attemptNumber,
                    response.statusCode(),
                    response.duration().toMillis(),
                    responseHash,
                    null,
                    null,
                    0
            );
        }
        if (response.statusCode() == 408
                || response.statusCode() == 425
                || response.statusCode() == 429
                || response.statusCode() >= 500) {
            return retryable(
                    attemptNumber,
                    configuration,
                    response.statusCode(),
                    response.duration().toMillis(),
                    responseHash,
                    "WEBHOOK_HTTP_RETRYABLE",
                    "Webhook endpoint returned a retryable status"
            );
        }
        return new DeliveryResult(
                Outcome.TERMINAL_FAILURE,
                attemptNumber,
                response.statusCode(),
                response.duration().toMillis(),
                responseHash,
                "WEBHOOK_HTTP_TERMINAL",
                "Webhook endpoint returned a terminal status",
                0
        );
    }

    private DeliveryResult transportFailure(
            int attemptNumber,
            WebhookConfiguration configuration,
            OutboundHttpTransport.TransportException.Kind kind
    ) {
        return switch (kind) {
            case TIMEOUT -> retryable(
                    attemptNumber, configuration, null, null, null,
                    "WEBHOOK_TIMEOUT", "Webhook delivery timed out");
            case IO, TLS -> retryable(
                    attemptNumber, configuration, null, null, null,
                    "WEBHOOK_IO_FAILURE", "Webhook delivery failed");
            case UNSAFE_TARGET -> terminal(
                    attemptNumber,
                    "WEBHOOK_TARGET_UNSAFE",
                    "Webhook target is not permitted");
            case RESPONSE_TOO_LARGE -> terminal(
                    attemptNumber,
                    "WEBHOOK_RESPONSE_TOO_LARGE",
                    "Webhook response exceeded the safe limit");
        };
    }

    private DeliveryResult retryable(
            int attemptNumber,
            WebhookConfiguration configuration,
            Integer httpStatus,
            Long durationMillis,
            String responseSha256,
            String code,
            String message
    ) {
        if (attemptNumber >= configuration.maxAttempts()) {
            return new DeliveryResult(
                    Outcome.TERMINAL_FAILURE,
                    attemptNumber,
                    httpStatus,
                    durationMillis,
                    responseSha256,
                    code,
                    message,
                    0
            );
        }
        return new DeliveryResult(
                Outcome.RETRYABLE_FAILURE,
                attemptNumber,
                httpStatus,
                durationMillis,
                responseSha256,
                code,
                message,
                backoffSeconds(
                        configuration.baseBackoffSeconds(),
                        attemptNumber)
        );
    }

    private static DeliveryResult terminal(
            int attemptNumber,
            String code,
            String message
    ) {
        return new DeliveryResult(
                Outcome.TERMINAL_FAILURE,
                attemptNumber,
                null,
                null,
                null,
                code,
                message,
                0
        );
    }

    static long backoffSeconds(int baseSeconds, int attemptNumber) {
        long value = baseSeconds;
        for (var index = 1; index < attemptNumber; index++) {
            value = Math.min(MAXIMUM_BACKOFF_SECONDS, value * 2);
        }
        return value;
    }

    private static String signature(
            SecretResolverFacade.ResolvedSecret secret,
            String timestamp,
            byte[] body
    ) {
        var key = secret.copyBytes();
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            mac.update(timestamp.getBytes(StandardCharsets.UTF_8));
            mac.update((byte) '.');
            return HexFormat.of().formatHex(mac.doFinal(body));
        } catch (Exception failure) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable", failure);
        } finally {
            Arrays.fill(key, (byte) 0);
        }
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 is unavailable", failure);
        }
    }

    public record WebhookConfiguration(
            String url,
            String secretRef,
            int timeoutSeconds,
            int maxAttempts,
            int baseBackoffSeconds
    ) {
        public WebhookConfiguration {
            if (url == null
                    || url.isBlank()
                    || url.length() > 1024
                    || timeoutSeconds < 1
                    || timeoutSeconds > 30
                    || maxAttempts < 1
                    || maxAttempts > 10
                    || baseBackoffSeconds < 1
                    || baseBackoffSeconds > 300) {
                throw new InvalidWebhookConfigurationException(
                        "Webhook configuration is invalid");
            }
            url = url.strip();
            if (secretRef != null) {
                secretRef = secretRef.strip();
                if (secretRef.isEmpty()
                        || secretRef.length() > 512
                        || WebhookTargetPolicy.maskedSecret(secretRef)) {
                    throw new InvalidWebhookConfigurationException(
                            "Webhook secret reference is invalid");
                }
            }
        }
    }

    public record DeliveryResult(
            Outcome outcome,
            int attemptNumber,
            Integer httpStatus,
            Long durationMillis,
            String responseSha256,
            String failureCode,
            String failureMessage,
            long backoffSeconds
    ) {
        public DeliveryResult {
            Objects.requireNonNull(outcome, "outcome");
            if (attemptNumber < 1
                    || durationMillis != null && durationMillis < 0
                    || responseSha256 != null
                            && !responseSha256.matches("^[0-9a-f]{64}$")
                    || backoffSeconds < 0
                    || outcome == Outcome.RETRYABLE_FAILURE
                            && backoffSeconds < 1
                    || outcome != Outcome.RETRYABLE_FAILURE
                            && backoffSeconds != 0) {
                throw new IllegalArgumentException(
                        "Webhook delivery result is invalid");
            }
        }
    }

    public enum Outcome {
        SUCCEEDED,
        RETRYABLE_FAILURE,
        TERMINAL_FAILURE
    }

    public static final class InvalidWebhookConfigurationException
            extends IllegalArgumentException {
        public InvalidWebhookConfigurationException(String message) {
            super(message);
        }
    }
}
