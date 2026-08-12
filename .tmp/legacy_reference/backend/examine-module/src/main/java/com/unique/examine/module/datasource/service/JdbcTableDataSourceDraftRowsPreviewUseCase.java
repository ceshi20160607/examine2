package com.unique.examine.module.datasource.service;

import com.unique.examine.module.datasource.domain.DataSourceActor;
import com.unique.examine.module.datasource.domain.DataSourceDraft;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Administrator boundary for at most 25 projected JDBC draft rows. */
public interface JdbcTableDataSourceDraftRowsPreviewUseCase {
    Result preview(
            DataSourceActor actor,
            long dataSourceId,
            long expectedVersion);

    record Result(
            boolean reachable,
            boolean contractValid,
            long durationMillis,
            String code,
            String message,
            long checkedDraftVersion,
            List<Field> fields,
            List<Row> rows
    ) {
        public Result {
            fields = fields == null ? List.of() : List.copyOf(fields);
            rows = rows == null ? List.of() : List.copyOf(rows);
            requireBoundedProjection(fields, rows);
        }

        @Override
        public String toString() {
            return "JdbcTableDataSourceDraftRowsPreviewUseCase.Result["
                    + "reachable=" + reachable
                    + ", contractValid=" + contractValid
                    + ", durationMillis=" + durationMillis
                    + ", code=" + code + ", message=" + message
                    + ", checkedDraftVersion=" + checkedDraftVersion
                    + ", fields=" + fields + ", rows=redacted]";
        }
    }

    record Field(String fieldCode, String sourceType) {
    }

    record Row(int rowIndex, Map<String, Object> values) {
        public Row {
            var ordered = new LinkedHashMap<String, Object>();
            if (values != null) {
                ordered.putAll(values);
            }
            values = Collections.unmodifiableMap(ordered);
        }

        @Override
        public String toString() {
            return "JdbcTableDataSourceDraftRowsPreviewUseCase.Row["
                    + "rowIndex=" + rowIndex + ", values=redacted]";
        }
    }

    private static void requireBoundedProjection(
            List<Field> fields,
            List<Row> rows
    ) {
        if (fields.size() > DataSourceDraft.MAX_JDBC_FIELD_PROJECTIONS
                || rows.size() > 25) {
            throw new IllegalArgumentException(
                    "JDBC draft preview exceeds its bounded result");
        }
        var fieldCodes = fields.stream().map(Field::fieldCode).toList();
        if (fieldCodes.stream().distinct().count() != fieldCodes.size()
                || rows.stream().anyMatch(row -> row.rowIndex() < 1
                || !List.copyOf(row.values().keySet()).equals(fieldCodes))) {
            throw new IllegalArgumentException(
                    "JDBC draft preview projection is inconsistent");
        }
    }
}
