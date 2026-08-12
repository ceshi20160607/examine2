package com.unique.examine.module.runtime.quickcreate;

import com.unique.examine.module.runtime.api.RecordRuntimeViews;
import com.unique.examine.module.runtime.api.RuntimeViews;
import com.unique.examine.module.runtime.security.RuntimeSession;

public interface QuickCreateRuntime {
    RuntimeViews.Navigation navigation(RuntimeSession session);

    RecordRuntimeViews.RecordSchema schema(RuntimeSession session, String moduleCode);
}
