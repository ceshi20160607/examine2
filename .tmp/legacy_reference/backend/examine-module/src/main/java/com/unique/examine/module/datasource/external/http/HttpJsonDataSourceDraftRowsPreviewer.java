package com.unique.examine.module.datasource.external.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.unique.examine.module.datasource.domain.DataSourceActor;
import com.unique.examine.module.datasource.domain.DataSourceCheckReport;
import com.unique.examine.module.datasource.domain.DataSourceDraft;
import com.unique.examine.module.datasource.domain.DataSourceException;
import com.unique.examine.module.datasource.service.DataSourceDraftRowsPreviewUseCase;
import com.unique.examine.module.datasource.service.DataSourceService;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Executes one bounded, read-only preview of an exact persisted HTTP draft. */
public final class HttpJsonDataSourceDraftRowsPreviewer
        implements DataSourceDraftRowsPreviewUseCase {
    private static final String BLOCKED_CODE =
            "DATA_SOURCE_HTTP_PREVIEW_BLOCKED";
    private static final String BLOCKED_MESSAGE =
            "The HTTP data source draft is not eligible for preview";
    private static final String STALE_MESSAGE =
            "The endpoint response no longer matches the configured projections";

    private final DataSourceService dataSources;
    private final HttpJsonDataSourceProbe probe;

    public HttpJsonDataSourceDraftRowsPreviewer(
            DataSourceService dataSources,
            HttpJsonDataSourceProbe probe
    ) {
        this.dataSources = Objects.requireNonNull(dataSources, "dataSources");
        this.probe = Objects.requireNonNull(probe, "probe");
    }

    @Override
    public Result preview(
            DataSourceActor actor,
            long dataSourceId,
            long expectedVersion
    ) {
        Objects.requireNonNull(actor, "actor");
        var source = dataSources.detail(actor, dataSourceId);
        requireVersion(source.draftVersion(), expectedVersion);
        var draft = source.draft();
        if (draft.sourceKind() != DataSourceDraft.SourceKind.HTTP_JSON
                || draft.httpConnection() == null) {
            throw new DataSourceException(
                    "DATA_SOURCE_HTTP_PREVIEW_INVALID",
                    "Draft rows preview requires an HTTP JSON data source");
        }

        var report = dataSources.check(actor, dataSourceId);
        requireVersion(report.draftVersion(), expectedVersion);
        if (report.dataSourceId() != source.id()
                || hasPreviewBlocker(report)) {
            throw new DataSourceException(BLOCKED_CODE, BLOCKED_MESSAGE);
        }

        var outcome = probe.execute(
                actor, draft.httpConnection(),
                HttpJsonDataSourceProbe.Purpose.PREVIEW);
        if (!"SUCCESS".equals(outcome.code())) {
            return fromProbe(outcome, expectedVersion);
        }

        var fields = draft.httpFieldProjections().stream()
                .map(projection -> new Field(
                        projection.fieldCode(),
                        projection.sourceType().name()))
                .toList();
        if (!validReturnedSchema(outcome.rows())) {
            return contractFailure(outcome, expectedVersion);
        }

        var rows = new ArrayList<Row>();
        var rowIndex = 1;
        for (var remoteRow : outcome.rows()) {
            var values = new LinkedHashMap<String, Object>();
            for (var projection : draft.httpFieldProjections()) {
                if (!remoteRow.has(projection.sourceField())) {
                    return stale(outcome, expectedVersion, fields);
                }
                var decoded = HttpJsonProjectedValueDecoder.decode(
                        remoteRow.get(projection.sourceField()),
                        projection.sourceType());
                if (!decoded.valid()) {
                    return stale(outcome, expectedVersion, fields);
                }
                values.put(projection.fieldCode(), decoded.value());
            }
            rows.add(new Row(rowIndex++, values));
        }
        return new Result(
                true, true, outcome.httpStatus(), outcome.durationMillis(),
                "SUCCESS", "Draft rows preview succeeded",
                expectedVersion, fields, rows);
    }

    private static boolean hasPreviewBlocker(DataSourceCheckReport report) {
        return report.issues().stream().anyMatch(issue ->
                issue.severity() == DataSourceCheckReport.Severity.BLOCKER
                        && !"SOURCE_RUNTIME_UNAVAILABLE".equals(
                        issue.code()));
    }

    private static boolean validReturnedSchema(JsonNode rows) {
        if (rows == null || !rows.isArray() || rows.size() > 25) {
            return false;
        }
        var exactNames = new LinkedHashSet<String>();
        var collisionNames = new LinkedHashMap<String, String>();
        for (var row : rows) {
            if (!row.isObject()) {
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

    private static Result fromProbe(
            HttpJsonDataSourceProbe.Result outcome,
            long checkedDraftVersion
    ) {
        return new Result(
                outcome.reachable(), outcome.contractValid(),
                outcome.httpStatus(), outcome.durationMillis(),
                outcome.code(), outcome.message(), checkedDraftVersion,
                List.of(), List.of());
    }

    private static Result contractFailure(
            HttpJsonDataSourceProbe.Result outcome,
            long checkedDraftVersion
    ) {
        return new Result(
                true, false, outcome.httpStatus(), outcome.durationMillis(),
                "CONTRACT_INVALID",
                "The endpoint response did not match the required contract",
                checkedDraftVersion, List.of(), List.of());
    }

    private static Result stale(
            HttpJsonDataSourceProbe.Result outcome,
            long checkedDraftVersion,
            List<Field> fields
    ) {
        return new Result(
                true, false, outcome.httpStatus(), outcome.durationMillis(),
                "SCHEMA_STALE", STALE_MESSAGE, checkedDraftVersion,
                fields, List.of());
    }

    private static void requireVersion(
            long actualVersion,
            long expectedVersion
    ) {
        if (expectedVersion <= 0 || actualVersion != expectedVersion) {
            throw new DataSourceException(
                    "DATA_SOURCE_VERSION_CONFLICT",
                    "Data source draft changed; refresh before retrying");
        }
    }
}
