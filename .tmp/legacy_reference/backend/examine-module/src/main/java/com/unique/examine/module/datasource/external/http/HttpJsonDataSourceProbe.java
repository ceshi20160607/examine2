package com.unique.examine.module.datasource.external.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.api.OutboundHttpTransport;
import com.unique.examine.core.api.SecretResolverFacade;
import com.unique.examine.module.datasource.domain.DataSourceActor;
import com.unique.examine.module.datasource.domain.DataSourceDraft;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Package-local, single-attempt HTTP probe shared by connection checking and
 * schema discovery and draft preview. Raw response values never leave this
 * package boundary.
 */
final class HttpJsonDataSourceProbe {
    static final int MAXIMUM_RESPONSE_BYTES = 65_536;
    static final int MAXIMUM_FIELDS = 50;
    static final int MAXIMUM_DEPTH = 8;
    static final int MAXIMUM_FIELD_NAME_LENGTH = 128;
    static final int MAXIMUM_SCALAR_LENGTH = 4_096;

    private static final int MAXIMUM_BEARER_LENGTH = 2_000;
    private static final long MAXIMUM_SAFE_DURATION_MILLIS = 30_000;
    private static final Pattern SECRET_ALIAS =
            Pattern.compile("^[A-Z][A-Z0-9_]{0,127}$");

    private final OutboundHttpTransport transport;
    private final SecretResolverFacade secrets;
    private final ObjectMapper json;
    private final HttpDataSourceHostPolicy hosts;

    HttpJsonDataSourceProbe(
            OutboundHttpTransport transport,
            SecretResolverFacade secrets,
            ObjectMapper json,
            HttpDataSourceCheckProperties properties
    ) {
        this.transport = Objects.requireNonNull(transport, "transport");
        this.secrets = Objects.requireNonNull(secrets, "secrets");
        this.hosts = new HttpDataSourceHostPolicy(properties);
        this.json = strictCopy(Objects.requireNonNull(json, "json"));
    }

    Result execute(
            DataSourceActor actor,
            DataSourceDraft.HttpJsonConnection connection,
            Purpose purpose
    ) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(purpose, "purpose");
        final URI endpoint;
        try {
            endpoint = hosts.requireAllowed(connection.endpoint());
        } catch (HttpDataSourceHostPolicy.UnsafeTargetException rejected) {
            return failure(purpose, "SAFE_TARGET", null, 0);
        }

        var secretRef = connection.authSecretRef();
        if (secretRef == null) {
            return send(endpoint, connection.timeoutSeconds(), Map.of(),
                    purpose);
        }
        if (!scopedSecretReference(
                actor.systemId(), actor.tenantId(), secretRef)) {
            return failure(purpose, "SECRET_UNAVAILABLE", null, 0);
        }

