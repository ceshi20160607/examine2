package com.unique.examine.module.datasource.service;

import com.unique.examine.module.datasource.domain.DataSourceActor;

import java.util.List;

/** Administrator application boundary for one read-only HTTP schema probe. */
public interface DataSourceSchemaDiscoveryUseCase {
    Result discover(
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
            List<Field> fields
    ) {
        public Result {
            fields = fields == null ? List.of() : List.copyOf(fields);
        }
    }

    record Field(
            String sourceField,
            String suggestedFieldCode,
            String inferredType,
            boolean nullable,
            boolean selectable,
            String issueCode
    ) {
    }
}
