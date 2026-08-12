package com.unique.examine.module.datasource.service;

import com.unique.examine.module.datasource.domain.DataSourceActor;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Administrator boundary for one read-only persisted HTTP draft preview. */
public interface DataSourceDraftRowsPreviewUseCase {
    Result preview(
            DataSourceActor actor,
            long dataSourceId,
            long expectedVersion);

    record Result(
            boolean reachable,
            boolean contractValid,
            Integer httpStatus,
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
        }

        @Override
        public String toString() {
            return "DataSourceDraftRowsPreviewUseCase.Result[reachable="
                    + reachable + ", contractValid=" + contractValid
                    + ", httpStatus=" + httpStatus
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
            return "DataSourceDraftRowsPreviewUseCase.Row[rowIndex="
                    + rowIndex + ", values=redacted]";
        }
    }
}
