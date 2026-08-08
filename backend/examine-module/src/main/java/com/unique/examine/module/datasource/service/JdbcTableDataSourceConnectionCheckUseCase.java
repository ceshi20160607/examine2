package com.unique.examine.module.datasource.service;

import com.unique.examine.module.datasource.domain.DataSourceActor;

/** Administrator boundary for one bounded read-only JDBC connection check. */
public interface JdbcTableDataSourceConnectionCheckUseCase {
    Result check(
            DataSourceActor actor,
            long dataSourceId,
            long expectedVersion);

    record Result(
            boolean reachable,
            boolean contractValid,
            long durationMillis,
            String code,
            String message
    ) {
    }
}
