package com.unique.unexamine.moduleconfig.manage;

/** 当前用户获权的运行目录；来源私有模块仅提供共享记录入口，不代表拥有其配置。 */
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
        Integer menuSortOrder,
        // CURRENT 使用本租户有效配置；SHARED_RECORDS 只消费逐记录核准的来源字段和动作。
        String configurationMode) {
}
