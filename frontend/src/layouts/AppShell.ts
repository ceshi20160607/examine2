import type { AppRouteRecord, RouteSection } from "../router";
import { APP_ROUTES, buildPath } from "../router";
import type { AuthStore } from "../stores/auth";
import type { PermissionDecision } from "../stores/permission";
import type { PermissionStore } from "../stores/permission";
import type { SystemContextStore } from "../stores/systemContext";

export interface ShellNavigationItem {
  key: string;
  label: string;
  path: string;
  section: RouteSection;
  disabled: boolean;
  disabledReason?: string;
  apiEvidence: string[];
}

export interface ShellNavigationGroup {
  key: RouteSection;
  label: string;
  items: ShellNavigationItem[];
}

export interface AppShellState {
  currentTitle: string;
  systemName?: string;
  tenantName?: string;
  memberName?: string;
  contextDisabledReason?: string;
  navigation: ShellNavigationGroup[];
  requestIdVisible: boolean;
}

export const SHELL_SECTION_LABELS: Record<RouteSection, string> = {
  auth: "认证",
  platform: "平台工作空间",
  system: "系统总览",
  rbac: "系统设置",
  app: "建模配置",
  runtime: "业务运行",
  flow: "协同流程",
  "file-export": "文件与导出",
  openapi: "对外应用",
  "audit-ops": "审计与运维",
};

export interface ResolveAppShellOptions {
  currentPath: string;
  auth?: AuthStore;
  systemContext: SystemContextStore;
  permission: PermissionStore;
}

export function resolveAppShellState(options: ResolveAppShellOptions): AppShellState {
  const context = options.systemContext.getState();
  const currentRoute = APP_ROUTES.find((item) => matchRouteForShell(item, options.currentPath));
  const navigation = buildNavigation(options.systemContext, options.permission, options.auth);

  return {
    currentTitle: currentRoute?.meta.title ?? "unexamine",
    systemName: context.current?.system.systemName,
    tenantName: context.current?.tenant?.tenantName,
    memberName: context.current?.member.displayName,
    contextDisabledReason: context.disabledReason,
    navigation,
    requestIdVisible: options.auth?.getState().status === "authenticated",
  };
}

export function buildNavigation(
  systemContext: SystemContextStore,
  permission: PermissionStore,
  auth?: AuthStore,
): ShellNavigationGroup[] {
  const groups = new Map<RouteSection, ShellNavigationItem[]>();

  APP_ROUTES.filter((item) => item.meta.layout !== "auth").forEach((item) => {
    const contextMissing = item.meta.requiredContext ? systemContext.validate(item.meta.requiredContext) : [];
    const decision =
      item.meta.layout === "platform"
        ? auth
          ? decidePlatformPermission(auth, item.meta.permission)
          : { visible: true, enabled: true }
        : permission.decide(item.meta.permission);
    const disabled = contextMissing.length > 0 || !decision.enabled || !decision.visible;
    const path = buildNavigationPath(item, systemContext);
    const navItem: ShellNavigationItem = {
      key: item.name,
      label: item.meta.title,
      path,
      section: item.meta.section,
      disabled,
      disabledReason:
        contextMissing.length > 0 ? `缺少上下文: ${contextMissing.join(",")}` : decision.disabledReason,
      apiEvidence: item.meta.apiIds,
    };

    const group = groups.get(item.meta.section) ?? [];
    group.push(navItem);
    groups.set(item.meta.section, group);
  });

  return Array.from(groups.entries()).map(([key, items]) => ({
    key,
    label: SHELL_SECTION_LABELS[key],
    items,
  }));
}

function decidePlatformPermission(auth: AuthStore, permission?: AppRouteRecord["meta"]["permission"]): PermissionDecision {
  if (!permission) {
    return { visible: true, enabled: true };
  }
  if (permission.allOperations?.some((item) => !auth.hasPlatformPermission(item))) {
    return { visible: true, enabled: false, disabledReason: "PERM_DENIED" };
  }
  if (permission.anyOperations && !permission.anyOperations.some((item) => auth.hasPlatformPermission(item))) {
    return { visible: true, enabled: false, disabledReason: "PERM_DENIED" };
  }
  return { visible: true, enabled: true };
}

function buildNavigationPath(routeRecord: AppRouteRecord, systemContext: SystemContextStore): string {
  const context = systemContext.getState().current;
  if (routeRecord.path.includes(":appId") || routeRecord.path.includes(":moduleId")) {
    return context ? `/systems/${context.system.systemId}/overview` : routeRecord.path;
  }
  try {
    return buildPath(routeRecord.name, {
      systemId: context?.system.systemId,
    });
  } catch {
    return routeRecord.path;
  }
}

function matchRouteForShell(routeRecord: AppRouteRecord, actualPath: string): boolean {
  const patternParts = routeRecord.path.split("/").filter(Boolean);
  const actualParts = actualPath.split("?")[0].split("/").filter(Boolean);
  if (patternParts.length !== actualParts.length) {
    return false;
  }
  return patternParts.every((part, index) => part.startsWith(":") || part === actualParts[index]);
}
