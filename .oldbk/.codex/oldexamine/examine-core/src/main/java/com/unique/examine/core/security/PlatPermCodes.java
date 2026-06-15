package com.unique.examine.core.security;

/**
 * 平台级权限码：由 {@code un_plat_menu.perm_code} + 角色-菜单-账号 解析；
 * 无 RBAC 绑定时可回退 {@code un_plat_account.plat_perm_codes}（逗号分隔）。
 * 仅约束<strong>平台态</strong>菜单与接口；系统内权限见 module 域。
 */
public final class PlatPermCodes {

    private PlatPermCodes() {}

    /** 平台控制台入口（菜单显隐） */
    public static final String CONSOLE = "CONSOLE";

    /** 创建自建系统 */
    public static final String SYSTEM_CREATE = "SYSTEM_CREATE";

    /** 自建系统启用/停用 */
    public static final String SYSTEM_STATUS = "SYSTEM_STATUS";

    /** 自建系统删除（软删） */
    public static final String SYSTEM_DELETE = "SYSTEM_DELETE";

    /** 预留：平台侧 App 新建 */
    public static final String APP_CREATE = "APP_CREATE";

    /** 预留：平台侧 App 删除 */
    public static final String APP_DELETE = "APP_DELETE";

    /** 预留：平台侧 App 启停 */
    public static final String APP_STATUS = "APP_STATUS";
}
