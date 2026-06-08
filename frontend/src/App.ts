import { createApiClient, type ApiClient } from "./api/client";
import { API_ENDPOINTS, type ApiEndpointId } from "./api/endpoints";
import { createFetchTransport } from "./api/fetchTransport";
import { resolveAppShellState } from "./layouts/AppShell";
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
  lastResult?: string;
  lastError?: string;
}

const STORAGE_KEY = "unexamine.ui.settings";
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

  seedPreviewState(ui.settings);
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
    const route = resolveRoute({ path }) ?? APP_ROUTES[0];
    const shell = resolveAppShellState({
      currentPath: path,
      auth: authStore,
      systemContext: systemContextStore,
      permission: permissionStore,
    });

    container.replaceChildren(
      node("div", { className: "app-frame" }, [
        renderSidebar(path, shell.navigation),
        node("main", { className: "workspace" }, [
          renderTopbar(shell.currentTitle, route),
          route.meta.section === "auth" ? renderAuth(route) : renderWorkspace(route),
        ]),
        renderInspector(),
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
        node("p", { className: "eyebrow", text: route.meta.section }),
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
    return node("section", { className: "auth-surface" }, [
      node("div", { className: "auth-panel" }, [
        node("h2", { text: isRegister ? "注册平台账号" : "登录平台" }),
        input("账号", "loginName", "admin"),
        input("密码", "password", "admin123", "password"),
        isRegister ? input("显示名称", "displayName", "平台管理员") : undefined,
        node("div", { className: "button-row" }, [
          button(isRegister ? "注册并进入" : "登录", "primary", () => submitAuth(isRegister)),
          button("本地预览", "secondary", () => {
            seedPreviewState(ui.settings);
            navigate(DEFAULT_AUTHENTICATED_ROUTE);
          }),
        ]),
        renderMessage(),
      ]),
      node("div", { className: "auth-media" }, [
        node("div", { className: "media-board" }, [
          stat("174", "冻结 API"),
          stat("9", "业务工作区"),
          stat("P7", "前端部署期"),
        ]),
      ]),
    ]);
  }

  function renderWorkspace(route: AppRouteRecord): HTMLElement {
    const endpointIds = route.meta.apiIds;
    const runnable = firstRunnableEndpoint(endpointIds);
    return node("section", { className: "content-grid" }, [
      node("div", { className: "work-panel primary-panel" }, [
        node("div", { className: "panel-heading" }, [
          node("div", {}, [
            node("p", { className: "eyebrow", text: route.meta.placeholderOwner }),
            node("h2", { text: route.meta.title }),
          ]),
          button("刷新", "secondary", () => runRouteLoad(route)),
        ]),
        renderRouteBody(route, runnable),
      ]),
      node("div", { className: "work-panel" }, [
        node("div", { className: "panel-heading" }, [
          node("h2", { text: "接口证据" }),
          node("span", { className: "count-pill", text: `${endpointIds.length}` }),
        ]),
        node("div", { className: "api-list" }, endpointIds.slice(0, 12).map(renderEndpoint)),
      ]),
      node("div", { className: "work-panel" }, [
        node("div", { className: "panel-heading" }, [
          node("h2", { text: "运行状态" }),
          node("span", { className: statusClass(), text: authStore.getState().status }),
        ]),
        renderMessage(),
      ]),
    ]);
  }

  function renderRouteBody(route: AppRouteRecord, runnable?: ApiEndpointId): HTMLElement {
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
      node("div", { className: "metric-grid" }, [
        stat(route.meta.section, "业务域"),
        stat(runnable ?? "无 GET", "可试跑接口"),
        stat(route.meta.layout, "布局"),
      ]),
      node("div", { className: "action-band" }, [
        button(runnable ? `调用 ${runnable}` : "无可调用 GET", "primary", () => runRouteLoad(route), !runnable),
        button("进入示例系统", "secondary", () => seedPreviewState(ui.settings)),
      ]),
      node("div", { className: "timeline" }, [
        node("span", { text: "契约" }),
        node("span", { text: "页面" }),
        node("span", { text: "权限" }),
        node("span", { text: "接口" }),
      ]),
    ]);
  }

  function renderInspector(): HTMLElement {
    return node("aside", { className: "inspector" }, [
      node("h2", { text: "连接" }),
      input("API 地址", "baseUrl", ui.settings.baseUrl),
      input("Token", "accessToken", ui.settings.accessToken),
      input("System ID", "systemId", ui.settings.systemId),
      input("Tenant ID", "tenantId", ui.settings.tenantId),
      input("App ID", "appId", ui.settings.appId),
      input("Module ID", "moduleId", ui.settings.moduleId),
      node("div", { className: "button-row vertical" }, [
        button("保存", "primary", saveSettings),
        button("重置", "secondary", resetSettings),
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
      ui.lastResult = JSON.stringify(result, null, 2).slice(0, 1800);
    } catch (error) {
      ui.lastError = error instanceof Error ? error.message : String(error);
    } finally {
      ui.busy = false;
      render();
    }
  }

  function saveSettings(): void {
    const values = readForm();
    ui.settings = {
      baseUrl: values.baseUrl,
      accessToken: values.accessToken,
      systemId: values.systemId,
      tenantId: values.tenantId,
      appId: values.appId,
      moduleId: values.moduleId,
    };
    localStorage.setItem(STORAGE_KEY, JSON.stringify(ui.settings));
    apiClient = createClient(ui.settings);
    seedPreviewState(ui.settings);
    ui.lastResult = "连接参数已保存。";
    render();
  }

  function resetSettings(): void {
    localStorage.removeItem(STORAGE_KEY);
    ui.settings = defaultSettings();
    apiClient = createClient(ui.settings);
    seedPreviewState(ui.settings);
    render();
  }

  function renderMessage(): HTMLElement {
    if (ui.busy) {
      return node("pre", { className: "message muted", text: `请求中: ${ui.lastRequestId}` });
    }
    if (ui.lastError) {
      return node("pre", { className: "message error", text: ui.lastError });
    }
    return node("pre", { className: "message", text: ui.lastResult ?? "等待操作。" });
  }

  function renderEndpoint(apiId: ApiEndpointId): HTMLElement {
    const endpoint = API_ENDPOINTS[apiId];
    return node("div", { className: "api-item" }, [
      node("span", { className: "method", text: endpoint.method }),
      node("span", { text: endpoint.id }),
      node("code", { text: endpoint.path }),
    ]);
  }

  function input(label: string, name: keyof RuntimeSettings | "loginName" | "password" | "displayName", value: string, type = "text"): HTMLElement {
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
  const stored = localStorage.getItem(STORAGE_KEY);
  if (!stored) {
    return defaultSettings();
  }
  try {
    return { ...defaultSettings(), ...JSON.parse(stored) };
  } catch {
    return defaultSettings();
  }
}

function defaultSettings(): RuntimeSettings {
  return {
    baseUrl: "http://localhost:8080",
    accessToken: "preview-token",
    systemId: "preview-system",
    tenantId: "preview-tenant",
    appId: "preview-app",
    moduleId: "preview-module",
  };
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
