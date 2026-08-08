package com.unique.examine.module.runtime.quickcreate;

import com.unique.examine.module.runtime.api.RecordRuntimeViews;
import com.unique.examine.module.runtime.api.RuntimeViews;
import com.unique.examine.module.runtime.security.RuntimeSession;
import com.unique.examine.module.runtime.service.ModuleRuntimeService;
import com.unique.examine.module.runtime.service.RecordRuntimeService;
import org.springframework.stereotype.Component;

@Component
public class QuickCreateRuntimeAdapter implements QuickCreateRuntime {
    private final ModuleRuntimeService modules;
    private final RecordRuntimeService records;

    public QuickCreateRuntimeAdapter(ModuleRuntimeService modules, RecordRuntimeService records) {
        this.modules = modules;
        this.records = records;
    }

    @Override
    public RuntimeViews.Navigation navigation(RuntimeSession session) {
        return modules.navigation(session);
    }

    @Override
    public RecordRuntimeViews.RecordSchema schema(RuntimeSession session, String moduleCode) {
        return records.schema(session, moduleCode);
    }
}
