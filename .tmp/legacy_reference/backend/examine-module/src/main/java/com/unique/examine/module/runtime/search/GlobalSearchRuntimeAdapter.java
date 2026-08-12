package com.unique.examine.module.runtime.search;

import com.unique.examine.module.runtime.api.RecordRuntimeViews;
import com.unique.examine.module.runtime.api.RuntimeViews;
import com.unique.examine.module.runtime.security.RuntimeSession;
import com.unique.examine.module.runtime.service.ModuleRuntimeService;
import com.unique.examine.module.runtime.service.RecordRuntimeService;
import org.springframework.stereotype.Component;

@Component
public class GlobalSearchRuntimeAdapter implements GlobalSearchRuntime {
    private final ModuleRuntimeService modules;
    private final RecordRuntimeService records;

    public GlobalSearchRuntimeAdapter(ModuleRuntimeService modules, RecordRuntimeService records) {
        this.modules = modules;
        this.records = records;
    }

    @Override
    public RuntimeViews.Navigation navigation(RuntimeSession session) {
        return modules.navigation(session);
    }

    @Override
    public RuntimeViews.Definition definition(RuntimeSession session, String moduleCode) {
        return modules.definition(session, moduleCode);
    }

    @Override
    public RecordRuntimeViews.RecordSchema schema(RuntimeSession session, String moduleCode) {
        return records.schema(session, moduleCode);
    }

    @Override
    public RecordRuntimeViews.RecordPage query(RuntimeSession session, String moduleCode, String body) {
        return records.query(session, moduleCode, body);
    }
}
