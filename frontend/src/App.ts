import { createApiClient, type ApiClient } from "./api/client";
import { API_ENDPOINTS, type ApiEndpointId } from "./api/endpoints";
import { createFetchTransport } from "./api/fetchTransport";
import { resolveAppShellState, SHELL_SECTION_LABELS } from "./layouts/AppShell";
import { createAuthPageModel } from "./pages/auth";
import { createMySystemsPageModel } from "./pages/my-systems";
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

const rootPermissions = [
  "PLAT_SYSTEM_VIEW",
  "PLAT_SYSTEM_CREATE",
  "PLAT_ACCOUNT_VIEW",
  "PLAT_ROLE_VIEW",
  "PLAT_CONFIG_VIEW",
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
  let apiClient = createClient(ui.settings);
  const authPage = createAuthPageModel({ apiClient });
  const mySystemsPage = createMySystemsPageModel({ apiClient });

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
            node("button", { className: "table-row", onClick: () => enterSystem(item.systemId, item.tenantId) }, [
              node("span", { text: item.systemName }),
              node("span", { text: item.tenantMode }),
              node("span", { text: item.status }),
            ]),
          ),
        ]),
      ]);
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

  async function enterSystem(systemId: string, tenantId?: string): Promise<void> {
    await execute("PLAT-004", async () => {
      systemContextStore.setContext({
        system: { systemId, systemName: `系统 ${systemId}`, tenantMode: tenantId ? "MULTI" : "SINGLE", status: "ENABLED" },
        tenant: tenantId ? { tenantId, tenantName: `租户 ${tenantId}`, status: "ENABLED" } : undefined,
        member: { memberId: "current", displayName: "当前成员", roles: ["系统管理员"], status: "ENABLED" },
      });
      navigate(`/systems/${systemId}/runtime`);
      return { systemId, tenantId };
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
    return values;
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
