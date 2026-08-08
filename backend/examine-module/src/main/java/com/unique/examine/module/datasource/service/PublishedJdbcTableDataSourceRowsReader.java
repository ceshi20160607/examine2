package com.unique.examine.module.datasource.service;

import com.unique.examine.module.datasource.domain.DataSourceActor;
import com.unique.examine.module.datasource.domain.DataSourceDraft;
import com.unique.examine.module.datasource.domain.DataSourcePublication;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Safe reader boundary for one immutable published JDBC table snapshot. */
public interface PublishedJdbcTableDataSourceRowsReader {
    Result read(DataSourceActor actor, DataSourcePublication publication);

    record Result(
            long dataSourceId,
            String dataSourceCode,
            long versionId,
            int versionNumber,
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
            return "PublishedJdbcTableDataSourceRowsReader.Result["
                    + "dataSourceId=" + dataSourceId
                    + ", dataSourceCode=" + dataSourceCode
                    + ", versionId=" + versionId
                    + ", versionNumber=" + versionNumber
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
            return "PublishedJdbcTableDataSourceRowsReader.Row[rowIndex="
                    + rowIndex + ", values=redacted]";
        }
    }

    private static void requireBoundedProjection(
            List<Field> fields,
            List<Row> rows
    ) {
        if (fields.size() > DataSourceDraft.MAX_JDBC_FIELD_PROJECTIONS
                || rows.size() > 25) {
            throw new IllegalArgumentException(
                    "Published JDBC rows exceed their bounded result");
        }
        var fieldCodes = fields.stream().map(Field::fieldCode).toList();
        if (fieldCodes.stream().distinct().count() != fieldCodes.size()
                || rows.stream().anyMatch(row -> row.rowIndex() < 1
                || !List.copyOf(row.values().keySet()).equals(fieldCodes))) {
            throw new IllegalArgumentException(
                    "Published JDBC rows projection is inconsistent");
        }
    }
}
