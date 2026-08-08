package com.unique.examine.module.datasource.service;

import com.unique.examine.module.datasource.domain.DataSourceActor;
import com.unique.examine.module.datasource.domain.DataSourcePublication;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Safe executor boundary for one immutable published HTTP first page. */
public interface PublishedHttpDataSourceRowsReader {
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
        }

        @Override
        public String toString() {
            return "PublishedHttpDataSourceRowsReader.Result[dataSourceId="
                    + dataSourceId + ", dataSourceCode=" + dataSourceCode
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
            return "PublishedHttpDataSourceRowsReader.Row[rowIndex="
                    + rowIndex + ", values=redacted]";
        }
    }
}
