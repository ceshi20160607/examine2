package com.unique.unexamine.moduleconfig.manage;

public record RuntimeModuleCatalogItem(
        Long groupId,
        String groupCode,
        String groupName,
        Integer groupSortOrder,
        Long moduleId,
        String moduleCode,
        String moduleName) {
}
