package com.unique.unexamine.moduleconfig.manage;

import com.unique.unexamine.moduleconfig.base.entity.ConfiguredModule;
import com.unique.unexamine.moduleconfig.base.entity.ConfiguredModuleGroup;

import java.util.List;

public record ModuleConfigurationOverview(
        List<ConfiguredModuleGroup> groups,
        List<ConfiguredModule> modules) {
}
