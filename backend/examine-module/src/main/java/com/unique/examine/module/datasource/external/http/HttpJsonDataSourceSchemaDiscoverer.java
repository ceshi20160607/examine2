package com.unique.examine.module.datasource.external.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.unique.examine.module.datasource.domain.DataSourceActor;
import com.unique.examine.module.datasource.domain.DataSourceDraft;
import com.unique.examine.module.datasource.domain.DataSourceException;
import com.unique.examine.module.datasource.service.DataSourceSchemaDiscoveryUseCase;
import com.unique.examine.module.datasource.service.DataSourceService;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** Discovers only bounded, value-free metadata from one persisted HTTP draft. */
public final class HttpJsonDataSourceSchemaDiscoverer
        implements DataSourceSchemaDiscoveryUseCase {
    private static final int MAXIMUM_DISCOVERED_FIELDS = 50;
    private static final Pattern FIELD_CODE =
            Pattern.compile("^[A-Za-z][A-Za-z0-9_]{0,63}$");

    private final DataSourceService dataSources;
    private final HttpJsonDataSourceProbe probe;

    public HttpJsonDataSourceSchemaDiscoverer(
            DataSourceService dataSources,
            HttpJsonDataSourceProbe probe
    ) {
        this.dataSources = Objects.requireNonNull(dataSources, "dataSources");
        this.probe = Objects.requireNonNull(probe, "probe");
    }

    @Override
    public Result discover(
            DataSourceActor actor,
            long dataSourceId,
            long expectedVersion
    ) {
        Objects.requireNonNull(actor, "actor");
        var source = dataSources.detail(actor, dataSourceId);
        if (expectedVersion <= 0
                || source.draftVersion() != expectedVersion) {
            throw new DataSourceException(
                    "DATA_SOURCE_VERSION_CONFLICT",
                    "Data source draft changed; refresh before retrying");
        }
        var draft = source.draft();
        if (draft.sourceKind() != DataSourceDraft.SourceKind.HTTP_JSON
                || draft.httpConnection() == null) {
            throw new DataSourceException(
                    "DATA_SOURCE_SCHEMA_DISCOVERY_INVALID",
                    "Schema discovery requires an HTTP JSON data-source draft");
        }

        var outcome = probe.execute(
                actor, draft.httpConnection(),
                HttpJsonDataSourceProbe.Purpose.DISCOVERY);
        if (!"SUCCESS".equals(outcome.code())) {
            return fromProbe(outcome, source.draftVersion());
        }
        return infer(outcome, source.draftVersion());
    }

    private static Result infer(
            HttpJsonDataSourceProbe.Result outcome,
            long draftVersion
    ) {
        var rows = outcome.rows();
        if (rows == null || !rows.isArray() || rows.isEmpty()) {
            return schemaFailure(
                    outcome, draftVersion, "SCHEMA_EMPTY",
                    "The endpoint returned no rows for schema discovery");
        }

        var states = new LinkedHashMap<String, FieldState>();
        var rowIndex = 0;
        for (var row : rows) {
            var present = new HashSet<String>();
            var fields = row.fields();
            while (fields.hasNext()) {
                var field = fields.next();
                var name = field.getKey();
                if (!HttpJsonDataSourceProbe.validSourceName(name)) {
                    return schemaFailure(
                            outcome, draftVersion,
                            "SCHEMA_FIELD_INVALID",
                            "The endpoint returned an invalid field name");
                }
                present.add(name);
                var state = states.get(name);
                if (state == null) {
                    if (states.size() >= MAXIMUM_DISCOVERED_FIELDS) {
                        return schemaFailure(
                                outcome, draftVersion,
                                "SCHEMA_TOO_WIDE",
                                "The discovered schema exceeded the field limit");
                    }
                    state = new FieldState(name, rowIndex > 0);
                    states.put(name, state);
                }
                state.observe(field.getValue());
            }
            for (var state : states.values()) {
                if (!present.contains(state.sourceField)) {
                    state.nullable = true;
                }
            }
            rowIndex++;
        }
        if (states.isEmpty()) {
            return schemaFailure(
                    outcome, draftVersion, "SCHEMA_EMPTY",
                    "The endpoint returned no fields for schema discovery");
        }

        var collisionCounts = collisionCounts(states.keySet());
        var discovered = new ArrayList<Field>();
        states.values().stream()
                .sorted(Comparator.comparing(state -> state.sourceField))
                .forEach(state -> {
                    var collision = collisionCounts.get(
                            collisionKey(state.sourceField)) > 1;
                    var inferred = state.inferredType();
                    var issue = collision
                            ? "FIELD_NAME_COLLISION"
                            : switch (inferred) {
                                case "UNKNOWN" -> "FIELD_TYPE_UNKNOWN";
                                case "MIXED" -> "FIELD_TYPE_MIXED";
                                default -> null;
                            };
                    var selectable = issue == null;
                    discovered.add(new Field(
                            state.sourceField,
                            selectable
                                    && FIELD_CODE.matcher(
                                    state.sourceField).matches()
                                    ? state.sourceField : null,
                            inferred,
                            state.nullable,
                            selectable,
                            issue));
                });
        var reviewRequired = discovered.stream()
                .anyMatch(field -> !field.selectable());
        return new Result(
                true, true, outcome.httpStatus(), outcome.durationMillis(),
                reviewRequired ? "SCHEMA_REVIEW_REQUIRED" : "SUCCESS",
                reviewRequired
                        ? "The discovered schema contains fields requiring review"
                        : "Schema discovery succeeded",
                draftVersion, discovered);
    }

    private static Map<String, Integer> collisionCounts(Set<String> names) {
        var counts = new HashMap<String, Integer>();
        names.forEach(name -> counts.merge(
                collisionKey(name), 1, Integer::sum));
        return counts;
    }

    private static String collisionKey(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
    }

    private static Result fromProbe(
            HttpJsonDataSourceProbe.Result outcome,
            long draftVersion
    ) {
        return new Result(
                outcome.reachable(), outcome.contractValid(),
                outcome.httpStatus(), outcome.durationMillis(),
                outcome.code(), outcome.message(), draftVersion, List.of());
    }

    private static Result schemaFailure(
            HttpJsonDataSourceProbe.Result outcome,
            long draftVersion,
            String code,
            String message
    ) {
        return new Result(
                true, false, outcome.httpStatus(), outcome.durationMillis(),
                code, message, draftVersion, List.of());
    }

    private enum TokenType { STRING, INTEGER, DECIMAL, BOOLEAN }

    private static final class FieldState {
        private final String sourceField;
        private final EnumSet<TokenType> observed =
                EnumSet.noneOf(TokenType.class);
        private boolean nullable;

        private FieldState(String sourceField, boolean nullable) {
            this.sourceField = sourceField;
            this.nullable = nullable;
        }

        private void observe(JsonNode value) {
            if (value == null || value.isNull()) {
                nullable = true;
            } else if (value.isTextual()) {
                observed.add(TokenType.STRING);
            } else if (value.isIntegralNumber()) {
                observed.add(TokenType.INTEGER);
            } else if (value.isFloatingPointNumber()) {
                observed.add(TokenType.DECIMAL);
            } else if (value.isBoolean()) {
                observed.add(TokenType.BOOLEAN);
            }
        }

        private String inferredType() {
            if (observed.isEmpty()) {
                return "UNKNOWN";
            }
            if (observed.equals(EnumSet.of(
                    TokenType.INTEGER, TokenType.DECIMAL))) {
                return "DECIMAL";
            }
            return observed.size() == 1
                    ? observed.iterator().next().name()
                    : "MIXED";
        }
    }
}
