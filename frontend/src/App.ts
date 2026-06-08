import { createApiClient, type ApiClient } from "./api/client";
import { API_ENDPOINTS, type ApiEndpointId } from "./api/endpoints";
import { createFetchTransport } from "./api/fetchTransport";
import type { EffectivePermissionVO, EntityId, FieldPermission, JsonValue } from "./api/types";
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

  async function enterSystem(systemId: string): Promise<void> {
    await execute("SYS-001", async () => {
      systemContextStore.beginEnter(systemId);
      permissionStore.beginRefresh();
      const response = await apiClient.call<SystemEnterVO>("SYS-001", {
        pathParams: { systemId },
      });
      const entered = response.data;
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

  function pathParams(): Record<string, string> {
    return {
      systemId: ui.settings.systemId,
      tenantId: ui.settings.tenantId,
      appId: ui.settings.appId,
      moduleId: ui.settings.moduleId,
      accountId: "preview-account",
      roleId: "preview-role",
      memberId: "preview-member",
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