        final SecretResolverFacade.ResolvedSecret resolved;
        try {
            resolved = secrets.resolve(new SecretResolverFacade.SecretRequest(
                            actor.systemId(), actor.tenantId(), secretRef))
                    .orElse(null);
        } catch (RuntimeException unavailable) {
            return failure(purpose, "SECRET_UNAVAILABLE", null, 0);
        }
        if (resolved == null) {
            return failure(purpose, "SECRET_UNAVAILABLE", null, 0);
        }
        try (resolved) {
            var bearer = bearer(resolved);
            if (bearer == null) {
                return failure(
                        purpose, "SECRET_UNAVAILABLE", null, 0);
            }
            return send(endpoint, connection.timeoutSeconds(),
                    Map.of("Authorization", "Bearer " + bearer), purpose);
        }
    }

    private Result send(
            URI endpoint,
            int timeoutSeconds,
            Map<String, String> authorization,
            Purpose purpose
    ) {
        var headers = new LinkedHashMap<String, String>();
        headers.put("Content-Type", "application/json");
        headers.put("Accept", "application/json");
        headers.putAll(authorization);
        final OutboundHttpTransport.Response response;
        var started = System.nanoTime();
        try {
            response = transport.post(new OutboundHttpTransport.Request(
                    endpoint, headers, purpose.body(),
                    Duration.ofSeconds(timeoutSeconds)));
        } catch (OutboundHttpTransport.TransportException failure) {
            return transportFailure(
                    purpose, failure.kind(), elapsedMillis(started));
        } catch (RuntimeException failure) {
            return failure(purpose, "IO", null, elapsedMillis(started));
        }
        if (response == null) {
            return failure(purpose, "IO", null, elapsedMillis(started));
        }

        final long duration;
        final int status;
        final byte[] body;
        try {
            duration = safeDurationMillis(response.duration());
            status = response.statusCode();
            body = response.body();
        } catch (RuntimeException failure) {
            return failure(purpose, "IO", null, elapsedMillis(started));
        }
        if (body.length > MAXIMUM_RESPONSE_BYTES) {
            Arrays.fill(body, (byte) 0);
            return failure(purpose, "RESPONSE_TOO_LARGE", null, duration);
        }
        if (status < 200 || status >= 300) {
            Arrays.fill(body, (byte) 0);
            return failure(purpose, "HTTP_STATUS", status, duration);
        }

        final JsonNode root;
        try {
            root = json.readTree(body);
        } catch (JsonProcessingException failure) {
            return failure(purpose, "JSON_INVALID", status, duration);
        } catch (IOException | RuntimeException failure) {
            return failure(purpose, "JSON_INVALID", status, duration);
        } finally {
            Arrays.fill(body, (byte) 0);
        }
        var contractFailure = contractFailure(root, purpose);
        if (contractFailure != null) {
            return failure(purpose, contractFailure, status, duration);
        }
        return new Result(
                true, true, status, duration, "SUCCESS",
                purpose.message("SUCCESS"), root.path("rows"));
    }

    private static Result transportFailure(
            Purpose purpose,
            OutboundHttpTransport.TransportException.Kind kind,
            long duration
    ) {
        return switch (kind) {
            case UNSAFE_TARGET -> failure(
                    purpose, "SAFE_TARGET", null, duration);
            case TIMEOUT -> failure(purpose, "TIMEOUT", null, duration);
            case TLS -> failure(purpose, "TLS", null, duration);
            case RESPONSE_TOO_LARGE -> failure(
                    purpose, "RESPONSE_TOO_LARGE", null, duration);
            case IO -> failure(purpose, "IO", null, duration);
        };
    }

    private static Result failure(
            Purpose purpose,
            String code,
            Integer status,
            long duration
    ) {
        return new Result(
                status != null,
                false,
                status,
                boundedDuration(duration),
                code,
                purpose.message(code),
                null);
    }

    private static boolean scopedSecretReference(
            long systemId,
            long tenantId,
            String reference
    ) {
        var prefix = "env://EXAMINE_DS_S" + systemId
                + "_T" + tenantId + "_";
        if (!reference.startsWith(prefix)) {
            return false;
        }
        var versionMarker = reference.lastIndexOf("_V");
        if (versionMarker <= prefix.length()
                || versionMarker + 2 >= reference.length()) {
            return false;
        }
        var alias = reference.substring(prefix.length(), versionMarker);
        var version = reference.substring(versionMarker + 2);
        return SECRET_ALIAS.matcher(alias).matches()
                && version.matches("^[1-9][0-9]{0,8}$");
    }

    private static String bearer(
            SecretResolverFacade.ResolvedSecret resolved
    ) {
        var bytes = resolved.copyBytes();
        try {
            if (bytes.length == 0 || bytes.length > MAXIMUM_BEARER_LENGTH) {
                return null;
            }
            final String value;
            try {
                value = StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(bytes)).toString();
            } catch (CharacterCodingException invalid) {
                return null;
            }
            if (value.isEmpty()
                    || value.length() > MAXIMUM_BEARER_LENGTH) {
                return null;
            }
            for (int index = 0; index < value.length(); index++) {
                var character = value.charAt(index);
                if (character < 0x21 || character > 0x7e) {
                    return null;
                }
            }
            return value;
        } finally {
            Arrays.fill(bytes, (byte) 0);
        }
    }

    private static String contractFailure(JsonNode root, Purpose purpose) {
        if (root == null || !root.isObject() || depth(root) > MAXIMUM_DEPTH) {
            return "CONTRACT_INVALID";
        }
        var rows = root.get("rows");
        if (rows == null
                || !rows.isArray()
                || rows.size() > purpose.maximumRows()) {
            return "CONTRACT_INVALID";
        }
        for (var row : rows) {
            if (!row.isObject() || row.size() > MAXIMUM_FIELDS) {
                return "CONTRACT_INVALID";
            }
            var fields = row.fields();
            while (fields.hasNext()) {
                var field = fields.next();
                if (!validSourceName(field.getKey())) {
                    return purpose == Purpose.DISCOVERY
                            ? "SCHEMA_FIELD_INVALID"
                            : "CONTRACT_INVALID";
                }
                if (!scalar(field.getValue())) {
                    return "CONTRACT_INVALID";
                }
            }
        }
        return null;
    }

    static boolean validSourceName(String value) {
        if (value == null
                || value.isEmpty()
                || !value.equals(value.strip())
                || value.codePointCount(0, value.length())
                > MAXIMUM_FIELD_NAME_LENGTH) {
            return false;
        }
        for (int offset = 0; offset < value.length();) {
            var character = value.charAt(offset);
            if (Character.isHighSurrogate(character)) {
                if (offset + 1 >= value.length()
                        || !Character.isLowSurrogate(
                        value.charAt(offset + 1))) {
                    return false;
                }
            } else if (Character.isLowSurrogate(character)) {
                return false;
            }
            var codePoint = value.codePointAt(offset);
            var type = Character.getType(codePoint);
            if (type == Character.CONTROL || type == Character.FORMAT) {
                return false;
            }
            offset += Character.charCount(codePoint);
        }
        return true;
    }

    private static boolean scalar(JsonNode value) {
        if (value == null || value.isNull()) {
            return true;
        }
        return value.isValueNode()
                && value.asText().length() <= MAXIMUM_SCALAR_LENGTH;
    }

    private static int depth(JsonNode root) {
        var pending = new ArrayDeque<NodeDepth>();
        pending.add(new NodeDepth(root, 1));
        var maximum = 0;
        while (!pending.isEmpty()) {
            var current = pending.removeFirst();
            maximum = Math.max(maximum, current.depth());
            if (maximum > MAXIMUM_DEPTH) {
                return maximum;
            }
            current.node().elements().forEachRemaining(child ->
                    pending.addLast(new NodeDepth(
                            child, current.depth() + 1)));
        }
        return maximum;
    }

    private static ObjectMapper strictCopy(ObjectMapper source) {
        var copy = source.copy();
        copy.getFactory().setStreamReadConstraints(
                StreamReadConstraints.builder()
                        .maxNestingDepth(256)
                        .maxStringLength(MAXIMUM_RESPONSE_BYTES)
                        .maxNumberLength(1_024)
                        .build());
        copy.getFactory().configure(
                StreamReadFeature.STRICT_DUPLICATE_DETECTION.mappedFeature(),
                true);
        copy.enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        copy.enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
        copy.enable(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS);
        return copy;
    }

    private static long elapsedMillis(long started) {
        return boundedDuration(
                Math.max(0, (System.nanoTime() - started) / 1_000_000));
    }

    private static long safeDurationMillis(Duration duration) {
        try {
            return boundedDuration(duration.toMillis());
        } catch (ArithmeticException overflow) {
            return MAXIMUM_SAFE_DURATION_MILLIS;
        }
    }

    private static long boundedDuration(long duration) {
        return Math.min(
                MAXIMUM_SAFE_DURATION_MILLIS, Math.max(0, duration));
    }

    enum Purpose {
        CHECK(1, 200),
        DISCOVERY(25, 25),
        PREVIEW(25, 25),
        PUBLISHED_ROWS(25, 25);

        private final byte[] body;
        private final int maximumRows;

        Purpose(int size, int maximumRows) {
            body = ("{\"page\":1,\"size\":" + size + "}")
                    .getBytes(StandardCharsets.UTF_8);
            this.maximumRows = maximumRows;
        }

        byte[] body() {
            return body.clone();
        }

        int maximumRows() {
            return maximumRows;
        }

        String message(String code) {
            return switch (code) {
                case "SUCCESS" -> switch (this) {
                    case CHECK -> "Connection check succeeded";
                    case DISCOVERY -> "Schema discovery succeeded";
                    case PREVIEW -> "Draft rows preview succeeded";
                    case PUBLISHED_ROWS ->
                            "Published HTTP rows read succeeded";
                };
                case "SAFE_TARGET" ->
                        "The configured endpoint is not permitted";
                case "SECRET_UNAVAILABLE" ->
                        "The configured credential is unavailable";
                case "TIMEOUT" -> switch (this) {
                    case CHECK -> "Connection check timed out";
                    case DISCOVERY -> "Schema discovery timed out";
                    case PREVIEW -> "Draft rows preview timed out";
                    case PUBLISHED_ROWS ->
                            "Published HTTP rows read timed out";
                };
                case "TLS" -> "TLS connection failed";
                case "IO" -> switch (this) {
                    case CHECK -> "Connection check failed";
                    case DISCOVERY -> "Schema discovery failed";
                    case PREVIEW -> "Draft rows preview failed";
                    case PUBLISHED_ROWS ->
                            "Published HTTP rows read failed";
                };
                case "RESPONSE_TOO_LARGE" ->
                        "The endpoint response exceeded the safe limit";
                case "HTTP_STATUS" ->
                        "The endpoint returned a non-success status";
                case "JSON_INVALID" ->
                        "The endpoint returned invalid JSON";
                case "CONTRACT_INVALID" ->
                        "The endpoint response did not match the required contract";
                case "SCHEMA_FIELD_INVALID" ->
                        "The endpoint returned an invalid field name";
                default -> throw new IllegalArgumentException(
                        "Unknown HTTP data-source result code");
            };
        }
    }

    record Result(
            boolean reachable,
            boolean contractValid,
            Integer httpStatus,
            long durationMillis,
            String code,
            String message,
            JsonNode rows
    ) {
        @Override
        public String toString() {
            return "HttpJsonDataSourceProbe.Result[reachable=" + reachable
                    + ", contractValid=" + contractValid
                    + ", httpStatus=" + httpStatus
                    + ", durationMillis=" + durationMillis
                    + ", code=" + code
                    + ", message=" + message
                    + ", rows=redacted]";
        }
    }

    private record NodeDepth(JsonNode node, int depth) {
    }
}
