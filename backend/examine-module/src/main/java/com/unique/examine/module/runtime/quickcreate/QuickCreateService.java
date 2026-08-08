package com.unique.examine.module.runtime.quickcreate;

import com.unique.examine.core.error.BusinessException;
import com.unique.examine.module.runtime.security.RuntimeSession;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;

@Service
public class QuickCreateService {
    private final QuickCreateRuntime runtime;

    public QuickCreateService(QuickCreateRuntime runtime) {
        this.runtime = runtime;
    }

    @Transactional(readOnly = true)
    public QuickCreateViews.ModuleList modules(RuntimeSession session) {
        var navigation = runtime.navigation(session);
        var items = new ArrayList<QuickCreateViews.ModuleItem>();
        for (var group : navigation.groups()) {
            for (var module : group.modules()) {
                try {
                    var schema = runtime.schema(session, module.code());
                    if ("READY".equals(schema.runtimeState()) && schema.actions().contains("CREATE")) {
                        items.add(new QuickCreateViews.ModuleItem(
                                module.code(), module.name(), schema.schemaVersionId()));
                    }
                } catch (BusinessException exception) {
                    // Navigation is only a snapshot. A module may disappear or be denied before schema lookup.
                }
            }
        }
        return new QuickCreateViews.ModuleList(items);
    }
}
