package com.unique.examine.module.report.runtime;

import com.unique.examine.module.runtime.security.RuntimeSession;

import java.util.List;

/** Current-authority reader for an exact published native data-source version. */
public interface ReportDataSourceRuntime {
    SourceMetadata metadata(
            RuntimeSession session,
            long dataSourceId,
            long dataSourceVersionId);

    SourceRows rows(
            RuntimeSession session,
            long dataSourceId,
            long dataSourceVersionId,
            int page,
            int size);

    record SourceMetadata(
            String dataSourceId,
            String dataSourceCode,
            String dataSourceName,
            String moduleCode,
            String dataSourceVersionId,
            int dataSourceVersionNumber,
            String schemaVersionId,
            List<SourceField> fields
    ) {
        public SourceMetadata {
            fields = List.copyOf(fields);
        }
    }

    record SourceField(String code, String name, String type) {
    }

    record SourceValue(
            String code,
            String name,
            String type,
            Object value,
            String displayValue
    ) {
    }

    record SourceRow(
            String recordId,
            String recordNo,
            long version,
            String status,
            String title,
            List<SourceValue> values,
            java.util.Map<String, String> drillThrough
    ) {
        public SourceRow {
            values = List.copyOf(values);
            drillThrough = java.util.Map.copyOf(drillThrough);
        }

        public SourceRow(
                String recordId, String recordNo, long version,
                String status, String title, List<SourceValue> values
        ) {
            this(recordId, recordNo, version, status, title, values,
                    java.util.Map.of());
        }
    }

    record SourceRows(
            List<SourceRow> rows,
            int page,
            int size,
            long total,
            String queryHash,
            boolean partial,
            String sourceKind,
            List<String> failedSourceAliases
    ) {
        public SourceRows {
            rows = List.copyOf(rows);
            failedSourceAliases = List.copyOf(failedSourceAliases);
        }

        public SourceRows(
                List<SourceRow> rows, int page, int size, long total,
                String queryHash
        ) {
            this(rows, page, size, total, queryHash, false,
                    "NATIVE_MODULE", List.of());
        }
    }
}
