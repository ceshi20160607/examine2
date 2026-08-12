package com.unique.examine.module.datasource.service;

import com.unique.examine.module.datasource.domain.DataSourceActor;
import com.unique.examine.module.datasource.domain.DataSourceDraft;

/** Fresh server-side proof required before a new HTTP publication. */
public interface DataSourcePublicationPreflight {
    void verify(DataSourceActor actor, DataSourceDraft normalizedHttpDraft);
}
