package com.unique.examine.event.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.api.OutboundHttpTransport;
import com.unique.examine.core.api.SecretResolverFacade;
import com.unique.examine.event.config.WebhookTransportProperties;
import com.unique.examine.event.domain.DeliveryChannel;
import com.unique.examine.event.port.EventChannelTargetDirectory;
import com.unique.examine.event.port.EventChannelTransport;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Signed, redirect-free JSON webhook transport with an outbound SSRF policy. */
public final class SignedWebhookTransport implements EventChannelTransport {
    public static final String HEADER_DELIVERY_ID = "X-Examine-Delivery-Id";
    public static final String HEADER_DEDUPE_KEY = "X-Examine-Dedupe-Key";
    public static final String HEADER_TIMESTAMP = "X-Examine-Timestamp";
    public static final String HEADER_SIGNATURE = "X-Examine-Signature";
    public static final String HEADER_TRACE_ID = "X-Examine-Trace-Id";
    private static final String MASKED_UNKNOWN = "webhook:[redacted]";
    private static final String HMAC_SHA_256 = "HmacSHA256";

    private final WebhookTransportProperties properties;
    private final EventChannelTargetDirectory targets;
    private final SecretResolverFacade secrets;
    private final OutboundHttpTransport outbound;
    private final ObjectMapper json;
    private final Clock clock;
    private final HttpClient localTestClient;
    private final HostnameResolver hostnameResolver;
    private final LongSupplier nanoTime;
    private final Supplier<String> traceIds;

    public SignedWebhookTransport(
            WebhookTransportProperties properties,
            EventChannelTargetDirectory targets,
            SecretResolverFacade secrets,
            OutboundHttpTransport outbound,
            ObjectMapper json,
            Clock clock
    ) {
        this(properties, targets, secrets, outbound, json, clock,
                HttpClient.newBuilder()
                        .connectTimeout(properties.getConnectTimeout())
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build(),
                InetAddress::getAllByName, System::nanoTime,
                () -> UUID.randomUUID().toString());
    }

    SignedWebhookTransport(
            WebhookTransportProperties properties,
            EventChannelTargetDirectory targets,
            SecretResolverFacade secrets,
            OutboundHttpTransport outbound,
            ObjectMapper json,
            Clock clock,
            HttpClient localTestClient,
            HostnameResolver hostnameResolver,
            LongSupplier nanoTime,
            Supplier<String> traceIds
    ) {
        this.properties = Objects.requireNonNull(properties, "Webhook properties are required");
        this.targets = Objects.requireNonNull(targets, "Channel target directory is required");
        this.secrets = Objects.requireNonNull(secrets, "SecretRef resolver is required");
        this.outbound = Objects.requireNonNull(outbound, "Outbound HTTP transport is required");
        this.json = Objects.requireNonNull(json, "ObjectMapper is required").copy();
        this.clock = Objects.requireNonNull(clock, "Clock is required");
        this.localTestClient = Objects.requireNonNull(localTestClient,
                "Local test HttpClient is required");
        this.hostnameResolver = Objects.requireNonNull(hostnameResolver,
                "Hostname resolver is required");
        this.nanoTime = Objects.requireNonNull(nanoTime, "Monotonic clock is required");
        this.traceIds = Objects.requireNonNull(traceIds, "Trace id supplier is required");
    }

    @Override
    public DeliveryChannel channel() {
        return DeliveryChannel.WEBHOOK;
    }

