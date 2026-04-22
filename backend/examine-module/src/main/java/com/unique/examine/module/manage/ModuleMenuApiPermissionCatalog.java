package com.unique.examine.module.manage;

import com.unique.examine.core.security.ModulePermCodes;

import java.util.List;

/**
 * 系统/租户种子写入菜单时的「接口门」默认清单：写入 {@code un_module_menu}（perm_key + api_pattern，visible_flag=0）。
 * 注意：菜单权限与业务功能可不统一；最终展示以实际配置为准。
 */
public final class ModuleMenuApiPermissionCatalog {

    private ModuleMenuApiPermissionCatalog() {}

    public record Entry(String apiPattern, String permKey, String menuTitle) {}

    public static final List<Entry> ENTRIES = List.of(
            e("/v1/system/moduleApp/**", "moduleApp", "应用"),
            e("/v1/system/moduleAppVersion/**", "moduleAppVersion", "应用版本"),
            e("/v1/system/moduleField/**", "moduleField", "字段"),
            e("/v1/system/moduleIndex/**", "moduleIndex", "索引"),
            e("/v1/system/moduleMenu/**", "moduleMenu", "菜单"),
            e("/v1/system/moduleModel/**", "moduleModel", "模型"),
            e("/v1/system/modulePage/**", "modulePage", "页面"),
            e("/v1/system/modulePageBlock/**", "modulePageBlock", "页面区块"),
            e("/v1/system/moduleRecord/**", "moduleRecord", "数据记录"),
            e("/v1/system/moduleRecordData/**", "moduleRecordData", "记录数据"),
            e("/v1/system/moduleRecordHistory/**", "moduleRecordHistory", "记录历史"),
            e("/v1/system/moduleRelation/**", "moduleRelation", "关系"),
            e("/v1/system/moduleMember/**", "moduleMember", "成员"),
            e("/v1/system/moduleRole/**", "moduleRole", "角色")
    );

    private static Entry e(String pattern, String resourceSegment, String menuTitle) {
        return new Entry(pattern, ModulePermCodes.menuKey(resourceSegment), menuTitle);
    }
}

