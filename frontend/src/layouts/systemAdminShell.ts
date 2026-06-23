import type { RouteSection } from "../router";

export interface SystemAdminNavGroup {
    key: string;
    label: string;
    items: SystemAdminNavItem[];
}

export interface SystemAdminNavItem {
    key: string;
    label: string;
    routeName: string;
    section: RouteSection;
}

/** 系统后台左侧分组菜单（page-inventory §0 系统配置态） */
export const SYSTEM_ADMIN_NAV_GROUPS: SystemAdminNavGroup[] = [
    {
        key: "overview",
        label: "系统总览",
        items: [
            { key: "system.overview", label: "总览", routeName: "system.overview", section: "system" },
            { key: "system.profile", label: "系统资料", routeName: "system.profile", section: "system" },
            { key: "system.tenants", label: "租户", routeName: "system.tenants", section: "system" },
        ],
    },
    {
        key: "rbac",
        label: "组织与权限",
        items: [
            { key: "system.members", label: "成员", routeName: "system.members", section: "rbac" },
            { key: "system.departments", label: "部门", routeName: "system.departments", section: "rbac" },
            { key: "system.roles", label: "系统角色", routeName: "system.roles", section: "rbac" },
            { key: "system.dict", label: "字典", routeName: "system.dict", section: "rbac" },
        ],
    },
    {
        key: "modeling",
        label: "建模配置",
        items: [
            { key: "apps.list", label: "业务应用", routeName: "apps.list", section: "app" },
            { key: "modules.fields", label: "字段设计", routeName: "modules.fields", section: "app" },
            { key: "modules.ui", label: "页面发布", routeName: "modules.ui", section: "app" },
        ],
    },
    {
        key: "flow",
        label: "协同流程",
        items: [
            { key: "flow.templates", label: "流程模板", routeName: "flow.templates", section: "flow" },
            { key: "flow.workbench", label: "流程工作台", routeName: "flow.workbench", section: "flow" },
        ],
    },
    {
        key: "assets",
        label: "文件与导出",
        items: [
            { key: "files.center", label: "文件", routeName: "files.center", section: "file-export" },
            { key: "exports.jobs", label: "导出任务", routeName: "exports.jobs", section: "file-export" },
        ],
    },
    {
        key: "openapi",
        label: "对外授权",
        items: [
            { key: "openapi.clients", label: "系统 OpenAPI", routeName: "openapi.clients", section: "openapi" },
        ],
    },
    {
        key: "audit",
        label: "审计",
        items: [
            { key: "audit.system", label: "系统审计", routeName: "audit.system", section: "audit-ops" },
        ],
    },
];

export const SYSTEM_ADMIN_ROUTE_NAMES = new Set(
    SYSTEM_ADMIN_NAV_GROUPS.flatMap((group) => group.items.map((item) => item.routeName)),
);

export function isSystemAdminRoute(routeName: string): boolean {
    return SYSTEM_ADMIN_ROUTE_NAMES.has(routeName);
}
