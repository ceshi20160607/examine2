package com.unique.examine.module.datasource.external.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.unique.examine.module.datasource.domain.DataSourceActor;
import com.unique.examine.module.datasource.domain.DataSourceDraft;
import com.unique.examine.module.datasource.domain.DataSourceException;
import com.unique.examine.module.datasource.domain.DataSourcePublication;
import com.unique.examine.module.datasource.service.PublishedHttpDataSourceRowsReader;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Reads one fixed, bounded first page from an immutable HTTP publication. */
public final class HttpJsonPublishedDataSourceRowsReader
        implements PublishedHttpDataSourceRowsReader {
    private static final String CODE_PREFIX = "DATA_SOURCE_HTTP_ROWS_";

    private final HttpJsonDataSourceProbe probe;

    public HttpJsonPublishedDataSourceRowsReader(
            HttpJsonDataSourceProbe probe
    ) {
        this.probe = Objects.requireNonNull(probe, "probe");
    }

    @Override
    public Result read(
            DataSourceActor actor,
            DataSourcePublication publication
    ) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(publication, "publication");
        var version = publication.version();
        if (version.systemId() != actor.systemId()
                || version.tenantId() != actor.tenantId()) {
            throw failure(
                    "UNAVAILABLE",
                    "Published HTTP rows are unavailable");
        }
        var snapshot = version.snapshot();
        if (snapshot.sourceKind() != DataSourceDraft.SourceKind.HTTP_JSON
                || snapshot.httpConnection() == null
                || snapshot.httpFieldProjections().isEmpty()) {
            throw new DataSourceException(
                    "DATA_SOURCE_HTTP_RUNTIME_UNAVAILABLE",
                    "Published HTTP rows require an HTTP JSON data-source version");
        }

        var outcome = probe.execute(
                actor, snapshot.httpConnection(),
                HttpJsonDataSourceProbe.Purpose.PUBLISHED_ROWS);
        if (!"SUCCESS".equals(outcome.code())) {
            throw probeFailure(outcome.code());
        }
        if (!validReturnedSchema(outcome.rows())) {
            throw contractFailure();
        }

        var fields = snapshot.httpFieldProjections().stream()
                .map(projection -> new Field(
                        projection.fieldCode(),
                        projection.sourceType().name()))
                .toList();
        var observed = new ArrayList<Boolean>(fields.size());
        fields.forEach(ignored -> observed.add(false));
        var rows = new ArrayList<Row>();
        var rowIndex = 1;
        for (var remoteRow : outcome.rows()) {
            var values = new LinkedHashMap<String, Object>();
            for (int index = 0;
                 index < snapshot.httpFieldProjections().size(); index++) {
                var projection = snapshot.httpFieldProjections().get(index);
                if (!remoteRow.has(projection.sourceField())) {
                    throw schemaFailure();
                }
                var sourceValue = remoteRow.get(projection.sourceField());
                var decoded = HttpJsonProjectedValueDecoder.decode(
                        sourceValue, projection.sourceType());
                if (!decoded.valid()) {
                    throw schemaFailure();
                }
                if (sourceValue != null && !sourceValue.isNull()) {
                    observed.set(index, true);
                }
                values.put(projection.fieldCode(), decoded.value());
            }
            rows.add(new Row(rowIndex++, values));
        }
        if (!rows.isEmpty()
                && observed.stream().anyMatch(value -> !value)) {
            throw schemaFailure();
        }
        return new Result(
                publication.root().id(), publication.root().code(),
                version.id(), version.versionNumber(), fields, rows);
    }

    private static boolean validReturnedSchema(JsonNode rows) {
        if (rows == null || !rows.isArray() || rows.size() > 25) {
            return false;
        }
        var exactNames = new LinkedHashSet<String>();
        var collisionNames = new LinkedHashMap<String, String>();
        for (var row : rows) {
            if (!row.isObject()
                    || row.size() > HttpJsonDataSourceProbe.MAXIMUM_FIELDS) {
                return false;
            }
            var values = row.fields();
            while (values.hasNext()) {
                var field = values.next();
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

    private static DataSourceException probeFailure(String probeCode) {
        return switch (probeCode) {
            case "SAFE_TARGET" -> failure(
                    "SAFE_TARGET",
                    "The published HTTP endpoint is not permitted");
            case "SECRET_UNAVAILABLE" -> failure(
                    "SECRET_UNAVAILABLE",
                    "The published HTTP credential is unavailable");
            case "TIMEOUT" -> failure(
                    "TIMEOUT",
                    "The published HTTP rows read timed out");
            case "TLS" -> failure(
                    "TLS",
                    "The published HTTP TLS connection failed");
            case "IO" -> failure(
                    "IO",
                    "The published HTTP rows read failed");
            case "RESPONSE_TOO_LARGE" -> failure(
                    "RESPONSE_TOO_LARGE",
                    "The published HTTP response exceeded the safe limit");
            case "HTTP_STATUS" -> failure(
                    "HTTP_STATUS",
                    "The published HTTP endpoint returned a non-success status");
            case "JSON_INVALID" -> failure(
                    "JSON_INVALID",
                    "The published HTTP endpoint returned invalid JSON");
            case "CONTRACT_INVALID", "SCHEMA_FIELD_INVALID" ->
                    contractFailure();
            default -> failure(
                    "FAILED",
                    "The published HTTP rows read failed");
        };
    }

    private static DataSourceException contractFailure() {
        return failure(
                "CONTRACT_INVALID",
                "The published HTTP response did not match the required contract");
    }

    private static DataSourceException schemaFailure() {
        return failure(
                "SCHEMA_STALE",
                "The published HTTP response no longer matches its projections");
    }

    private static DataSourceException failure(
            String suffix,
            String message
    ) {
        return new DataSourceException(CODE_PREFIX + suffix, message);
    }
}
