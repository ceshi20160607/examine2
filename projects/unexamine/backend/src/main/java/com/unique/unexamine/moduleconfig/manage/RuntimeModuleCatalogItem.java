package com.unique.unexamine.moduleconfig.manage;

public record RuntimeModuleCatalogItem(
        Long groupId,
        String groupCode,
        String groupName,
        Integer groupSortOrder,
        Long moduleId,
        String moduleCode,
        String moduleName,
        Long menuId,
        Long menuParentId,
        String menuCode,
        String menuName,
        String menuIcon,
        String menuRoutePath,
        Integer menuSortOrder) {
}
