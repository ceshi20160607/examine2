import type { RouteSection } from "../router";

export type PlatformShellMode = "workspace" | "admin";

export interface PlatformShellNavItem {
    key: string;
    label: string;
    routeName: string;
    section: RouteSection;
    adminOnly?: boolean;
}

export const PLATFORM_WORKSPACE_NAV: PlatformShellNavItem[] = [
    { key: "platform.mySystems", label: "我的系统", routeName: "platform.mySystems", section: "platform" },
];

export const PLATFORM_ADMIN_NAV: PlatformShellNavItem[] = [
    { key: "platform.systems", label: "业务系统", routeName: "platform.systems", section: "platform", adminOnly: true },
    { key: "platform.accounts", label: "平台账号", routeName: "platform.accounts", section: "platform", adminOnly: true },
    { key: "platform.roles", label: "平台角色", routeName: "platform.roles", section: "platform", adminOnly: true },
    { key: "platform.configs", label: "平台配置", routeName: "platform.configs", section: "platform", adminOnly: true },
    { key: "platform.openapi", label: "对外应用", routeName: "platform.openapi", section: "openapi", adminOnly: true },
    { key: "audit.platform", label: "平台审计", routeName: "audit.platform", section: "audit-ops", adminOnly: true },
    { key: "ops.health", label: "运维诊断", routeName: "ops.health", section: "audit-ops", adminOnly: true },
];

const ADMIN_ROUTE_NAMES = new Set(PLATFORM_ADMIN_NAV.map((item) => item.routeName));

export function resolvePlatformShellMode(routeName: string): PlatformShellMode {
    return ADMIN_ROUTE_NAMES.has(routeName) ? "admin" : "workspace";
}

export function platformNavForMode(mode: PlatformShellMode): PlatformShellNavItem[] {
    return mode === "admin" ? PLATFORM_ADMIN_NAV : PLATFORM_WORKSPACE_NAV;
}
