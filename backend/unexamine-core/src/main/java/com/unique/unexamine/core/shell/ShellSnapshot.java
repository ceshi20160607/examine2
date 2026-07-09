package com.unique.unexamine.core.shell;

import java.util.List;

public record ShellSnapshot(
        String context,
        String activeSystemName,
        List<ShellNavItem> navigation,
        List<ShellNavItem> adminNavigation,
        List<ModuleGroup> moduleGroups,
        List<String> flowManagement,
        List<String> applicationGateway,
        List<ShellMetric> dashboardMetrics,
        List<ShellActionItem> workItems,
        List<ShellActionItem> todoItems,
        List<ShellActionItem> messageItems,
        List<ShellActionItem> aiItems,
        List<String> layoutRules
) {
}
