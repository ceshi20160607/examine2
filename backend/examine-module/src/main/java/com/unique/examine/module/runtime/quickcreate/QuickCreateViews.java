package com.unique.examine.module.runtime.quickcreate;

import java.util.List;

public final class QuickCreateViews {
    private QuickCreateViews() {
    }

    public record ModuleItem(String moduleCode, String moduleName, String schemaVersionId) {
    }

    public record ModuleList(List<ModuleItem> items) {
        public ModuleList {
            items = List.copyOf(items);
        }
    }
}
