package com.unique.examine.core.security;

/**
 * 自建系统 module 权限键（与 {@code un_module_menu.perm_key}、{@code un_module_role_perm.perm_key} 一致）。
 * HTTP 路径与权限的绑定挂在菜单 {@code un_module_menu.api_pattern} 上，系统建立时种子写入。
 */
public final class ModulePermCodes {

    private ModulePermCodes() {}

    /** 菜单/工作台功能类权限前缀，如 {@code mod.menu.moduleRecord} */
    public static final String MENU_PREFIX = "mod.menu.";

    /**
     * 与 {@code /v1/system/{resource}/**} 中 resource 段一致，例如 resource={@code moduleRecord}。
     */
    public static String menuKey(String resourceSegment) {
        if (resourceSegment == null || resourceSegment.isBlank()) {
            throw new IllegalArgumentException("resourceSegment");
        }
        return MENU_PREFIX + resourceSegment.trim();
    }
}