    @Override
    public DeliveryResult deliver(DeliveryCommand command) {
        var started = nanoTime.getAsLong();
        var traceId = safeTraceId();
        if (!valid(command)) {
            return failure(Status.PERMANENT_FAILURE, "WEBHOOK_COMMAND_INVALID",
                    MASKED_UNKNOWN, started, traceId);
        }
        if (!properties.isReady()) {
            return failure(Status.PERMANENT_FAILURE, "WEBHOOK_TRANSPORT_UNAVAILABLE",
                    MASKED_UNKNOWN, started, traceId);
        }

        final EventChannelTargetDirectory.Target target;
        try {
            target = targets.resolve(channel(), command.systemId(), command.tenantId(),
                    command.recipientMemberId()).orElse(null);
        } catch (RuntimeException unavailable) {
            return failure(Status.TEMPORARY_FAILURE, "WEBHOOK_TARGET_LOOKUP_FAILED",
                    MASKED_UNKNOWN, started, traceId);
        }
        if (target == null || target.endpoint() == null || target.secretRef() == null) {
            return failure(Status.PERMANENT_FAILURE, "WEBHOOK_TARGET_UNAVAILABLE",
                    MASKED_UNKNOWN, started, traceId);
        }
        var destination = maskEndpoint(target.endpoint());
        var requestTimeout = targetTimeout(target.timeoutMs());
        if (requestTimeout == null) {
            return failure(Status.PERMANENT_FAILURE, "WEBHOOK_TIMEOUT_INVALID",
                    destination, started, traceId);
        }

        final EndpointPolicy policy;
        try {
            policy = inspect(target.endpoint());
        } catch (UnknownHostException unresolved) {
            return failure(Status.TEMPORARY_FAILURE, "WEBHOOK_DNS_UNAVAILABLE",
                    destination, started, traceId);
        }
        if (!policy.allowed()) {
            return failure(Status.PERMANENT_FAILURE, policy.failureCode(),
                    destination, started, traceId);
        }

        SecretResolverFacade.ResolvedSecret resolvedSecret = null;
        byte[] key = null;
        try {
            resolvedSecret = resolve(command, target.secretRef());
            key = resolvedSecret.copyBytes();
            if (key.length < 16) {
                return failure(Status.PERMANENT_FAILURE, "WEBHOOK_SECRET_TOO_SHORT",
                        destination, started, traceId);
            }
            var timestamp = clock.instant().getEpochSecond();
            var body = payload(command, timestamp);
            var signature = sign(key, signatureInput(timestamp, command, body));
            var headers = new LinkedHashMap<String, String>();
            headers.put("Content-Type", "application/json; charset=utf-8");
            headers.put(HEADER_DELIVERY_ID, Long.toString(command.deliveryId()));
            headers.put(HEADER_DEDUPE_KEY, command.dedupeKey());
            headers.put(HEADER_TIMESTAMP, Long.toString(timestamp));
            headers.put(HEADER_SIGNATURE, "v1=" + signature);
            headers.put(HEADER_TRACE_ID, traceId);
            var statusCode = post(target.endpoint(), headers, body, requestTimeout,
                    policy.localTest());
            return response(statusCode, destination, started, traceId);
        } catch (SecretUnavailableException unavailable) {
            return failure(Status.PERMANENT_FAILURE, "WEBHOOK_SECRET_UNAVAILABLE",
                    destination, started, traceId);
        } catch (JsonProcessingException invalidPayload) {
            return failure(Status.PERMANENT_FAILURE, "WEBHOOK_PAYLOAD_INVALID",
                    destination, started, traceId);
        } catch (java.net.http.HttpTimeoutException timeout) {
            return failure(Status.TEMPORARY_FAILURE, "WEBHOOK_TIMEOUT",
                    destination, started, traceId);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return failure(Status.TEMPORARY_FAILURE, "WEBHOOK_INTERRUPTED",
                    destination, started, traceId);
        } catch (ConnectException unavailable) {
            return failure(Status.TEMPORARY_FAILURE, "WEBHOOK_CONNECT_FAILED",
                    destination, started, traceId);
        } catch (OutboundHttpTransport.TransportException unavailable) {
            return switch (unavailable.kind()) {
                case TIMEOUT -> failure(Status.TEMPORARY_FAILURE, "WEBHOOK_TIMEOUT",
                        destination, started, traceId);
                case IO, TLS -> failure(Status.TEMPORARY_FAILURE, "WEBHOOK_IO_FAILED",
                        destination, started, traceId);
                case UNSAFE_TARGET -> failure(Status.PERMANENT_FAILURE,
                        "WEBHOOK_TARGET_FORBIDDEN", destination, started, traceId);
                case RESPONSE_TOO_LARGE -> failure(Status.PERMANENT_FAILURE,
                        "WEBHOOK_RESPONSE_TOO_LARGE", destination, started, traceId);
            };
        } catch (IOException unavailable) {
            return failure(Status.TEMPORARY_FAILURE, "WEBHOOK_IO_FAILED",
                    destination, started, traceId);
        } catch (GeneralSecurityException unavailable) {
            return failure(Status.PERMANENT_FAILURE, "WEBHOOK_SIGNING_FAILED",
                    destination, started, traceId);
        } catch (IllegalArgumentException invalidRequest) {
            return failure(Status.PERMANENT_FAILURE, "WEBHOOK_REQUEST_INVALID",
                    destination, started, traceId);
        } catch (CompletionException unavailable) {
            return failure(Status.TEMPORARY_FAILURE, "WEBHOOK_DELIVERY_FAILED",
                    destination, started, traceId);
        } finally {
            if (resolvedSecret != null) resolvedSecret.close();
            wipe(key);
        }
    }

