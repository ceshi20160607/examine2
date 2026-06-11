import { createApiClient, type ApiClient } from "./api/client";
import { API_ENDPOINTS, type ApiEndpointId } from "./api/endpoints";
import { createFetchTransport } from "./api/fetchTransport";
import type {
  DynamicFieldType,
  EffectivePermissionVO,
  EntityId,
  FieldPermission,
  JsonValue,
  PageResult,
  RecordDetailVO,
  RecordListItemVO,
  RuntimeModuleSchemaVO,
} from "./api/types";
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
import { APP_ROUTES, DEFAULT_AUTHENTICATED_ROUTE, LOGIN_ROUTE, buildPath, resolveRoute, type AppRouteRecord } from "./router";
import { authStore, errorStore, permissionStore, systemContextStore } from "./stores";
import {
  createModuleConfigPageModel,
  type AppListItemVO,
  type FieldTypeVO,
  type ModuleFieldVO,
  type ModuleListItemVO,
  type ModulePublishResultVO,
  type PageSchemaVO,
  type PublishCheckResultVO,
} from "./pages/module-config";
import {
  createRuntimeWorkbenchPageModel,
  type RuntimeMenuVO,
  type RuntimeRecordHistoryVO,
  type RuntimeRecordRelationVO,
} from "./pages/runtime";
import {
  createFlowWorkbenchPageModel,
  type FlowCcItemVO,
  type FlowInstanceListItemVO,
  type FlowTaskListItemVO,
  type FlowTemplateVO,
} from "./pages/flow";
import {
  createFileCenterPageModel,
  type FileDetailVO,
  type FileListItemVO,
} from "./pages/files";
import {
  createExportPageModel,
  type ExportJobListItemVO,
  type ExportTemplateVO,
} from "./pages/export";
import {
  createOpenApiPageModel,
  createSecretDisplayFromCreatedClient,
  type OpenApiClientListItemVO,
  type OpenApiPageState,
} from "./pages/openapi";
import {
  createAuditPageModel,
  type AuditLogDetailPageVO,
  type AuditLogKind,
  type AuditLogListItemVO,
  type AuditPageState,
} from "./pages/audit";
import {
  createOpsPageModel,
  type OpsPageState,
} from "./pages/ops";

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
  editingAccountId?: EntityId;
  selectedAccountRoleIds: EntityId[];
  editingRoleId?: EntityId;
  authorizingRoleId?: EntityId;
  platformRoleOperationCodes: string;
  platformRoleMenuIds: string;
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
  rolePermissionOperationCodes: string;
  rolePermissionMenuIds: string;
  rolePermissionScopeType: "SELF" | "DEPT" | "DEPT_TREE" | "ALL" | "CUSTOM";
  dictTypes: DictTypeVO[];
  selectedDictTypeId?: EntityId;
  dictItems: DictItemVO[];
  dictUsage?: DictUsageVO;
  dictCache?: DictCacheRefreshVO;
}

interface AppRuntimeUiState {
  loadedSystemId?: EntityId;
  apps: AppListItemVO[];
  selectedAppId?: EntityId;
  modules: ModuleListItemVO[];
  selectedModuleId?: EntityId;
  fields: ModuleFieldVO[];
  fieldTypes: FieldTypeVO[];
  listSchema?: PageSchemaVO;
  formSchema?: PageSchemaVO;
  detailSchema?: PageSchemaVO;
  publishCheck?: PublishCheckResultVO;
  publishResult?: ModulePublishResultVO;
  runtimeMenus: RuntimeMenuVO[];
  runtimeSchema?: RuntimeModuleSchemaVO;
  recordPage?: PageResult<RecordListItemVO>;
  selectedRecord?: RecordDetailVO;
  recordHistory: RuntimeRecordHistoryVO[];
  recordRelations: RuntimeRecordRelationVO[];
}

interface OperationsUiState {
  flowTemplates?: PageResult<FlowTemplateVO>;
  flowTodos?: PageResult<FlowTaskListItemVO>;
  flowCc?: PageResult<FlowCcItemVO>;
  flowStarted?: PageResult<FlowInstanceListItemVO>;
  flowInstances?: PageResult<FlowInstanceListItemVO>;
  files?: PageResult<FileListItemVO>;
  selectedFile?: FileDetailVO;
  exportTemplates: ExportTemplateVO[];
  exportJobs?: PageResult<ExportJobListItemVO>;
  openApi: OpenApiPageState;
  audit: AuditPageState;
  platformAudit: AuditPageState;
  ops: OpsPageState;
}

type RuntimeDetailTab = "detail" | "attachments" | "flow" | "history" | "relations";
type FlowWorkbenchTab = "todo" | "cc" | "started" | "instances";
type ExportTab = "templates" | "jobs";

interface InteractionUiState {
  runtimeDetailTab: RuntimeDetailTab;
  flowWorkbenchTab: FlowWorkbenchTab;
  exportTab: ExportTab;
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
  const moduleConfigPage = createModuleConfigPageModel({
    apiClient,
    auth: authStore,
    systemContext: systemContextStore,
    permission: permissionStore,
    error: errorStore,
  });
  const runtimeWorkbench = createRuntimeWorkbenchPageModel({
    apiClient,
    auth: authStore,
    systemContext: systemContextStore,
    permission: permissionStore,
    error: errorStore,
  });
  const flowWorkbench = createFlowWorkbenchPageModel({
    apiClient,
    auth: authStore,
    systemContext: systemContextStore,
    permission: permissionStore,
    error: errorStore,
  });
  const fileCenter = createFileCenterPageModel({
    apiClient,
    auth: authStore,
    systemContext: systemContextStore,
    permission: permissionStore,
    error: errorStore,
  });
  const exportPage = createExportPageModel({
    apiClient,
    auth: authStore,
    systemContext: systemContextStore,
    permission: permissionStore,
    error: errorStore,
  });
  const openApiPage = createOpenApiPageModel({
    apiClient,
    auth: authStore,
    systemContext: systemContextStore,
    permission: permissionStore,
  });
  const auditPage = createAuditPageModel({
    apiClient,
    auth: authStore,
    systemContext: systemContextStore,
    permission: permissionStore,
  });
  const opsPage = createOpsPageModel({
    apiClient,
    auth: authStore,
  });
  const platformState: PlatformUiState = {
    systems: emptyPlatformPageState(),
    accounts: emptyPlatformPageState(),
    roles: emptyPlatformPageState(),
    configs: emptyPlatformPageState(),
    selectedAccountRoleIds: [],
    platformRoleOperationCodes: "",
    platformRoleMenuIds: "",
  };
  const systemState: SystemUiState = {
    tenants: [],
    members: [],
    memberDetails: {},
    departments: [],
    roles: [],
    rolePermissions: {},
    rolePermissionOperationCodes: "",
    rolePermissionMenuIds: "",
    rolePermissionScopeType: "ALL",
    dictTypes: [],
    dictItems: [],
  };
  const appRuntimeState: AppRuntimeUiState = {
    loadedSystemId: undefined,
    apps: [],
    modules: [],
    fields: [],
    fieldTypes: [],
    runtimeMenus: [],
    recordHistory: [],
    recordRelations: [],
  };
  const operationsState: OperationsUiState = {
    exportTemplates: [],
    openApi: openApiPage.createInitialState(),
    audit: auditPage.createInitialState("operation"),
    platformAudit: auditPage.createInitialState("platformOperation"),
    ops: opsPage.createInitialState(),
  };
  const interactionState: InteractionUiState = {
    runtimeDetailTab: "detail",
    flowWorkbenchTab: "todo",
    exportTab: "templates",
  };
  const autoLoadedRoutes = new Set<string>();

