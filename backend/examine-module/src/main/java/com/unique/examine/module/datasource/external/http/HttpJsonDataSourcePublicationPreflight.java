package com.unique.examine.module.datasource.external.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.unique.examine.module.datasource.domain.DataSourceActor;
import com.unique.examine.module.datasource.domain.DataSourceDraft;
import com.unique.examine.module.datasource.domain.DataSourceException;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Fresh, value-free schema proof for one new HTTP data-source publication. */
public final class HttpJsonDataSourcePublicationPreflight
        implements com.unique.examine.module.datasource.service
        .DataSourcePublicationPreflight {
    private static final String CODE_PREFIX =
            "DATA_SOURCE_HTTP_PUBLICATION_";

    private final HttpJsonDataSourceProbe probe;

    public HttpJsonDataSourcePublicationPreflight(
            HttpJsonDataSourceProbe probe
    ) {
        this.probe = Objects.requireNonNull(probe, "probe");
    }

    @Override
    public void verify(
            DataSourceActor actor,
            DataSourceDraft normalizedHttpDraft
    ) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(normalizedHttpDraft, "normalizedHttpDraft");
        if (normalizedHttpDraft.sourceKind()
                != DataSourceDraft.SourceKind.HTTP_JSON
                || normalizedHttpDraft.httpConnection() == null
                || normalizedHttpDraft.httpFieldProjections().isEmpty()) {
            throw failure(
                    "INVALID",
                    "HTTP publication preflight requires a mapped HTTP draft");
        }

        var outcome = probe.execute(
                actor, normalizedHttpDraft.httpConnection(),
                HttpJsonDataSourceProbe.Purpose.DISCOVERY);
        if (!"SUCCESS".equals(outcome.code())) {
            throw probeFailure(outcome.code());
        }
        var rows = outcome.rows();
        if (!validReturnedSchema(rows)) {
            throw contractFailure();
        }
        verifyProjections(
                rows, normalizedHttpDraft.httpFieldProjections());
    }

    private static boolean validReturnedSchema(JsonNode rows) {
        if (rows == null || !rows.isArray()
                || rows.isEmpty() || rows.size() > 25) {
            return false;
        }
        var exactNames = new LinkedHashSet<String>();
        var collisionNames = new LinkedHashMap<String, String>();
        for (var row : rows) {
            if (!row.isObject()
                    || row.size() > HttpJsonDataSourceProbe.MAXIMUM_FIELDS) {
                return false;
            }
            var fields = row.fields();
            while (fields.hasNext()) {
                var field = fields.next();
                var name = field.getKey();
                if (!HttpJsonDataSourceProbe.validSourceName(name)) {
                    return false;
                }
                exactNames.add(name);
                if (exactNames.size() > HttpJsonDataSourceProbe.MAXIMUM_FIELDS) {
                    return false;
                }
                var collisionKey = Normalizer.normalize(
                                name, Normalizer.Form.NFKC)
                        .toLowerCase(Locale.ROOT);
                var previous = collisionNames.putIfAbsent(
                        collisionKey, name);
                if (previous != null && !previous.equals(name)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static void verifyProjections(
            JsonNode rows,
            List<DataSourceDraft.HttpJsonFieldProjection> projections
    ) {
        var observed = new ArrayList<Boolean>(projections.size());
        projections.forEach(ignored -> observed.add(false));
        for (var row : rows) {
            for (int index = 0; index < projections.size(); index++) {
                var projection = projections.get(index);
                if (!row.has(projection.sourceField())) {
                    throw schemaFailure();
                }
                var value = row.get(projection.sourceField());
                if (value == null || value.isNull()) {
                    continue;
                }
                if (!matches(value, projection.sourceType())) {
                    throw schemaFailure();
                }
                observed.set(index, true);
            }
        }
        if (observed.stream().anyMatch(value -> !value)) {
            throw schemaFailure();
        }
    }

    private static boolean matches(
            JsonNode value,
            DataSourceDraft.HttpJsonSourceType sourceType
    ) {
        return switch (sourceType) {
            case STRING -> value.isTextual();
            case INTEGER -> value.isIntegralNumber();
            case DECIMAL -> value.isIntegralNumber()
                    || value.isFloatingPointNumber();
            case BOOLEAN -> value.isBoolean();
        };
    }

    private static DataSourceException probeFailure(String probeCode) {
        return switch (probeCode) {
            case "SAFE_TARGET" -> failure(
                    "SAFE_TARGET",
                    "The HTTP publication endpoint is not permitted");
            case "SECRET_UNAVAILABLE" -> failure(
                    "SECRET_UNAVAILABLE",
                    "The HTTP publication credential is unavailable");
            case "TIMEOUT" -> failure(
                    "TIMEOUT",
                    "The HTTP publication preflight timed out");
            case "TLS" -> failure(
                    "TLS",
                    "The HTTP publication TLS connection failed");
            case "IO" -> failure(
                    "IO",
                    "The HTTP publication preflight failed");
            case "RESPONSE_TOO_LARGE" -> failure(
                    "RESPONSE_TOO_LARGE",
                    "The HTTP publication response exceeded the safe limit");
            case "HTTP_STATUS" -> failure(
                    "HTTP_STATUS",
                    "The HTTP publication endpoint returned a non-success status");
            case "JSON_INVALID" -> failure(
                    "JSON_INVALID",
                    "The HTTP publication endpoint returned invalid JSON");
            case "CONTRACT_INVALID", "SCHEMA_FIELD_INVALID" ->
                    contractFailure();
            default -> failure(
                    "FAILED",
                    "The HTTP publication preflight failed");
        };
    }

    private static DataSourceException contractFailure() {
        return failure(
                "CONTRACT_INVALID",
                "The HTTP publication response did not match the required contract");
    }

    private static DataSourceException schemaFailure() {
        return failure(
                "SCHEMA_STALE",
                "The HTTP publication response no longer matches the configured projections");
    }

    private static DataSourceException failure(
            String suffix,
            String message
    ) {
        return new DataSourceException(CODE_PREFIX + suffix, message);
    }
}
