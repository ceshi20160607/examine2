package com.unique.unexamine.moduleconfig.manage;

import com.unique.unexamine.moduleconfig.base.entity.ConfiguredModule;
import com.unique.unexamine.moduleconfig.base.entity.ConfiguredModuleAction;
import com.unique.unexamine.moduleconfig.base.entity.ConfiguredModuleField;
import com.unique.unexamine.moduleconfig.base.entity.ConfiguredModulePage;

import java.util.List;

public record ModuleDraft(
        ConfiguredModule module,
        List<ConfiguredModuleField> fields,
        List<ConfiguredModulePage> pages,
        List<ConfiguredModuleAction> actions,
        boolean published) {
}
