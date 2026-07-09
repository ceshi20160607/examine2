package com.unique.unexamine.core.module;

import java.util.List;

public record BusinessModuleConfig(
        String moduleCode,
        String moduleName,
        String groupCode,
        String groupName,
        String purpose,
        List<ModuleField> fields,
        List<ModuleAction> actions,
        List<ModulePage> pages,
        List<PrintTemplate> printTemplates
) {
}