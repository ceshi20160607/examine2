import { createApiClient, type ApiClient } from "./api/client";
import { API_ENDPOINTS, type ApiEndpointId } from "./api/endpoints";
import { createFetchTransport } from "./api/fetchTransport";
import type { EffectivePermissionVO, EntityId, FieldPermission, JsonValue, PageResult } from "./api/types";
import { resolveAppShellState, SHELL_SECTION_LABELS } from "./layouts/AppShell";
import { createAuthPageModel } from "./pages/auth";
import { createMySystemsPageModel } from "./pages/my-systems";
import {
  createPlatformCenterPageModel,
  redactPlatformConfigValue,
  type PlatformAccountListVO,
  type PlatformConfigVO,
  type PlatformPageState,
  type PlatformRoleVO,
  type PlatformSystemListVO,
} from "./pages/platform/platformCenter";
import type {
  DepartmentNodeVO,
  DictCacheRefreshVO,
  DictItemVO,
  DictStatus,
  DictTypeVO,
  DictUsageVO,
  ManageStatus,
  MemberDetailVO,
  MemberListVO,
  PermissionCatalogVO,
  RolePermissionDetailVO,
  RoleVO,
} from "./pages/system/types";
import { APP_ROUTES, DEFAULT_AUTHENTICATED_ROUTE, LOGIN_ROUTE, resolveRoute, type AppRouteRecord } from "./router";
import { authStore, permissionStore, systemContextStore } from "./stores";

interface RuntimeSettings {
  baseUrl: string;
  accessToken: string;
  systemId: string;
  tenantId: string;
  appId: string;
  moduleId: string;
}

interface UiState {
  settings: RuntimeSettings;
  busy: boolean;
  lastRequestId?: string;
  lastMessage?: string;
  lastError?: string;
}

interface PlatformUiState {
  systems: PlatformPageState<PlatformSystemListVO>;
  accounts: PlatformPageState<PlatformAccountListVO>;
  roles: PlatformPageState<PlatformRoleVO>;
  configs: PlatformPageState<PlatformConfigVO>;
  permissionCatalogSummary?: string;
}

interface SystemUiState {
  profile?: SystemEnterVO;
  tenants: SystemTenantVO[];
  members: MemberListVO[];
  memberDetails: Record<string, MemberDetailVO>;
  departments: DepartmentNodeVO[];
  roles: RoleVO[];
  permissionCatalog?: PermissionCatalogVO;
  rolePermissions: Record<string, RolePermissionDetailVO>;
  dictTypes: DictTypeVO[];
  selectedDictTypeId?: EntityId;
  dictItems: DictItemVO[];
  dictUsage?: DictUsageVO;
  dictCache?: DictCacheRefreshVO;
}

interface SystemEnterVO {
  systemId: EntityId;
  systemCode?: string;
  systemName: string;
  status: string;
  tenantMode: "SINGLE" | "MULTI";
  currentTenant?: {
    tenantId: EntityId;
    code?: string;
    name: string;
    status: string;
  };
  tenants?: SystemTenantVO[];
  currentMember: {
    memberId: EntityId;
    displayName: string;
    status?: "ENABLED" | "DISABLED";
    roleIds?: EntityId[];
    roles?: string[];
  };
  permissions?: {
    memberId?: EntityId;
    roles?: string[];
    menus?: string[];
    operations?: string[];
    fieldPermissions?: FieldPermission[] | Record<string, JsonValue>;
    dataScopes?: EffectivePermissionVO["dataScopes"];
    availableActions?: EffectivePermissionVO["availableActions"];
    version?: number | string;
  };
}

interface SystemTenantVO {
  tenantId: EntityId;
  systemId?: EntityId;
  code?: string;
  name?: string;
  tenantCode?: string;
  tenantName?: string;
  status: "ENABLED" | "DISABLED" | string;
  description?: string;
}

const rootPermissions = [
  "PLAT_SYSTEM_VIEW",
  "PLAT_SYSTEM_CREATE",
  "PLAT_SYSTEM_STATUS",
  "PLAT_ACCOUNT_VIEW",
  "PLAT_ACCOUNT_CREATE",
  "PLAT_ACCOUNT_STATUS",
  "PLAT_ROLE_VIEW",
  "PLAT_ROLE_AUTH",
  "PLAT_CONFIG_VIEW",
  "PLAT_CONFIG_EDIT",
  "SYS_PROFILE_VIEW",
  "SYS_TENANT_VIEW",
  "SYS_MEMBER_VIEW",
  "SYS_DEPT_VIEW",
  "SYS_ROLE_VIEW",
  "DICT_VIEW",
  "APP_VIEW",
  "MODULE_VIEW",
  "FIELD_VIEW",
  "PAGE_VIEW",
  "RECORD_VIEW",
  "FLOW_TEMPLATE_VIEW",
  "FILE_VIEW",
  "EXPORT_JOB_VIEW",
  "OPENAPI_CLIENT_VIEW",
  "AUDIT_OPERATION_VIEW",
  "AUDIT_REQUEST_VIEW",
  "AUDIT_ERROR_VIEW",
  "PLAT_AUDIT_VIEW",
  "OPS_HEALTH_VIEW",
  "OPS_CONFIG_VIEW",
  "OPS_VERSION_VIEW",
  "OPS_MIGRATION_VIEW",
];