    private int post(
            URI endpoint,
            Map<String, String> headers,
            byte[] body,
            Duration timeout,
            boolean localTest
    ) throws IOException, InterruptedException, OutboundHttpTransport.TransportException {
        if (!localTest) {
            return outbound.post(new OutboundHttpTransport.Request(
                    endpoint, headers, body, timeout)).statusCode();
        }
        var request = HttpRequest.newBuilder(endpoint)
                .timeout(timeout)
                .headers(flatten(headers))
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        return localTestClient.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    private static String[] flatten(Map<String, String> headers) {
        var values = new String[headers.size() * 2];
        var index = 0;
        for (var entry : headers.entrySet()) {
            values[index++] = entry.getKey();
            values[index++] = entry.getValue();
        }
        return values;
    }

    private SecretResolverFacade.ResolvedSecret resolve(
            DeliveryCommand command, String secretRef
    ) {
        try {
            return secrets.resolve(new SecretResolverFacade.SecretRequest(
                    command.systemId(), command.tenantId(), secretRef))
                    .orElseThrow(SecretUnavailableException::new);
        } catch (RuntimeException unavailable) {
            throw new SecretUnavailableException();
        }
    }

    private DeliveryResult response(int statusCode, String destination,
                                    long started, String traceId) {
        if (statusCode >= HttpURLConnection.HTTP_OK && statusCode < 300) {
            return new DeliveryResult(Status.SENT, null, destination,
                    elapsedMillis(started), traceId);
        }
        if (statusCode == 408 || statusCode == 425 || statusCode == 429
                || statusCode >= 500) {
            return failure(Status.TEMPORARY_FAILURE, "WEBHOOK_PROVIDER_TEMPORARY",
                    destination, started, traceId);
        }
        return failure(Status.PERMANENT_FAILURE,
                statusCode >= 300 && statusCode < 400
                        ? "WEBHOOK_REDIRECT_REJECTED" : "WEBHOOK_PROVIDER_REJECTED",
                destination, started, traceId);
    }

    private Duration targetTimeout(Integer timeoutMs) {
        if (timeoutMs == null) return properties.getRequestTimeout();
        return timeoutMs < 1 || timeoutMs > 30_000
                ? null : Duration.ofMillis(timeoutMs);
    }

    private byte[] payload(DeliveryCommand command, long timestamp)
            throws JsonProcessingException {
        var event = new LinkedHashMap<String, Object>();
        event.put("templateCode", command.templateCode());
        event.put("subject", command.subject());
        event.put("body", command.body());
        event.put("targetType", command.targetType());
        event.put("targetId", command.targetId());
        event.put("targetPath", command.targetPath());
        event.put("variables", new TreeMap<>(command.variables()));

        var root = new LinkedHashMap<String, Object>();
        root.put("schemaVersion", "1");
        root.put("deliveryId", command.deliveryId());
        root.put("dedupeKey", command.dedupeKey());
        root.put("timestamp", timestamp);
        root.put("event", event);
        return json.writeValueAsBytes(root);
    }

    static byte[] signatureInput(long timestamp, DeliveryCommand command, byte[] body) {
        var prefix = timestamp + "." + command.deliveryId() + "." + command.dedupeKey() + ".";
        var prefixBytes = prefix.getBytes(StandardCharsets.UTF_8);
        var input = Arrays.copyOf(prefixBytes, prefixBytes.length + body.length);
        System.arraycopy(body, 0, input, prefixBytes.length, body.length);
        return input;
    }

    private static String sign(byte[] key, byte[] input) throws GeneralSecurityException {
        try {
            var mac = Mac.getInstance(HMAC_SHA_256);
            mac.init(new SecretKeySpec(key, HMAC_SHA_256));
            return java.util.HexFormat.of().formatHex(mac.doFinal(input));
        } finally {
            wipe(input);
        }
    }

    private EndpointPolicy inspect(URI endpoint) throws UnknownHostException {
        if (!absoluteWithoutCredentialsOrQuery(endpoint)) {
            return EndpointPolicy.reject("WEBHOOK_ENDPOINT_INVALID");
        }
        var addresses = hostnameResolver.resolve(endpoint.getHost());
        if (addresses == null || addresses.length == 0) throw new UnknownHostException();
        var allLoopback = Arrays.stream(addresses).allMatch(InetAddress::isLoopbackAddress);
        var httpTestEndpoint = "http".equalsIgnoreCase(endpoint.getScheme())
                && properties.isAllowLoopbackHttpForTesting() && allLoopback;
        if (!"https".equalsIgnoreCase(endpoint.getScheme()) && !httpTestEndpoint) {
            return EndpointPolicy.reject("WEBHOOK_HTTPS_REQUIRED");
        }
        if (httpTestEndpoint) return EndpointPolicy.allow(true);
        if (Arrays.stream(addresses).anyMatch(SignedWebhookTransport::restricted)) {
            return EndpointPolicy.reject("WEBHOOK_TARGET_FORBIDDEN");
        }
        return EndpointPolicy.allow(false);
    }

    private static boolean absoluteWithoutCredentialsOrQuery(URI endpoint) {
        return endpoint != null && endpoint.isAbsolute() && endpoint.getHost() != null
                && !endpoint.getHost().isBlank() && endpoint.getUserInfo() == null
                && endpoint.getRawQuery() == null && endpoint.getRawFragment() == null
                && endpoint.getPort() <= 65_535
                && endpoint.getPath() != null && endpoint.getPath().startsWith("/");
    }

    private static boolean restricted(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                || address.isMulticastAddress()) return true;
        var bytes = address.getAddress();
        if (address instanceof Inet4Address) {
            var first = Byte.toUnsignedInt(bytes[0]);
            var second = Byte.toUnsignedInt(bytes[1]);
            return first == 0 || first == 10 || first == 127 || first >= 224
                    || first == 100 && second >= 64 && second <= 127
                    || first == 169 && second == 254
                    || first == 172 && second >= 16 && second <= 31
                    || first == 192 && (second == 0 || second == 168)
                    || first == 198 && (second == 18 || second == 19)
                    || first == 203 && second == 0 && Byte.toUnsignedInt(bytes[2]) == 113;
        }
        if (address instanceof Inet6Address) {
            var first = Byte.toUnsignedInt(bytes[0]);
            var second = Byte.toUnsignedInt(bytes[1]);
            return (first & 0xfe) == 0xfc
                    || first == 0xfe && (second & 0xc0) == 0x80
                    || first == 0x20 && second == 0x01
                    && Byte.toUnsignedInt(bytes[2]) == 0x0d
                    && Byte.toUnsignedInt(bytes[3]) == 0xb8;
        }
        return true;
    }

