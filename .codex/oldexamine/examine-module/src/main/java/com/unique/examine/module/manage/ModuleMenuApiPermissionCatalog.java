package com.unique.examine.module.manage;

import com.unique.examine.core.security.ModulePermCodes;

import java.util.List;

/**
 * Default invisible API gates seeded into un_module_menu.
 */
public final class ModuleMenuApiPermissionCatalog {

    private ModuleMenuApiPermissionCatalog() {}

    public record Entry(String apiPattern, String permKey, String menuTitle) {}

    public static final List<Entry> ENTRIES = List.of(
            e("/v1/system/module/meta/apps/**", "moduleApp", "应用"),
            e("/v1/system/module/meta/models/**", "moduleModel", "模型"),
            e("/v1/system/module/meta/fields/**", "moduleField", "字段"),
            e("/v1/system/module/meta/relations/**", "moduleRelation", "模型关系"),
            e("/v1/system/module/dicts/**", "moduleDict", "字典"),
            e("/v1/system/module/depts/**", "moduleDept", "部门"),
            e("/v1/system/module/pages/**", "modulePage", "页面"),
            e("/v1/system/module/list-views/**", "moduleListView", "列表视图"),
            e("/v1/system/module/exports/**", "moduleExport", "导出模板"),
            e("/v1/system/module/export-jobs/**", "moduleExport", "导出任务"),
            e("/v1/system/module/rbac/**", "moduleRbac", "权限"),
            e("/v1/system/module/flow-bindings/**", "moduleFlowBinding", "流程绑定"),
            e("/v1/system/records/**", "moduleRecord", "数据记录"),
            e("/v1/system/uploads/**", "uploadFile", "附件"),
            e("/v1/system/flow/inbox/**", "flowInbox", "流程待办"),
            e("/v1/system/flow/**", "flow", "流程")
    );

    private static Entry e(String pattern, String resourceSegment, String menuTitle) {
        return new Entry(pattern, ModulePermCodes.menuKey(resourceSegment), menuTitle);
    }
}
