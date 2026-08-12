package com.unique.examine.module.runtime.search;

import com.unique.examine.module.runtime.api.RecordRuntimeViews;
import com.unique.examine.module.runtime.api.RuntimeViews;
import com.unique.examine.module.runtime.security.RuntimeSession;

public interface GlobalSearchRuntime {
    RuntimeViews.Navigation navigation(RuntimeSession session);

    RuntimeViews.Definition definition(RuntimeSession session, String moduleCode);

    RecordRuntimeViews.RecordSchema schema(RuntimeSession session, String moduleCode);

    RecordRuntimeViews.RecordPage query(RuntimeSession session, String moduleCode, String body);
}
