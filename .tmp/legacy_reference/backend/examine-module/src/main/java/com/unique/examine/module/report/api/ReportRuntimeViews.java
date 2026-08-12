package com.unique.examine.module.report.api;

import java.util.List;

public final class ReportRuntimeViews {
    private ReportRuntimeViews() {
    }

    public record Field(
            String fieldCode,
            String fieldName,
            String type
    ) {
    }

    public record Metadata(
            String id,
            String code,
            String name,
            String description,
            String versionId,
            int versionNumber,
            String dataSourceId,
            String dataSourceCode,
            String dataSourceName,
            String dataSourceVersionId,
            int dataSourceVersionNumber,
            String moduleId,
            String moduleCode,
            String schemaVersionId,
            List<Field> fields
    ) {
        public Metadata {
            fields = List.copyOf(fields);
        }
    }

    public record Value(
            String fieldCode,
            String fieldName,
            String type,
            Object value,
            String displayValue
    ) {
    }

    public record Row(
            String recordId,
            String recordNo,
            long version,
            String status,
            String title,
            List<Value> values,
            java.util.Map<String, String> drillThrough
    ) {
        public Row {
            values = List.copyOf(values);
            drillThrough = java.util.Map.copyOf(drillThrough);
        }

        public Row(
                String recordId, String recordNo, long version,
                String status, String title, List<Value> values
        ) {
            this(recordId, recordNo, version, status, title, values,
                    java.util.Map.of());
        }
    }

    public record Rows(
            List<Row> rows,
            List<Row> items,
            int page,
            int size,
            long total,
            String queryHash,
            boolean partial,
            String sourceKind,
            List<String> failedSourceAliases
    ) {
        public Rows {
            rows = List.copyOf(rows);
            items = rows;
            failedSourceAliases = List.copyOf(failedSourceAliases);
        }

        public Rows(
                List<Row> rows, List<Row> items, int page, int size,
                long total, String queryHash
        ) {
            this(rows, items, page, size, total, queryHash, false,
                    "NATIVE_MODULE", List.of());
        }
    }
}
