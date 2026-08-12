package com.unique.examine.module.datasource.service;

import com.unique.examine.module.datasource.domain.DataSourceActor;
import com.unique.examine.module.datasource.domain.DataSourcePublication;

/** Internal tenant-scoped resolver for one immutable source publication. */
@FunctionalInterface
public interface ExactDataSourcePublicationResolver {
    DataSourcePublication publication(
            DataSourceActor actor,
            long dataSourceId,
            long dataSourceVersionId);
}
