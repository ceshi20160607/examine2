package com.unique.examine.module.datasource.runtime;

import com.unique.examine.module.datasource.api.DataSourceViews;
import com.unique.examine.module.runtime.security.RuntimeSession;

/** Internal exact-version row port reused by bounded composite sources. */
@FunctionalInterface
public interface ExactPublishedDataSourceRowsReader {
    DataSourceViews.RuntimeRows rows(
            RuntimeSession session,
            long dataSourceId,
            long dataSourceVersionId,
            int page,
            int size);
}