    private DeliveryResult failure(Status status, String code, String destination,
                                   long started, String traceId) {
        return new DeliveryResult(status, code, destination, elapsedMillis(started), traceId);
    }

    private long elapsedMillis(long started) {
        return Math.max(0, Duration.ofNanos(Math.max(0, nanoTime.getAsLong() - started)).toMillis());
    }

    private String safeTraceId() {
        var value = traceIds.get();
        return value == null || value.isBlank() || hasLineBreak(value)
                ? UUID.randomUUID().toString() : value.strip();
    }

    private static boolean valid(DeliveryCommand command) {
        return command != null && command.deliveryId() > 0 && command.systemId() > 0
                && command.tenantId() > 0 && command.recipientMemberId() > 0
                && bounded(command.templateCode(), 100, false)
                && bounded(command.dedupeKey(), 256, false)
                && bounded(command.subject(), 998, true)
                && bounded(command.body(), 1_048_576, true)
                && optional(command.targetType(), 100)
                && optional(command.targetId(), 200)
                && optional(command.targetPath(), 1_000)
                && command.variables().size() <= 100;
    }

    private static boolean bounded(String value, int max, boolean multiline) {
        return value != null && !value.isBlank() && value.length() <= max
                && (multiline || !hasLineBreak(value));
    }

    private static boolean optional(String value, int max) {
        return value == null || value.length() <= max;
    }

    private static String maskEndpoint(URI endpoint) {
        if (endpoint == null || endpoint.getHost() == null) return MASKED_UNKNOWN;
        var port = endpoint.getPort() < 0 ? "" : ":" + endpoint.getPort();
        return endpoint.getScheme() + "://" + endpoint.getHost() + port + "/[redacted]";
    }

    private static boolean hasLineBreak(String value) {
        return value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0;
    }

    private static void wipe(byte[] value) {
        if (value != null) Arrays.fill(value, (byte) 0);
    }

    @FunctionalInterface
    interface HostnameResolver {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }

    private record EndpointPolicy(boolean allowed, String failureCode, boolean localTest) {
        private static EndpointPolicy allow(boolean localTest) {
            return new EndpointPolicy(true, null, localTest);
        }

        private static EndpointPolicy reject(String failureCode) {
            return new EndpointPolicy(false, failureCode, false);
        }
    }

    private static final class SecretUnavailableException extends RuntimeException {
    }
}
