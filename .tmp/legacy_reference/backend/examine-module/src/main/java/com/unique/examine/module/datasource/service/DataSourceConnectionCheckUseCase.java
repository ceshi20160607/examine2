package com.unique.examine.module.datasource.service;

import com.unique.examine.module.datasource.domain.DataSourceActor;

/** Administrator application boundary for checking one persisted HTTP draft. */
public interface DataSourceConnectionCheckUseCase {
    Result check(
            DataSourceActor actor,
            long dataSourceId,
            long expectedVersion);

    record Result(
            boolean reachable,
            boolean contractValid,
            Integer httpStatus,
            long durationMillis,
            String code,
            String message
    ) {
    }
}
