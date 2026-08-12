package com.unique.examine.module.datasource.service;

import com.unique.examine.module.datasource.domain.DataSourceActor;
import com.unique.examine.module.datasource.domain.DataSourceDraft;

/** Fresh connection, schema, and projection proof before JDBC publication. */
public interface JdbcTableDataSourcePublicationPreflight {
    void verify(DataSourceActor actor, DataSourceDraft normalizedJdbcDraft);
}