  syncHash();
  window.addEventListener("hashchange", render);
  authStore.subscribe(render);
  systemContextStore.subscribe(render);
  permissionStore.subscribe(render);
  errorStore.subscribe(render);

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
        renderSidebar(path, shell.navigation, route),
        node("main", { className: "workspace" }, [
          renderTopbar(shell.currentTitle, route),
          renderWorkspace(route),
        ]),
      ]),
    );
  }

  function renderSidebar(path: string, groups: ReturnType<typeof resolveAppShellState>["navigation"], route: AppRouteRecord): HTMLElement {
    const context = systemContextStore.getState().current;
    const visibleGroups = groups
      .map((group) => ({
        ...group,
        items: group.items.filter((item) => shouldShowNavItem(item, route.meta.layout, Boolean(context))),
      }))
      .filter((group) => group.items.length > 0);
    const shellMode = route.meta.layout === "system" && context ? "系统工作空间" : "平台工作空间";
    return node("aside", { className: `sidebar ${route.meta.layout === "system" ? "system-sidebar" : "platform-sidebar"}` }, [
      node("a", { className: "brand", href: "#/platform/my-systems" }, [
        node("span", { className: "brand-mark", text: "U" }),
        node("span", { className: "brand-name", text: "unexamine" }),
      ]),
      node("div", { className: "workspace-badge", text: shellMode }),
      context
        ? node("div", { className: "system-switcher" }, [
            node("span", { text: "当前系统" }),
            node("strong", { text: context.system.systemName }),
            node("small", { text: `${context.tenant?.tenantName ?? "默认租户"} / ${context.member.displayName}` }),
            node("a", { href: "#/platform/my-systems", text: "切换系统" }),
          ])
        : node("div", { className: "system-switcher" }, [
            node("span", { text: "平台工作台" }),
            node("strong", { text: "选择系统或管理平台能力" }),
          ]),
      node("nav", { className: "nav-groups" }, visibleGroups.map((group) =>
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

  function shouldShowNavItem(item: { key: string; section: string; disabled?: boolean }, layout: string, hasSystemContext: boolean): boolean {
    const contextOnlyRoutes = new Set(["modules.list", "modules.fields", "modules.ui", "runtime.module"]);
    if (contextOnlyRoutes.has(item.key)) {
      return false;
    }
    if (item.disabled) {
      return false;
    }
    if (layout !== "system" && item.key === "audit.system") {
      return false;
    }
    if (layout === "system" && (item.key === "audit.platform" || item.key === "ops.health")) {
      return false;
    }
    if (layout === "system") {
      return hasSystemContext && item.section !== "platform";
    }
    return item.section === "platform" || item.section === "audit-ops";
  }

  function renderTopbar(title: string, route: AppRouteRecord): HTMLElement {
    const context = systemContextStore.getState().current;
    return node("header", { className: "topbar" }, [
      node("div", {}, [
        node("p", { className: "eyebrow", text: sectionLabel(route) }),
        node("h1", { text: title }),
      ]),
      node("div", { className: "context-strip" }, [
        node("span", { text: route.meta.layout === "system" ? context?.system.systemName ?? "未进入系统" : "平台层" }),
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
          node("p", { className: "form-note", text: "登录后可以创建业务系统、配置业务模块、让普通用户处理数据，并通过对外应用安全开放数据和流程能力。" }),
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
            stat("建系统", "配置业务空间"),
            stat("用业务", "处理真实数据"),
            stat("对外开放", "授权外部接入"),
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
      node("div", { className: "work-panel guidance-panel" }, [
        node("div", { className: "panel-heading" }, [
          node("h2", { text: "下一步" }),
          node("span", { className: "count-pill", text: "流程提示" }),
        ]),
        renderUserGuidance(route),
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
        node("section", { className: "task-card primary-task" }, [
          node("div", { className: "task-card-title" }, [
            node("strong", { text: "创建一个业务系统" }),
            node("span", { text: "如果还没有系统，从这里填写名称和编码，创建后进入系统总览继续配置成员、建模和业务运行台。" }),
          ]),
          node("div", { className: "form-grid" }, [
            input("系统名称", "mySystemName", ""),
            input("系统编码", "mySystemCode", ""),
            selectField("租户模式", "mySystemTenantMode", "SINGLE", [
              ["SINGLE", "单租户"],
              ["MULTI", "多租户"],
            ]),
            textarea("系统说明", "mySystemDescription", ""),
            button("创建并进入", "primary", createMySystemAndEnter, !mySystemsPage.state.createEnabled),
          ]),
        ]),
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
            node("span", { text: "操作" }),
          ]),
          ...mySystemsPage.state.systems.map((item) =>
            node("div", { className: "table-row my-system-row" }, [
              node("span", { text: item.systemName }),
              node("span", { text: item.tenantMode }),
              node("span", { text: item.status }),
              node("span", { className: "row-actions" }, [
                button("进入系统", "primary", () => enterSystem(item.systemId)),
              ]),
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

    if (route.name === "system.overview") {
      return renderSystemOverview();
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

    if (route.name === "apps.list") {
      return renderAppList();
    }

    if (route.name === "modules.list") {
      return renderModuleList();
    }

    if (route.name === "modules.fields") {
      return renderModuleFields();
    }

    if (route.name === "modules.ui") {
      return renderModuleUi();
    }

    if (route.name === "runtime.home") {
      return renderRuntimeHome();
    }

    if (route.name === "runtime.module") {
      return renderRuntimeModule();
    }

    if (route.name === "flow.templates") {
      return renderFlowTemplates();
    }

    if (route.name === "flow.workbench") {
      return renderFlowWorkbench();
    }

    if (route.name === "files.center") {
      return renderFileCenter();
    }

    if (route.name === "exports.jobs") {
      return renderExportJobs();
    }

    if (route.name === "openapi.clients") {
      return renderOpenApiClients();
    }

    if (route.name === "audit.system") {
      return renderAuditLogs(false);
    }

    if (route.name === "audit.platform") {
      return renderAuditLogs(true);
    }

    if (route.name === "ops.health") {
      return renderOpsHealth();
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

  function renderSystemOverview(): HTMLElement {
    const context = systemContextStore.getState().current;
    const permissionCount = permissionStore.getState().effective?.operations.length ?? systemState.profile?.permissions?.operations?.length ?? 0;
    const publishedModules = appRuntimeState.modules.filter((item) => item.status === "PUBLISHED").length;
    const canConfigureSystem = permissionStore.decide({ anyOperations: ["SYS_MEMBER_VIEW", "SYS_ROLE_VIEW", "APP_VIEW", "MODULE_VIEW"] }).enabled;
    const readinessItems: [string, boolean, string, string][] = [
      ["成员", systemState.members.length > 0, "至少添加一个系统成员", systemPath("system.members")],
      ["角色", systemState.roles.length > 0, "至少配置一个系统角色", systemPath("system.roles")],
      ["业务应用", appRuntimeState.apps.length > 0, "创建系统内业务应用", systemPath("apps.list")],
      ["业务模块", appRuntimeState.modules.length > 0, "创建业务模块和字段", systemPath("modules.list")],
      ["发布", publishedModules > 0, "发布后普通用户才能使用", systemPath("modules.ui")],
    ];
    const setupSteps: [string, string, string, string][] = [
      ["1", "系统设置", "维护租户、成员、部门、角色和字典，为业务运行建立权限上下文。", systemPath("system.members")],
      ["2", "建模配置", "创建业务应用和模块，配置字段、页面和发布检查。", systemPath("apps.list")],
      ["3", "业务运行", "进入业务运行台使用已发布模块，新增记录并提交审批。", systemPath("runtime.home")],
      ["4", "协同与开放", "配置流程、文件导出、对外应用和审计追踪。", systemPath("flow.workbench")],
    ];
    const healthItems: [string, string, string][] = [
      ["成员", String(systemState.members.length), "系统内可授权成员"],
      ["部门", String(flattenDepartments(systemState.departments).length), "组织结构节点"],
      ["角色", String(systemState.roles.length), "系统内权限角色"],
      ["业务应用", String(appRuntimeState.apps.length), "已配置业务分组"],
      ["业务模块", String(appRuntimeState.modules.length), "业务应用下模块"],
      ["已发布", String(publishedModules), "可进入业务运行台"],
    ];

    return node("div", { className: "overview-page" }, [
      node("section", { className: "overview-hero" }, [
        node("div", {}, [
          node("p", { className: "eyebrow", text: "系统工作台" }),
          node("h2", { text: context?.system.systemName ?? "未进入系统" }),
          node("p", { className: "form-note", text: "先完成系统设置，再配置业务应用和模块，最后进入业务运行台处理真实数据。这里聚合当前系统的配置进度和下一步入口。" }),
          node("div", { className: canConfigureSystem ? "role-banner is-admin" : "role-banner is-user" }, [
            node("strong", { text: canConfigureSystem ? "你正在管理这个系统" : "你正在使用这个系统" }),
            node("span", {
              text: canConfigureSystem
                ? "请按推荐路径完成成员、角色、建模和发布。"
                : "你只能看到被授权的业务模块和数据范围，配置入口会自动隐藏。",
            }),
          ]),
        ]),
        node("div", { className: "overview-context" }, [
          node("span", { text: `租户：${context?.tenant?.tenantName ?? "默认租户"}` }),
          node("span", { text: `成员：${context?.member.displayName ?? "-"}` }),
          node("span", { text: `权限：${permissionCount}` }),
        ]),
      ]),
      node("div", { className: "metric-grid overview-metrics" }, healthItems.map(([value, label, hint]) =>
        node("div", { className: "stat" }, [
          node("strong", { text: value }),
          node("span", { text: label }),
          node("small", { text: hint }),
        ]),
      )),
      node("section", { className: "flow-board" }, [
        node("div", { className: "panel-heading" }, [
          node("div", {}, [
            node("p", { className: "eyebrow", text: "推荐路径" }),
            node("h2", { text: "从配置到运行" }),
          ]),
        ]),
        node("div", { className: "step-grid" }, setupSteps.map(([order, title, desc, href]) =>
          node("a", { className: "step-card", href: `#${href}` }, [
            node("span", { className: "step-index", text: order }),
            node("strong", { text: title }),
            node("p", { text: desc }),
          ]),
        )),
      ]),
      node("section", { className: "flow-board" }, [
        node("div", { className: "panel-heading" }, [
          node("div", {}, [
            node("p", { className: "eyebrow", text: "就绪度检查" }),
            node("h2", { text: "普通用户能不能开始使用" }),
          ]),
          node("span", { className: publishedModules > 0 ? "status-pill ok" : "status-pill", text: publishedModules > 0 ? "可运行" : "待发布" }),
        ]),
        node("div", { className: "readiness-list" }, readinessItems.map(([title, passed, description, href]) =>
          node("a", { className: `readiness-item${passed ? " is-done" : " is-pending"}`, href: `#${href}` }, [
            node("span", { text: passed ? "完成" : "待办" }),
            node("strong", { text: title }),
            node("small", { text: description }),
          ]),
        )),
      ]),
      node("section", { className: "quick-grid" }, [
        quickAction("成员与权限", "添加成员、部门和角色授权", systemPath("system.members")),
        quickAction("建模配置", "创建业务应用、模块、字段和页面", systemPath("apps.list")),
        quickAction("业务运行", "使用已发布模块填报业务", systemPath("runtime.home")),
        quickAction("系统审计", "按 requestId 追踪系统内问题", systemPath("audit.system")),
      ]),
    ]);
  }

  function renderPlatformAccounts(): HTMLElement {
    const actions = platformCenter.accounts.actions();
    const editing = platformState.accounts.records.find((item) => item.accountId === platformState.editingAccountId);
    return node("div", { className: "admin-page" }, [
      renderPageMetrics([
        [String(platformState.accounts.total), "账号总数"],
        [enabledCount(platformState.accounts.records), "正常账号"],
        [platformState.accounts.loading ? "同步中" : "就绪", "列表状态"],
      ]),
      node("section", { className: "task-card" }, [
        node("div", { className: "task-card-title" }, [
          node("strong", { text: editing ? `编辑账号：${editing.loginName}` : "账号资料" }),
          node("span", { text: "创建、编辑、重置密码和角色分配都在页面内完成，不再使用浏览器弹窗。" }),
        ]),
        node("div", { className: "form-grid" }, [
          input("登录名", "platformAccountLoginName", editing?.loginName ?? ""),
          input("显示名称", "platformAccountDisplayName", editing?.displayName ?? ""),
          input("手机号", "platformAccountMobile", editing?.mobile ?? ""),
          input("邮箱", "platformAccountEmail", editing?.email ?? ""),
          input("初始/新密码", "platformAccountInitialPassword", "", "password"),
          multiSelectField("平台角色", "platformAccountRoleIds", platformState.selectedAccountRoleIds, platformRoleOptions()),
          button(editing ? "保存账号" : "创建账号", "primary", editing ? () => editPlatformAccount(editing) : createPlatformAccount, editing ? !actions.edit.enabled : !actions.create.enabled),
          button("加载角色", "secondary", loadPlatformRoles),
          editing ? button("取消编辑", "secondary", clearPlatformAccountEdit) : undefined,
        ]),
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
            button("带入编辑", "secondary", () => fillPlatformAccountForm(item), !platformCenter.accounts.actions(item).edit.enabled),
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
    const editing = platformState.roles.records.find((item) => item.roleId === platformState.editingRoleId);
    const authorizing = platformState.roles.records.find((item) => item.roleId === platformState.authorizingRoleId);
    return node("div", { className: "admin-page" }, [
      renderPageMetrics([
        [String(platformState.roles.total), "角色总数"],
        [enabledCount(platformState.roles.records), "启用角色"],
        [platformState.permissionCatalogSummary ?? "未加载", "权限目录"],
      ]),
      node("section", { className: "task-card" }, [
        node("div", { className: "task-card-title" }, [
          node("strong", { text: editing ? `编辑角色：${editing.name}` : "角色资料" }),
          node("span", { text: "角色资料和授权拆分为两个面板，避免在弹窗里录入权限编码。" }),
        ]),
        node("div", { className: "form-grid" }, [
          input("角色编码", "platformRoleCode", editing?.code ?? ""),
          input("角色名称", "platformRoleName", editing?.name ?? ""),
          textarea("角色说明", "platformRoleDescription", editing?.description ?? ""),
          button(editing ? "保存角色" : "创建角色", "primary", editing ? () => editPlatformRole(editing) : createPlatformRole, editing ? !actions.edit.enabled : !actions.create.enabled),
          editing ? button("取消编辑", "secondary", clearPlatformRoleEdit) : undefined,
          button("加载权限目录", "secondary", loadPlatformPermissionCatalog, !actions.authorize.enabled),
        ]),
      ]),
      node("section", { className: "task-card" }, [
        node("div", { className: "task-card-title" }, [
          node("strong", { text: authorizing ? `授权：${authorizing.name}` : "角色授权" }),
          node("span", { text: "选择角色后维护菜单和操作权限，权限目录加载后可看到目录规模。" }),
        ]),
        node("div", { className: "form-grid" }, [
          textarea("操作权限编码", "platformRoleOperationCodes", platformState.platformRoleOperationCodes),
          textarea("菜单 ID", "platformRoleMenuIds", platformState.platformRoleMenuIds),
          button("保存授权", "primary", authorizing ? () => authorizePlatformRole(authorizing) : noop, !authorizing || !actions.authorize.enabled),
          authorizing ? button("取消授权", "secondary", clearPlatformRoleAuth) : undefined,
        ]),
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
            button("带入编辑", "secondary", () => fillPlatformRoleForm(item), !platformCenter.roles.actions(item).edit.enabled),
            button(item.status === "ENABLED" ? "禁用" : "启用", "secondary", () => togglePlatformRole(item), !platformCenter.roles.actions(item).status.enabled),
            button("授权", "secondary", () => fillPlatformRoleAuth(item), !platformCenter.roles.actions(item).authorize.enabled),
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
      pageIntro("系统资料", "这里维护当前业务系统的基础信息。平台账号负责登录，系统资料只定义这个业务空间本身。"),
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
      pageIntro("租户管理", "单租户系统通常只需要默认租户；多租户系统需要先建租户，再给成员分配租户上下文。"),
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
      pageIntro("成员是平台账号在当前系统里的身份", "先选择平台账号，再补充岗位、部门、租户和系统角色。普通业务用户能看到什么，由这里分配的角色和数据范围决定。", "warn"),
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
      pageIntro("部门组织", "部门用于组织成员和数据范围。删除前先确认没有子部门和成员引用。"),
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
          `${"  ".repeat(item.depth)}${departmentName(item)}`,
          departmentCode(item),
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
      pageIntro("系统角色", "角色由菜单权限、操作权限、字段权限和数据范围组成。页面可见性不能写死角色名称，必须基于这些权限判断。"),
      node("section", { className: "task-card" }, [
        node("div", { className: "task-card-title" }, [
          node("strong", { text: "角色资料" }),
          node("span", { text: "创建或编辑系统内角色，授权请先在列表中读取角色权限。" }),
        ]),
        node("div", { className: "form-grid" }, [
          input("角色编码", "systemRoleCode", ""),
          input("角色名称", "systemRoleName", ""),
          textarea("角色说明", "systemRoleDescription", ""),
          button("创建角色", "primary", createSystemRole, !actions.create.enabled),
          button("加载权限目录", "secondary", loadSystemPermissionCatalog, !actions.permission.enabled),
        ]),
      ]),
      node("section", { className: "task-card" }, [
        node("div", { className: "task-card-title" }, [
          node("strong", { text: "权限授权" }),
          node("span", { text: "从权限目录中选择操作权限和菜单权限，再保存到当前行角色。" }),
        ]),
        node("div", { className: "form-grid" }, [
          multiSelectField("操作权限", "systemRolePermissionOperations", splitValues(systemState.rolePermissionOperationCodes), catalogOperationOptions(systemState.permissionCatalog)),
          multiSelectField("菜单权限", "systemRolePermissionMenus", splitValues(systemState.rolePermissionMenuIds), catalogMenuOptions(systemState.permissionCatalog)),
          selectField("数据范围", "systemRolePermissionScope", systemState.rolePermissionScopeType, [
            ["ALL", "全部"],
            ["SELF", "本人"],
            ["DEPT", "本部门"],
            ["DEPT_TREE", "本部门及下级"],
            ["CUSTOM", "自定义"],
          ]),
        ]),
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
      pageIntro("业务字典", "字典用于字段选项、状态和分类。先维护字典类型，再维护字典项；存在引用时不要直接删除。"),
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

  function renderAppList(): HTMLElement {
    const actions = moduleConfigPage.permissions();
    return renderConfigWorkbench("业务应用", [
      renderPageMetrics([
        [String(appRuntimeState.apps.length), "业务应用总数"],
        [selectedApp()?.name ?? "未选择", "当前业务应用"],
        [enabledCount(appRuntimeState.apps), "启用业务应用"],
      ]),
      node("section", { className: "task-card" }, [
        node("div", { className: "task-card-title" }, [
          node("strong", { text: "创建业务应用" }),
          node("span", { text: "业务应用用于组织系统内模块和运行入口，不是平台级对外应用。" }),
        ]),
        node("div", { className: "form-grid" }, [
          input("业务应用名称", "appName", ""),
          input("业务应用编码", "appCode", ""),
          input("图标", "appIcon", "grid"),
          textarea("业务应用说明", "appDescription", ""),
          button("创建业务应用", "primary", createAppConfig, !actions.appCreate.enabled),
        ]),
      ]),
      renderDataTable(
        "apps",
        ["业务应用", "编码", "状态", "模块数", "操作"],
        appRuntimeState.apps.map((item) => [
          item.name,
          item.code,
          statusLabel(item.status),
          String(item.moduleCount ?? 0),
          node("div", { className: "row-actions" }, [
            button("选择", "secondary", () => selectAppConfig(item)),
            button("编辑", "secondary", () => updateAppConfig(item), !actions.appEdit.enabled),
            button(item.status === "ENABLED" ? "停用" : "启用", "secondary", () => toggleAppConfig(item), !actions.appStatus.enabled),
            button("模块", "primary", () => openModulesForApp(item)),
          ]),
        ]),
        "暂无业务应用，创建后可继续配置模块、字段、页面和发布。",
      ),
    ]);
  }

  function renderModuleList(): HTMLElement {
    const actions = moduleConfigPage.permissions();
    const app = selectedApp();
    return renderConfigWorkbench("模块", [
      renderPageMetrics([
        [app?.name ?? "未选择", "所属业务应用"],
        [String(appRuntimeState.modules.length), "模块总数"],
        [selectedModule()?.name ?? "未选择", "当前模块"],
      ]),
      node("section", { className: "task-card" }, [
        node("div", { className: "task-card-title" }, [
          node("strong", { text: "创建模块" }),
          node("span", { text: app ? "模块就是一类业务数据，发布后会出现在业务运行台。" : "请先创建或选择业务应用。" }),
        ]),
        node("div", { className: "form-grid" }, [
          selectField("业务应用", "selectedAppId", appRuntimeState.selectedAppId ?? "", appSelectOptions()),
          input("模块名称", "moduleName", ""),
          input("模块编码", "moduleCode", ""),
          textarea("模块说明", "moduleDescription", ""),
          button("切换业务应用", "secondary", switchSelectedApp),
          button("创建模块", "primary", createModuleConfig, !app || !actions.moduleCreate.enabled),
        ]),
      ]),
      renderDataTable(
        "modules",
        ["模块", "编码", "状态", "字段数", "操作"],
        appRuntimeState.modules.map((item) => [
          item.name,
          item.code,
          statusLabel(item.status),
          String(item.fieldCount ?? 0),
          node("div", { className: "row-actions" }, [
            button("选择", "secondary", () => selectModuleConfig(item)),
            button("编辑", "secondary", () => updateModuleConfig(item), !actions.moduleEdit.enabled),
            button(item.status === "DISABLED" ? "启用" : "停用", "secondary", () => toggleModuleConfig(item), !actions.moduleEdit.enabled),
            button("字段", "secondary", () => openFieldsForModule(item)),
            button("页面配置", "secondary", () => openUiForModule(item)),
            button("运行", "primary", () => openRuntimeModule(item), item.status !== "PUBLISHED"),
          ]),
        ]),
        app ? "暂无模块，创建后会显示在这里。" : "请先创建或选择业务应用。",
      ),
    ]);
  }

  function renderModuleFields(): HTMLElement {
    const actions = moduleConfigPage.permissions();
    const module = selectedModule();
    return renderConfigWorkbench("字段", [
      renderPageMetrics([
        [module?.name ?? "未选择", "当前模块"],
        [String(appRuntimeState.fields.length), "字段总数"],
        [String(appRuntimeState.fieldTypes.length || moduleConfigPage.fieldDesigner.availableFieldTypes.length), "字段类型"],
      ]),
      node("section", { className: "task-card" }, [
        node("div", { className: "task-card-title" }, [
          node("strong", { text: "字段设计" }),
          node("span", { text: module ? "字段决定业务运行台列表、表单、详情和权限控制。普通用户只能看到授权字段。" : "请先选择模块。" }),
        ]),
        node("div", { className: "form-grid" }, [
          selectField("模块", "selectedModuleId", appRuntimeState.selectedModuleId ?? "", moduleSelectOptions()),
          input("字段名称", "fieldName", ""),
          input("字段编码", "fieldCode", ""),
          selectField("字段类型", "fieldType", "TEXT", fieldTypeOptions()),
          selectField("是否必填", "fieldRequired", "false", [["false", "否"], ["true", "是"]]),
          selectField("是否唯一", "fieldUnique", "false", [["false", "否"], ["true", "是"]]),
          input("选项", "fieldOptions", ""),
          button("切换模块", "secondary", switchSelectedModule),
          button("创建字段", "primary", createModuleField, !module || !actions.fieldCreate.enabled),
        ]),
      ]),
      renderDataTable(
        "fields",
        ["字段", "编码", "类型", "状态", "操作"],
        appRuntimeState.fields.map((item) => [
          item.name,
          item.code,
          item.fieldType,
          statusLabel(item.status),
          node("div", { className: "row-actions" }, [
            button("编辑", "secondary", () => updateModuleField(item), !actions.fieldEdit.enabled),
            button(item.status === "ENABLED" ? "停用" : "启用", "secondary", () => toggleModuleField(item), !actions.fieldEdit.enabled),
          ]),
        ]),
        module ? "暂无字段，创建后会显示在这里。" : "请先选择模块。",
      ),
    ]);
  }

  function renderModuleUi(): HTMLElement {
    const actions = moduleConfigPage.permissions();
    const module = selectedModule();
    return renderConfigWorkbench("页面与发布", [
      renderPageMetrics([
        [module?.name ?? "未选择", "当前模块"],
        [appRuntimeState.publishCheck?.passed ? "通过" : appRuntimeState.publishCheck ? "未通过" : "未检查", "发布检查"],
        [appRuntimeState.publishResult?.versionNo ? `v${appRuntimeState.publishResult.versionNo}` : "-", "发布版本"],
      ]),
      node("section", { className: "task-card" }, [
        node("div", { className: "task-card-title" }, [
          node("strong", { text: "页面、菜单与发布" }),
          node("span", { text: "先保存默认页面和菜单动作，再执行发布检查，通过后发布到业务运行台。" }),
        ]),
        node("div", { className: "form-grid" }, [
          selectField("模块", "selectedModuleId", appRuntimeState.selectedModuleId ?? "", moduleSelectOptions()),
          input("菜单编码", "menuCode", module ? `${module.code}_menu` : ""),
          input("菜单名称", "menuName", module?.name ?? ""),
          button("切换模块", "secondary", switchSelectedModule),
          button("加载配置", "secondary", loadModuleUiConfig, !module || !actions.pageView.enabled),
          button("保存默认页面", "primary", saveDefaultPageConfig, !module || appRuntimeState.fields.length === 0 || !actions.pageEdit.enabled),
          button("保存菜单动作", "secondary", saveMenuAndActions, !module || !actions.menuEdit.enabled),
          button("发布检查", "secondary", publishCheckModule, !module || !actions.modulePublish.enabled),
          button("发布模块", "primary", publishModuleConfig, !module || !actions.modulePublish.enabled),
        ]),
      ]),
      renderDataTable(
        "schemas",
        ["配置", "状态", "版本", "内容"],
        [
          ["列表视图", appRuntimeState.listSchema?.status ?? "-", String(appRuntimeState.listSchema?.version ?? "-"), schemaSummary(appRuntimeState.listSchema)],
          ["表单视图", appRuntimeState.formSchema?.status ?? "-", String(appRuntimeState.formSchema?.version ?? "-"), schemaSummary(appRuntimeState.formSchema)],
          ["详情视图", appRuntimeState.detailSchema?.status ?? "-", String(appRuntimeState.detailSchema?.version ?? "-"), schemaSummary(appRuntimeState.detailSchema)],
        ],
        "加载或保存页面配置后会显示 schema 摘要。",
      ),
      renderDataTable(
        "publish-issues",
        ["级别", "对象", "编码", "说明"],
        (appRuntimeState.publishCheck?.issues ?? []).map((item) => [
          item.level,
          item.targetType,
          item.targetCode ?? item.targetId ?? "-",
          item.message,
        ]),
        "发布检查通过或尚未执行。",
      ),
    ]);
  }

  function renderRuntimeHome(): HTMLElement {
    return node("div", { className: "admin-page" }, [
      renderPageMetrics([
        [String(appRuntimeState.runtimeMenus.length), "可用业务入口"],
        [selectedModule()?.name ?? "未选择", "当前模块"],
        [appRuntimeState.runtimeSchema ? "已准备" : "待打开", "业务表单状态"],
      ]),
      node("section", { className: "task-card" }, [
        node("div", { className: "task-card-title" }, [
          node("strong", { text: "我的业务入口" }),
          node("span", { text: "这里只展示已经发布且你有权限访问的业务模块。看不到模块时，请联系管理员完成建模发布或分配权限。" }),
        ]),
      ]),
      node("div", { className: "form-grid" }, [
        button("刷新业务入口", "primary", loadRuntimeMenus),
      ]),
      renderDataTable(
        "runtime-menus",
        ["业务模块", "入口编码", "操作"],
        flattenRuntimeMenus(appRuntimeState.runtimeMenus).map((item) => [
          item.name,
          item.code,
          node("div", { className: "row-actions" }, [
            button("进入", "primary", () => item.moduleId ? openRuntimeModuleById(item.moduleId) : undefined, !item.moduleId),
          ]),
        ]),
        "暂无可用业务模块，请联系管理员在建模配置中发布模块。",
      ),
    ]);
  }

  function renderRuntimeModule(): HTMLElement {
    const schema = appRuntimeState.runtimeSchema;
    const records = appRuntimeState.recordPage?.records ?? [];
    const detail = appRuntimeState.selectedRecord;
    return node("div", { className: "runtime-page" }, [
      renderPageMetrics([
        [selectedModule()?.name ?? schema?.moduleCode ?? "-", "业务模块"],
        [String(records.length), "列表记录"],
        [detail ? "已选择" : "未选择", "当前记录"],
      ]),
      node("section", { className: "task-card" }, [
        node("div", { className: "task-card-title" }, [
          node("strong", { text: "业务数据列表" }),
          node("span", { text: "你看到的是当前角色和数据范围内的记录。保存、提交和导出按钮会按权限自动启用或禁用。" }),
        ]),
        node("div", { className: "form-grid" }, [
          selectField("模块", "selectedModuleId", appRuntimeState.selectedModuleId ?? "", moduleSelectOptions()),
          input("关键字", "runtimeKeyword", ""),
          button("切换模块", "secondary", switchSelectedModule),
          button("打开业务表单", "primary", loadRuntimeModule, !selectedModuleId()),
          button("查询记录", "secondary", loadRuntimeRecords, !schema),
        ]),
      ]),
      renderRuntimeForm(schema, detail),
      renderDataTable(
        "records",
        ["标题", "状态", "版本", "更新时间", "操作"],
        records.map((item) => [
          item.title ?? item.recordNo ?? item.recordId,
          statusLabel(item.recordStatus),
          String(item.recordVersion),
          item.updatedAt ?? "-",
          node("div", { className: "row-actions" }, [
            button("详情", "secondary", () => loadRuntimeRecordDetail(item.recordId)),
            button("编辑保存", "secondary", () => updateRuntimeRecord(item), !schema),
            button("提交", "secondary", () => submitRuntimeRecord(item), !schema),
            button("历史", "secondary", () => loadRuntimeHistory(item.recordId), !schema),
          ]),
        ]),
        "暂无记录，创建后会显示在这里。",
      ),
      renderRuntimeDetailTabs(detail),
    ]);
  }

  function renderRuntimeForm(schema?: RuntimeModuleSchemaVO, detail?: RecordDetailVO): HTMLElement {
    if (!schema) {
      return node("div", { className: "empty-state" }, [
        node("strong", { text: "请选择业务模块" }),
        node("span", { text: "选择已发布模块后点击“打开业务表单”，即可新增或查询记录。" }),
      ]);
    }
    const currentValues = Object.fromEntries((detail?.values ?? []).map((item) => [item.fieldCode, String(item.value ?? "")]));
    return node("div", { className: "form-grid" }, [
      ...schema.fieldDefinitions
        .filter((field) => field.status !== "DELETED")
        .map((field) => {
          const fieldCode = runtimeFieldCode(field);
          return renderRuntimeFieldInput(runtimeFieldName(field), `runtime_${fieldCode}`, currentValues[fieldCode] ?? runtimeFieldDefaultValue(field));
        }),
      textarea("备注", "runtimeRemark", ""),
      button("新建记录", "primary", createRuntimeRecord),
      button("保存当前详情", "secondary", () => detail ? updateRuntimeRecord(detail) : undefined, !detail),
    ]);
  }

  function renderRuntimeDetail(detail?: RecordDetailVO): HTMLElement {
    return renderDataTable(
      "record-detail",
      ["字段", "值", "说明"],
      (detail?.values ?? []).map((item) => [
        item.fieldCode,
        formatValue(item.displayValue ?? item.value),
        item.fieldType ?? "-",
      ]),
      "选择记录详情后会显示字段值。",
    );
  }

  function renderRuntimeDetailTabs(detail?: RecordDetailVO): HTMLElement {
    return node("section", { className: "detail-tabs" }, [
      node("div", { className: "tab-strip" }, [
        tabButton("详情", interactionState.runtimeDetailTab === "detail", () => { interactionState.runtimeDetailTab = "detail"; render(); }),
        tabButton("附件", interactionState.runtimeDetailTab === "attachments", () => { interactionState.runtimeDetailTab = "attachments"; render(); }),
        tabButton("审批", interactionState.runtimeDetailTab === "flow", () => { interactionState.runtimeDetailTab = "flow"; render(); }),
        tabButton("历史", interactionState.runtimeDetailTab === "history", () => { interactionState.runtimeDetailTab = "history"; render(); }),
        tabButton("关联", interactionState.runtimeDetailTab === "relations", () => { interactionState.runtimeDetailTab = "relations"; render(); }),
      ]),
      node("div", { className: "tab-panel" }, renderRuntimeDetailTabContent(detail)),
    ]);
  }

  function renderRuntimeDetailTabContent(detail?: RecordDetailVO): Child[] {
    if (interactionState.runtimeDetailTab === "detail") {
      return [renderRuntimeDetail(detail)];
    }
    if (interactionState.runtimeDetailTab === "history") {
      return [
        renderDataTable(
          "history",
          ["版本", "操作", "状态", "请求号"],
          appRuntimeState.recordHistory.map((item) => [
            String(item.recordVersion),
            item.operationType,
            [item.beforeStatus, item.afterStatus].filter(Boolean).join(" -> ") || "-",
            item.requestId ?? "-",
          ]),
          "暂无历史，查看记录历史后会显示在这里。",
        ),
      ];
    }
    if (interactionState.runtimeDetailTab === "relations") {
      return [
        renderDataTable(
          "relations",
          ["关联类型", "目标模块", "目标记录", "快照"],
          appRuntimeState.recordRelations.map((item) => [
            item.relationType ?? "-",
            item.targetModuleId ?? "-",
            item.targetRecordId ?? "-",
            formatValue(item.displaySnapshot),
          ]),
          "暂无关联记录。",
        ),
      ];
    }
    if (interactionState.runtimeDetailTab === "attachments") {
      return [emptyPanel("暂无附件", "附件上传和引用会在文件字段配置后显示在这里。")];
    }
    return [emptyPanel("审批信息", detail?.flowSummary ? formatValue(detail.flowSummary) : "记录提交审批后会显示当前节点和审批历史。")];
  }

  function renderFlowWorkbenchTabContent(
    todos: FlowTaskListItemVO[],
    cc: FlowCcItemVO[],
    started: FlowInstanceListItemVO[],
    instances: FlowInstanceListItemVO[],
  ): Child[] {
    if (interactionState.flowWorkbenchTab === "todo") {
      return [
        renderDataTable(
          "flow-todos",
          ["记录", "节点", "处理人", "状态", "操作"],
          todos.map((item) => [
            item.recordTitle ?? item.recordId,
            item.nodeName,
            item.claimedByName ?? item.assigneeName ?? "-",
            statusLabel(item.taskStatus),
            node("div", { className: "row-actions" }, [
              button("签收", "secondary", () => claimFlowTask(item), Boolean(flowWorkbench.workbench.claimDisabledReason(item))),
              button("通过", "primary", () => approveFlowTask(item), Boolean(flowWorkbench.workbench.taskActionDisabledReason(item, flowWorkbench.createActionBody("APPROVE", item.taskVersion, { comment: "同意" })))),
              button("退回", "secondary", () => rejectFlowTask(item), Boolean(flowWorkbench.workbench.taskActionDisabledReason(item, flowWorkbench.createActionBody("REJECT", item.taskVersion, { comment: "退回" })))),
              button("详情", "secondary", () => loadFlowTaskDetail(item.taskId)),
            ]),
          ]),
          "暂无待办任务。",
        ),
      ];
    }
    if (interactionState.flowWorkbenchTab === "cc") {
      return [
        renderDataTable(
          "flow-cc",
          ["记录", "节点", "已读", "时间"],
          cc.map((item) => [
            item.recordTitle ?? item.recordId,
            item.nodeName ?? "-",
            item.read ? "是" : "否",
            item.createdAt ?? "-",
          ]),
          "暂无抄送。",
        ),
      ];
    }
    const source = interactionState.flowWorkbenchTab === "started" ? started : instances;
    return [
      renderDataTable(
        "flow-instances",
        ["记录", "发起人", "当前节点", "状态", "操作"],
        source.map((item) => [
          item.recordTitle ?? item.recordId,
          item.starterName ?? item.starterMemberId ?? "-",
          item.currentNodeName ?? "-",
          statusLabel(item.status),
          node("div", { className: "row-actions" }, [
            button("详情", "secondary", () => loadFlowInstanceDetail(item.instanceId)),
            button("撤回", "secondary", () => withdrawFlowInstance(item), Boolean(flowWorkbench.workbench.withdrawDisabledReason(item))),
          ]),
        ]),
        interactionState.flowWorkbenchTab === "started" ? "暂无我的申请。" : "暂无流程实例。",
      ),
    ];
  }

  function renderExportTabContent(templates: ExportTemplateVO[], jobs: ExportJobListItemVO[]): Child[] {
    if (interactionState.exportTab === "templates") {
      return [
        renderDataTable(
          "export-templates",
          ["模板", "编码", "格式", "字段数", "操作"],
          templates.map((item) => [
            item.templateName,
            item.templateCode,
            item.exportFormat ?? "CSV",
            String(item.fields.length),
            node("div", { className: "row-actions" }, [
              button("更新", "secondary", () => updateExportTemplate(item)),
            ]),
          ]),
          "暂无导出模板。",
        ),
      ];
    }
    return [
      renderDataTable(
        "export-jobs",
        ["文件", "状态", "进度", "结果文件", "操作"],
        jobs.map((item) => [
          item.fileName ?? item.jobId,
          statusLabel(item.status),
          `${item.progress}%`,
          item.resultFileId ?? item.failureReason?.message ?? "-",
          node("div", { className: "row-actions" }, [
            button("详情", "secondary", () => loadExportJobDetail(item.jobId)),
            button("重试", "secondary", () => retryExportJob(item), Boolean(exportPage.jobs.retryDisabledReason(item))),
            button("取消", "secondary", () => cancelExportJob(item), Boolean(exportPage.jobs.cancelDisabledReason(item))),
          ]),
        ]),
        "暂无导出任务。",
      ),
    ];
  }

  function renderFlowTemplates(): HTMLElement {
    const templates = operationsState.flowTemplates?.records ?? [];
    const actions = {
      templateCreate: permissionStore.decide({ anyOperations: ["FLOW_TEMPLATE_CREATE"] }),
      templateEdit: permissionStore.decide({ anyOperations: ["FLOW_TEMPLATE_EDIT"] }),
      templatePublish: permissionStore.decide({ anyOperations: ["FLOW_TEMPLATE_PUBLISH"] }),
      bindingEdit: permissionStore.decide({ anyOperations: ["FLOW_BINDING_EDIT"] }),
    };
    return node("div", { className: "domain-page" }, [
      renderPageMetrics([
        [String(operationsState.flowTemplates?.total ?? templates.length), "模板总数"],
        [String(templates.filter((item) => item.status === "PUBLISHED").length), "已发布"],
        [selectedModule()?.name ?? "未选择", "默认绑定模块"],
      ]),
      node("section", { className: "task-card" }, [
        node("div", { className: "task-card-title" }, [
          node("strong", { text: "模板设计" }),
          node("span", { text: "先创建模板并保存默认审批图，通过发布检查后再绑定到模块。" }),
        ]),
        node("div", { className: "form-grid" }, [
          selectField("模块", "selectedModuleId", appRuntimeState.selectedModuleId ?? "", moduleSelectOptions()),
          input("模板名称", "flowTemplateName", `P11流程${p10Seed()}`),
          textarea("模板备注", "flowTemplateRemark", "P11 流程模板页面创建"),
          button("切换模块", "secondary", switchSelectedModule),
          button("创建模板", "primary", createFlowTemplate, !actions.templateCreate.enabled),
        ]),
      ]),
      renderDataTable(
        "flow-templates",
        ["模板", "模块", "状态", "版本", "操作"],
        templates.map((item) => [
          item.name,
          item.moduleId ?? "-",
          statusLabel(item.status),
          String(item.versionNo ?? item.graphVersion ?? "-"),
          node("div", { className: "row-actions" }, [
            button("默认图", "secondary", () => saveDefaultFlowGraph(item), !actions.templateEdit.enabled),
            button("发布检查", "secondary", () => checkFlowTemplate(item), !actions.templatePublish.enabled),
            button("发布", "primary", () => publishFlowTemplate(item), !actions.templatePublish.enabled || Boolean(flowWorkbench.templates.publishDisabledReason(item))),
            button(item.status === "DISABLED" ? "启用" : "停用", "secondary", () => toggleFlowTemplate(item), !actions.templateEdit.enabled),
            button("绑定模块", "secondary", () => bindFlowTemplate(item), !selectedModuleId() || !item.currentVersionId || !actions.bindingEdit.enabled),
          ]),
        ]),
        "暂无流程模板，可在上方创建并保存默认审批图。",
      ),
    ]);
  }

  function renderFlowWorkbench(): HTMLElement {
    const todos = operationsState.flowTodos?.records ?? [];
    const cc = operationsState.flowCc?.records ?? [];
    const started = operationsState.flowStarted?.records ?? [];
    const instances = operationsState.flowInstances?.records ?? [];
    return node("div", { className: "domain-page" }, [
      renderPageMetrics([
        [String(todos.length), "待办"],
        [String(cc.length), "抄送"],
        [String(instances.length), "流程实例"],
      ]),
      node("section", { className: "task-card" }, [
        node("div", { className: "task-card-title" }, [
          node("strong", { text: "审批工作台" }),
          node("span", { text: "按待办、流程实例和抄送分区处理，不再把审批动作散落成无说明按钮。" }),
        ]),
        node("div", { className: "form-grid" }, [
          input("关键字", "flowKeyword", ""),
          selectField("模块", "selectedModuleId", appRuntimeState.selectedModuleId ?? "", moduleSelectOptions()),
          button("切换模块", "secondary", switchSelectedModule),
          button("刷新工作台", "primary", loadFlowWorkbench),
        ]),
      ]),
      node("section", { className: "detail-tabs" }, [
        node("div", { className: "tab-strip" }, [
          tabButton("待办", interactionState.flowWorkbenchTab === "todo", () => { interactionState.flowWorkbenchTab = "todo"; render(); }),
          tabButton("抄送", interactionState.flowWorkbenchTab === "cc", () => { interactionState.flowWorkbenchTab = "cc"; render(); }),
          tabButton("我的申请", interactionState.flowWorkbenchTab === "started", () => { interactionState.flowWorkbenchTab = "started"; render(); }),
          tabButton("流程实例", interactionState.flowWorkbenchTab === "instances", () => { interactionState.flowWorkbenchTab = "instances"; render(); }),
        ]),
        node("div", { className: "tab-panel" }, renderFlowWorkbenchTabContent(todos, cc, started, instances)),
      ]),
    ]);
  }

  function renderFileCenter(): HTMLElement {
    const files = operationsState.files?.records ?? [];
    const actions = {
      upload: permissionStore.decide({ anyOperations: ["FILE_UPLOAD"] }),
      view: permissionStore.decide({ anyOperations: ["FILE_VIEW"] }),
    };
    return node("div", { className: "domain-page" }, [
      renderPageMetrics([
        [String(operationsState.files?.total ?? files.length), "文件总数"],
        [String(files.filter((item) => item.previewable).length), "可预览"],
        [operationsState.selectedFile?.fileName ?? "-", "当前文件"],
      ]),
      node("section", { className: "task-card upload-card" }, [
        node("div", { className: "task-card-title" }, [
          node("strong", { text: "上传与引用" }),
          node("span", { text: "上传后可在详情中查看预览、下载状态和引用对象。" }),
        ]),
        node("div", { className: "form-grid" }, [
          input("业务类型", "fileBizType", ""),
          input("字段编码", "fileFieldCode", ""),
          node("label", { className: "field wide-field" }, [
            node("span", { text: "上传文件" }),
            node("input", { name: "fileUpload", type: "file" }),
          ]),
          textarea("未选择文件时上传文本", "fileFallbackText", "P11 文件中心测试内容"),
          button("上传文件", "primary", uploadFileCenterFile, !actions.upload.enabled),
        ]),
      ]),
      renderDataTable(
        "files",
        ["文件", "类型", "大小", "状态", "操作"],
        files.map((item) => [
          item.fileName,
          item.contentType,
          formatFileSize(item.size),
          statusLabel(item.status),
          node("div", { className: "row-actions" }, [
            button("详情", "secondary", () => loadFileDetail(item.fileId), !actions.view.enabled),
            button("预览", "secondary", () => previewFile(item), Boolean(fileCenter.files.previewDisabledReason(item))),
            button("下载", "secondary", () => downloadFile(item), Boolean(fileCenter.files.downloadDisabledReason(item))),
            button("删除", "secondary", () => deleteFile(item), Boolean(fileCenter.files.deleteDisabledReason(item))),
          ]),
        ]),
        "暂无文件，上传后会显示在这里。",
      ),
    ]);
  }

  function renderExportJobs(): HTMLElement {
    const jobs = operationsState.exportJobs?.records ?? [];
    const templates = operationsState.exportTemplates;
    const exportModule = selectedModule();
    const exportModuleReady = Boolean(exportModule && moduleIsPublished(exportModule));
    return node("div", { className: "domain-page" }, [
      renderPageMetrics([
        [String(templates.length), "导出模板"],
        [String(operationsState.exportJobs?.total ?? jobs.length), "导出任务"],
        [selectedModule()?.name ?? "未选择", "当前模块"],
      ]),
      node("section", { className: `task-card ${exportModuleReady ? "is-ok" : "is-warn"}` }, [
        node("div", { className: "task-card-title" }, [
          node("strong", { text: "导出准备" }),
          node("span", {
            text: exportModuleReady ? "当前模块已发布，可创建导出模板和任务。" : "当前模块尚未发布，请先在建模配置中发布模块。",
          }),
        ]),
        node("div", { className: "form-grid" }, [
          selectField("模块", "selectedModuleId", appRuntimeState.selectedModuleId ?? "", moduleSelectOptions()),
          input("模板编码", "exportTemplateCode", `p11_tpl_${p10Seed()}`),
          input("模板名称", "exportTemplateName", `P11导出模板${p10Seed()}`),
          input("文件名", "exportFileName", `p11-export-${p10Seed()}.csv`),
          button("切换模块", "secondary", switchSelectedModule),
          button("创建模板", "primary", createExportTemplate, !selectedModuleId() || !exportModuleReady || appRuntimeState.fields.length === 0),
          button("创建导出任务", "primary", createExportJob, !selectedModuleId() || !exportModuleReady),
        ]),
      ]),
      node("section", { className: "detail-tabs" }, [
        node("div", { className: "tab-strip" }, [
          tabButton("导出模板", interactionState.exportTab === "templates", () => { interactionState.exportTab = "templates"; render(); }),
          tabButton("导出任务", interactionState.exportTab === "jobs", () => { interactionState.exportTab = "jobs"; render(); }),
        ]),
        node("div", { className: "tab-panel" }, renderExportTabContent(templates, jobs)),
      ]),
    ]);
  }

  function renderOpenApiClients(): HTMLElement {
    const state = operationsState.openApi;
    const clients = state.clients.records ?? [];
    const permissions = openApiPage.permissions();
    return node("div", { className: "domain-page" }, [
      renderPageMetrics([
        [String(state.clients.total ?? clients.length), "对外应用"],
        [String(clients.filter((item) => item.status === "ENABLED").length), "启用中"],
        [String(state.accessLogs.total ?? state.accessLogs.records.length), "调用日志"],
      ]),
      node("section", { className: "task-card" }, [
        node("div", { className: "task-card-title" }, [
          node("strong", { text: "创建对外应用" }),
          node("span", { text: "对外应用用于外部系统接入，密钥只展示一次，授权范围、IP 白名单和限流集中在同一面板处理。" }),
        ]),
        node("div", { className: "form-grid" }, [
          input("对外应用名称", "openApiClientName", ""),
          input("对外应用编码", "openApiClientCode", ""),
          input("授权 scope", "openApiScopes", "record:read,record:write"),
          input("IP 白名单", "openApiIpWhitelist", ""),
          input("每分钟限流", "openApiRateLimit", "60"),
          button("加载 Scope", "secondary", loadOpenApiScopeCatalog),
          button("创建对外应用", "primary", createOpenApiClient, !permissions.createClient.enabled),
        ]),
      ]),
      state.secretOnce?.visible
        ? node("div", { className: "message secret-message" }, [
            node("strong", { text: `AccessKey: ${state.secretOnce.accessKey}` }),
            node("span", { text: `Secret: ${state.secretOnce.secretOnce ?? state.secretOnce.maskedSecret ?? "-"}` }),
            button("我已保存", "secondary", consumeOpenApiSecret),
          ])
        : undefined,
      renderDataTable(
        "openapi-clients",
        ["对外应用", "AccessKey", "授权 scope", "状态", "操作"],
        clients.map((item) => [
          item.name,
          item.accessKey,
          item.scopes.map((scope) => scope.scopeCode).join(", ") || "-",
          statusLabel(item.status),
          node("div", { className: "row-actions" }, [
            button("编辑", "secondary", () => updateOpenApiClient(item), !permissions.editClient.enabled),
            button(item.status === "ENABLED" ? "停用" : "启用", "secondary", () => toggleOpenApiClient(item), !permissions.changeStatus.enabled),
            button("轮换密钥", "secondary", () => rotateOpenApiCredential(item), !permissions.rotateCredential.enabled),
            button("保存Scope", "secondary", () => updateOpenApiScopes(item), !permissions.editScope.enabled),
            button("保存IP", "secondary", () => updateOpenApiIpWhitelist(item), !permissions.editIp.enabled),
          ]),
        ]),
        "暂无对外应用。创建后可授权外部系统访问指定业务数据和平台能力。",
      ),
      renderDataTable(
        "openapi-logs",
        ["请求号", "API", "状态码", "错误码", "耗时"],
        state.accessLogs.records.map((item) => [
          item.requestId,
          String(item.apiId),
          String(item.statusCode),
          item.errorCode ?? "-",
          `${item.durationMs}ms`,
        ]),
        "暂无对外调用日志。",
      ),
    ]);
  }

  function renderAuditLogs(platform: boolean): HTMLElement {
    const state = platform ? operationsState.platformAudit : operationsState.audit;
    const kinds: [AuditLogKind, string][] = platform
      ? [["platformOperation", "平台操作"]]
      : [["operation", "操作"], ["request", "请求"], ["error", "错误"], ["recordChange", "记录变更"], ["openapi", "对外调用"]];
    return node("div", { className: "domain-page" }, [
      renderPageMetrics([
        [String(state.logs.total ?? state.logs.records.length), "日志总数"],
        [state.activeKind, "当前分类"],
        [state.selectedDetail?.requestId ?? "-", "当前请求号"],
      ]),
      node("section", { className: "task-card" }, [
        node("div", { className: "task-card-title" }, [
          node("strong", { text: platform ? "平台审计检索" : "系统审计检索" }),
          node("span", { text: "优先按 requestId 追踪，再按日志类型和业务对象过滤。" }),
        ]),
        node("div", { className: "form-grid" }, [
          selectField("日志类型", platform ? "platformAuditKind" : "auditKind", state.activeKind, kinds),
          input("请求号", "auditRequestId", ""),
          input("业务类型", "auditBizType", ""),
          button("查询", "primary", () => loadAuditLogs(platform)),
        ]),
      ]),
      renderDataTable(
        platform ? "platform-audit" : "audit",
        ["请求号", "操作人", "业务", "结果", "时间", "操作"],
        state.logs.records.map((item) => [
          item.requestId,
          item.operatorName ?? item.operatorType ?? "-",
          [item.bizType, item.bizId].filter(Boolean).join("/") || "-",
          item.result ?? item.errorCode ?? "-",
          item.createdAt,
          node("div", { className: "row-actions" }, [
            button("详情", "secondary", () => loadAuditDetail(platform, item)),
            button("标记请求号", "secondary", () => markAuditRequestId(platform, item.requestId)),
          ]),
        ]),
        "暂无审计日志。",
      ),
      renderAuditDetail(state.selectedDetail),
    ]);
  }

  function renderAuditDetail(detail?: AuditLogDetailPageVO): HTMLElement {
    return renderDataTable(
      "audit-detail",
      ["字段", "值", "说明"],
      detail
        ? [
            ["API", detail.apiId, detail.path],
            ["Trace", detail.traceId, detail.source],
            ["操作人", detail.operatorName ?? detail.operatorId ?? "-", detail.operatorType ?? "-"],
            ["变更字段", detail.changedFields?.join(", ") ?? "-", detail.beforeStatus && detail.afterStatus ? `${detail.beforeStatus} -> ${detail.afterStatus}` : "-"],
            ["快照", formatValue(detail.afterSnapshot ?? detail.beforeSnapshot), detail.errorCode ?? "-"],
          ]
        : [],
      "选择日志详情后会显示追踪信息。",
    );
  }

  function renderOpsHealth(): HTMLElement {
    const state = operationsState.ops;
    return node("div", { className: "domain-page ops-page" }, [
      renderPageMetrics([
        [state.health?.status ?? "-", "健康状态"],
        [state.version?.version ?? "-", "版本"],
        [state.migration?.migrationVersion ?? "-", "迁移版本"],
      ]),
      node("section", { className: "task-card" }, [
        node("div", { className: "task-card-title" }, [
          node("strong", { text: "运维巡检" }),
          node("span", { text: "异常项会在检查结果中给出说明和处理建议。" }),
        ]),
        button("刷新运维状态", "primary", loadOpsHealth),
      ]),
      renderDataTable(
        "ops-components",
        ["组件", "结果", "说明", "建议", "时间"],
        state.components.map((item) => [
          item.component,
          item.result,
          item.message ?? "-",
          item.suggestion ?? "-",
          item.checkedAt ?? "-",
        ]),
        "暂无运维检查结果。",
      ),
    ]);
  }

  function renderPageMetrics(items: [string, string][]): HTMLElement {
    return node("div", { className: "metric-grid" }, items.map(([value, label]) => stat(value, label)));
  }

  function renderConfigWorkbench(activeStep: string, children: Child[]): HTMLElement {
    const steps = ["业务应用", "模块", "字段", "页面与发布"];
    return node("div", { className: "config-workbench" }, [
      node("div", { className: "config-steps" }, steps.map((item, index) =>
        {
          const stepPath = configStepPath(item);
          return node("a", {
            className: `config-step${item === activeStep ? " active" : ""}${stepPath ? "" : " disabled"}`,
            href: stepPath ? `#${stepPath}` : `#${getCurrentPath()}`,
          }, [
            node("span", { text: String(index + 1) }),
            node("strong", { text: item }),
          ]);
        },
      )),
      node("div", { className: "config-main" }, children),
      node("aside", { className: "publish-aside" }, [
        node("strong", { text: "发布提示" }),
        node("span", { text: selectedModule()?.name ? `当前模块：${selectedModule()?.name}` : "请先选择模块" }),
        node("span", { text: appRuntimeState.publishCheck?.passed ? "发布检查已通过" : appRuntimeState.publishCheck ? "发布检查未通过" : "尚未执行发布检查" }),
        node("span", { text: appRuntimeState.publishResult?.versionNo ? `最近发布版本：v${appRuntimeState.publishResult.versionNo}` : "暂无发布版本" }),
      ]),
    ]);
  }

  function configStepPath(step: string): string | undefined {
    if (step === "业务应用") {
      return systemPath("apps.list");
    }
    if (step === "模块") {
      if (!selectedApp()) {
        return undefined;
      }
      return systemPath("modules.list");
    }
    if (step === "字段") {
      if (!selectedModule()) {
        return undefined;
      }
      return systemPath("modules.fields");
    }
    if (!selectedModule()) {
      return undefined;
    }
    return systemPath("modules.ui");
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

  function quickAction(title: string, description: string, href: string): HTMLElement {
    return node("a", { className: "quick-card", href: `#${href}` }, [
      node("strong", { text: title }),
      node("span", { text: description }),
    ]);
  }

  function pageIntro(title: string, description: string, variant: "default" | "warn" = "default"): HTMLElement {
    return node("section", { className: `task-card${variant === "warn" ? " is-warn" : ""}` }, [
      node("div", { className: "task-card-title" }, [
        node("strong", { text: title }),
        node("span", { text: description }),
      ]),
    ]);
  }

  function systemPath(routeName: string): string {
    const context = systemContextStore.getState().current;
    const appId = appRuntimeState.selectedAppId ?? appRuntimeState.apps[0]?.appId ?? "current";
    const moduleId = appRuntimeState.selectedModuleId ?? appRuntimeState.modules[0]?.moduleId ?? "current";
    if (!context) {
      return "/platform/my-systems";
    }
    try {
      return buildPath(routeName, {
        systemId: context.system.systemId,
        appId,
        moduleId,
      });
    } catch {
      return `/systems/${context.system.systemId}/overview`;
    }
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

  function renderUserGuidance(route: AppRouteRecord): HTMLElement {
    const context = systemContextStore.getState().current;
    const publishedModules = appRuntimeState.modules.filter((item) => item.status === "PUBLISHED").length;
    const guidance: Record<string, [string, string, string, string][]> = {
      "platform.mySystems": [
        ["1", "新建或进入系统", "先选择一个系统。没有系统时，直接在左侧表单创建。", "#/platform/my-systems"],
        ["2", "进入系统总览", "系统总览会告诉你成员、业务应用、模块和业务运行台还差什么。", "#/platform/my-systems"],
      ],
      "system.overview": [
        ["1", "配置成员与权限", "先让系统有人、有角色，再配置业务。", `#${systemPath("system.members")}`],
        ["2", "创建业务应用和模块", "用业务应用和模块描述你的业务对象。", `#${systemPath("apps.list")}`],
        ["3", "发布后使用", "模块发布后再进入业务运行台填报数据。", `#${systemPath("runtime.home")}`],
      ],
      "apps.list": [
        ["1", "创建业务应用", "业务应用相当于一个系统内业务分组，例如合同、车辆、客户。", `#${systemPath("apps.list")}`],
        ["2", "进入模块", "创建业务应用后点击“模块”，继续创建业务模块。", `#${systemPath("modules.list")}`],
      ],
      "modules.list": [
        ["1", "创建模块", "模块就是一张业务表，例如客户档案或订单。", `#${systemPath("modules.list")}`],
        ["2", "配置字段", "创建模块后点击“字段”，定义表单里要填写的内容。", `#${systemPath("modules.fields")}`],
      ],
      "modules.fields": [
        ["1", "添加字段", "至少添加一个文本字段，作为记录标题。", `#${systemPath("modules.fields")}`],
        ["2", "页面与发布", "字段完成后保存页面配置并发布模块。", `#${systemPath("modules.ui")}`],
      ],
      "modules.ui": [
        ["1", "保存页面", "先保存默认页面和菜单动作。", `#${systemPath("modules.ui")}`],
        ["2", "发布模块", "发布检查通过后发布，业务运行台才会出现。", `#${systemPath("runtime.home")}`],
      ],
      "runtime.home": [
        ["1", "加载业务入口", "只显示已发布模块。没有菜单时，回到建模配置发布模块。", `#${systemPath("runtime.home")}`],
        ["2", "开始填报", publishedModules > 0 ? "点击模块进入列表并新增记录。" : "当前没有已发布模块，请先完成建模配置。", `#${systemPath("apps.list")}`],
      ],
    };
    const items = guidance[route.name] ?? [
      ["1", "查看当前页面", "先点击刷新加载数据，再按页面主按钮完成操作。", `#${getCurrentPath()}`],
      ["2", "回到总览", context ? "不确定下一步时回到系统总览。" : "不确定下一步时回到我的系统。", context ? `#${systemPath("system.overview")}` : "#/platform/my-systems"],
    ];
    return node("div", { className: "guidance-list" }, items.map(([index, title, description, href]) =>
      node("a", { className: "guidance-item", href }, [
        node("span", { text: index }),
        node("div", {}, [
          node("strong", { text: title }),
          node("small", { text: description }),
        ]),
      ]),
    ));
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
    if (route.meta.requiredContext && !await ensureRouteSystemContext(route)) {
      return;
    }
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
    if (route.name === "system.overview") {
      await loadSystemOverview();
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
    if (route.name === "apps.list") {
      await loadAppsConfig();
      return;
    }
    if (route.name === "modules.list") {
      await loadModulesConfig();
      return;
    }
    if (route.name === "modules.fields") {
      await loadModuleFields();
      return;
    }
    if (route.name === "modules.ui") {
      await loadModuleUiConfig();
      return;
    }
    if (route.name === "runtime.home") {
      await loadRuntimeMenus();
      return;
    }
    if (route.name === "runtime.module") {
      await loadRuntimeModule();
      return;
    }
    if (route.name === "flow.templates") {
      await loadFlowTemplates();
      return;
    }
    if (route.name === "flow.workbench") {
      await loadFlowWorkbench();
      return;
    }
    if (route.name === "files.center") {
      await loadFiles();
      return;
    }
    if (route.name === "exports.jobs") {
      await loadExports();
      return;
    }
    if (route.name === "openapi.clients") {
      await loadOpenApiClients();
      return;
    }
    if (route.name === "audit.system") {
      await loadAuditLogs(false);
      return;
    }
    if (route.name === "audit.platform") {
      await loadAuditLogs(true);
      return;
    }
    if (route.name === "ops.health") {
      await loadOpsHealth();
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

  async function createMySystemAndEnter(): Promise<void> {
    const form = readForm();
    await execute("PLAT-002/SYS-001", async () => {
      const result = await mySystemsPage.createSystem({
        name: required(form.mySystemName, "系统名称"),
        code: required(form.mySystemCode, "系统编码"),
        tenantMode: (form.mySystemTenantMode || "SINGLE") as "SINGLE" | "MULTI",
        description: form.mySystemDescription?.trim() || undefined,
      });
      navigate(result.route);
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

  function fillPlatformAccountForm(item: PlatformAccountListVO): void {
    platformState.editingAccountId = item.accountId;
    platformState.selectedAccountRoleIds = item.roleIds ?? [];
    setFormValue("platformAccountLoginName", item.loginName);
    setFormValue("platformAccountDisplayName", item.displayName ?? "");
    setFormValue("platformAccountMobile", item.mobile ?? "");
    setFormValue("platformAccountEmail", item.email ?? "");
    setFormValue("platformAccountInitialPassword", "");
    render();
  }

  function clearPlatformAccountEdit(): void {
    platformState.editingAccountId = undefined;
    platformState.selectedAccountRoleIds = [];
    setFormValue("platformAccountLoginName", "");
    setFormValue("platformAccountDisplayName", "");
    setFormValue("platformAccountMobile", "");
    setFormValue("platformAccountEmail", "");
    setFormValue("platformAccountInitialPassword", "");
    render();
  }

  async function editPlatformAccount(item: PlatformAccountListVO): Promise<void> {
    const form = readForm();
    await execute("PLAT-014", async () => {
      const result = await platformCenter.accounts.update(item.accountId, {
        displayName: form.platformAccountDisplayName?.trim() || undefined,
        mobile: form.platformAccountMobile?.trim() || undefined,
        email: form.platformAccountEmail?.trim() || undefined,
      });
      platformState.accounts = await platformCenter.accounts.query({ pageNo: 1, pageSize: 20 });
      clearPlatformAccountEdit();
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
    const newPassword = readForm().platformAccountInitialPassword;
    if (!newPassword) {
      ui.lastError = `请先在“初始/新密码”输入框填写账号“${item.loginName}”的新密码。`;
      render();
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
    const roleIds = readForm().platformAccountRoleIds || platformState.selectedAccountRoleIds.join(",");
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

  function fillPlatformRoleForm(item: PlatformRoleVO): void {
    platformState.editingRoleId = item.roleId;
    setFormValue("platformRoleCode", item.code);
    setFormValue("platformRoleName", item.name);
    setFormValue("platformRoleDescription", item.description ?? "");
    render();
  }

  function clearPlatformRoleEdit(): void {
    platformState.editingRoleId = undefined;
    setFormValue("platformRoleCode", "");
    setFormValue("platformRoleName", "");
    setFormValue("platformRoleDescription", "");
    render();
  }

  async function editPlatformRole(item: PlatformRoleVO): Promise<void> {
    const form = readForm();
    await execute("PLAT-018", async () => {
      const result = await platformCenter.roles.update(item.roleId, {
        code: item.code,
        name: required(form.platformRoleName, "角色名称"),
        description: form.platformRoleDescription?.trim() || undefined,
      });
      platformState.roles = await platformCenter.roles.query({ pageNo: 1, pageSize: 20 });
      clearPlatformRoleEdit();
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

  function fillPlatformRoleAuth(item: PlatformRoleVO): void {
    platformState.authorizingRoleId = item.roleId;
    platformState.platformRoleOperationCodes = item.operationCodes?.join(",") ?? "";
    platformState.platformRoleMenuIds = item.menuIds?.join(",") ?? "";
    setFormValue("platformRoleOperationCodes", platformState.platformRoleOperationCodes);
    setFormValue("platformRoleMenuIds", platformState.platformRoleMenuIds);
    render();
  }

  function clearPlatformRoleAuth(): void {
    platformState.authorizingRoleId = undefined;
    platformState.platformRoleOperationCodes = "";
    platformState.platformRoleMenuIds = "";
    setFormValue("platformRoleOperationCodes", "");
    setFormValue("platformRoleMenuIds", "");
    render();
  }

  async function authorizePlatformRole(item: PlatformRoleVO): Promise<void> {
    const form = readForm();
    const operationCodes = form.platformRoleOperationCodes ?? platformState.platformRoleOperationCodes;
    const menuIds = form.platformRoleMenuIds ?? platformState.platformRoleMenuIds;
    await execute("PLAT-010", async () => {
      const result = await platformCenter.roles.saveMenus(item.roleId, {
        menuIds: splitValues(menuIds),
        operationCodes: splitValues(operationCodes),
      });
      platformState.roles = await platformCenter.roles.query({ pageNo: 1, pageSize: 20 });
      clearPlatformRoleAuth();
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

  async function loadSystemOverview(): Promise<void> {
    await loadSystemProfile();
    await loadSystemTenants();
    await refreshSystemManagementOptions();
    if (systemContextStore.getState().current?.tenant) {
      await loadAppsConfig();
      await loadRuntimeMenus();
    }
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
    const form = readForm();
    const deptIds = form.memberDeptIds || detail.deptIds?.join(",") || "";
    const tenantIds = form.memberTenantIds || detail.tenantIds?.join(",") || "";
    const postName = form.memberPostName || detail.postName;
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
    const form = readForm();
    const roleIds = form.memberRoleIds || memberRoleIds(detail).join(",");
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
      const response = await apiClient.call<DepartmentNodeVO, { parentId?: EntityId; code: string; name: string; sortOrder?: number }>("RBAC-002", {
        pathParams: pathParams(),
        body: {
          parentId: form.deptParentId?.trim() || undefined,
          code: required(form.deptCode, "部门编码"),
          name: required(form.deptName, "部门名称"),
          sortOrder: optionalNumber(form.deptSortOrder),
        },
      });
      await loadSystemDepartments();
      return response;
    });
  }

  async function editSystemDepartment(item: DepartmentNodeVO): Promise<void> {
    const form = readForm();
    const deptName = form.deptName || departmentName(item);
    const deptCode = form.deptCode || departmentCode(item);
    const sortOrder = form.deptSortOrder || String(item.sortOrder ?? 0);
    await execute("RBAC-003", async () => {
      requireSystemContext();
      const response = await apiClient.call<DepartmentNodeVO, { parentId?: EntityId; code: string; name: string; sortOrder?: number }>("RBAC-003", {
        pathParams: {
          ...pathParams(),
          deptId: item.deptId,
        },
        body: {
          parentId: item.parentId,
          code: required(deptCode, "部门编码"),
          name: required(deptName, "部门名称"),
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
    const form = readForm();
    const name = form.systemRoleName || item.name;
    const description = form.systemRoleDescription || item.description || "";
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
      systemState.rolePermissionOperationCodes = response.data.operationCodes?.join(",") ?? "";
      systemState.rolePermissionMenuIds = response.data.menuIds?.join(",") ?? "";
      systemState.rolePermissionScopeType = normalizeScopeType(response.data.dataScopes?.[0]?.scopeType ?? "ALL");
      return response;
    });
  }

  async function saveSystemRolePermissions(item: RoleVO): Promise<void> {
    const form = readForm();
    const operationCodes = form.systemRolePermissionOperations ?? systemState.rolePermissionOperationCodes;
    const menuIds = form.systemRolePermissionMenus ?? systemState.rolePermissionMenuIds;
    const scopeType = normalizeScopeType(form.systemRolePermissionScope ?? systemState.rolePermissionScopeType);
    await execute("RBAC-009", async () => {
      requireSystemContext();
      const response = await apiClient.call<RolePermissionDetailVO, {
        menuIds?: EntityId[];
        operationCodes?: string[];
        dataScopes?: Array<{ scopeType: "SELF" | "DEPT" | "DEPT_TREE" | "ALL" | "CUSTOM" }>;
      }>("RBAC-009", {
        pathParams: {
          ...pathParams(),
          roleId: item.roleId,
        },
        body: {
          menuIds: splitValues(menuIds),
          operationCodes: splitValues(operationCodes),
          dataScopes: [{ scopeType }],
        },
      });
      systemState.rolePermissions[item.roleId] = response.data;
      systemState.rolePermissionOperationCodes = response.data.operationCodes?.join(",") ?? operationCodes;
      systemState.rolePermissionMenuIds = response.data.menuIds?.join(",") ?? menuIds;
      systemState.rolePermissionScopeType = normalizeScopeType(response.data.dataScopes?.[0]?.scopeType ?? scopeType);
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
    const form = readForm();
    const name = form.dictTypeName || item.name;
    const description = form.dictTypeDescription || item.description || "";
    const sortOrder = form.dictTypeSortOrder || String(item.sortOrder ?? 0);
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
    const form = readForm();
    const label = form.dictItemLabel || item.label;
    const value = form.dictItemValue || item.value;
    const description = item.description ?? "";
    const sortOrder = form.dictItemSortOrder || String(item.sortOrder ?? 0);
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

  async function loadAppsConfig(): Promise<void> {
    await execute("APP-001", async () => {
      requireSystemContext();
      const result = await refreshAppsConfig(readForm().keyword);
      return pageModelResponse(result);
    });
  }

  async function createAppConfig(): Promise<void> {
    const form = readForm();
    await execute("APP-002", async () => {
      requireSystemContext();
      const result = await moduleConfigPage.apps.create({
        name: required(form.appName, "业务应用名称"),
        code: required(form.appCode, "业务应用编码"),
        icon: form.appIcon?.trim() || undefined,
        description: form.appDescription?.trim() || undefined,
        status: "ENABLED",
      });
      raisePageModelError(result);
      appRuntimeState.selectedAppId = result.data?.appId;
      await refreshAppsConfig();
      return pageModelResponse(result);
    });
  }

  async function updateAppConfig(item: AppListItemVO): Promise<void> {
    const form = readForm();
    await execute("APP-004", async () => {
      requireSystemContext();
      const result = await moduleConfigPage.apps.update(item.appId, {
        name: form.appName?.trim() || item.name,
        code: form.appCode?.trim() || item.code,
        icon: form.appIcon?.trim() || item.icon,
        description: form.appDescription?.trim() || item.description,
        status: item.status,
        version: item.version,
      });
      raisePageModelError(result);
      await refreshAppsConfig();
      return pageModelResponse(result);
    });
  }

  async function toggleAppConfig(item: AppListItemVO): Promise<void> {
    const targetStatus = item.status === "ENABLED" ? "DISABLED" : "ENABLED";
    await execute("APP-005", async () => {
      requireSystemContext();
      const result = await moduleConfigPage.apps.changeStatus(item.appId, {
        targetStatus,
        reason: "建模配置页面操作",
        version: item.version,
      });
      raisePageModelError(result);
      await refreshAppsConfig();
      return pageModelResponse(result);
    });
  }

  function selectAppConfig(item: AppListItemVO): void {
    appRuntimeState.selectedAppId = item.appId;
    ui.settings.appId = item.appId;
    appRuntimeState.modules = [];
    appRuntimeState.selectedModuleId = undefined;
    appRuntimeState.fields = [];
    render();
  }

  async function openModulesForApp(item: AppListItemVO): Promise<void> {
    selectAppConfig(item);
    navigate(`/systems/${currentSystemId()}/apps/${item.appId}/modules`);
  }

  async function loadModulesConfig(): Promise<void> {
    await execute("MOD-001", async () => {
      requireSystemContext();
      await ensureAppsLoaded();
      readRouteSelection();
      const appId = required(selectedAppId(), "业务应用");
      const result = await refreshModulesConfig(appId, readForm().keyword);
      return pageModelResponse(result);
    });
  }

  async function switchSelectedApp(): Promise<void> {
    const form = readForm();
    const appId = required(form.selectedAppId, "业务应用");
    appRuntimeState.selectedAppId = appId;
    ui.settings.appId = appId;
    appRuntimeState.modules = [];
    appRuntimeState.selectedModuleId = undefined;
    await loadModulesConfig();
  }

  async function createModuleConfig(): Promise<void> {
    const form = readForm();
    await execute("MOD-002", async () => {
      requireSystemContext();
      const appId = required(selectedAppId() ?? form.selectedAppId, "业务应用");
      const result = await moduleConfigPage.modules.create(appId, {
        name: required(form.moduleName, "模块名称"),
        code: required(form.moduleCode, "模块编码"),
        description: form.moduleDescription?.trim() || undefined,
      });
      raisePageModelError(result);
      appRuntimeState.selectedModuleId = result.data?.moduleId;
      ui.settings.moduleId = result.data?.moduleId ?? ui.settings.moduleId;
      await refreshModulesConfig(appId);
      return pageModelResponse(result);
    });
  }

  async function updateModuleConfig(item: ModuleListItemVO): Promise<void> {
    const form = readForm();
    await execute("MOD-004", async () => {
      requireSystemContext();
      const result = await moduleConfigPage.modules.update(item.moduleId, {
        name: form.moduleName?.trim() || item.name,
        code: form.moduleCode?.trim() || item.code,
        description: form.moduleDescription?.trim() || item.description,
        version: item.version,
      });
      raisePageModelError(result);
      await refreshModulesConfig(required(selectedAppId(), "业务应用"));
      return pageModelResponse(result);
    });
  }

  async function toggleModuleConfig(item: ModuleListItemVO): Promise<void> {
    const targetStatus = item.status === "DISABLED" ? "DRAFT" : "DISABLED";
    await execute("MOD-005", async () => {
      requireSystemContext();
      const result = await moduleConfigPage.modules.changeStatus(item.moduleId, {
        targetStatus,
        reason: "模块配置页面操作",
        version: item.version,
      });
      raisePageModelError(result);
      await refreshModulesConfig(required(selectedAppId(), "业务应用"));
      return pageModelResponse(result);
    });
  }

  function selectModuleConfig(item: ModuleListItemVO): void {
    appRuntimeState.selectedModuleId = item.moduleId;
    ui.settings.moduleId = item.moduleId;
    appRuntimeState.fields = [];
    appRuntimeState.runtimeSchema = undefined;
    appRuntimeState.recordPage = undefined;
    render();
  }

  function openFieldsForModule(item: ModuleListItemVO): void {
    selectModuleConfig(item);
    navigate(`/systems/${currentSystemId()}/modules/${item.moduleId}/fields`);
  }

  function openUiForModule(item: ModuleListItemVO): void {
    selectModuleConfig(item);
    navigate(`/systems/${currentSystemId()}/modules/${item.moduleId}/ui`);
  }

  function openRuntimeModule(item: ModuleListItemVO): void {
    selectModuleConfig(item);
    navigate(`/systems/${currentSystemId()}/runtime/modules/${item.moduleId}`);
  }

  function openRuntimeModuleById(moduleId: EntityId): void {
    appRuntimeState.selectedModuleId = moduleId;
    ui.settings.moduleId = moduleId;
    navigate(`/systems/${currentSystemId()}/runtime/modules/${moduleId}`);
  }

  async function loadModuleFields(): Promise<void> {
    await execute("FIELD-001", async () => {
      requireSystemContext();
      await ensureModulesLoaded();
      readRouteSelection();
      const result = await refreshModuleFields(required(selectedModuleId(), "模块"));
      return pageModelResponse(result);
    });
  }

  async function switchSelectedModule(): Promise<void> {
    const form = readForm();
    const moduleId = required(form.selectedModuleId, "模块");
    appRuntimeState.selectedModuleId = moduleId;
    ui.settings.moduleId = moduleId;
    await loadModuleFields();
  }

  async function createModuleField(): Promise<void> {
    const form = readForm();
    await execute("FIELD-002", async () => {
      requireSystemContext();
      const fieldType = required(form.fieldType, "字段类型") as DynamicFieldType;
      const result = await moduleConfigPage.fields.create(required(selectedModuleId(), "模块"), {
        name: required(form.fieldName, "字段名称"),
        code: required(form.fieldCode, "字段编码"),
        fieldType,
        required: form.fieldRequired === "true",
        unique: form.fieldUnique === "true",
        indexed: false,
        options: optionSupported(fieldType) ? parseFieldOptions(form.fieldOptions) : undefined,
        status: "ENABLED",
        sortOrder: appRuntimeState.fields.length + 1,
      });
      raisePageModelError(result);
      await refreshModuleFields(required(selectedModuleId(), "模块"));
      return pageModelResponse(result);
    });
  }

  async function updateModuleField(item: ModuleFieldVO): Promise<void> {
    const form = readForm();
    await execute("FIELD-003", async () => {
      requireSystemContext();
      const fieldType = (form.fieldType || item.fieldType) as DynamicFieldType;
      const result = await moduleConfigPage.fields.update(required(selectedModuleId(), "模块"), item.fieldId, {
        name: form.fieldName?.trim() || item.name,
        code: form.fieldCode?.trim() || item.code,
        fieldType,
        required: form.fieldRequired ? form.fieldRequired === "true" : item.required,
        unique: form.fieldUnique ? form.fieldUnique === "true" : item.unique,
        indexed: item.indexed,
        options: optionSupported(fieldType) ? parseFieldOptions(form.fieldOptions) || item.options : item.options,
        status: item.status,
        sortOrder: item.sortOrder,
        version: item.version,
      });
      raisePageModelError(result);
      await refreshModuleFields(required(selectedModuleId(), "模块"));
      return pageModelResponse(result);
    });
  }

  async function toggleModuleField(item: ModuleFieldVO): Promise<void> {
    const targetStatus = item.status === "ENABLED" ? "DISABLED" : "ENABLED";
    await execute("FIELD-004", async () => {
      requireSystemContext();
      const result = await moduleConfigPage.fields.changeStatus(required(selectedModuleId(), "模块"), item.fieldId, {
        targetStatus,
        reason: "字段配置页面操作",
        version: item.version,
      });
      raisePageModelError(result);
      await refreshModuleFields(required(selectedModuleId(), "模块"));
      return pageModelResponse(result);
    });
  }

  async function loadModuleUiConfig(): Promise<void> {
    await execute("UI-LOAD", async () => {
      requireSystemContext();
      await ensureModulesLoaded();
      readRouteSelection();
      const moduleId = required(selectedModuleId(), "模块");
      await refreshModuleFields(moduleId);
      const [listView, formView, detailView] = await Promise.all([
        moduleConfigPage.ui.loadListView(moduleId),
        moduleConfigPage.ui.loadForm(moduleId),
        moduleConfigPage.ui.loadDetail(moduleId),
      ]);
      raisePageModelError(listView);
      raisePageModelError(formView);
      raisePageModelError(detailView);
      appRuntimeState.listSchema = listView.data;
      appRuntimeState.formSchema = formView.data;
      appRuntimeState.detailSchema = detailView.data;
      return { requestId: listView.state.requestId ?? formView.state.requestId ?? detailView.state.requestId };
    });
  }

  async function saveDefaultPageConfig(): Promise<void> {
    await execute("UI-002/UI-004/UI-006", async () => {
      requireSystemContext();
      const moduleId = required(selectedModuleId(), "模块");
      if (appRuntimeState.fields.length === 0) {
        await refreshModuleFields(moduleId);
      }
      const refs = activeFieldRefs();
      const [listView, formView, detailView] = await Promise.all([
        moduleConfigPage.ui.saveListView(moduleId, {
          columns: refs,
          filters: refs.slice(0, 3),
          sorters: [],
        }),
        moduleConfigPage.ui.saveForm(moduleId, {
          formSections: [{ sectionCode: "base", title: "基础信息", fields: refs }],
          fieldWritable: refs.map((ref) => ({ fieldCode: ref.fieldCode, writable: true })),
        }),
        moduleConfigPage.ui.saveDetail(moduleId, {
          detailBlocks: [{ blockCode: "base", title: "基础信息", fields: refs }],
        }),
      ]);
      raisePageModelError(listView);
      raisePageModelError(formView);
      raisePageModelError(detailView);
      appRuntimeState.listSchema = listView.data;
      appRuntimeState.formSchema = formView.data;
      appRuntimeState.detailSchema = detailView.data;
      return { requestId: listView.state.requestId ?? formView.state.requestId ?? detailView.state.requestId };
    });
  }

  async function saveMenuAndActions(): Promise<void> {
    const form = readForm();
    await execute("UI-007/UI-008", async () => {
      requireSystemContext();
      const module = requiredSelectedModule();
      const menu = await moduleConfigPage.ui.saveMenu(module.moduleId, {
        menuCode: form.menuCode?.trim() || `${module.code}_menu`,
        menuName: form.menuName?.trim() || module.name,
        routePath: `/systems/${currentSystemId()}/runtime/modules/${module.moduleId}`,
        visible: true,
        enabled: true,
        sortOrder: 1,
      });
      raisePageModelError(menu);
      const actions = await moduleConfigPage.ui.saveActions(module.moduleId, {
        actions: [
          { actionCode: "RECORD_CREATE", label: "新建", visible: true, enabled: true, requiredPermission: "RECORD_CREATE" },
          { actionCode: "RECORD_EDIT", label: "编辑", visible: true, enabled: true, requiredPermission: "RECORD_EDIT" },
          { actionCode: "RECORD_DELETE", label: "删除", visible: true, enabled: true, requiredPermission: "RECORD_DELETE", danger: true },
          { actionCode: "RECORD_SUBMIT", label: "提交", visible: true, enabled: true, requiredPermission: "RECORD_SUBMIT" },
        ],
      });
      raisePageModelError(actions);
      await loadRuntimeMenus();
      return { requestId: menu.state.requestId ?? actions.state.requestId };
    });
  }

  async function publishCheckModule(): Promise<void> {
    await execute("MOD-006", async () => {
      requireSystemContext();
      const result = await moduleConfigPage.modules.publishCheck(required(selectedModuleId(), "模块"));
      raisePageModelError(result);
      appRuntimeState.publishCheck = result.data;
      return pageModelResponse(result);
    });
  }

  async function publishModuleConfig(): Promise<void> {
    await execute("MOD-007", async () => {
      requireSystemContext();
      const module = requiredSelectedModule();
      const detail = await moduleConfigPage.modules.detail(module.moduleId);
      raisePageModelError(detail);
      const freshModule = detail.data ?? module;
      appRuntimeState.modules = appRuntimeState.modules.map((item) => (item.moduleId === freshModule.moduleId ? freshModule : item));
      const result = await moduleConfigPage.modules.publish(module.moduleId, {
        moduleVersion: freshModule.version ?? module.version ?? 1,
        publishRemark: "页面发布",
      });
      raisePageModelError(result);
      appRuntimeState.publishResult = result.data;
      await refreshModulesConfig(required(selectedAppId(), "业务应用"));
      await loadRuntimeMenus();
      return pageModelResponse(result);
    });
  }

  async function loadRuntimeMenus(): Promise<void> {
    await execute("RUN-001", async () => {
      requireSystemContext();
      const result = await runtimeWorkbench.menus.load();
      raiseRuntimeError(result);
      appRuntimeState.runtimeMenus = result.data ?? [];
      return runtimeResponse(result);
    });
  }

  async function loadRuntimeModule(): Promise<void> {
    await execute("RUN-002/RUN-003", async () => {
      requireSystemContext();
      await ensureModulesLoaded();
      readRouteSelection();
      const moduleId = required(selectedModuleId(), "模块");
      const schema = await runtimeWorkbench.schema.load(moduleId);
      raiseRuntimeError(schema);
      appRuntimeState.runtimeSchema = schema.data;
      if (schema.data) {
        const records = await runtimeWorkbench.records.query(moduleId, schema.data, { pageNo: 1, pageSize: 20, keyword: readForm().runtimeKeyword });
        raiseRuntimeError(records);
        appRuntimeState.recordPage = toRuntimeRecordPage(records.data);
      }
      return { requestId: schema.state.requestId };
    });
  }

  async function loadRuntimeRecords(): Promise<void> {
    await execute("RUN-003", async () => {
      requireSystemContext();
      const schema = requiredRuntimeSchema();
      const result = await runtimeWorkbench.records.query(schema.moduleId, schema, {
        pageNo: 1,
        pageSize: 20,
        keyword: readForm().runtimeKeyword,
      });
      raiseRuntimeError(result);
      appRuntimeState.recordPage = toRuntimeRecordPage(result.data);
      return runtimeResponse(result);
    });
  }

  async function createRuntimeRecord(): Promise<void> {
    const form = readForm();
    await execute("RUN-004", async () => {
      requireSystemContext();
      const schema = requiredRuntimeSchema();
      const result = await runtimeWorkbench.records.create(schema.moduleId, schema, runtimeValues(form, schema), form.runtimeRemark);
      raiseRuntimeError(result);
      await loadRuntimeRecords();
      if (result.data?.recordId) {
        await loadRuntimeRecordDetail(result.data.recordId);
      }
      return runtimeResponse(result);
    });
  }

  async function loadRuntimeRecordDetail(recordId: EntityId): Promise<void> {
    await execute("RUN-005", async () => {
      requireSystemContext();
      const schema = requiredRuntimeSchema();
      const result = await runtimeWorkbench.records.detail(schema.moduleId, recordId, schema);
      raiseRuntimeError(result);
      appRuntimeState.selectedRecord = toRuntimeRecordDetail(result.data);
      return runtimeResponse(result);
    });
  }

  async function updateRuntimeRecord(item: Pick<RecordListItemVO | RecordDetailVO, "recordId" | "recordVersion">): Promise<void> {
    const form = readForm();
    await execute("RUN-006", async () => {
      requireSystemContext();
      const schema = requiredRuntimeSchema();
      const result = await runtimeWorkbench.records.update(
        schema.moduleId,
        item.recordId,
        schema,
        item.recordVersion,
        runtimeValues(form, schema),
        form.runtimeRemark,
      );
      raiseRuntimeError(result);
      await loadRuntimeRecords();
      await loadRuntimeRecordDetail(item.recordId);
      return runtimeResponse(result);
    });
  }

  async function submitRuntimeRecord(item: Pick<RecordListItemVO | RecordDetailVO, "recordId" | "recordVersion">): Promise<void> {
    await execute("RUN-008", async () => {
      requireSystemContext();
      const schema = requiredRuntimeSchema();
      const result = await runtimeWorkbench.records.submit(schema.moduleId, item.recordId, {
        recordVersion: item.recordVersion,
        reason: "页面提交",
      });
      raiseRuntimeError(result);
      await loadRuntimeRecords();
      await loadRuntimeRecordDetail(item.recordId);
      return runtimeResponse(result);
    });
  }

  async function loadRuntimeHistory(recordId: EntityId): Promise<void> {
    await execute("RUN-009", async () => {
      requireSystemContext();
      const schema = requiredRuntimeSchema();
      const result = await runtimeWorkbench.records.history(schema.moduleId, recordId, schema);
      raiseRuntimeError(result);
      appRuntimeState.recordHistory = toRuntimeHistory(result.data);
      return runtimeResponse(result);
    });
  }

  async function loadFlowTemplates(): Promise<void> {
    await execute("FLOW-001", async () => {
      requireSystemContext();
      await ensureModulesLoaded();
      const result = await flowWorkbench.templates.list({ pageNo: 1, pageSize: 50 });
      raisePageModelError(result);
      operationsState.flowTemplates = normalizePage((result.data ?? []) as FlowTemplateVO[] | PageResult<FlowTemplateVO>);
      return pageModelResponse(result);
    });
  }

  async function createFlowTemplate(): Promise<void> {
    const form = readForm();
    await execute("FLOW-002", async () => {
      requireSystemContext();
      const result = await flowWorkbench.templates.create({
        code: `flow_${p10Seed()}_${Date.now().toString().slice(-4)}`,
        name: required(form.flowTemplateName, "模板名称"),
        description: form.flowTemplateRemark?.trim() || undefined,
      });
      raisePageModelError(result);
      await loadFlowTemplates();
      return pageModelResponse(result);
    });
  }

  async function saveDefaultFlowGraph(item: FlowTemplateVO): Promise<void> {
    await execute("FLOW-003", async () => {
      requireSystemContext();
      const result = await flowWorkbench.templates.saveGraph(item.templateId, {
        nodes: [
          { nodeKey: "start", nodeName: "开始", nodeType: "START", sortOrder: 1 },
          { nodeKey: "approve", nodeName: "审批", nodeType: "APPROVAL", actorStrategy: "STARTER", approvalRequired: true, sortOrder: 2 },
          { nodeKey: "end", nodeName: "结束", nodeType: "END", sortOrder: 3 },
        ],
        lines: [
          { lineKey: "start-approve", fromNodeKey: "start", toNodeKey: "approve", conditionMode: "ALWAYS", sortOrder: 1 },
          { lineKey: "approve-end", fromNodeKey: "approve", toNodeKey: "end", conditionMode: "ALWAYS", sortOrder: 2 },
        ],
      });
      raisePageModelError(result);
      await loadFlowTemplates();
      return pageModelResponse(result);
    });
  }

  async function checkFlowTemplate(item: FlowTemplateVO): Promise<void> {
    await execute("FLOW-004", async () => {
      requireSystemContext();
      const result = await flowWorkbench.templates.publishCheck(item.templateId);
      raisePageModelError(result);
      ui.lastMessage = `发布检查：${result.data?.passed ? "通过" : "未通过"}，问题 ${result.data?.issues?.length ?? 0}`;
      return pageModelResponse(result);
    });
  }

  async function publishFlowTemplate(item: FlowTemplateVO): Promise<void> {
    await execute("FLOW-005", async () => {
      requireSystemContext();
      const result = await flowWorkbench.templates.publish(item.templateId);
      raisePageModelError(result);
      await loadFlowTemplates();
      return pageModelResponse(result);
    });
  }

  async function bindFlowTemplate(item: FlowTemplateVO): Promise<void> {
    await execute("FLOW-006", async () => {
      requireSystemContext();
      const moduleId = required(selectedModuleId(), "模块");
      const result = await flowWorkbench.templates.bindModule(moduleId, {
        templateId: item.templateId,
        templateVersionId: required(item.currentVersionId, "发布版本"),
        actionCode: "RECORD_SUBMIT",
        status: "ENABLED",
      });
      raisePageModelError(result);
      await loadFlowTemplates();
      return pageModelResponse(result);
    });
  }

  async function toggleFlowTemplate(item: FlowTemplateVO): Promise<void> {
    await execute("FLOW-021", async () => {
      requireSystemContext();
      const result = await flowWorkbench.templates.changeStatus(item.templateId, {
        targetStatus: item.status === "DISABLED" ? "DRAFT" : "DISABLED",
        versionNo: item.versionNo,
        reason: "流程模板页面操作",
      });
      raisePageModelError(result);
      await loadFlowTemplates();
      return pageModelResponse(result);
    });
  }

  async function loadFlowWorkbench(): Promise<void> {
    await execute("FLOW-007/FLOW-013/FLOW-014/FLOW-017", async () => {
      requireSystemContext();
      await ensureModulesLoaded();
      const query = {
        pageNo: 1,
        pageSize: 20,
        moduleId: readForm().selectedModuleId || selectedModuleId(),
        keyword: readForm().flowKeyword,
      };
      const [todo, cc, started, instances] = await Promise.all([
        flowWorkbench.workbench.todo(query),
        flowWorkbench.workbench.cc({ pageNo: 1, pageSize: 20 }),
        flowWorkbench.workbench.started({ pageNo: 1, pageSize: 20 }),
        flowWorkbench.workbench.instances({ pageNo: 1, pageSize: 20 }),
      ]);
      [todo, cc, started, instances].forEach(raisePageModelError);
      operationsState.flowTodos = todo.data;
      operationsState.flowCc = cc.data;
      operationsState.flowStarted = started.data;
      operationsState.flowInstances = instances.data;
      return { requestId: todo.state.requestId ?? instances.state.requestId };
    });
  }

  async function claimFlowTask(item: FlowTaskListItemVO): Promise<void> {
    await execute("FLOW-015", async () => {
      requireSystemContext();
      const result = await flowWorkbench.workbench.claim(item.taskId, flowWorkbench.createTaskVersionBody("FLOW-015", item.taskVersion));
      raisePageModelError(result);
      await loadFlowWorkbench();
      return pageModelResponse(result);
    });
  }

  async function approveFlowTask(item: FlowTaskListItemVO): Promise<void> {
    await handleFlowTask(item, "APPROVE", "同意");
  }

  async function rejectFlowTask(item: FlowTaskListItemVO): Promise<void> {
    await handleFlowTask(item, "REJECT", "退回");
  }

  async function handleFlowTask(item: FlowTaskListItemVO, action: "APPROVE" | "REJECT", comment: string): Promise<void> {
    await execute("FLOW-009", async () => {
      requireSystemContext();
      const result = await flowWorkbench.workbench.handleTask(item.taskId, flowWorkbench.createActionBody(action, item.taskVersion, { comment }));
      raisePageModelError(result);
      await loadFlowWorkbench();
      return pageModelResponse(result);
    });
  }

  async function loadFlowTaskDetail(taskId: EntityId): Promise<void> {
    await execute("FLOW-008", async () => {
      requireSystemContext();
      const result = await flowWorkbench.workbench.taskDetail(taskId);
      raisePageModelError(result);
      const detail = result.data as { task?: { nodeName?: string }; actions?: Array<{ label: string }> } | undefined;
      ui.lastMessage = `待办详情：${detail?.task?.nodeName ?? "-"}，动作 ${detail?.actions?.map((item) => item.label).join(", ") || "-"}`;
      return pageModelResponse(result);
    });
  }

  async function loadFlowInstanceDetail(instanceId: EntityId): Promise<void> {
    await execute("FLOW-011/FLOW-012/FLOW-018", async () => {
      requireSystemContext();
      const result = await flowWorkbench.workbench.instanceDetail(instanceId);
      raisePageModelError(result);
      const detail = result.data as { instance?: { recordTitle?: string; recordId?: EntityId }; history?: unknown[] } | undefined;
      ui.lastMessage = `实例详情：${detail?.instance?.recordTitle ?? detail?.instance?.recordId ?? "-"}，历史 ${detail?.history?.length ?? 0}`;
      return pageModelResponse(result);
    });
  }

  async function withdrawFlowInstance(item: FlowInstanceListItemVO): Promise<void> {
    await execute("FLOW-010", async () => {
      requireSystemContext();
      const result = await flowWorkbench.workbench.withdraw(item.instanceId, flowWorkbench.createWithdrawBody("页面撤回"));
      raisePageModelError(result);
      await loadFlowWorkbench();
      return pageModelResponse(result);
    });
  }

  async function loadFiles(): Promise<void> {
    await execute("FILE-002", async () => {
      requireSystemContext();
      const result = await fileCenter.files.list({
        pageNo: 1,
        pageSize: 50,
        bizType: readForm().fileBizType?.trim() || undefined,
      });
      raisePageModelError(result);
      operationsState.files = result.data;
      return pageModelResponse(result);
    });
  }

  async function uploadFileCenterFile(): Promise<void> {
    const form = readForm();
    const inputFile = container.querySelector<HTMLInputElement>('input[name="fileUpload"]')?.files?.[0];
    const file = inputFile ?? new File([form.fileFallbackText || "P11 文件中心测试内容"], `p11-${p10Seed()}.txt`, { type: "text/plain" });
    await execute("FILE-001", async () => {
      requireSystemContext();
      const result = await fileCenter.files.upload(fileCenter.createUploadDraft(file, form.fileFieldCode?.trim() || undefined, {
        fileName: file.name,
        bizType: form.fileBizType?.trim() || undefined,
      }));
      raisePageModelError(result);
      await loadFiles();
      return pageModelResponse(result);
    });
  }

  async function loadFileDetail(fileId: EntityId): Promise<void> {
    await execute("FILE-003", async () => {
      requireSystemContext();
      const result = await fileCenter.files.detail(fileId);
      raisePageModelError(result);
      operationsState.selectedFile = result.data;
      return pageModelResponse(result);
    });
  }

  async function previewFile(item: FileListItemVO): Promise<void> {
    await execute("FILE-004", async () => {
      requireSystemContext();
      const result = await fileCenter.files.preview(item);
      raisePageModelError(result);
      ui.lastMessage = `预览地址：${result.data?.downloadUrl ?? "后端未返回地址"}`;
      return pageModelResponse(result);
    });
  }

  async function downloadFile(item: FileListItemVO): Promise<void> {
    await execute("FILE-005", async () => {
      requireSystemContext();
      const result = await fileCenter.files.download(item);
      raisePageModelError(result);
      if (result.data?.downloadUrl) {
        window.open(result.data.downloadUrl, "_blank", "noopener");
      }
      return pageModelResponse(result);
    });
  }

  async function deleteFile(item: FileListItemVO): Promise<void> {
    await execute("FILE-006", async () => {
      requireSystemContext();
      const result = await fileCenter.files.delete(item);
      raisePageModelError(result);
      await loadFiles();
      return pageModelResponse(result);
    });
  }

  async function loadExports(): Promise<void> {
    await execute("EXP-001/EXP-005", async () => {
      requireSystemContext();
      await ensureModulesLoaded(true);
      preferPublishedModule();
      if (selectedModuleId()) {
        await refreshModuleFields(required(selectedModuleId(), "模块"));
      }
      const [templates, jobs] = await Promise.all([
        exportPage.templates.list(selectedModuleId()),
        exportPage.jobs.list({ pageNo: 1, pageSize: 50, moduleId: selectedModuleId(), keyword: readForm().keyword }),
      ]);
      raisePageModelError(templates);
      raisePageModelError(jobs);
      operationsState.exportTemplates = templates.data ?? [];
      operationsState.exportJobs = jobs.data;
      return { requestId: jobs.state.requestId ?? templates.state.requestId };
    });
  }

  async function createExportTemplate(): Promise<void> {
    const form = readForm();
    await execute("EXP-002", async () => {
      requireSystemContext();
      const moduleId = requiredPublishedModuleId(selectedModuleId() ?? form.selectedModuleId);
      if (appRuntimeState.fields.length === 0) {
        await refreshModuleFields(moduleId);
      }
      const result = await exportPage.templates.create({
        moduleId,
        templateCode: required(form.exportTemplateCode, "模板编码"),
        templateName: required(form.exportTemplateName, "模板名称"),
        exportFormat: "CSV",
        includeHistoryFlag: 0,
        fields: appRuntimeState.fields.slice(0, 10).map((field, index) => ({
          fieldId: field.fieldId,
          headerName: field.name,
          columnOrder: index + 1,
          plainRequiredFlag: 1,
        })),
      });
      raisePageModelError(result);
      await loadExports();
      return pageModelResponse(result);
    });
  }

  async function updateExportTemplate(item: ExportTemplateVO): Promise<void> {
    await execute("EXP-003", async () => {
      requireSystemContext();
      const result = await exportPage.templates.update(item.templateId, exportPage.toTemplateSaveBody(item));
      raisePageModelError(result);
      await loadExports();
      return pageModelResponse(result);
    });
  }

  async function createExportJob(): Promise<void> {
    const form = readForm();
    await execute("EXP-004", async () => {
      requireSystemContext();
      const moduleId = requiredPublishedModuleId(selectedModuleId() ?? form.selectedModuleId);
      const result = await exportPage.jobs.create(exportPage.createJobBody(moduleId, {
        templateId: operationsState.exportTemplates[0]?.templateId,
        fileName: form.exportFileName?.trim() || undefined,
      }));
      raisePageModelError(result);
      await loadExports();
      return pageModelResponse(result);
    });
  }

  async function loadExportJobDetail(jobId: EntityId): Promise<void> {
    await execute("EXP-006", async () => {
      requireSystemContext();
      const result = await exportPage.jobs.detail(jobId);
      raisePageModelError(result);
      const detail = result.data as { job?: { status?: string }; poll?: { shouldPoll?: boolean } } | undefined;
      ui.lastMessage = `导出任务：${detail?.job?.status ?? "-"}，轮询 ${detail?.poll?.shouldPoll ? "需要" : "不需要"}`;
      return pageModelResponse(result);
    });
  }

  async function retryExportJob(item: ExportJobListItemVO): Promise<void> {
    await execute("EXP-007", async () => {
      requireSystemContext();
      const result = await exportPage.jobs.retry(item, exportPage.createActionBody("EXP-007", "页面重试"));
      raisePageModelError(result);
      await loadExports();
      return pageModelResponse(result);
    });
  }

  async function cancelExportJob(item: ExportJobListItemVO): Promise<void> {
    await execute("EXP-008", async () => {
      requireSystemContext();
      const result = await exportPage.jobs.cancel(item, exportPage.createActionBody("EXP-008", "页面取消"));
      raisePageModelError(result);
      await loadExports();
      return pageModelResponse(result);
    });
  }

  async function loadOpenApiClients(): Promise<void> {
    await execute("OPM-001/OPM-008", async () => {
      requireSystemContext();
      const [clients, logs] = await Promise.all([
        openApiPage.loadClients({ pageNo: 1, pageSize: 50 }),
        openApiPage.loadAccessLogs({ pageNo: 1, pageSize: 20 }),
      ]);
      operationsState.openApi = {
        ...operationsState.openApi,
        clients: normalizePage(clients as unknown as OpenApiClientListItemVO[] | PageResult<OpenApiClientListItemVO>),
        accessLogs: logs,
      };
      return { requestId: operationsState.openApi.lastRequestId };
    });
  }

  async function loadOpenApiScopeCatalog(): Promise<void> {
    await execute("OPM-009", async () => {
      requireSystemContext();
      operationsState.openApi.scopeCatalog = await openApiPage.loadScopeCatalog();
      return { requestId: operationsState.openApi.lastRequestId };
    });
  }

  async function createOpenApiClient(): Promise<void> {
    const form = readForm();
    await execute("OPM-002", async () => {
      requireSystemContext();
      const client = await openApiPage.createClient({
        name: required(form.openApiClientName, "对外应用名称"),
        code: required(form.openApiClientCode, "对外应用编码"),
        tenantId: systemContextStore.toTenantHeader(),
        status: "ENABLED",
        scopes: parseOpenApiScopes(form.openApiScopes),
        ipWhitelist: parseOpenApiIpWhitelist(form.openApiIpWhitelist),
        rateLimitPolicy: [{
          windowSeconds: 60,
          maxRequests: optionalNumber(form.openApiRateLimit) ?? 60,
          status: "ENABLED",
        }],
        idempotencyKey: `OPM-002-${Date.now()}`,
      });
      operationsState.openApi.secretOnce = createSecretDisplayFromCreatedClient(client, `OPM-002-${Date.now()}`);
      await loadOpenApiClients();
      return { requestId: operationsState.openApi.secretOnce?.requestId };
    });
  }

  async function updateOpenApiClient(item: OpenApiClientListItemVO): Promise<void> {
    const form = readForm();
    await execute("OPM-003", async () => {
      requireSystemContext();
      await openApiPage.updateClient(item.clientId, {
        name: form.openApiClientName?.trim() || item.name,
        code: item.code,
        status: item.status,
        tenantId: item.tenantId,
        scopes: item.scopes,
        ipWhitelist: parseOpenApiIpWhitelist(form.openApiIpWhitelist),
        rateLimitPolicy: [{
          windowSeconds: 60,
          maxRequests: optionalNumber(form.openApiRateLimit) ?? item.rateLimitPolicy?.[0]?.maxRequests ?? 60,
          status: "ENABLED",
        }],
        expiresAt: item.expiresAt,
      });
      await loadOpenApiClients();
      return { requestId: `OPM-003-${Date.now()}` };
    });
  }

  async function toggleOpenApiClient(item: OpenApiClientListItemVO): Promise<void> {
    await execute("OPM-004", async () => {
      requireSystemContext();
      await openApiPage.changeClientStatus(item.clientId, {
        status: item.status === "ENABLED" ? "DISABLED" : "ENABLED",
      });
      await loadOpenApiClients();
      return { requestId: `OPM-004-${Date.now()}` };
    });
  }

  async function rotateOpenApiCredential(item: OpenApiClientListItemVO): Promise<void> {
    await execute("OPM-005", async () => {
      requireSystemContext();
      operationsState.openApi.secretOnce = await openApiPage.rotateCredential(item.clientId, `OPM-005-${Date.now()}`);
      await loadOpenApiClients();
      return { requestId: operationsState.openApi.secretOnce?.requestId };
    });
  }

  async function updateOpenApiScopes(item: OpenApiClientListItemVO): Promise<void> {
    const form = readForm();
    await execute("OPM-006", async () => {
      requireSystemContext();
      await openApiPage.updateScopes(item.clientId, {
        scopes: parseOpenApiScopes(form.openApiScopes),
      });
      await loadOpenApiClients();
      return { requestId: `OPM-006-${Date.now()}` };
    });
  }

  async function updateOpenApiIpWhitelist(item: OpenApiClientListItemVO): Promise<void> {
    const form = readForm();
    await execute("OPM-007", async () => {
      requireSystemContext();
      await openApiPage.updateIpWhitelist(item.clientId, {
        ipWhitelist: parseOpenApiIpWhitelist(form.openApiIpWhitelist),
      });
      await loadOpenApiClients();
      return { requestId: `OPM-007-${Date.now()}` };
    });
  }

  function consumeOpenApiSecret(): void {
    operationsState.openApi = openApiPage.consumeSecretOnce(operationsState.openApi);
    render();
  }

  async function loadAuditLogs(platform: boolean): Promise<void> {
    await execute(platform ? "AUD-006" : "AUD-001", async () => {
      if (!platform) {
        requireSystemContext();
      }
      const form = readForm();
      const state = platform ? operationsState.platformAudit : operationsState.audit;
      const kind = (platform ? form.platformAuditKind : form.auditKind) as AuditLogKind | undefined;
      const activeKind = kind || state.activeKind;
      const logs = await auditPage.queryLogs(activeKind, {
        pageNo: 1,
        pageSize: 50,
        requestId: form.auditRequestId?.trim() || undefined,
        bizType: form.auditBizType?.trim() || undefined,
      });
      const next = { ...state, activeKind, logs };
      if (platform) {
        operationsState.platformAudit = next;
      } else {
        operationsState.audit = next;
      }
      return { requestId: `AUD-${Date.now()}` };
    });
  }

  async function loadAuditDetail(platform: boolean, item: AuditLogListItemVO): Promise<void> {
    await execute(platform ? "AUD-008" : "AUD-007", async () => {
      if (!platform) {
        requireSystemContext();
      }
      const state = platform ? operationsState.platformAudit : operationsState.audit;
      const detail = platform
        ? await auditPage.loadPlatformDetail(item.logId)
        : await auditPage.loadDetail(state.activeKind as Exclude<AuditLogKind, "platformOperation">, item.logId);
      if (platform) {
        operationsState.platformAudit = { ...state, selectedDetail: detail };
      } else {
        operationsState.audit = { ...state, selectedDetail: detail };
      }
      return { requestId: detail.requestId };
    });
  }

  function markAuditRequestId(platform: boolean, requestId: string): void {
    if (platform) {
      operationsState.platformAudit = auditPage.markRequestIdCopied(operationsState.platformAudit, requestId);
    } else {
      operationsState.audit = auditPage.markRequestIdCopied(operationsState.audit, requestId);
    }
    ui.lastMessage = `已标记请求号：${requestId}`;
    render();
  }

  async function loadOpsHealth(): Promise<void> {
    await execute("OPS-001/OPS-002/OPS-003/OPS-004/OPS-006", async () => {
      const [health, configCheck, version, migration, components] = await Promise.all([
        opsPage.loadHealth(),
        opsPage.loadConfigCheck(),
        opsPage.loadVersion(),
        opsPage.loadMigrationStatus(),
        opsPage.loadComponents(),
      ]);
      operationsState.ops = opsPage.summarize({
        ...operationsState.ops,
        health,
        configCheck,
        version,
        migration,
        components,
      });
      return { requestId: operationsState.ops.lastRequestId };
    });
  }

  async function enterSystem(systemId: string): Promise<void> {
    await execute("SYS-001", async () => {
      const result = await enterSystemContext(systemId);
      navigate(`/systems/${systemId}/overview`);
      return result;
    });
  }

  async function ensureRouteSystemContext(route: AppRouteRecord): Promise<boolean> {
    const systemId = routeSystemId();
    if (!systemId) {
      return false;
    }
    const state = systemContextStore.getState();
    if (state.status === "ready" && state.current?.system.systemId === systemId) {
      return true;
    }
    await execute("SYS-001", async () => enterSystemContext(systemId));
    return systemContextStore.getState().status === "ready";
  }

  async function enterSystemContext(systemId: string): Promise<{ requestId?: string }> {
    systemContextStore.beginEnter(systemId);
    permissionStore.beginRefresh();
    const response = await apiClient.call<SystemEnterVO>("SYS-001", {
      pathParams: { systemId },
      body: {},
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
    resetAppRuntimeStateForSystem(entered.systemId);
    return { requestId: response.requestId };
  }

  function resetAppRuntimeStateForSystem(systemId: EntityId): void {
    if (appRuntimeState.loadedSystemId === systemId) {
      return;
    }
    appRuntimeState.loadedSystemId = systemId;
    appRuntimeState.apps = [];
    appRuntimeState.selectedAppId = undefined;
    appRuntimeState.modules = [];
    appRuntimeState.selectedModuleId = undefined;
    appRuntimeState.fields = [];
    appRuntimeState.fieldTypes = [];
    appRuntimeState.listSchema = undefined;
    appRuntimeState.formSchema = undefined;
    appRuntimeState.detailSchema = undefined;
    appRuntimeState.publishCheck = undefined;
    appRuntimeState.publishResult = undefined;
    appRuntimeState.runtimeMenus = [];
    appRuntimeState.runtimeSchema = undefined;
    appRuntimeState.recordPage = undefined;
    appRuntimeState.selectedRecord = undefined;
    appRuntimeState.recordHistory = [];
    appRuntimeState.recordRelations = [];
    ui.settings.appId = "";
    ui.settings.moduleId = "";
  }

  async function execute<T>(scope: string, action: () => Promise<T>): Promise<void> {
    ui.busy = true;
    ui.lastError = undefined;
    ui.lastRequestId = `${scope}-${Date.now()}`;
    render();
    try {
      const result = await action();
      const response = result as { requestId?: string; code?: string; message?: string };
      ui.lastRequestId = response.requestId ?? ui.lastRequestId;
      ui.lastMessage = formatSuccessMessage(response);
    } catch (error) {
      ui.lastError = error instanceof Error ? error.message : String(error);
    } finally {
      ui.busy = false;
      render();
    }
  }

  async function runRequestedSmoke(): Promise<void> {
    if (!isDevelopmentMode()) {
      return;
    }
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
      return node("pre", { className: "message muted", text: "正在处理，请稍候。" });
    }
    if (ui.lastError) {
      return node("div", { className: "message error", text: ui.lastError });
    }
    return node("div", { className: "message" }, [
      node("span", { text: ui.lastMessage ?? "暂无操作结果。" }),
      ui.lastMessage && ui.lastRequestId ? node("small", { text: `请求号：${ui.lastRequestId}` }) : undefined,
    ]);
  }

  function formatSuccessMessage(response: { message?: string }): string {
    const message = response.message?.trim();
    if (message && message !== "COMMON_OK") {
      return message;
    }
    return "操作已完成。";
  }

  function isDevelopmentMode(): boolean {
    const meta = import.meta as unknown as { env?: Record<string, string | boolean | undefined> };
    return meta.env?.DEV === true || meta.env?.MODE === "development";
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

  function multiSelectField(label: string, name: string, values: string[], options: [string, string][]): HTMLElement {
    return node("label", { className: "field wide-field" }, [
      node("span", { text: label }),
      node("select", { name, multiple: true, size: Math.min(Math.max(options.length, 3), 8) }, options.map(([optionValue, optionLabel]) =>
        node("option", { value: optionValue, selected: values.includes(optionValue), text: optionLabel }),
      )),
      options.length ? undefined : node("small", { text: "暂无可选项，请先加载目录或创建角色。" }),
    ]);
  }

  function button(label: string, variant: "primary" | "secondary", onClick: () => void | Promise<void>, disabled = false): HTMLElement {
    return node("button", { className: `btn ${variant}`, disabled, onClick, text: label });
  }

  function tabButton(label: string, active: boolean, onClick: () => void): HTMLElement {
    return node("button", { className: `tab${active ? " active" : ""}`, onClick, text: label });
  }

  function emptyPanel(title: string, description: string): HTMLElement {
    return node("div", { className: "empty-state" }, [
      node("strong", { text: title }),
      node("span", { text: description }),
    ]);
  }

  function noop(): void {
    // 空操作用于禁用按钮占位，避免给用户暴露弹窗式临时交互。
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
      values[item.name] = item.multiple
        ? Array.from(item.selectedOptions).map((option) => option.value).join(",")
        : item.value;
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

  async function refreshAppsConfig(keyword?: string) {
    const result = await moduleConfigPage.apps.load({ pageNo: 1, pageSize: 50, keyword });
    raisePageModelError(result);
    appRuntimeState.apps = result.data ?? [];
    if (appRuntimeState.selectedAppId && !appRuntimeState.apps.some((item) => item.appId === appRuntimeState.selectedAppId)) {
      appRuntimeState.selectedAppId = undefined;
      appRuntimeState.modules = [];
      appRuntimeState.selectedModuleId = undefined;
      appRuntimeState.fields = [];
      ui.settings.appId = "";
      ui.settings.moduleId = "";
    }
    if (!appRuntimeState.selectedAppId && appRuntimeState.apps[0]) {
      appRuntimeState.selectedAppId = appRuntimeState.apps[0].appId;
      ui.settings.appId = appRuntimeState.apps[0].appId;
    }
    return result;
  }

  async function refreshModulesConfig(appId: EntityId, keyword?: string) {
    const result = await moduleConfigPage.modules.load(appId, { pageNo: 1, pageSize: 50, keyword });
    raisePageModelError(result);
    appRuntimeState.modules = result.data ?? [];
    if (appRuntimeState.selectedModuleId && !appRuntimeState.modules.some((item) => item.moduleId === appRuntimeState.selectedModuleId)) {
      appRuntimeState.selectedModuleId = undefined;
      appRuntimeState.fields = [];
      ui.settings.moduleId = "";
    }
    if (!appRuntimeState.selectedModuleId && appRuntimeState.modules[0]) {
      appRuntimeState.selectedModuleId = appRuntimeState.modules[0].moduleId;
      ui.settings.moduleId = appRuntimeState.modules[0].moduleId;
    }
    return result;
  }

  async function refreshModuleFields(moduleId: EntityId) {
    const [fields, fieldTypes] = await Promise.all([
      moduleConfigPage.fields.load(moduleId),
      moduleConfigPage.fields.fieldTypes(),
    ]);
    raisePageModelError(fields);
    raisePageModelError(fieldTypes);
    appRuntimeState.fields = fields.data ?? [];
    appRuntimeState.fieldTypes = fieldTypes.data ?? [];
    return fields;
  }

  async function ensureAppsLoaded(force = false): Promise<void> {
    if (force || appRuntimeState.apps.length === 0) {
      await refreshAppsConfig();
    }
  }

  async function ensureModulesLoaded(force = false): Promise<void> {
    await ensureAppsLoaded(force);
    if ((force || appRuntimeState.modules.length === 0) && selectedAppId()) {
      await refreshModulesConfig(required(selectedAppId(), "业务应用"));
    }
  }

  function readRouteSelection(): void {
    const path = getCurrentPath();
    const appMatch = path.match(/\/apps\/([^/]+)\/modules/);
    if (appMatch?.[1] && appMatch[1] !== "current") {
      appRuntimeState.selectedAppId = decodeURIComponent(appMatch[1]);
      ui.settings.appId = appRuntimeState.selectedAppId;
    }
    const runtimeModuleMatch = path.match(/\/runtime\/modules\/([^/]+)/);
    const configModuleMatch = path.match(/\/modules\/([^/]+)(?:\/fields|\/ui|$)/);
    const moduleId = runtimeModuleMatch?.[1] ?? configModuleMatch?.[1];
    if (moduleId && moduleId !== "current") {
      appRuntimeState.selectedModuleId = decodeURIComponent(moduleId);
      ui.settings.moduleId = appRuntimeState.selectedModuleId;
    }
  }

  function routeSystemId(): string | undefined {
    const match = getCurrentPath().match(/\/systems\/([^/]+)/);
    return match?.[1] && match[1] !== "preview-system" ? decodeURIComponent(match[1]) : undefined;
  }

  function selectedAppId(): EntityId | undefined {
    return appRuntimeState.selectedAppId;
  }

  function selectedModuleId(): EntityId | undefined {
    return appRuntimeState.selectedModuleId;
  }

  function selectedApp(): AppListItemVO | undefined {
    return appRuntimeState.apps.find((item) => item.appId === appRuntimeState.selectedAppId);
  }

  function selectedModule(): ModuleListItemVO | undefined {
    return appRuntimeState.modules.find((item) => item.moduleId === appRuntimeState.selectedModuleId);
  }

  function requiredSelectedModule(): ModuleListItemVO {
    const module = selectedModule();
    if (!module) {
      throw new Error("请先选择模块");
    }
    return module;
  }

  function moduleIsPublished(module: ModuleListItemVO): boolean {
    return module.status === "PUBLISHED" || Boolean(module.publishedVersion);
  }

  function preferPublishedModule(): void {
    const current = selectedModule();
    if (current && moduleIsPublished(current)) {
      return;
    }
    const published = appRuntimeState.modules.find(moduleIsPublished);
    if (published) {
      appRuntimeState.selectedModuleId = published.moduleId;
      ui.settings.moduleId = published.moduleId;
      appRuntimeState.fields = [];
    }
  }

  function requiredPublishedModuleId(moduleId?: EntityId): EntityId {
    const module = appRuntimeState.modules.find((item) => item.moduleId === moduleId);
    if (!module) {
      throw new Error("请先选择模块");
    }
    if (!moduleIsPublished(module)) {
      throw new Error("当前模块尚未发布，请先发布模块后再操作");
    }
    return module.moduleId;
  }

  function requiredRuntimeSchema(): RuntimeModuleSchemaVO {
    if (!appRuntimeState.runtimeSchema) {
      throw new Error("请先打开业务表单");
    }
    return appRuntimeState.runtimeSchema;
  }

  function currentSystemId(): EntityId {
    return required(systemContextStore.getState().current?.system.systemId ?? ui.settings.systemId, "系统");
  }

  function appSelectOptions(): [string, string][] {
    return [
      ["", "请选择业务应用"],
      ...appRuntimeState.apps.map((item) => [item.appId, `${item.name} / ${item.code}`] as [string, string]),
    ];
  }

  function moduleSelectOptions(): [string, string][] {
    return [
      ["", "请选择模块"],
      ...appRuntimeState.modules.map((item) => [item.moduleId, `${item.name} / ${item.code}`] as [string, string]),
    ];
  }

  function fieldTypeOptions(): [string, string][] {
    const types = appRuntimeState.fieldTypes.length > 0
      ? appRuntimeState.fieldTypes.map((item) => ({ code: String(item.code), name: item.name }))
      : moduleConfigPage.fieldDesigner.availableFieldTypes.map((item) => ({ code: item, name: item }));
    return types.map((item) => [item.code, item.name || item.code]);
  }

  function optionSupported(fieldType: string): boolean {
    return ["SELECT", "MULTI_SELECT", "RADIO", "CHECKBOX", "DICT"].includes(fieldType);
  }

  function parseFieldOptions(value?: string) {
    const text = value?.trim();
    if (!text) {
      return undefined;
    }
    return text.split(",").map((item, index) => {
      const [code, label] = item.split(":").map((part) => part.trim());
      return {
        code: code || `OPT_${index + 1}`,
        label: label || code || `选项${index + 1}`,
        value: code || `OPT_${index + 1}`,
        enabled: true,
        sortOrder: index + 1,
      };
    });
  }

  function activeFieldRefs() {
    return appRuntimeState.fields
      .filter((field) => field.status !== "DELETED")
      .map((field, index) => ({
        fieldCode: field.code,
        fieldId: field.fieldId,
        label: field.name,
        visible: true,
        required: field.required,
        width: index === 0 ? 220 : 160,
      }));
  }

  function schemaSummary(schema?: PageSchemaVO): string {
    if (!schema) {
      return "-";
    }
    return formatValue(schema.schema).slice(0, 180);
  }

  function flattenRuntimeMenus(menus: RuntimeMenuVO[] = []): RuntimeMenuVO[] {
    return menus.flatMap((menu) => [menu, ...flattenRuntimeMenus(menu.children ?? [])]);
  }

  function renderRuntimeFieldInput(label: string, name: string, value: string): HTMLElement {
    return input(label, name, value);
  }

  function p10Seed(): string {
    return new Date().toISOString().replace(/\D/g, "").slice(8, 14);
  }

  function runtimeFieldDefaultValue(field: RuntimeModuleSchemaVO["fieldDefinitions"][number]): string {
    const defaultValue = field.defaultValue;
    if (defaultValue === undefined || defaultValue === null) {
      return "";
    }
    if (Array.isArray(defaultValue)) {
      return defaultValue.map(String).join(",");
    }
    if (typeof defaultValue === "object") {
      return "";
    }
    return String(defaultValue);
  }

  function runtimeValues(form: Record<string, string>, schema: RuntimeModuleSchemaVO): Record<string, unknown> {
    return Object.fromEntries(schema.fieldDefinitions
      .filter((field) => field.status !== "DELETED")
      .map((field) => {
        const fieldCode = runtimeFieldCode(field);
        const raw = form[`runtime_${fieldCode}`] ?? "";
        if (["NUMBER", "DECIMAL", "MONEY"].includes(field.fieldType)) {
          return [fieldCode, raw === "" ? null : Number(raw)];
        }
        if (["MULTI_SELECT", "CHECKBOX", "TAG"].includes(field.fieldType)) {
          return [fieldCode, splitValues(raw)];
        }
        if (String(field.fieldType) === "SWITCH" || String(field.fieldType) === "BOOLEAN") {
          return [fieldCode, raw === "true" || raw === "是" || raw === "1"];
        }
        return [fieldCode, raw];
      }));
  }

  function runtimeFieldCode(field: RuntimeModuleSchemaVO["fieldDefinitions"][number]): string {
    const source = field as RuntimeModuleSchemaVO["fieldDefinitions"][number] & { code?: string };
    return field.fieldCode || source.code || "";
  }

  function runtimeFieldName(field: RuntimeModuleSchemaVO["fieldDefinitions"][number]): string {
    const source = field as RuntimeModuleSchemaVO["fieldDefinitions"][number] & { name?: string; code?: string };
    return field.fieldName || source.name || field.fieldCode || source.code || "未命名字段";
  }

  function formatFileSize(size: number): string {
    if (size < 1024) {
      return `${size} B`;
    }
    if (size < 1024 * 1024) {
      return `${(size / 1024).toFixed(1)} KB`;
    }
    return `${(size / 1024 / 1024).toFixed(1)} MB`;
  }

  function parseOpenApiScopes(value?: string) {
    const scopes = splitValues(value ?? "");
    return scopes.length > 0
      ? scopes.map((scopeCode) => ({
          scopeCode,
          actions: scopeCode.includes("write") ? ["CREATE", "UPDATE", "DELETE"] : ["READ"],
          readableFieldCodes: [],
          writableFieldCodes: scopeCode.includes("write") ? [] : undefined,
        }))
      : [
          {
            scopeCode: "record:read",
            actions: ["READ"],
            readableFieldCodes: [],
          },
        ];
  }

  function parseOpenApiIpWhitelist(value?: string) {
    return splitValues(value ?? "").map((ipRule) => ({
      ipRule,
      ruleType: ipRule.includes("/") ? "CIDR" as const : "IP" as const,
      status: "ENABLED" as const,
      description: "页面维护",
    }));
  }

  function toRuntimeRecordPage(value: unknown): PageResult<RecordListItemVO> | undefined {
    if (!value) {
      return undefined;
    }
    if (isRecord(value) && isRecord(value.page)) {
      return value.page as unknown as PageResult<RecordListItemVO>;
    }
    if (isRecord(value) && Array.isArray(value.records)) {
      return value as unknown as PageResult<RecordListItemVO>;
    }
    return undefined;
  }

  function toRuntimeRecordDetail(value: unknown): RecordDetailVO | undefined {
    if (!value) {
      return undefined;
    }
    if (isRecord(value) && isRecord(value.record)) {
      return value.record as unknown as RecordDetailVO;
    }
    if (isRecord(value) && value.recordId) {
      return value as unknown as RecordDetailVO;
    }
    return undefined;
  }

  function toRuntimeHistory(value: unknown): RuntimeRecordHistoryVO[] {
    if (Array.isArray(value)) {
      return value as RuntimeRecordHistoryVO[];
    }
    if (isRecord(value) && Array.isArray(value.history)) {
      return value.history as RuntimeRecordHistoryVO[];
    }
    return [];
  }

  function pageModelResponse(result: { state: { requestId?: string }; data?: unknown }) {
    return {
      requestId: result.state.requestId,
      data: result.data,
      code: "COMMON_OK",
    };
  }

  function runtimeResponse(result: { state: { requestId?: string }; data?: unknown }) {
    return {
      requestId: result.state.requestId,
      data: result.data,
      code: "COMMON_OK",
    };
  }

  function raisePageModelError(result: { state: { errorMessage?: string; requestId?: string } }): void {
    if (result.state.errorMessage) {
      throw new Error(`${result.state.errorMessage}${result.state.requestId ? `，请求号 ${result.state.requestId}` : ""}`);
    }
  }

  function raiseRuntimeError(result: { state: { errorMessage?: string; requestId?: string } }): void {
    if (result.state.errorMessage) {
      throw new Error(`${result.state.errorMessage}${result.state.requestId ? `，请求号 ${result.state.requestId}` : ""}`);
    }
  }

  function formatValue(value: unknown): string {
    if (value === undefined || value === null || value === "") {
      return "-";
    }
    if (typeof value === "string") {
      return value;
    }
    return JSON.stringify(value);
  }

  function permissionCatalogLabel(catalog?: PermissionCatalogVO): string {
    if (!catalog) {
      return "未加载";
    }
    return `${catalog.menus.length} 菜单 / ${catalog.operations.length} 操作`;
  }

  function platformRoleOptions(): [string, string][] {
    return platformState.roles.records.map((item) => [item.roleId, `${item.name}（${item.code}）`]);
  }

  function catalogOperationOptions(catalog?: PermissionCatalogVO): [string, string][] {
    return ((catalog?.operations ?? []) as unknown[])
      .map((item) => {
        if (typeof item === "string") {
          return [item, item] as [string, string];
        }
        if (isRecord(item)) {
          const code = stringValue(item.operationCode) ?? stringValue(item.code) ?? stringValue(item.permissionCode) ?? stringValue(item.value);
          const label = stringValue(item.operationName) ?? stringValue(item.name) ?? stringValue(item.label) ?? code;
          return code ? [code, label ?? code] as [string, string] : undefined;
        }
        return undefined;
      })
      .filter((item): item is [string, string] => Boolean(item));
  }

  function catalogMenuOptions(catalog?: PermissionCatalogVO): [string, string][] {
    return (catalog?.menus ?? []).flatMap((item) => menuOptionFromJson(item));
  }

  function menuOptionFromJson(value: JsonValue): [string, string][] {
    if (!isRecord(value)) {
      return [];
    }
    const id = stringValue(value.menuId) ?? stringValue(value.id) ?? stringValue(value.code) ?? stringValue(value.menuCode);
    const label = stringValue(value.menuName) ?? stringValue(value.name) ?? stringValue(value.title) ?? id;
    const current: [string, string][] = id ? [[id, label ?? id]] : [];
    const children = Array.isArray(value.children)
      ? value.children.flatMap((item) => menuOptionFromJson(item as JsonValue))
      : [];
    return [...current, ...children];
  }

  function stringValue(value: unknown): string | undefined {
    if (typeof value === "string" && value.trim()) {
      return value;
    }
    if (typeof value === "number") {
      return String(value);
    }
    return undefined;
  }

  function memberDeptLabel(item: MemberListVO): string {
    const candidate = item as MemberListVO & { deptIds?: EntityId[] };
    return item.deptPath ?? candidate.deptIds?.join(", ") ?? "-";
  }

  function departmentName(item: DepartmentNodeVO): string {
    return item.name ?? item.deptName ?? "-";
  }

  function departmentCode(item: DepartmentNodeVO): string {
    return item.code ?? item.deptCode ?? "-";
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
      appId: appRuntimeState.selectedAppId ?? ui.settings.appId,
      moduleId: appRuntimeState.selectedModuleId ?? ui.settings.moduleId,
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
      "apps.list",
      "modules.list",
      "modules.fields",
      "modules.ui",
      "runtime.home",
      "runtime.module",
      "flow.templates",
      "flow.workbench",
      "files.center",
      "exports.jobs",
      "openapi.clients",
      "audit.system",
      "audit.platform",
      "ops.health",
    ]);
    const routeLoadKey = `${route.name}:${getCurrentPath()}:${systemContextStore.getState().current?.member.memberId ?? ""}`;
    if (!autoLoadNames.has(route.name) || autoLoadedRoutes.has(routeLoadKey) || ui.busy) {
      return;
    }
    autoLoadedRoutes.add(routeLoadKey);
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
