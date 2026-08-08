package com.unique.examine.module.datasource.service;

import com.unique.examine.module.datasource.domain.DataSourcePublication;
import com.unique.examine.module.runtime.security.RuntimeSession;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Permission-scoped bounded execution of one exact published join plan. */
public interface PublishedMultiModuleJoinRowsReader {
    Result read(RuntimeSession session, DataSourcePublication publication);

    record Result(
            long dataSourceId,
            long versionId,
            int versionNumber,
            String queryHash,
            List<Row> rows,
            boolean partial,
            List<String> failedAliases,
            int sourceRowLimit
    ) {
        public Result {
            if (queryHash == null || queryHash.isBlank()) {
                throw new IllegalArgumentException(
                        "Published join query hash is required");
            }
            rows = List.copyOf(rows);
            failedAliases = List.copyOf(failedAliases);
        }

        public Result(
                long dataSourceId,
                long versionId,
                int versionNumber,
                List<Row> rows,
                boolean partial,
                List<String> failedAliases,
                int sourceRowLimit
        ) {
            this(dataSourceId, versionId, versionNumber,
                    "join:" + versionId, rows, partial, failedAliases,
                    sourceRowLimit);
        }
    }

    record Row(
            String recordId,
            Map<String, Object> values,
            Map<String, String> drillThrough
    ) {
        public Row {
            values = immutable(values);
            drillThrough = immutable(drillThrough);
        }

        private static <T> Map<String, T> immutable(Map<String, T> source) {
            var copy = new LinkedHashMap<String, T>();
            if (source != null) {
                copy.putAll(source);
            }
            return Collections.unmodifiableMap(copy);
        }
    }
}