export function mountApp(container: HTMLElement): void {
  const ui: UiState = {
    settings: loadSettings(),
    busy: false,
  };
  if (ui.settings.accessToken) {
    seedPreviewState(ui.settings);
  }
  let apiClient = createClient(ui.settings);
  const authPage = createAuthPageModel({ apiClient });
  const mySystemsPage = createMySystemsPageModel({ apiClient });
  const platformCenter = createPlatformCenterPageModel({ api: apiClient });
  const platformState: PlatformUiState = {
    systems: emptyPlatformPageState(),
    accounts: emptyPlatformPageState(),
    roles: emptyPlatformPageState(),
    configs: emptyPlatformPageState(),
  };
  const systemState: SystemUiState = {
    tenants: [],
    members: [],
    memberDetails: {},
    departments: [],
    roles: [],
    rolePermissions: {},
    dictTypes: [],
    dictItems: [],
  };
  const autoLoadedRoutes = new Set<string>();

  syncHash();
  window.addEventListener("hashchange", render);
  authStore.subscribe(render);
  systemContextStore.subscribe(render);
  permissionStore.subscribe(render);

  function createClient(settings: RuntimeSettings): ApiClient {
    return createApiClient({
      transport: createFetchTransport({ baseUrl: settings.baseUrl }),
      getContext: () => ({
        accessToken: authStore.getState().token?.accessToken ?? settings.accessToken,
        tenantId: systemContextStore.toTenantHeader() ?? settings.tenantId,
        systemId: systemContextStore.getState().current?.system.systemId ?? settings.systemId,
        memberId: systemContextStore.getState().current?.member.memberId,
      }),
    });
  }

  function render(): void {
    const path = getCurrentPath();
    const resolvedRoute = resolveRoute({ path }) ?? APP_ROUTES[0];
    const authState = authStore.getState();
    const route = resolvedRoute.meta.requiresAuth && authState.status !== "authenticated"
      ? resolveRoute({ path: LOGIN_ROUTE }) ?? APP_ROUTES[0]
      : resolvedRoute;
    const shell = resolveAppShellState({
      currentPath: path,
      auth: authStore,
      systemContext: systemContextStore,
      permission: permissionStore,
    });

    if (route.meta.section === "auth") {
      container.replaceChildren(renderAuth(route));
      return;
    }

    autoLoadRoute(route);

    container.replaceChildren(
      node("div", { className: "app-frame" }, [
        renderSidebar(path, shell.navigation),
        node("main", { className: "workspace" }, [
          renderTopbar(shell.currentTitle, route),
          renderWorkspace(route),
        ]),
      ]),
    );
  }

  function renderSidebar(path: string, groups: ReturnType<typeof resolveAppShellState>["navigation"]): HTMLElement {
    return node("aside", { className: "sidebar" }, [
      node("a", { className: "brand", href: "#/platform/my-systems" }, [
        node("span", { className: "brand-mark", text: "U" }),
        node("span", { className: "brand-name", text: "unexamine" }),
      ]),
      node("nav", { className: "nav-groups" }, groups.map((group) =>
        node("section", { className: "nav-group" }, [
          node("div", { className: "nav-title", text: group.label }),
          ...group.items.map((item) =>
            node("a", {
              className: `nav-link${path === item.path ? " active" : ""}${item.disabled ? " is-muted" : ""}`,
              href: `#${item.path}`,
              title: item.disabledReason ?? item.apiEvidence.join(", "),
              text: item.label,
            }),
          ),
        ]),
      )),
    ]);
  }

  function renderTopbar(title: string, route: AppRouteRecord): HTMLElement {
    const context = systemContextStore.getState().current;
    return node("header", { className: "topbar" }, [
      node("div", {}, [
        node("p", { className: "eyebrow", text: sectionLabel(route) }),
        node("h1", { text: title }),
      ]),
      node("div", { className: "context-strip" }, [
        node("span", { text: context?.system.systemName ?? "未进入系统" }),
        node("span", { text: context?.tenant?.tenantName ?? "默认租户" }),
        node("span", { text: authStore.getState().user?.displayName ?? "未登录" }),
      ]),
    ]);
  }

  function renderAuth(route: AppRouteRecord): HTMLElement {
    const isRegister = route.name === "auth.register";
    return node("main", { className: "login-page" }, [
      node("section", { className: "auth-surface" }, [
        node("div", { className: "auth-panel" }, [
          node("div", { className: "brand auth-brand" }, [
            node("span", { className: "brand-mark", text: "U" }),
            node("span", { className: "brand-name", text: "unexamine" }),
          ]),
          node("h1", { text: isRegister ? "创建平台账号" : "登录平台" }),
          node("p", { className: "form-note", text: "进入平台后可管理系统、成员、应用配置、运行台、流程、文件、OpenAPI 和审计运维。" }),
          input("账号", "loginName", ""),
          input("密码", "password", "", "password"),
          isRegister ? input("显示名称", "displayName", "") : undefined,
          node("div", { className: "button-row" }, [
            button(isRegister ? "注册并登录" : "登录", "primary", () => submitAuth(isRegister)),
            node("a", {
              className: "btn secondary",
              href: isRegister ? "#/auth/login" : "#/auth/register",
              text: isRegister ? "返回登录" : "注册账号",
            }),
          ]),
          renderMessage(),
        ]),
        node("div", { className: "auth-media" }, [
          node("div", { className: "media-board" }, [
            stat("系统", "动态建模"),
            stat("流程", "审批协同"),
            stat("审计", "全链路追踪"),
          ]),
        ]),
      ]),
    ]);
  }

  function renderWorkspace(route: AppRouteRecord): HTMLElement {
    const endpointIds = route.meta.apiIds;
    return node("section", { className: "content-grid" }, [
      node("div", { className: "work-panel primary-panel" }, [
        node("div", { className: "panel-heading" }, [
          node("div", {}, [
            node("p", { className: "eyebrow", text: sectionLabel(route) }),
            node("h2", { text: route.meta.title }),
          ]),
          button("刷新", "secondary", () => runRouteLoad(route)),
        ]),
        renderRouteBody(route),
      ]),
      node("div", { className: "work-panel" }, [
        node("div", { className: "panel-heading" }, [
          node("h2", { text: "当前模块" }),
          node("span", { className: "count-pill", text: `${endpointIds.length} 个能力` }),
        ]),
        renderModuleSummary(route),
      ]),
      node("div", { className: "work-panel" }, [
        node("div", { className: "panel-heading" }, [
          node("h2", { text: "操作结果" }),
          node("span", { className: statusClass(), text: authStatusLabel() }),
        ]),
        renderMessage(),
      ]),
    ]);
  }

  function renderRouteBody(route: AppRouteRecord): HTMLElement {
    if (route.name === "platform.mySystems") {
      return node("div", {}, [
        node("div", { className: "metric-grid" }, [
          stat(String(mySystemsPage.state.systems.length), "我的系统"),
          stat(mySystemsPage.state.createEnabled ? "可用" : "禁用", "创建系统"),
          stat(mySystemsPage.state.loading ? "同步中" : "就绪", "列表状态"),
        ]),
        node("div", { className: "table-shell" }, [
          node("div", { className: "table-row table-head" }, [
            node("span", { text: "系统" }),
            node("span", { text: "租户模式" }),
            node("span", { text: "状态" }),
          ]),
          ...mySystemsPage.state.systems.map((item) =>
            node("button", { className: "table-row", onClick: () => enterSystem(item.systemId) }, [
              node("span", { text: item.systemName }),
              node("span", { text: item.tenantMode }),
              node("span", { text: item.status }),
            ]),
          ),
        ]),
      ]);
    }

    if (route.name === "platform.systems") {
      return renderPlatformSystems();
    }

    if (route.name === "platform.accounts") {
      return renderPlatformAccounts();
    }

    if (route.name === "platform.roles") {
      return renderPlatformRoles();
    }

    if (route.name === "platform.configs") {
      return renderPlatformConfigs();
    }

    if (route.name === "system.profile") {
      return renderSystemProfile();
    }

    if (route.name === "system.tenants") {
      return renderSystemTenants();
    }

    if (route.name === "system.members") {
      return renderSystemMembers();
    }

    if (route.name === "system.departments") {
      return renderSystemDepartments();
    }

    if (route.name === "system.roles") {
      return renderSystemRoles();
    }

    if (route.name === "system.dict") {
      return renderSystemDict();
    }

    return node("div", {}, [
      node("div", { className: "toolbar-row" }, [
        input("关键字", "keyword", ""),
        button("查询", "primary", () => runRouteLoad(route)),
      ]),
      node("div", { className: "table-shell" }, [
        node("div", { className: "table-row table-head" }, [
          node("span", { text: "名称" }),
          node("span", { text: "状态" }),
          node("span", { text: "更新时间" }),
        ]),
        node("div", { className: "empty-state" }, [
          node("strong", { text: "暂无数据" }),
          node("span", { text: "点击查询后会加载当前模块数据。" }),
        ]),
      ]),
    ]);
  }

  function renderPlatformSystems(): HTMLElement {
    const actions = platformCenter.systems.actions();
    return node("div", { className: "admin-page" }, [
      renderPageMetrics([
        [String(platformState.systems.total), "系统总数"],
        [enabledCount(platformState.systems.records), "启用中"],
        [platformState.systems.loading ? "同步中" : "就绪", "列表状态"],
      ]),
      node("div", { className: "form-grid" }, [
        input("系统名称", "platformSystemName", ""),
        input("系统编码", "platformSystemCode", ""),
        selectField("租户模式", "platformSystemTenantMode", "SINGLE", [
          ["SINGLE", "单租户"],
          ["MULTI", "多租户"],
        ]),
        textarea("系统说明", "platformSystemDescription", ""),
        button("创建系统", "primary", createPlatformSystem, !actions.create.enabled),
      ]),
      renderDataTable(
        "systems",
        ["系统", "编码", "租户模式", "状态", "操作"],
        platformState.systems.records.map((item) => [
          item.systemName,
          item.systemCode,
          tenantModeLabel(item.tenantMode),
          statusLabel(item.status),
          node("div", { className: "row-actions" }, [
            button(item.status === "ENABLED" ? "禁用" : "启用", "secondary", () => togglePlatformSystem(item), !platformCenter.systems.actions(item).status.enabled),
            button("进入", "secondary", () => enterSystem(item.systemId), !platformCenter.systems.actions(item).view.enabled),
          ]),
        ]),
        platformState.systems.empty ? "暂无平台系统，创建后会显示在这里。" : undefined,
      ),
    ]);
  }

  function renderPlatformAccounts(): HTMLElement {
    const actions = platformCenter.accounts.actions();
    return node("div", { className: "admin-page" }, [
      renderPageMetrics([
        [String(platformState.accounts.total), "账号总数"],
        [enabledCount(platformState.accounts.records), "正常账号"],
        [platformState.accounts.loading ? "同步中" : "就绪", "列表状态"],
      ]),
      node("div", { className: "form-grid" }, [
        input("登录名", "platformAccountLoginName", ""),
        input("显示名称", "platformAccountDisplayName", ""),
        input("手机号", "platformAccountMobile", ""),
        input("邮箱", "platformAccountEmail", ""),
        input("初始密码", "platformAccountInitialPassword", "", "password"),
        button("创建账号", "primary", createPlatformAccount, !actions.create.enabled),
      ]),
      renderDataTable(
        "accounts",
        ["账号", "显示名称", "联系方式", "状态", "操作"],
        platformState.accounts.records.map((item) => [
          item.loginName,
          item.displayName ?? "-",
          [item.mobile, item.email].filter(Boolean).join(" / ") || "-",
          statusLabel(item.status),
          node("div", { className: "row-actions" }, [
            button("编辑", "secondary", () => editPlatformAccount(item), !platformCenter.accounts.actions(item).edit.enabled),
            button(item.status === "NORMAL" ? "禁用" : "启用", "secondary", () => togglePlatformAccount(item), !platformCenter.accounts.actions(item).status.enabled),
            button("重置密码", "secondary", () => resetPlatformAccountPassword(item), !platformCenter.accounts.actions(item).resetPassword.enabled),
            button("分配角色", "secondary", () => assignPlatformAccountRoles(item), !platformCenter.accounts.actions(item).assignRoles.enabled),
          ]),
        ]),
        platformState.accounts.empty ? "暂无平台账号，可在上方创建。" : undefined,
      ),
    ]);
  }

  function renderPlatformRoles(): HTMLElement {
    const actions = platformCenter.roles.actions();
    return node("div", { className: "admin-page" }, [
      renderPageMetrics([
        [String(platformState.roles.total), "角色总数"],
        [enabledCount(platformState.roles.records), "启用角色"],
        [platformState.permissionCatalogSummary ?? "未加载", "权限目录"],
      ]),
      node("div", { className: "form-grid" }, [
        input("角色编码", "platformRoleCode", ""),
        input("角色名称", "platformRoleName", ""),
        textarea("角色说明", "platformRoleDescription", ""),
        button("创建角色", "primary", createPlatformRole, !actions.create.enabled),
        button("加载权限目录", "secondary", loadPlatformPermissionCatalog, !actions.authorize.enabled),
      ]),
      renderDataTable(
        "roles",
        ["角色", "编码", "状态", "权限数", "操作"],
        platformState.roles.records.map((item) => [
          item.name,
          item.code,
          statusLabel(item.status),
          String(item.operationCodes?.length ?? 0),
          node("div", { className: "row-actions" }, [
            button("编辑", "secondary", () => editPlatformRole(item), !platformCenter.roles.actions(item).edit.enabled),
            button(item.status === "ENABLED" ? "禁用" : "启用", "secondary", () => togglePlatformRole(item), !platformCenter.roles.actions(item).status.enabled),
            button("授权", "secondary", () => authorizePlatformRole(item), !platformCenter.roles.actions(item).authorize.enabled),
          ]),
        ]),
        platformState.roles.empty ? "暂无平台角色，可在上方创建。" : undefined,
      ),
    ]);
  }

  function renderPlatformConfigs(): HTMLElement {
    const actions = platformCenter.configs.actions();
    return node("div", { className: "admin-page" }, [
      renderPageMetrics([
        [String(platformState.configs.total), "配置总数"],
        [String(platformState.configs.records.filter((item) => item.sensitive).length), "敏感配置"],
        [platformState.configs.loading ? "同步中" : "就绪", "列表状态"],
      ]),
      node("div", { className: "form-grid" }, [
        input("配置 Key", "platformConfigKey", ""),
        input("配置值", "platformConfigValue", ""),
        textarea("备注", "platformConfigRemark", ""),
        button("更新配置", "primary", updatePlatformConfig, !actions.edit.enabled),
      ]),
      renderDataTable(
        "configs",
        ["配置", "Key", "值", "状态", "操作"],
        platformState.configs.records.map((item) => [
          item.configName,
          item.configKey,
          redactPlatformConfigValue(item),
          statusLabel(item.status),
          node("div", { className: "row-actions" }, [
            button("带入编辑", "secondary", () => fillPlatformConfigForm(item), !platformCenter.configs.actions(item).edit.enabled),
          ]),
        ]),
        platformState.configs.empty ? "暂无平台配置。" : undefined,
      ),
    ]);
  }

  function renderSystemProfile(): HTMLElement {
    const profile = systemState.profile;
    const context = systemContextStore.getState().current;
    const actions = {
      edit: permissionStore.decide({ anyOperations: ["SYS_PROFILE_EDIT"] }),
    };
    const systemName = profile?.systemName ?? context?.system.systemName ?? "";
    const systemCode = profile?.systemCode ?? context?.system.systemCode ?? "";
    const tenantMode = profile?.tenantMode ?? context?.system.tenantMode ?? "SINGLE";
    const currentTenant = profile?.currentTenant ?? context?.tenant;
    const member = profile?.currentMember ?? context?.member;

    return node("div", { className: "admin-page" }, [
      renderPageMetrics([
        [systemName || "-", "系统名称"],
        [statusLabel(profile?.status ?? context?.system.status), "系统状态"],
        [tenantModeLabel(tenantMode), "租户模式"],
      ]),
      node("div", { className: "form-grid" }, [
        input("系统名称", "systemProfileName", systemName),
        input("访问域名", "systemProfileDomain", ""),
        textarea("系统说明", "systemProfileDescription", ""),
        button("保存资料", "primary", saveSystemProfile, !actions.edit.enabled),
      ]),
      renderDataTable(
        "systems",
        ["字段", "内容", "说明"],
        [
          ["系统 ID", profile?.systemId ?? context?.system.systemId ?? "-", "当前系统上下文"],
          ["系统编码", systemCode || "-", "平台创建系统时生成或录入"],
          ["当前租户", tenantName(currentTenant), currentTenant?.status ? statusLabel(currentTenant.status) : "-"],
          ["当前成员", member?.displayName ?? "-", member?.status ? statusLabel(member.status) : "-"],
          ["系统权限", String(profile?.permissions?.operations?.length ?? permissionStore.getState().effective?.operations?.length ?? 0), "当前成员已加载操作权限数"],
        ],
        "进入系统后会自动加载系统资料。",
      ),
    ]);
  }

  function renderSystemTenants(): HTMLElement {
    const actions = {
      create: permissionStore.decide({ anyOperations: ["SYS_TENANT_CREATE"] }),
      status: permissionStore.decide({ anyOperations: ["SYS_TENANT_STATUS"] }),
    };

    return node("div", { className: "admin-page" }, [
      renderPageMetrics([
        [String(systemState.tenants.length), "租户总数"],
        [enabledCount(systemState.tenants), "启用中"],
        [systemContextStore.getState().current?.system.systemName ?? "-", "所属系统"],
      ]),
      node("div", { className: "form-grid" }, [
        input("租户编码", "tenantCode", ""),
        input("租户名称", "tenantName", ""),
        textarea("租户说明", "tenantDescription", ""),
        button("创建租户", "primary", createSystemTenant, !actions.create.enabled),
      ]),
      renderDataTable(
        "systems",
        ["租户", "编码", "状态", "说明", "操作"],
        systemState.tenants.map((item) => [
          tenantName(item),
          tenantCode(item),
          statusLabel(item.status),
          item.description ?? "-",
          node("div", { className: "row-actions" }, [
            button(item.status === "ENABLED" ? "禁用" : "启用", "secondary", () => toggleSystemTenant(item), !actions.status.enabled),
          ]),
        ]),
        "暂无租户，创建后会显示在这里。",
      ),
    ]);
  }

  function renderSystemMembers(): HTMLElement {
    const actions = {
      invite: permissionStore.decide({ anyOperations: ["SYS_MEMBER_INVITE"] }),
      edit: permissionStore.decide({ anyOperations: ["SYS_MEMBER_EDIT"] }),
      status: permissionStore.decide({ anyOperations: ["SYS_MEMBER_STATUS"] }),
      assignRoles: permissionStore.decide({ anyOperations: ["SYS_ROLE_ASSIGN"] }),
    };

    return node("div", { className: "admin-page" }, [
      renderPageMetrics([
        [String(systemState.members.length), "成员总数"],
        [enabledCount(systemState.members), "启用成员"],
        [String(systemState.roles.length), "可分配角色"],
      ]),
      node("div", { className: "form-grid" }, [
        input("平台账号登录名", "memberLoginName", ""),
        input("岗位", "memberPostName", ""),
        input("部门 ID", "memberDeptIds", ""),
        input("角色 ID", "memberRoleIds", ""),
        input("租户 ID", "memberTenantIds", systemContextStore.getState().current?.tenant?.tenantId ?? ""),
        button("邀请成员", "primary", inviteSystemMember, !actions.invite.enabled),
      ]),
      renderDataTable(
        "members",
        ["成员", "登录名", "部门", "角色", "状态", "操作"],
        systemState.members.map((item) => [
          item.displayName,
          item.loginName,
          memberDeptLabel(item),
          item.roles?.join(", ") || "-",
          statusLabel(item.status),
          node("div", { className: "row-actions" }, [
            button("详情", "secondary", () => loadSystemMemberDetail(item.memberId)),
            button("编辑", "secondary", () => editSystemMember(item), !actions.edit.enabled),
            button(item.status === "ENABLED" ? "停用" : "启用", "secondary", () => toggleSystemMember(item), !actions.status.enabled),
            button("分配角色", "secondary", () => assignSystemMemberRoles(item), !actions.assignRoles.enabled),
          ]),
        ]),
        "暂无成员，邀请或刷新后会显示在这里。",
      ),
    ]);
  }

  function renderSystemDepartments(): HTMLElement {
    const actions = {
      create: permissionStore.decide({ anyOperations: ["SYS_DEPT_CREATE"] }),
      edit: permissionStore.decide({ anyOperations: ["SYS_DEPT_EDIT"] }),
      delete: permissionStore.decide({ anyOperations: ["SYS_DEPT_DELETE"] }),
    };
    const departments = flattenDepartments(systemState.departments);

    return node("div", { className: "admin-page" }, [
      renderPageMetrics([
        [String(departments.length), "部门总数"],
        [String(departments.filter((item) => !item.parentId).length), "根部门"],
        [String(departments.reduce((sum, item) => sum + (item.memberCount ?? 0), 0)), "关联成员"],
      ]),
      node("div", { className: "form-grid" }, [
        input("部门编码", "deptCode", ""),
        input("部门名称", "deptName", ""),
        input("父部门 ID", "deptParentId", ""),
        input("排序", "deptSortOrder", "0"),
        button("创建部门", "primary", createSystemDepartment, !actions.create.enabled),
      ]),
      renderDataTable(
        "departments",
        ["部门", "编码", "父级", "成员数", "操作"],
        departments.map((item) => [
          `${"  ".repeat(item.depth)}${item.deptName}`,
          item.deptCode,
          item.parentId ?? "根部门",
          String(item.memberCount ?? 0),
          node("div", { className: "row-actions" }, [
            button("编辑", "secondary", () => editSystemDepartment(item), !actions.edit.enabled),
            button("删除", "secondary", () => deleteSystemDepartment(item), !actions.delete.enabled || Boolean(departmentDeleteReason(item))),
          ]),
        ]),
        "暂无部门，创建后会显示在这里。",
      ),
    ]);
  }

  function renderSystemRoles(): HTMLElement {
    const actions = {
      create: permissionStore.decide({ anyOperations: ["SYS_ROLE_CREATE"] }),
      edit: permissionStore.decide({ anyOperations: ["SYS_ROLE_EDIT"] }),
      status: permissionStore.decide({ anyOperations: ["SYS_ROLE_STATUS"] }),
      permission: permissionStore.decide({ anyOperations: ["SYS_ROLE_PERMISSION_EDIT"] }),
    };

    return node("div", { className: "admin-page" }, [
      renderPageMetrics([
        [String(systemState.roles.length), "角色总数"],
        [enabledCount(systemState.roles), "启用角色"],
        [permissionCatalogLabel(systemState.permissionCatalog), "权限目录"],
      ]),
      node("div", { className: "form-grid" }, [
        input("角色编码", "systemRoleCode", ""),
        input("角色名称", "systemRoleName", ""),
        textarea("角色说明", "systemRoleDescription", ""),
        button("创建角色", "primary", createSystemRole, !actions.create.enabled),
        button("加载权限目录", "secondary", loadSystemPermissionCatalog),
      ]),
      renderDataTable(
        "roles",
        ["角色", "编码", "成员数", "状态", "操作"],
        systemState.roles.map((item) => [
          item.name,
          item.code,
          String(item.memberCount ?? 0),
          statusLabel(item.status),
          node("div", { className: "row-actions" }, [
            button("编辑", "secondary", () => editSystemRole(item), !actions.edit.enabled || Boolean(item.protected)),
            button(item.status === "ENABLED" ? "停用" : "启用", "secondary", () => toggleSystemRole(item), !actions.status.enabled || Boolean(item.protected)),
            button("读取授权", "secondary", () => loadSystemRolePermissions(item)),
            button("保存授权", "secondary", () => saveSystemRolePermissions(item), !actions.permission.enabled),
          ]),
        ]),
        "暂无系统角色，创建后会显示在这里。",
      ),
    ]);
  }

  function renderSystemDict(): HTMLElement {
    const actions = {
      createType: permissionStore.decide({ anyOperations: ["DICT_CREATE"] }),
      editType: permissionStore.decide({ anyOperations: ["DICT_EDIT"] }),
      statusType: permissionStore.decide({ anyOperations: ["DICT_STATUS"] }),
      createItem: permissionStore.decide({ anyOperations: ["DICT_ITEM_CREATE"] }),
      editItem: permissionStore.decide({ anyOperations: ["DICT_ITEM_EDIT"] }),
      statusItem: permissionStore.decide({ anyOperations: ["DICT_ITEM_STATUS"] }),
      deleteType: permissionStore.decide({ anyOperations: ["DICT_DELETE"] }),
      deleteItem: permissionStore.decide({ anyOperations: ["DICT_ITEM_DELETE"] }),
    };
    const selectedType = selectedDictType();
    const items = flattenDictItems(systemState.dictItems);

    return node("div", { className: "admin-page" }, [
      renderPageMetrics([
        [String(systemState.dictTypes.length), "字典类型"],
        [selectedType?.name ?? "未选择", "当前类型"],
        [systemState.dictCache ? String(systemState.dictCache.cacheVersion) : "-", "缓存版本"],
      ]),
      node("div", { className: "form-grid" }, [
        input("类型编码", "dictTypeCode", ""),
        input("类型名称", "dictTypeName", ""),
        selectField("作用域", "dictScopeType", "SYSTEM", [["SYSTEM", "系统"], ["TENANT", "租户"]]),
        input("排序", "dictTypeSortOrder", "0"),
        textarea("类型说明", "dictTypeDescription", ""),
        button("创建类型", "primary", createDictType, !actions.createType.enabled),
      ]),
      renderDataTable(
        "dict-types",
        ["类型", "编码", "项数", "状态", "操作"],
        systemState.dictTypes.map((item) => [
          item.name,
          item.code,
          `${item.enabledItemCount}/${item.itemCount}`,
          statusLabel(item.status),
          node("div", { className: "row-actions" }, [
            button("选择", "secondary", () => selectDictTypeAndLoad(item)),
            button("编辑", "secondary", () => editDictType(item), !actions.editType.enabled || item.systemBuiltIn),
            button(item.status === "ENABLED" ? "停用" : "启用", "secondary", () => toggleDictType(item), !actions.statusType.enabled || item.systemBuiltIn),
            button("使用情况", "secondary", () => loadDictUsage(item.dictTypeId)),
            button("删除", "secondary", () => deleteDictType(item), !actions.deleteType.enabled || item.systemBuiltIn),
          ]),
        ]),
        "暂无字典类型，创建或刷新后会显示在这里。",
      ),
      node("div", { className: "form-grid" }, [
        input("字典项编码", "dictItemCode", ""),
        input("字典项标签", "dictItemLabel", ""),
        input("字典项值", "dictItemValue", ""),
        input("父项 ID", "dictItemParentId", ""),
        input("排序", "dictItemSortOrder", "0"),
        button("创建字典项", "primary", createDictItem, !selectedType || !actions.createItem.enabled),
      ]),
      renderDataTable(
        "dict-items",
        ["字典项", "值", "层级", "状态", "操作"],
        items.map((item) => [
          `${"  ".repeat(item.depth)}${item.label}`,
          item.value,
          String(item.depthLevel),
          statusLabel(item.status),
          node("div", { className: "row-actions" }, [
            button("编辑", "secondary", () => editDictItem(item), !actions.editItem.enabled || item.systemBuiltIn),
            button(item.status === "ENABLED" ? "停用" : "启用", "secondary", () => toggleDictItem(item), !actions.statusItem.enabled || item.systemBuiltIn),
            button("删除", "secondary", () => deleteDictItem(item), !actions.deleteItem.enabled || item.systemBuiltIn),
          ]),
        ]),
        selectedType ? "暂无字典项，创建后会显示在这里。" : "请先选择一个字典类型。",
      ),
    ]);
  }

  function renderPageMetrics(items: [string, string][]): HTMLElement {
    return node("div", { className: "metric-grid" }, items.map(([value, label]) => stat(value, label)));
  }

  function renderDataTable(className: string, headers: string[], rows: Child[][], emptyText?: string): HTMLElement {
    return node("div", { className: "data-table" }, [
      node("div", { className: `data-row data-head ${className}` }, headers.map((item) => node("span", { text: item }))),
      rows.length > 0
        ? node("div", { className: "data-body" }, rows.map((cells) =>
            node("div", { className: `data-row ${className}` }, cells.map((cell) => (cell instanceof Node ? cell : node("span", { text: cell })))),
          ))
        : node("div", { className: "empty-state" }, [
            node("strong", { text: "暂无数据" }),
            node("span", { text: emptyText ?? "点击刷新后会加载当前模块数据。" }),
          ]),
    ]);
  }

  function renderModuleSummary(route: AppRouteRecord): HTMLElement {
    return node("div", { className: "summary-list" }, [
      node("div", {}, [
        node("span", { text: "业务域" }),
        node("strong", { text: sectionLabel(route) }),
      ]),
      node("div", {}, [
        node("span", { text: "权限" }),
        node("strong", { text: route.meta.permission?.anyOperations?.[0] ?? "登录用户" }),
      ]),
      node("div", {}, [
        node("span", { text: "上下文" }),
        node("strong", { text: route.meta.requiredContext ? "系统内" : "平台级" }),
      ]),
    ]);
  }

  async function submitAuth(register: boolean): Promise<void> {
    const form = readForm();
    await execute(register ? "AUTH-001" : "AUTH-002", async () => {
      const result = register
        ? await authPage.register({
            loginName: form.loginName,
            password: form.password,
            displayName: form.displayName,
          })
        : await authPage.login({
            loginName: form.loginName,
            password: form.password,
          });
      navigate(result.route);
      return result;
    });
  }

  async function runRouteLoad(route: AppRouteRecord): Promise<void> {
    if (route.name === "platform.mySystems") {
      await execute("PLAT-001", () => mySystemsPage.loadSystems());
      return;
    }
    if (route.name === "platform.systems") {
      await loadPlatformSystems();
      return;
    }
    if (route.name === "platform.accounts") {
      await loadPlatformAccounts();
      return;
    }
    if (route.name === "platform.roles") {
      await loadPlatformRoles();
      return;
    }
    if (route.name === "platform.configs") {
      await loadPlatformConfigs();
      return;
    }
    if (route.name === "system.profile") {
      await loadSystemProfile();
      return;
    }
    if (route.name === "system.tenants") {
      await loadSystemTenants();
      return;
    }
    if (route.name === "system.members") {
      await loadSystemMembers();
      return;
    }
    if (route.name === "system.departments") {
      await loadSystemDepartments();
      return;
    }
    if (route.name === "system.roles") {
      await loadSystemRoles();
      return;
    }
    if (route.name === "system.dict") {
      await loadDictTypes();
      return;
    }
    const apiId = firstRunnableEndpoint(route.meta.apiIds);
    if (!apiId) {
      return;
    }
    await execute(apiId, () =>
      apiClient.call(apiId, {
        pathParams: pathParams(),
        query: { pageNo: 1, pageSize: 10 },
        idempotencyKey: `ui-${Date.now()}`,
      }),
    );
  }

  async function loadPlatformSystems(): Promise<void> {
    await execute("PLAT-003", async () => {
      const page = await platformCenter.systems.query({ pageNo: 1, pageSize: 20, keyword: readForm().keyword });
      raisePageError(page);
      platformState.systems = page;
      return { requestId: page.lastRequestId };
    });
  }

  async function createPlatformSystem(): Promise<void> {
    const form = readForm();
    await execute("PLAT-002", async () => {
      const result = await platformCenter.systems.create({
        name: required(form.platformSystemName, "系统名称"),
        code: required(form.platformSystemCode, "系统编码"),
        tenantMode: (form.platformSystemTenantMode || "SINGLE") as "SINGLE" | "MULTI",
        description: form.platformSystemDescription?.trim() || undefined,
      });
      platformState.systems = await platformCenter.systems.query({ pageNo: 1, pageSize: 20 });
      return { requestId: result.requestId };
    });
  }

  async function togglePlatformSystem(item: PlatformSystemListVO): Promise<void> {
    const nextStatus = item.status === "ENABLED" ? "DISABLED" : "ENABLED";
    if (!window.confirm(`确认将系统“${item.systemName}”变更为${statusLabel(nextStatus)}？`)) {
      return;
    }
    await execute("PLAT-005", async () => {
      const result = await platformCenter.systems.changeStatus(item.systemId, { status: nextStatus });
      platformState.systems = await platformCenter.systems.query({ pageNo: 1, pageSize: 20 });
      return { requestId: result.requestId };
    });
  }

  async function loadPlatformAccounts(): Promise<void> {
    await execute("PLAT-006", async () => {
      const page = await platformCenter.accounts.query({ pageNo: 1, pageSize: 20, keyword: readForm().keyword });
      raisePageError(page);
      platformState.accounts = page;
      return { requestId: page.lastRequestId };
    });
  }

  async function createPlatformAccount(): Promise<void> {
    const form = readForm();
    await execute("PLAT-007", async () => {
      const result = await platformCenter.accounts.create({
        loginName: required(form.platformAccountLoginName, "登录名"),
        displayName: form.platformAccountDisplayName?.trim() || undefined,
        mobile: form.platformAccountMobile?.trim() || undefined,
        email: form.platformAccountEmail?.trim() || undefined,
        initialPassword: required(form.platformAccountInitialPassword, "初始密码"),
      });
      platformState.accounts = await platformCenter.accounts.query({ pageNo: 1, pageSize: 20 });
      return { requestId: result.requestId };
    });
  }

  async function editPlatformAccount(item: PlatformAccountListVO): Promise<void> {
    const displayName = window.prompt("显示名称", item.displayName ?? "") ?? item.displayName;
    const mobile = window.prompt("手机号", item.mobile ?? "") ?? item.mobile;
    const email = window.prompt("邮箱", item.email ?? "") ?? item.email;
    await execute("PLAT-014", async () => {
      const result = await platformCenter.accounts.update(item.accountId, {
        displayName: displayName?.trim() || undefined,
        mobile: mobile?.trim() || undefined,
        email: email?.trim() || undefined,
      });
      platformState.accounts = await platformCenter.accounts.query({ pageNo: 1, pageSize: 20 });
      return { requestId: result.requestId };
    });
  }

  async function togglePlatformAccount(item: PlatformAccountListVO): Promise<void> {
    const nextStatus = item.status === "NORMAL" ? "DISABLED" : "NORMAL";
    if (!window.confirm(`确认将账号“${item.loginName}”变更为${statusLabel(nextStatus)}？`)) {
      return;
    }
    await execute("PLAT-008", async () => {
      const result = await platformCenter.accounts.changeStatus(item.accountId, { status: nextStatus });
      platformState.accounts = await platformCenter.accounts.query({ pageNo: 1, pageSize: 20 });
      return { requestId: result.requestId };
    });
  }

  async function resetPlatformAccountPassword(item: PlatformAccountListVO): Promise<void> {
    const newPassword = window.prompt(`请输入账号“${item.loginName}”的新密码`, "");
    if (!newPassword) {
      return;
    }
    await execute("PLAT-015", async () => {
      const result = await platformCenter.accounts.resetPassword(item.accountId, {
        newPassword,
        forceChangeOnLogin: false,
        reason: "平台管理员重置",
      });
      return { requestId: result.requestId };
    });
  }

  async function assignPlatformAccountRoles(item: PlatformAccountListVO): Promise<void> {
    const roleIds = window.prompt("请输入角色 ID，多个用英文逗号分隔", item.roleIds?.join(",") ?? "");
    if (roleIds === null) {
      return;
    }
    await execute("PLAT-016", async () => {
      const result = await platformCenter.accounts.assignRoles(item.accountId, { roleIds: splitValues(roleIds) });
      platformState.accounts = await platformCenter.accounts.query({ pageNo: 1, pageSize: 20 });
      return { requestId: result.requestId };
    });
  }

  async function loadPlatformRoles(): Promise<void> {
    await execute("PLAT-009", async () => {
      const page = await platformCenter.roles.query({ pageNo: 1, pageSize: 20, keyword: readForm().keyword });
      raisePageError(page);
      platformState.roles = page;
      return { requestId: page.lastRequestId };
    });
  }

  async function createPlatformRole(): Promise<void> {
    const form = readForm();
    await execute("PLAT-017", async () => {
      const result = await platformCenter.roles.create({
        code: required(form.platformRoleCode, "角色编码"),
        name: required(form.platformRoleName, "角色名称"),
        description: form.platformRoleDescription?.trim() || undefined,
      });
      platformState.roles = await platformCenter.roles.query({ pageNo: 1, pageSize: 20 });
      return { requestId: result.requestId };
    });
  }

  async function editPlatformRole(item: PlatformRoleVO): Promise<void> {
    const name = window.prompt("角色名称", item.name) ?? item.name;
    const description = window.prompt("角色说明", item.description ?? "") ?? item.description;
    await execute("PLAT-018", async () => {
      const result = await platformCenter.roles.update(item.roleId, {
        code: item.code,
        name: required(name, "角色名称"),
        description: description?.trim() || undefined,
      });
      platformState.roles = await platformCenter.roles.query({ pageNo: 1, pageSize: 20 });
      return { requestId: result.requestId };
    });
  }

  async function togglePlatformRole(item: PlatformRoleVO): Promise<void> {
    const nextStatus = item.status === "ENABLED" ? "DISABLED" : "ENABLED";
    if (!window.confirm(`确认将角色“${item.name}”变更为${statusLabel(nextStatus)}？`)) {
      return;
    }
    await execute("PLAT-019", async () => {
      const result = await platformCenter.roles.changeStatus(item.roleId, { status: nextStatus });
      platformState.roles = await platformCenter.roles.query({ pageNo: 1, pageSize: 20 });
      return { requestId: result.requestId };
    });
  }

  async function authorizePlatformRole(item: PlatformRoleVO): Promise<void> {
    const operationCodes = window.prompt("请输入操作权限编码，多个用英文逗号分隔", item.operationCodes?.join(",") ?? "");
    if (operationCodes === null) {
      return;
    }
    const menuIds = window.prompt("请输入菜单 ID，多个用英文逗号分隔", item.menuIds?.join(",") ?? "");
    if (menuIds === null) {
      return;
    }
    await execute("PLAT-010", async () => {
      const result = await platformCenter.roles.saveMenus(item.roleId, {
        menuIds: splitValues(menuIds),
        operationCodes: splitValues(operationCodes),
      });
      platformState.roles = await platformCenter.roles.query({ pageNo: 1, pageSize: 20 });
      return { requestId: result.requestId };
    });
  }

  async function loadPlatformPermissionCatalog(): Promise<void> {
    await execute("PLAT-020", async () => {
      const catalog = await platformCenter.roles.permissionCatalog();
      if (catalog.error) {
        throw new Error(catalog.error.message);
      }
      const menus = catalog.detail?.menus.length ?? 0;
      const operations = catalog.detail?.operationCodes.length ?? 0;
      platformState.permissionCatalogSummary = `${menus} 个菜单 / ${operations} 个操作`;
      return { requestId: catalog.lastRequestId };
    });
  }

  async function loadPlatformConfigs(): Promise<void> {
    await execute("PLAT-011", async () => {
      const page = await platformCenter.configs.list({ pageNo: 1, pageSize: 20, keyword: readForm().keyword });
      raisePageError(page);
      platformState.configs = page;
      return { requestId: page.lastRequestId };
    });
  }

  async function updatePlatformConfig(): Promise<void> {
    const form = readForm();
    await execute("PLAT-012", async () => {
      const result = await platformCenter.configs.update(required(form.platformConfigKey, "配置 Key"), {
        value: required(form.platformConfigValue, "配置值"),
        remark: form.platformConfigRemark?.trim() || undefined,
      });
      platformState.configs = await platformCenter.configs.list({ pageNo: 1, pageSize: 20 });
      return { requestId: result.requestId };
    });
  }

  function fillPlatformConfigForm(item: PlatformConfigVO): void {
    setFormValue("platformConfigKey", item.configKey);
    setFormValue("platformConfigValue", item.sensitive ? "" : String(item.value ?? ""));
    setFormValue("platformConfigRemark", item.remark ?? "");
  }

  async function loadSystemProfile(): Promise<void> {
    await execute("SYS-002", async () => {
      const response = await apiClient.call<SystemEnterVO>("SYS-002", {
        pathParams: pathParams(),
      });
      systemState.profile = response.data;
      if (response.data.tenants) {
        systemState.tenants = response.data.tenants;
      }
      return response;
    });
  }

  async function saveSystemProfile(): Promise<void> {
    const form = readForm();
    await execute("SYS-003", async () => {
      const response = await apiClient.call<SystemEnterVO, { name: string; description?: string; domain?: string }>("SYS-003", {
        pathParams: pathParams(),
        body: {
          name: required(form.systemProfileName, "系统名称"),
          description: form.systemProfileDescription?.trim() || undefined,
          domain: form.systemProfileDomain?.trim() || undefined,
        },
      });
      systemState.profile = response.data;
      return response;
    });
  }

  async function loadSystemTenants(): Promise<void> {
    await execute("SYS-004", async () => {
      const response = await apiClient.call<SystemTenantVO[]>("SYS-004", {
        pathParams: pathParams(),
      });
      systemState.tenants = response.data ?? [];
      return response;
    });
  }

  async function createSystemTenant(): Promise<void> {
    const form = readForm();
    await execute("SYS-005", async () => {
      const response = await apiClient.call<SystemTenantVO, { code: string; name: string; description?: string }>("SYS-005", {
        pathParams: pathParams(),
        body: {
          code: required(form.tenantCode, "租户编码"),
          name: required(form.tenantName, "租户名称"),
          description: form.tenantDescription?.trim() || undefined,
        },
      });
      const tenantsResponse = await apiClient.call<SystemTenantVO[]>("SYS-004", {
        pathParams: pathParams(),
      });
      systemState.tenants = tenantsResponse.data ?? [];
      return response;
    });
  }

  async function toggleSystemTenant(item: SystemTenantVO): Promise<void> {
    const nextStatus = item.status === "ENABLED" ? "DISABLED" : "ENABLED";
    if (!window.confirm(`确认将租户“${tenantName(item)}”变更为${statusLabel(nextStatus)}？`)) {
      return;
    }
    await execute("SYS-006", async () => {
      const response = await apiClient.call<SystemTenantVO, { targetStatus: string; reason?: string }>("SYS-006", {
        pathParams: {
          ...pathParams(),
          tenantId: item.tenantId,
        },
        body: {
          targetStatus: nextStatus,
          reason: "页面操作",
        },
      });
      const tenantsResponse = await apiClient.call<SystemTenantVO[]>("SYS-004", {
        pathParams: pathParams(),
      });
      systemState.tenants = tenantsResponse.data ?? [];
      return response;
    });
  }

  async function loadSystemMembers(): Promise<void> {
    await execute("MEM-001", async () => {
      requireSystemContext();
      const response = await apiClient.call<MemberListVO[] | PageResult<MemberListVO>, undefined, Record<string, string | number | undefined>>("MEM-001", {
        pathParams: pathParams(),
        query: {
          pageNo: 1,
          pageSize: 50,
          keyword: readForm().keyword,
        },
      });
      const page = normalizePage(response.data);
      systemState.members = page.records;
      return response;
    });
  }

  async function inviteSystemMember(): Promise<void> {
    const form = readForm();
    await execute("MEM-002", async () => {
      requireSystemContext();
      const response = await apiClient.call<MemberDetailVO, {
        loginName?: string;
        tenantIds?: EntityId[];
        deptIds?: EntityId[];
        postName?: string;
        roleIds?: EntityId[];
      }>("MEM-002", {
        pathParams: pathParams(),
        body: {
          loginName: required(form.memberLoginName, "平台账号登录名"),
          tenantIds: splitValues(form.memberTenantIds ?? ""),
          deptIds: splitValues(form.memberDeptIds ?? ""),
          postName: form.memberPostName?.trim() || undefined,
          roleIds: splitValues(form.memberRoleIds ?? ""),
        },
      });
      await refreshSystemManagementOptions();
      await loadSystemMembers();
      return response;
    });
  }

  async function loadSystemMemberDetail(memberId: EntityId): Promise<void> {
    await execute("MEM-003", async () => {
      requireSystemContext();
      const response = await apiClient.call<MemberDetailVO>("MEM-003", {
        pathParams: {
          ...pathParams(),
          memberId,
        },
      });
      systemState.memberDetails[memberId] = response.data;
      ui.lastMessage = `成员详情：${response.data.displayName}，部门 ${response.data.deptIds?.join(",") || "-"}，角色 ${memberRoleIds(response.data).join(",") || "-"}`;
      return response;
    });
  }

  async function editSystemMember(item: MemberListVO): Promise<void> {
    const detail = systemState.memberDetails[item.memberId] ?? await fetchMemberDetail(item.memberId);
    const deptIds = window.prompt("部门 ID，多个用英文逗号分隔", detail.deptIds?.join(",") ?? "") ?? detail.deptIds?.join(",");
    const tenantIds = window.prompt("租户 ID，多个用英文逗号分隔", detail.tenantIds?.join(",") ?? "") ?? detail.tenantIds?.join(",");
    const postName = window.prompt("岗位", detail.postName ?? "") ?? detail.postName;
    await execute("MEM-004", async () => {
      requireSystemContext();
      const response = await apiClient.call<MemberDetailVO, { tenantIds?: EntityId[]; deptIds?: EntityId[]; postName?: string }>("MEM-004", {
        pathParams: {
          ...pathParams(),
          memberId: item.memberId,
        },
        body: {
          tenantIds: splitValues(tenantIds ?? ""),
          deptIds: splitValues(deptIds ?? ""),
          postName: postName?.trim() || undefined,
        },
      });
      systemState.memberDetails[item.memberId] = response.data;
      await loadSystemMembers();
      return response;
    });
  }

  async function toggleSystemMember(item: MemberListVO): Promise<void> {
    const nextStatus = item.status === "ENABLED" ? "DISABLED" : "ENABLED";
    if (!window.confirm(`确认将成员“${item.displayName}”变更为${statusLabel(nextStatus)}？`)) {
      return;
    }
    await execute("MEM-005", async () => {
      requireSystemContext();
      const response = await apiClient.call<MemberDetailVO, { targetStatus: string; reason?: string }>("MEM-005", {
        pathParams: {
          ...pathParams(),
          memberId: item.memberId,
        },
        body: {
          targetStatus: nextStatus,
          reason: "系统管理页面操作",
        },
      });
      systemState.memberDetails[item.memberId] = response.data;
      await loadSystemMembers();
      return response;
    });
  }

  async function assignSystemMemberRoles(item: MemberListVO): Promise<void> {
    const detail = systemState.memberDetails[item.memberId] ?? await fetchMemberDetail(item.memberId);
    const roleIds = window.prompt("角色 ID，多个用英文逗号分隔", memberRoleIds(detail).join(","));
    if (roleIds === null) {
      return;
    }
    await execute("MEM-006", async () => {
      requireSystemContext();
      const response = await apiClient.call<MemberDetailVO, { roleIds: EntityId[] }>("MEM-006", {
        pathParams: {
          ...pathParams(),
          memberId: item.memberId,
        },
        body: {
          roleIds: splitValues(roleIds),
        },
      });
      systemState.memberDetails[item.memberId] = response.data;
      await loadSystemMembers();
      return response;
    });
  }

  async function loadSystemDepartments(): Promise<void> {
    await execute("RBAC-001", async () => {
      requireSystemContext();
      const response = await apiClient.call<DepartmentNodeVO[]>("RBAC-001", {
        pathParams: pathParams(),
      });
      systemState.departments = response.data ?? [];
      return response;
    });
  }

  async function createSystemDepartment(): Promise<void> {
    const form = readForm();
    await execute("RBAC-002", async () => {
      requireSystemContext();
      const response = await apiClient.call<DepartmentNodeVO, { parentId?: EntityId; deptCode: string; deptName: string; sortOrder?: number }>("RBAC-002", {
        pathParams: pathParams(),
        body: {
          parentId: form.deptParentId?.trim() || undefined,
          deptCode: required(form.deptCode, "部门编码"),
          deptName: required(form.deptName, "部门名称"),
          sortOrder: optionalNumber(form.deptSortOrder),
        },
      });
      await loadSystemDepartments();
      return response;
    });
  }

  async function editSystemDepartment(item: DepartmentNodeVO): Promise<void> {
    const deptName = window.prompt("部门名称", item.deptName) ?? item.deptName;
    const deptCode = window.prompt("部门编码", item.deptCode) ?? item.deptCode;
    const sortOrder = window.prompt("排序", String(item.sortOrder ?? 0)) ?? String(item.sortOrder ?? 0);
    await execute("RBAC-003", async () => {
      requireSystemContext();
      const response = await apiClient.call<DepartmentNodeVO, { parentId?: EntityId; deptCode: string; deptName: string; sortOrder?: number }>("RBAC-003", {
        pathParams: {
          ...pathParams(),
          deptId: item.deptId,
        },
        body: {
          parentId: item.parentId,
          deptCode: required(deptCode, "部门编码"),
          deptName: required(deptName, "部门名称"),
          sortOrder: optionalNumber(sortOrder),
        },
      });
      await loadSystemDepartments();
      return response;
    });
  }

  async function deleteSystemDepartment(item: DepartmentNodeVO): Promise<void> {
    const reason = departmentDeleteReason(item);
    if (reason) {
      ui.lastError = reason;
      render();
      return;
    }
    if (!window.confirm(`确认删除部门“${item.deptName}”？`)) {
      return;
    }
    await execute("RBAC-004", async () => {
      requireSystemContext();
      const response = await apiClient.call<{ deleted: boolean }>("RBAC-004", {
        pathParams: {
          ...pathParams(),
          deptId: item.deptId,
        },
      });
      await loadSystemDepartments();
      return response;
    });
  }

  async function loadSystemRoles(): Promise<void> {
    await execute("RBAC-005", async () => {
      requireSystemContext();
      const response = await apiClient.call<RoleVO[] | PageResult<RoleVO>>("RBAC-005", {
        pathParams: pathParams(),
        query: {
          pageNo: 1,
          pageSize: 50,
          keyword: readForm().keyword,
        },
      });
      systemState.roles = normalizePage(response.data).records;
      return response;
    });
  }

  async function createSystemRole(): Promise<void> {
    const form = readForm();
    await execute("RBAC-006", async () => {
      requireSystemContext();
      const response = await apiClient.call<RoleVO, { code: string; name: string; description?: string }>("RBAC-006", {
        pathParams: pathParams(),
        body: {
          code: required(form.systemRoleCode, "角色编码"),
          name: required(form.systemRoleName, "角色名称"),
          description: form.systemRoleDescription?.trim() || undefined,
        },
      });
      await loadSystemRoles();
      return response;
    });
  }

  async function editSystemRole(item: RoleVO): Promise<void> {
    const name = window.prompt("角色名称", item.name) ?? item.name;
    const description = window.prompt("角色说明", item.description ?? "") ?? item.description;
    await execute("RBAC-007", async () => {
      requireSystemContext();
      const response = await apiClient.call<RoleVO, { code: string; name: string; description?: string }>("RBAC-007", {
        pathParams: {
          ...pathParams(),
          roleId: item.roleId,
        },
        body: {
          code: item.code,
          name: required(name, "角色名称"),
          description: description?.trim() || undefined,
        },
      });
      await loadSystemRoles();
      return response;
    });
  }

  async function toggleSystemRole(item: RoleVO): Promise<void> {
    const nextStatus = item.status === "ENABLED" ? "DISABLED" : "ENABLED";
    if (!window.confirm(`确认将系统角色“${item.name}”变更为${statusLabel(nextStatus)}？`)) {
      return;
    }
    await execute("RBAC-008", async () => {
      requireSystemContext();
      const response = await apiClient.call<RoleVO, { targetStatus: string; reason?: string; version?: number }>("RBAC-008", {
        pathParams: {
          ...pathParams(),
          roleId: item.roleId,
        },
        body: {
          targetStatus: nextStatus,
          reason: "系统管理页面操作",
          version: item.version,
        },
      });
      await loadSystemRoles();
      return response;
    });
  }

  async function loadSystemPermissionCatalog(): Promise<void> {
    await execute("RBAC-013", async () => {
      requireSystemContext();
      const response = await apiClient.call<PermissionCatalogVO>("RBAC-013", {
        pathParams: pathParams(),
      });
      systemState.permissionCatalog = response.data;
      return response;
    });
  }

  async function loadSystemRolePermissions(item: RoleVO): Promise<void> {
    await execute("RBAC-012", async () => {
      requireSystemContext();
      const response = await apiClient.call<RolePermissionDetailVO>("RBAC-012", {
        pathParams: {
          ...pathParams(),
          roleId: item.roleId,
        },
      });
      systemState.rolePermissions[item.roleId] = response.data;
      return response;
    });
  }

  async function saveSystemRolePermissions(item: RoleVO): Promise<void> {
    const current = systemState.rolePermissions[item.roleId];
    const operationCodes = window.prompt("操作权限编码，多个用英文逗号分隔", current?.operationCodes?.join(",") ?? "");
    if (operationCodes === null) {
      return;
    }
    const menuIds = window.prompt("菜单 ID，多个用英文逗号分隔", current?.menuIds?.join(",") ?? "");
    if (menuIds === null) {
      return;
    }
    const scopeType = window.prompt("数据范围 SELF/DEPT/DEPT_TREE/ALL/CUSTOM", current?.dataScope?.scopeType ?? "ALL") ?? "ALL";
    await execute("RBAC-009", async () => {
      requireSystemContext();
      const response = await apiClient.call<RolePermissionDetailVO, {
        menuIds?: EntityId[];
        operationCodes?: string[];
        dataScope?: { scopeType: "SELF" | "DEPT" | "DEPT_TREE" | "ALL" | "CUSTOM" };
      }>("RBAC-009", {
        pathParams: {
          ...pathParams(),
          roleId: item.roleId,
        },
        body: {
          menuIds: splitValues(menuIds),
          operationCodes: splitValues(operationCodes),
          dataScope: { scopeType: normalizeScopeType(scopeType) },
        },
      });
      systemState.rolePermissions[item.roleId] = response.data;
      await refreshEffectivePermissions();
      return response;
    });
  }

  async function loadDictTypes(): Promise<void> {
    await execute("DICT-001", async () => {
      requireSystemContext();
      const response = await apiClient.call<DictTypeVO[] | PageResult<DictTypeVO>, undefined, Record<string, string | number | undefined>>("DICT-001", {
        pathParams: pathParams(),
        query: {
          pageNo: 1,
          pageSize: 50,
          keyword: readForm().keyword,
        },
      });
      systemState.dictTypes = normalizePage(response.data).records;
      if (!systemState.selectedDictTypeId && systemState.dictTypes[0]) {
        systemState.selectedDictTypeId = systemState.dictTypes[0].dictTypeId;
        await loadDictItems(systemState.selectedDictTypeId);
      }
      return response;
    });
  }

  async function createDictType(): Promise<void> {
    const form = readForm();
    await execute("DICT-002", async () => {
      requireSystemContext();
      const response = await apiClient.call<DictTypeVO | { entity: DictTypeVO; cacheRefresh?: DictCacheRefreshVO }, {
        scopeType: "SYSTEM" | "TENANT";
        tenantId?: EntityId;
        code: string;
        name: string;
        description?: string;
        sortOrder?: number;
      }>("DICT-002", {
        pathParams: pathParams(),
        body: {
          scopeType: (form.dictScopeType || "SYSTEM") as "SYSTEM" | "TENANT",
          tenantId: form.dictScopeType === "TENANT" ? systemContextStore.toTenantHeader() : undefined,
          code: required(form.dictTypeCode, "类型编码"),
          name: required(form.dictTypeName, "类型名称"),
          description: form.dictTypeDescription?.trim() || undefined,
          sortOrder: optionalNumber(form.dictTypeSortOrder),
        },
      });
      rememberDictWrite(response.data);
      await loadDictTypes();
      return response;
    });
  }

  async function editDictType(item: DictTypeVO): Promise<void> {
    const name = window.prompt("类型名称", item.name) ?? item.name;
    const description = window.prompt("类型说明", item.description ?? "") ?? item.description;
    const sortOrder = window.prompt("排序", String(item.sortOrder ?? 0)) ?? String(item.sortOrder ?? 0);
    await execute("DICT-003", async () => {
      requireSystemContext();
      const response = await apiClient.call<DictTypeVO | { entity: DictTypeVO; cacheRefresh?: DictCacheRefreshVO }, {
        name: string;
        description?: string;
        sortOrder?: number;
        version: number;
      }>("DICT-003", {
        pathParams: {
          ...pathParams(),
          dictTypeId: item.dictTypeId,
        },
        body: {
          name: required(name, "类型名称"),
          description: description?.trim() || undefined,
          sortOrder: optionalNumber(sortOrder),
          version: item.version,
        },
      });
      rememberDictWrite(response.data);
      await loadDictTypes();
      return response;
    });
  }

  async function toggleDictType(item: DictTypeVO): Promise<void> {
    const nextStatus = item.status === "ENABLED" ? "DISABLED" : "ENABLED";
    if (nextStatus === "DISABLED") {
      await loadDictUsage(item.dictTypeId);
      if (systemState.dictUsage && !systemState.dictUsage.canDisable) {
        ui.lastError = systemState.dictUsage.blockingReasons.join("; ") || "DICT_TYPE_IN_USE";
        render();
        return;
      }
    }
    await execute("DICT-004", async () => {
      requireSystemContext();
      const response = await apiClient.call<DictTypeVO | { entity: DictTypeVO; cacheRefresh?: DictCacheRefreshVO }, {
        targetStatus: Exclude<DictStatus, "DELETED">;
        reason?: string;
        version?: number;
      }>("DICT-004", {
        pathParams: {
          ...pathParams(),
          dictTypeId: item.dictTypeId,
        },
        body: {
          targetStatus: nextStatus as Exclude<DictStatus, "DELETED">,
          reason: "系统管理页面操作",
          version: item.version,
        },
      });
      rememberDictWrite(response.data);
      await loadDictTypes();
      return response;
    });
  }

  async function selectDictTypeAndLoad(item: DictTypeVO): Promise<void> {
    systemState.selectedDictTypeId = item.dictTypeId;
    await loadDictItems(item.dictTypeId);
  }

  async function loadDictItems(dictTypeId = systemState.selectedDictTypeId): Promise<void> {
    if (!dictTypeId) {
      systemState.dictItems = [];
      return;
    }
    await execute("DICT-005", async () => {
      requireSystemContext();
      const response = await apiClient.call<DictItemVO[]>("DICT-005", {
        pathParams: {
          ...pathParams(),
          dictTypeId,
        },
        query: {
          treeMode: true,
        },
      });
      systemState.dictItems = response.data ?? [];
      return response;
    });
  }

  async function createDictItem(): Promise<void> {
    const dictTypeId = required(systemState.selectedDictTypeId, "字典类型");
    const form = readForm();
    await execute("DICT-006", async () => {
      requireSystemContext();
      const response = await apiClient.call<DictItemVO | { entity: DictItemVO; cacheRefresh?: DictCacheRefreshVO }, {
        parentId?: EntityId;
        code: string;
        label: string;
        value: string;
        description?: string;
        status?: ManageStatus;
        sortOrder?: number;
      }>("DICT-006", {
        pathParams: {
          ...pathParams(),
          dictTypeId,
        },
        body: {
          parentId: form.dictItemParentId?.trim() || undefined,
          code: required(form.dictItemCode, "字典项编码"),
          label: required(form.dictItemLabel, "字典项标签"),
          value: required(form.dictItemValue, "字典项值"),
          sortOrder: optionalNumber(form.dictItemSortOrder),
          status: "ENABLED",
        },
      });
      rememberDictWrite(response.data);
      await loadDictItems(dictTypeId);
      return response;
    });
  }

  async function editDictItem(item: DictItemVO): Promise<void> {
    const label = window.prompt("字典项标签", item.label) ?? item.label;
    const value = window.prompt("字典项值", item.value) ?? item.value;
    const description = window.prompt("说明", item.description ?? "") ?? item.description;
    const sortOrder = window.prompt("排序", String(item.sortOrder ?? 0)) ?? String(item.sortOrder ?? 0);
    await execute("DICT-007", async () => {
      requireSystemContext();
      const response = await apiClient.call<DictItemVO | { entity: DictItemVO; cacheRefresh?: DictCacheRefreshVO }, {
        label: string;
        value: string;
        description?: string;
        sortOrder?: number;
        version: number;
      }>("DICT-007", {
        pathParams: {
          ...pathParams(),
          dictItemId: item.dictItemId,
        },
        body: {
          label: required(label, "字典项标签"),
          value: required(value, "字典项值"),
          description: description?.trim() || undefined,
          sortOrder: optionalNumber(sortOrder),
          version: item.version,
        },
      });
      rememberDictWrite(response.data);
      await loadDictItems();
      return response;
    });
  }

  async function toggleDictItem(item: DictItemVO): Promise<void> {
    const nextStatus = item.status === "ENABLED" ? "DISABLED" : "ENABLED";
    await execute("DICT-008", async () => {
      requireSystemContext();
      const response = await apiClient.call<DictItemVO | { entity: DictItemVO; cacheRefresh?: DictCacheRefreshVO }, {
        targetStatus: Exclude<DictStatus, "DELETED">;
        reason?: string;
        version?: number;
      }>("DICT-008", {
        pathParams: {
          ...pathParams(),
          dictItemId: item.dictItemId,
        },
        body: {
          targetStatus: nextStatus as Exclude<DictStatus, "DELETED">,
          reason: "系统管理页面操作",
          version: item.version,
        },
      });
      rememberDictWrite(response.data);
      await loadDictItems();
      return response;
    });
  }

  async function loadDictUsage(dictTypeId: EntityId, dictItemId?: EntityId): Promise<void> {
    await execute("DICT-009", async () => {
      requireSystemContext();
      const response = await apiClient.call<DictUsageVO>("DICT-009", {
        pathParams: {
          ...pathParams(),
          dictTypeId,
        },
        query: dictItemId ? { dictItemId } : undefined,
      });
      systemState.dictUsage = response.data;
      ui.lastMessage = `字典使用情况：记录 ${response.data.recordUsageCount}，字段 ${response.data.fieldUsages.length}，可删除 ${response.data.canDelete ? "是" : "否"}`;
      return response;
    });
  }

  async function deleteDictType(item: DictTypeVO): Promise<void> {
    await loadDictUsage(item.dictTypeId);
    if (systemState.dictUsage && !systemState.dictUsage.canDelete) {
      ui.lastError = systemState.dictUsage.blockingReasons.join("; ") || "DICT_TYPE_IN_USE";
      render();
      return;
    }
    if (!window.confirm(`确认删除字典类型“${item.name}”？`)) {
      return;
    }
    await execute("DICT-010", async () => {
      requireSystemContext();
      const response = await apiClient.call<DictTypeVO | { entity: DictTypeVO; cacheRefresh?: DictCacheRefreshVO }, { version: number }>("DICT-010", {
        pathParams: {
          ...pathParams(),
          dictTypeId: item.dictTypeId,
        },
        body: {
          version: item.version,
        },
      });
      rememberDictWrite(response.data);
      systemState.selectedDictTypeId = undefined;
      systemState.dictItems = [];
      await loadDictTypes();
      return response;
    });
  }

  async function deleteDictItem(item: DictItemVO): Promise<void> {
    if (!window.confirm(`确认删除字典项“${item.label}”？`)) {
      return;
    }
    await execute("DICT-011", async () => {
      requireSystemContext();
      const response = await apiClient.call<DictItemVO | { entity: DictItemVO; cacheRefresh?: DictCacheRefreshVO }, { version: number }>("DICT-011", {
        pathParams: {
          ...pathParams(),
          dictItemId: item.dictItemId,
        },
        body: {
          version: item.version,
        },
      });
      rememberDictWrite(response.data);
      await loadDictItems();
      return response;
    });
  }

  async function enterSystem(systemId: string): Promise<void> {
    await execute("SYS-001", async () => {
      systemContextStore.beginEnter(systemId);
      permissionStore.beginRefresh();
      const response = await apiClient.call<SystemEnterVO>("SYS-001", {
        pathParams: { systemId },
      });
      const entered = response.data;
      systemState.profile = entered;
      systemState.tenants = entered.tenants ?? (entered.currentTenant ? [entered.currentTenant] : []);
      systemContextStore.setContext({
        system: {
          systemId: entered.systemId,
          systemCode: entered.systemCode,
          systemName: entered.systemName,
          tenantMode: entered.tenantMode,
          status: entered.status as "DRAFT" | "ENABLED" | "DISABLED" | "ARCHIVED",
        },
        tenant: entered.currentTenant
          ? {
              tenantId: entered.currentTenant.tenantId,
              tenantCode: entered.currentTenant.code,
              tenantName: entered.currentTenant.name,
              status: entered.currentTenant.status as "ENABLED" | "DISABLED",
            }
          : undefined,
        member: {
          memberId: entered.currentMember.memberId,
          displayName: entered.currentMember.displayName,
          roles: entered.currentMember.roles ?? entered.currentMember.roleIds ?? [],
          status: entered.currentMember.status,
        },
      });
      permissionStore.setEffectivePermission(toEffectivePermission(entered), entered.permissions?.menus ?? []);
      navigate(`/systems/${systemId}/profile`);
      return { requestId: response.requestId };
    });
  }

  async function execute<T>(scope: string, action: () => Promise<T>): Promise<void> {
    ui.busy = true;
    ui.lastError = undefined;
    ui.lastRequestId = `${scope}-${Date.now()}`;
    render();
    try {
      const result = await action();
      const response = result as { requestId?: string; code?: string; message?: string };
      ui.lastMessage = `${scope} 操作成功${response.code ? `：${response.code}` : ""}${response.requestId ? `，请求号 ${response.requestId}` : ""}`;
    } catch (error) {
      ui.lastError = error instanceof Error ? error.message : String(error);
    } finally {
      ui.busy = false;
      render();
    }
  }

  async function runRequestedSmoke(): Promise<void> {
    const params = new URLSearchParams(window.location.search);
    const smokeApi = params.get("smokeApi") as ApiEndpointId | null;
    if (!smokeApi || !API_ENDPOINTS[smokeApi]) {
      return;
    }
    const smokeLoginName = params.get("smokeLoginName");
    const smokePassword = params.get("smokePassword");
    if (smokeLoginName && smokePassword) {
      await execute("AUTH-002", () =>
        authPage.login({
          loginName: smokeLoginName,
          password: smokePassword,
        }),
      );
    }
    await execute(smokeApi, () =>
      apiClient.call(smokeApi, {
        pathParams: pathParams(),
        query: { pageNo: 1, pageSize: 10 },
        idempotencyKey: `ui-smoke-${Date.now()}`,
      }),
    );
  }

  function renderMessage(): HTMLElement {
    if (ui.busy) {
      return node("pre", { className: "message muted", text: `请求中: ${ui.lastRequestId}` });
    }
    if (ui.lastError) {
      return node("div", { className: "message error", text: ui.lastError });
    }
    return node("div", { className: "message", text: ui.lastMessage ?? "暂无操作结果。" });
  }

  function input(label: string, name: string, value: string, type = "text"): HTMLElement {
    return node("label", { className: "field" }, [
      node("span", { text: label }),
      node("input", { name, type, value }),
    ]);
  }

  function textarea(label: string, name: string, value: string): HTMLElement {
    return node("label", { className: "field wide-field" }, [
      node("span", { text: label }),
      node("textarea", { name, value, rows: 3 }),
    ]);
  }

  function selectField(label: string, name: string, value: string, options: [string, string][]): HTMLElement {
    return node("label", { className: "field" }, [
      node("span", { text: label }),
      node("select", { name, value }, options.map(([optionValue, optionLabel]) =>
        node("option", { value: optionValue, selected: optionValue === value, text: optionLabel }),
      )),
    ]);
  }

  function button(label: string, variant: "primary" | "secondary", onClick: () => void | Promise<void>, disabled = false): HTMLElement {
    return node("button", { className: `btn ${variant}`, disabled, onClick, text: label });
  }

  function stat(value: string, label: string): HTMLElement {
    return node("div", { className: "stat" }, [
      node("strong", { text: value }),
      node("span", { text: label }),
    ]);
  }

  function readForm(): Record<string, string> {
    const values: Record<string, string> = {};
    container.querySelectorAll<HTMLInputElement>("input[name]").forEach((item) => {
      values[item.name] = item.value;
    });
    container.querySelectorAll<HTMLSelectElement>("select[name]").forEach((item) => {
      values[item.name] = item.value;
    });
    container.querySelectorAll<HTMLTextAreaElement>("textarea[name]").forEach((item) => {
      values[item.name] = item.value;
    });
    return values;
  }

  function setFormValue(name: string, value: string): void {
    const field = container.querySelector<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>(`[name="${name}"]`);
    if (field) {
      field.value = value;
    }
  }

  function requireSystemContext(): void {
    const current = systemContextStore.getState().current;
    if (!current?.system.systemId || !current.member.memberId || current.member.memberId.startsWith("preview-")) {
      throw new Error("请先从“我的系统”进入真实系统，加载系统、租户、成员和权限上下文。");
    }
  }

  async function fetchMemberDetail(memberId: EntityId): Promise<MemberDetailVO> {
    requireSystemContext();
    const response = await apiClient.call<MemberDetailVO>("MEM-003", {
      pathParams: {
        ...pathParams(),
        memberId,
      },
    });
    systemState.memberDetails[memberId] = response.data;
    return response.data;
  }

  async function refreshSystemManagementOptions(): Promise<void> {
    await Promise.allSettled([
      loadSystemDepartments(),
      loadSystemRoles(),
    ]);
  }

  async function refreshEffectivePermissions(): Promise<void> {
    const [effective, menus] = await Promise.all([
      apiClient.call<EffectivePermissionVO>("RBAC-010", { pathParams: pathParams() }),
      apiClient.call<Array<{ code?: string; menuCode?: string; children?: Array<{ code?: string; menuCode?: string }> }>>("RBAC-011", { pathParams: pathParams() }),
    ]);
    permissionStore.setEffectivePermission(effective.data, flattenMenuCodes(menus.data));
  }

  function normalizePage<TRecord>(value: TRecord[] | PageResult<TRecord>): PageResult<TRecord> {
    if (Array.isArray(value)) {
      return {
        records: value,
        total: value.length,
        pageNo: 1,
        pageSize: value.length,
        hasNext: false,
      };
    }
    return value;
  }

  function flattenDepartments(nodes: DepartmentNodeVO[], depth = 0): Array<DepartmentNodeVO & { depth: number }> {
    return nodes.flatMap((item) => [
      { ...item, depth },
      ...flattenDepartments(item.children ?? [], depth + 1),
    ]);
  }

  function flattenDictItems(nodes: DictItemVO[], depth = 0): Array<DictItemVO & { depth: number }> {
    return nodes.flatMap((item) => [
      { ...item, depth },
      ...flattenDictItems(item.children ?? [], depth + 1),
    ]);
  }

  function departmentDeleteReason(item: DepartmentNodeVO): string | undefined {
    if ((item.children?.length ?? 0) > 0) {
      return "存在子部门，不能删除";
    }
    if ((item.memberCount ?? 0) > 0) {
      return "存在关联成员，不能删除";
    }
    return undefined;
  }

  function selectedDictType(): DictTypeVO | undefined {
    return systemState.dictTypes.find((item) => item.dictTypeId === systemState.selectedDictTypeId);
  }

  function permissionCatalogLabel(catalog?: PermissionCatalogVO): string {
    if (!catalog) {
      return "未加载";
    }
    return `${catalog.menus.length} 菜单 / ${catalog.operations.length} 操作`;
  }

  function memberDeptLabel(item: MemberListVO): string {
    const candidate = item as MemberListVO & { deptIds?: EntityId[] };
    return item.deptPath ?? candidate.deptIds?.join(", ") ?? "-";
  }

  function memberRoleIds(item: MemberListVO | MemberDetailVO): EntityId[] {
    const candidate = item as MemberListVO & { roleIds?: EntityId[] };
    return candidate.roleIds ?? [];
  }

  function rememberDictWrite(value: unknown): void {
    if (isRecord(value) && isRecord(value.cacheRefresh)) {
      systemState.dictCache = value.cacheRefresh as unknown as DictCacheRefreshVO;
    }
  }

  function isRecord(value: unknown): value is Record<string, unknown> {
    return typeof value === "object" && value !== null && !Array.isArray(value);
  }

  function optionalNumber(value: string | undefined): number | undefined {
    const text = value?.trim();
    if (!text) {
      return undefined;
    }
    const parsed = Number(text);
    if (Number.isNaN(parsed)) {
      throw new Error(`数字格式不正确: ${value}`);
    }
    return parsed;
  }

  function normalizeScopeType(value: string): "SELF" | "DEPT" | "DEPT_TREE" | "ALL" | "CUSTOM" {
    const upper = value.trim().toUpperCase();
    if (upper === "SELF" || upper === "DEPT" || upper === "DEPT_TREE" || upper === "ALL" || upper === "CUSTOM") {
      return upper;
    }
    return "ALL";
  }

  function flattenMenuCodes(nodes: Array<{ code?: string; menuCode?: string; children?: Array<{ code?: string; menuCode?: string }> }>): string[] {
    return nodes.flatMap((item) => {
      const current = item.menuCode ?? item.code;
      const children = flattenMenuCodes(item.children ?? []);
      return current ? [current, ...children] : children;
    });
  }

  function pathParams(): Record<string, string> {
    const current = systemContextStore.getState().current;
    return {
      systemId: current?.system.systemId ?? ui.settings.systemId,
      tenantId: current?.tenant?.tenantId ?? ui.settings.tenantId,
      appId: ui.settings.appId,
      moduleId: ui.settings.moduleId,
      accountId: "preview-account",
      roleId: "preview-role",
      memberId: current?.member?.memberId ?? "preview-member",
      deptId: "preview-dept",
      dictId: "preview-dict",
      fieldId: "preview-field",
      pageId: "preview-page",
      recordId: "preview-record",
      flowTemplateId: "preview-flow",
      flowInstanceId: "preview-instance",
      taskId: "preview-task",
      fileId: "preview-file",
      exportJobId: "preview-export",
      clientId: "preview-client",
      configKey: "preview-config",
    };
  }

  function firstRunnableEndpoint(apiIds: ApiEndpointId[]): ApiEndpointId | undefined {
    return apiIds.find((apiId) => API_ENDPOINTS[apiId].method === "GET");
  }

  function autoLoadRoute(route: AppRouteRecord): void {
    const autoLoadNames = new Set([
      "platform.mySystems",
      "platform.systems",
      "platform.accounts",
      "platform.roles",
      "platform.configs",
      "system.profile",
      "system.tenants",
      "system.members",
      "system.departments",
      "system.roles",
      "system.dict",
    ]);
    if (!autoLoadNames.has(route.name) || autoLoadedRoutes.has(route.name) || ui.busy) {
      return;
    }
    autoLoadedRoutes.add(route.name);
    void runRouteLoad(route);
  }

  function raisePageError(page: { error?: { message: string } }): void {
    if (page.error) {
      throw new Error(page.error.message);
    }
  }

  function required(value: string | undefined, label: string): string {
    const text = value?.trim();
    if (!text) {
      throw new Error(`${label}不能为空`);
    }
    return text;
  }

  function splitValues(value: string): string[] {
    return value
      .split(",")
      .map((item) => item.trim())
      .filter(Boolean);
  }

  function enabledCount(records: Array<{ status?: string }>): string {
    return String(records.filter((item) => item.status === "ENABLED" || item.status === "NORMAL").length);
  }

  function statusLabel(status?: string): string {
    const mapping: Record<string, string> = {
      DRAFT: "草稿",
      ENABLED: "启用",
      DISABLED: "禁用",
      ARCHIVED: "归档",
      NORMAL: "正常",
      LOCKED: "锁定",
    };
    return status ? mapping[status] ?? status : "-";
  }

  function tenantModeLabel(mode?: string): string {
    if (mode === "SINGLE") {
      return "单租户";
    }
    if (mode === "MULTI") {
      return "多租户";
    }
    return mode ?? "-";
  }

  function tenantName(item?: Partial<SystemTenantVO> & { tenantName?: string; tenantNameCn?: string }): string {
    return item?.name ?? item?.tenantName ?? item?.tenantNameCn ?? "-";
  }

  function tenantCode(item?: Partial<SystemTenantVO> & { tenantCode?: string }): string {
    return item?.code ?? item?.tenantCode ?? "-";
  }

  function statusClass(): string {
    return authStore.getState().status === "authenticated" ? "status-pill ok" : "status-pill";
  }

  function authStatusLabel(): string {
    const status = authStore.getState().status;
    if (status === "authenticated") {
      return "已登录";
    }
    if (status === "authenticating") {
      return "登录中";
    }
    if (status === "expired") {
      return "已过期";
    }
    return "未登录";
  }

  function sectionLabel(route: AppRouteRecord): string {
    return SHELL_SECTION_LABELS[route.meta.section];
  }

  function syncHash(): void {
    if (!window.location.hash) {
      navigate(authStore.getState().status === "authenticated" ? DEFAULT_AUTHENTICATED_ROUTE : LOGIN_ROUTE);
    }
  }

  function navigate(path: string): void {
    window.location.hash = path;
  }

  function getCurrentPath(): string {
    return window.location.hash.replace(/^#/, "") || LOGIN_ROUTE;
  }

  render();
  void runRequestedSmoke();
}

type Child = HTMLElement | Text | string | undefined | false;

function node<K extends keyof HTMLElementTagNameMap>(
  tag: K,
  options: Record<string, unknown> = {},
  children: Child[] = [],
): HTMLElementTagNameMap[K] {
  const element = document.createElement(tag);
  Object.entries(options).forEach(([key, value]) => {
    if (value === undefined || value === false) {
      return;
    }
    if (key === "className") {
      element.className = String(value);
      return;
    }
    if (key === "text") {
      element.textContent = String(value);
      return;
    }
    if (key === "onClick") {
      element.addEventListener("click", (event) => {
        event.preventDefault();
        void (value as () => void | Promise<void>)();
      });
      return;
    }
    if (key in element) {
      (element as unknown as Record<string, unknown>)[key] = value;
      return;
    }
    element.setAttribute(key, String(value));
  });
  children.forEach((child) => {
    if (child === undefined || child === false) {
      return;
    }
    element.append(child instanceof Node ? child : document.createTextNode(String(child)));
  });
  return element;
}

function loadSettings(): RuntimeSettings {
  const query = new URLSearchParams(window.location.search);
  const base = defaultSettings();
  return {
    ...base,
    baseUrl: query.get("baseUrl") ?? base.baseUrl,
    accessToken: query.get("accessToken") ?? base.accessToken,
    systemId: query.get("systemId") ?? base.systemId,
    tenantId: query.get("tenantId") ?? base.tenantId,
    appId: query.get("appId") ?? base.appId,
    moduleId: query.get("moduleId") ?? base.moduleId,
  };
}

function defaultSettings(): RuntimeSettings {
  return {
    baseUrl: runtimeApiBaseUrl(),
    accessToken: "",
    systemId: "preview-system",
    tenantId: "preview-tenant",
    appId: "preview-app",
    moduleId: "preview-module",
  };
}

function runtimeApiBaseUrl(): string {
  const meta = import.meta as unknown as { env?: Record<string, string | undefined> };
  return meta.env?.VITE_API_BASE_URL ?? "";
}

function emptyPlatformPageState<TRecord>(): PlatformPageState<TRecord> {
  return {
    loading: false,
    records: [],
    total: 0,
    pageNo: 1,
    pageSize: 20,
    empty: true,
  };
}

function toEffectivePermission(entered: SystemEnterVO): EffectivePermissionVO {
  const permissions = entered.permissions;
  const operations = permissions?.operations ?? [];
  return {
    memberId: permissions?.memberId ?? entered.currentMember.memberId,
    roles: permissions?.roles ?? entered.currentMember.roles ?? entered.currentMember.roleIds ?? [],
    version: Number(permissions?.version ?? 1),
    menus: permissions?.menus ?? [],
    operations,
    fieldPermissions: normalizeFieldPermissions(permissions?.fieldPermissions),
    availableActions: permissions?.availableActions ?? operations.map((operationCode) => ({
      actionCode: operationCode,
      label: operationCode,
      visible: true,
      enabled: true,
      requiredPermission: operationCode,
    })),
    dataScopes: permissions?.dataScopes ?? [],
  };
}

function normalizeFieldPermissions(value: SystemEnterVO["permissions"] extends infer T
  ? T extends { fieldPermissions?: infer P } ? P : never
  : never): FieldPermission[] {
  if (Array.isArray(value)) {
    return value;
  }
  return [];
}

function seedPreviewState(settings: RuntimeSettings): void {
  authStore.setAuthenticated(
    {
      accountId: "preview-account",
      loginName: "admin",
      displayName: "平台管理员",
      accountStatus: "NORMAL",
      platformRoles: ["SUPER_ADMIN"],
      platformPermissions: rootPermissions,
    },
    {
      accessToken: settings.accessToken,
    },
  );
  systemContextStore.setContext({
    system: {
      systemId: settings.systemId,
      systemName: "示例自定义系统",
      tenantMode: settings.tenantId ? "MULTI" : "SINGLE",
      status: "ENABLED",
    },
    tenant: settings.tenantId
      ? {
          tenantId: settings.tenantId,
          tenantName: "默认租户",
          status: "ENABLED",
        }
      : undefined,
    member: {
      memberId: "preview-member",
      displayName: "系统管理员",
      roles: ["系统管理员"],
      status: "ENABLED",
    },
  });
  permissionStore.setEffectivePermission(
    {
      memberId: "preview-member",
      roles: ["系统管理员"],
      version: 1,
      menus: [],
      operations: rootPermissions,
      fieldPermissions: [],
      availableActions: rootPermissions.map((item) => ({
        actionCode: item,
        label: item,
        visible: true,
        enabled: true,
        requiredPermission: item,
      })),
      dataScopes: [],
    },
    ["runtime.home", "runtime.module"],
  );
}
