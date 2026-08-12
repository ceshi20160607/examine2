package com.unique.examine.module.datasource.service;

import com.unique.examine.module.datasource.domain.DataSourceActor;
import com.unique.examine.module.datasource.domain.DataSourceDraft;

import java.util.List;

/** Administrator boundary for metadata of one exact read-only JDBC table. */
public interface JdbcTableDataSourceSchemaDiscoveryUseCase {
    Result discover(
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
            List<Field> fields
    ) {
        public Result {
            fields = fields == null ? List.of() : List.copyOf(fields);
            if (fields.size() > DataSourceDraft.MAX_JDBC_FIELD_PROJECTIONS) {
                throw new IllegalArgumentException(
                        "JDBC schema discovery exceeds 50 columns");
            }
        }
    }

    record Field(
            String sourceColumn,
            String suggestedFieldCode,
            String inferredType,
            boolean nullable,
            boolean selectable,
            String issueCode
    ) {
    }
}
