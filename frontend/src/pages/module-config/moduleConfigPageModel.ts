import type {
  ApiClient,
  ApiContext,
  ApiEndpointId,
  AppStatus,
  AvailableAction,
  DynamicFieldType,
  EntityId,
  FieldDefinitionVO,
  FieldPermission,
  FieldStatus,
  IsoDateTimeString,
  JsonValue,
  ModuleStatus,
  PageQuery,
  PageResult,
  PermissionHint,
  RuntimeModuleSchemaVO,
  UniqueConstraintDTO,
  VersionStatus,
} from "../../api";
import { API_ENDPOINTS, DYNAMIC_FIELD_TYPES } from "../../api";
import type { AuthStore, ErrorStore, PermissionDecision, PermissionStore, SystemContextStore } from "../../stores";

export interface ModuleConfigPageDeps {
  apiClient: ApiClient;
  auth: AuthStore;
  systemContext: SystemContextStore;
  permission: PermissionStore;
  error: ErrorStore;
}

export interface PageRequestState {
  loading: boolean;
  empty: boolean;
  requestId?: string;
  errorMessage?: string;
  retryable: boolean;
}

export interface PageMutationResult<TData> {
  data?: TData;
  state: PageRequestState;
  idempotencyReplay?: boolean;
}

export interface PageActionState extends PermissionDecision {
  actionCode: string;
  apiId: ApiEndpointId;
  label: string;
}

export interface AppListItemVO {
  appId: EntityId;
  name: string;
  code: string;
  icon?: string;
  description?: string;
  status: AppStatus;
  moduleCount?: number;
  version?: number;
  createdAt?: IsoDateTimeString;
  updatedAt?: IsoDateTimeString;
  availableActions?: AvailableAction[];
}

export interface AppDetailVO extends AppListItemVO {
  latestVersionId?: EntityId;
  publishedVersionId?: EntityId;
  versionStatus?: VersionStatus;
}

export interface AppSaveBO {
  name: string;
  code: string;
  icon?: string;
  description?: string;
  status?: AppStatus;
}

export interface AppUpdateBO extends AppSaveBO {
  version?: number;
}

export interface AppStatusBO {
  targetStatus: AppStatus;
  reason?: string;
  version?: number;
}

export interface ModuleListItemVO {
  moduleId: EntityId;
  appId: EntityId;
  name: string;
  code: string;
  description?: string;
  status: ModuleStatus;
  versionStatus?: VersionStatus;
  fieldCount?: number;
  publishedVersionId?: EntityId;
  flowBindingId?: EntityId;
  version?: number;
  updatedAt?: IsoDateTimeString;
  availableActions?: AvailableAction[];
}

export interface ModuleDetailVO extends ModuleListItemVO {
  listSchemaVersion?: number;
  formSchemaVersion?: number;
  detailSchemaVersion?: number;
  menuCode?: string;
  actionCodes?: string[];
}

export interface ModuleSaveBO {
  name: string;
  code: string;
  description?: string;
}

export interface ModuleUpdateBO extends ModuleSaveBO {
  version?: number;
}

export interface ModuleStatusBO {
  targetStatus: Exclude<ModuleStatus, "PUBLISHED">;
  reason?: string;
  version?: number;
}

export interface FieldSaveBO {
  name: string;
  code: string;
  fieldType: DynamicFieldType;
  required?: boolean;
  unique?: boolean;
  defaultValue?: JsonValue;
  options?: JsonValue;
  dictTypeId?: EntityId;
  relationConfig?: JsonValue;
  subTableConfig?: JsonValue;
  serialConfig?: JsonValue;
  fieldPermissions?: FieldPermission[];
  uniqueConstraints?: UniqueConstraintDTO[];
}

export interface FieldUpdateBO extends FieldSaveBO {
  version?: number;
}

export interface FieldStatusBO {
  targetStatus: FieldStatus;
  reason?: string;
  version?: number;
}

export type SchemaViewType = "list" | "form" | "detail";

export interface ListViewSchemaBO {
  columns: SchemaFieldRef[];
  filters?: SchemaFieldRef[];
  sorters?: SchemaSorterRef[];
  fieldVisibility?: FieldVisibilityRule[];
  schemaVersion?: number;
}

export interface FormSchemaBO {
  formSections: FormSectionConfig[];
  fieldWritable?: FieldWritableRule[];
  schemaVersion?: number;
}

export interface DetailSchemaBO {
  detailBlocks: DetailBlockConfig[];
  fieldVisibility?: FieldVisibilityRule[];
  schemaVersion?: number;
}

export interface ModuleMenuBO {
  menuCode: string;
  menuName: string;
  menuParentId?: EntityId;
  visible?: boolean;
  sortOrder?: number;
  schemaVersion?: number;
}

export interface ModuleActionBO {
  actionCodes: string[];
  actions: ModuleActionConfig[];
  schemaVersion?: number;
  idempotencyKey?: string;
}

export interface SchemaFieldRef {
  fieldCode: string;
  fieldId?: EntityId;
  label?: string;
  visible?: boolean;
  required?: boolean;
  width?: number;
  component?: string;
  defaultValue?: JsonValue;
}

export interface SchemaSorterRef {
  fieldCode: string;
  direction: "ASC" | "DESC";
}

export interface FieldVisibilityRule {
  fieldCode: string;
  visible: boolean;
  reason?: string;
}

export interface FieldWritableRule {
  fieldCode: string;
  writable: boolean;
  readonlyReason?: string;
}

export interface FormSectionConfig {
  sectionCode: string;
  title: string;
  fields: SchemaFieldRef[];
}

export interface DetailBlockConfig {
  blockCode: string;
  title: string;
  fields: SchemaFieldRef[];
}

export interface ModuleActionConfig {
  actionCode: string;
  label: string;
  visible: boolean;
  enabled: boolean;
  requiredPermission?: string;
  confirmRequired?: boolean;
  danger?: boolean;
}

export interface UiSchemaVO {
  schemaVersion: number;
  draftVersion?: number;
  publishedVersion?: number;
  listSchema?: ListViewSchemaBO;
  formSchema?: FormSchemaBO;
  detailSchema?: DetailSchemaBO;
  permissionHints?: PermissionHint[];
  menu?: ModuleMenuBO;
  actions?: ModuleActionConfig[];
}

export interface PublishCheckIssueVO {
  issueId: string;
  targetType: "MODULE" | "FIELD" | "LIST_SCHEMA" | "FORM_SCHEMA" | "DETAIL_SCHEMA" | "MENU" | "ACTION";
  targetId?: EntityId;
  fieldCode?: string;
  schemaPath?: string;
  code: string;
  message: string;
  blocking: boolean;
}

export interface PublishCheckResultVO {
  pass: boolean;
  moduleId: EntityId;
  draftVersion?: number;
  issues: PublishCheckIssueVO[];
  requestId?: string;
}

export interface ModulePublishBO {
  draftVersion?: number;
  publishRemark?: string;
}

export interface ModulePublishResultVO {
  moduleId: EntityId;
  publishedVersionId: EntityId;
  versionStatus: VersionStatus;
  moduleStatus: ModuleStatus;
  publishedAt: IsoDateTimeString;
  runtimeMenuRefreshed?: boolean;
  runtimeSchemaRefreshed?: boolean;
}

export interface RuntimeRefreshState {
  menusRefreshed: boolean;
  schemaRefreshed: boolean;
  menuRequestId?: string;
  schemaRequestId?: string;
  schema?: RuntimeModuleSchemaVO;
}

export interface FieldDesignerState {
  availableFieldTypes: readonly DynamicFieldType[];
  uniqueSupportedTypes: readonly DynamicFieldType[];
  uniqueUnsupportedTypes: readonly DynamicFieldType[];
}

export interface SchemaValidationResult {
  valid: boolean;
  missingFieldCodes: string[];
  duplicateFieldCodes: string[];
}

const MODULE_CONFIG_CONTEXT = { system: true, tenant: true, member: true };

const MODULE_CONFIG_API_IDS = [
  "APP-001",
  "APP-002",
  "APP-003",
  "APP-004",
  "APP-005",
  "APP-006",
  "APP-007",
  "MOD-001",
  "MOD-002",
  "MOD-003",
  "MOD-004",
  "MOD-005",
  "MOD-006",
  "MOD-007",
  "FIELD-001",
  "FIELD-002",
  "FIELD-003",
  "FIELD-004",
  "FIELD-005",
  "UI-001",
  "UI-002",
  "UI-003",
  "UI-004",
  "UI-005",
  "UI-006",
  "UI-007",
  "UI-008",
  "RUN-001",
  "RUN-002",
] as const satisfies readonly ApiEndpointId[];

const UNIQUE_SUPPORTED_FIELD_TYPES = [
  "TEXT",
  "NUMBER",
  "MONEY",
  "DATE",
  "DATETIME",
  "SELECT",
  "SWITCH",
  "MEMBER",
  "DEPT",
  "AUTO_NO",
  "RELATION",
] as const satisfies readonly DynamicFieldType[];

const UNIQUE_UNSUPPORTED_FIELD_TYPES = DYNAMIC_FIELD_TYPES.filter(
  (item) => !(UNIQUE_SUPPORTED_FIELD_TYPES as readonly DynamicFieldType[]).includes(item),
);

export function createModuleConfigPageModel(deps: ModuleConfigPageDeps) {
  return {
    routeNames: ["apps.list", "modules.list", "modules.fields", "modules.ui"] as const,
    apiIds: MODULE_CONFIG_API_IDS,
    fieldDesigner: createFieldDesignerState(),
    permissions: () => createPermissionState(deps.permission),
    apps: {
      load: (query: PageQuery = {}) => callPage<AppListItemVO>(deps, "APP-001", { query }),
      create: (body: AppSaveBO, idempotencyKey = createIdempotencyKey("APP-002")) =>
        mutate<AppDetailVO, AppSaveBO>(deps, "APP-002", { body, idempotencyKey }),
      detail: (appId: EntityId) => callData<AppDetailVO>(deps, "APP-003", { pathParams: { appId } }),
      update: (appId: EntityId, body: AppUpdateBO) =>
        mutate<AppDetailVO, AppUpdateBO>(deps, "APP-004", { pathParams: { appId }, body }),
      changeStatus: (appId: EntityId, body: AppStatusBO) =>
        mutate<AppDetailVO, AppStatusBO>(deps, "APP-005", { pathParams: { appId }, body }),
      copy: (appId: EntityId) => mutate<AppDetailVO, undefined>(deps, "APP-006", { pathParams: { appId } }),
      templates: () => callData<JsonValue[]>(deps, "APP-007"),
    },
    modules: {
      load: (appId: EntityId, query: PageQuery = {}) =>
        callData<ModuleListItemVO[], undefined, PageQuery>(deps, "MOD-001", { pathParams: { appId }, query }),
      create: (appId: EntityId, body: ModuleSaveBO, idempotencyKey = createIdempotencyKey("MOD-002")) =>
        mutate<ModuleDetailVO, ModuleSaveBO>(deps, "MOD-002", { pathParams: { appId }, body, idempotencyKey }),
      detail: (moduleId: EntityId) => callData<ModuleDetailVO>(deps, "MOD-003", { pathParams: { moduleId } }),
      update: (moduleId: EntityId, body: ModuleUpdateBO) =>
        mutate<ModuleDetailVO, ModuleUpdateBO>(deps, "MOD-004", { pathParams: { moduleId }, body }),
      changeStatus: (moduleId: EntityId, body: ModuleStatusBO) =>
        mutate<ModuleDetailVO, ModuleStatusBO>(deps, "MOD-005", { pathParams: { moduleId }, body }),
      publishCheck: (moduleId: EntityId, idempotencyKey = createIdempotencyKey("MOD-006")) =>
        mutate<PublishCheckResultVO, { idempotencyKey: string }>(deps, "MOD-006", {
          pathParams: { moduleId },
          body: { idempotencyKey },
          idempotencyKey,
        }),
      async publish(moduleId: EntityId, body: ModulePublishBO = {}) {
        const result = await mutate<ModulePublishResultVO, ModulePublishBO>(deps, "MOD-007", {
          pathParams: { moduleId },
          body,
        });
        const runtimeRefresh = result.data ? await refreshRuntimeAfterPublish(deps, moduleId) : undefined;
        return {
          ...result,
          runtimeRefresh,
        };
      },
      locatePublishIssue(issue: PublishCheckIssueVO) {
        return locatePublishIssue(issue);
      },
    },
    fields: {
      load: (moduleId: EntityId) => callData<FieldDefinitionVO[]>(deps, "FIELD-001", { pathParams: { moduleId } }),
      create: (moduleId: EntityId, body: FieldSaveBO, idempotencyKey = createIdempotencyKey("FIELD-002")) =>
        mutate<FieldDefinitionVO, FieldSaveBO>(deps, "FIELD-002", { pathParams: { moduleId }, body, idempotencyKey }),
      update: (moduleId: EntityId, fieldId: EntityId, body: FieldUpdateBO) =>
        mutate<FieldDefinitionVO, FieldUpdateBO>(deps, "FIELD-003", { pathParams: { moduleId, fieldId }, body }),
      changeStatus: (moduleId: EntityId, fieldId: EntityId, body: FieldStatusBO) =>
        mutate<FieldDefinitionVO, FieldStatusBO>(deps, "FIELD-004", { pathParams: { moduleId, fieldId }, body }),
      fieldTypes: () => callData<DynamicFieldType[]>(deps, "FIELD-005"),
      duplicateCodes(fields: Array<Pick<FieldDefinitionVO, "fieldCode">>) {
        return duplicateFieldCodes(fields.map((field) => field.fieldCode));
      },
    },
    ui: {
      loadListView: (moduleId: EntityId) => callData<UiSchemaVO>(deps, "UI-001", { pathParams: { moduleId } }),
      saveListView: (moduleId: EntityId, body: ListViewSchemaBO) =>
        mutate<UiSchemaVO, ListViewSchemaBO>(deps, "UI-002", { pathParams: { moduleId }, body }),
      loadForm: (moduleId: EntityId) => callData<UiSchemaVO>(deps, "UI-003", { pathParams: { moduleId } }),
      saveForm: (moduleId: EntityId, body: FormSchemaBO) =>
        mutate<UiSchemaVO, FormSchemaBO>(deps, "UI-004", { pathParams: { moduleId }, body }),
      loadDetail: (moduleId: EntityId) => callData<UiSchemaVO>(deps, "UI-005", { pathParams: { moduleId } }),
      saveDetail: (moduleId: EntityId, body: DetailSchemaBO) =>
        mutate<UiSchemaVO, DetailSchemaBO>(deps, "UI-006", { pathParams: { moduleId }, body }),
      saveMenu: (moduleId: EntityId, body: ModuleMenuBO) =>
        mutate<UiSchemaVO, ModuleMenuBO>(deps, "UI-007", { pathParams: { moduleId }, body }),
      saveActions: (moduleId: EntityId, body: ModuleActionBO) => {
        const idempotencyKey = body.idempotencyKey ?? createIdempotencyKey("UI-008");
        return mutate<UiSchemaVO, ModuleActionBO>(deps, "UI-008", {
          pathParams: { moduleId },
          body: { ...body, idempotencyKey },
          idempotencyKey,
        });
      },
      validateListSchema: (schema: ListViewSchemaBO, fields: FieldDefinitionVO[]) =>
        validateSchemaRefs(schema.columns.concat(schema.filters ?? []), fields),
      validateFormSchema: (schema: FormSchemaBO, fields: FieldDefinitionVO[]) =>
        validateSchemaRefs(schema.formSections.flatMap((section) => section.fields), fields),
      validateDetailSchema: (schema: DetailSchemaBO, fields: FieldDefinitionVO[]) =>
        validateSchemaRefs(schema.detailBlocks.flatMap((block) => block.fields), fields),
    },
  };
}

function createFieldDesignerState(): FieldDesignerState {
  return {
    availableFieldTypes: DYNAMIC_FIELD_TYPES,
    uniqueSupportedTypes: UNIQUE_SUPPORTED_FIELD_TYPES,
    uniqueUnsupportedTypes: UNIQUE_UNSUPPORTED_FIELD_TYPES,
  };
}

function createPermissionState(permission: PermissionStore) {
  return {
    appView: actionState(permission.decide({ anyOperations: ["APP_VIEW"] }), "APP-001", "APP_VIEW", "View applications"),
    appCreate: actionState(permission.decide({ anyOperations: ["APP_CREATE"] }), "APP-002", "APP_CREATE", "Create application"),
    appEdit: actionState(permission.decide({ anyOperations: ["APP_EDIT"] }), "APP-004", "APP_EDIT", "Edit application"),
    appStatus: actionState(permission.decide({ anyOperations: ["APP_STATUS"] }), "APP-005", "APP_STATUS", "Change application status"),
    moduleView: actionState(permission.decide({ anyOperations: ["MODULE_VIEW"] }), "MOD-001", "MODULE_VIEW", "View modules"),
    moduleCreate: actionState(permission.decide({ anyOperations: ["MODULE_CREATE"] }), "MOD-002", "MODULE_CREATE", "Create module"),
    moduleEdit: actionState(permission.decide({ anyOperations: ["MODULE_EDIT"] }), "MOD-004", "MODULE_EDIT", "Edit module"),
    modulePublish: actionState(permission.decide({ anyOperations: ["MODULE_PUBLISH"] }), "MOD-007", "MODULE_PUBLISH", "Publish module"),
    fieldView: actionState(permission.decide({ anyOperations: ["FIELD_VIEW"] }), "FIELD-001", "FIELD_VIEW", "View fields"),
    fieldCreate: actionState(permission.decide({ anyOperations: ["FIELD_CREATE"] }), "FIELD-002", "FIELD_CREATE", "Create field"),
    fieldEdit: actionState(permission.decide({ anyOperations: ["FIELD_EDIT"] }), "FIELD-003", "FIELD_EDIT", "Edit field"),
    pageView: actionState(permission.decide({ anyOperations: ["PAGE_VIEW"] }), "UI-001", "PAGE_VIEW", "View page schema"),
    pageEdit: actionState(permission.decide({ anyOperations: ["PAGE_EDIT"] }), "UI-002", "PAGE_EDIT", "Edit page schema"),
    menuEdit: actionState(permission.decide({ anyOperations: ["MENU_EDIT"] }), "UI-007", "MENU_EDIT", "Edit runtime menu"),
    actionEdit: actionState(permission.decide({ anyOperations: ["ACTION_EDIT"] }), "UI-008", "ACTION_EDIT", "Edit actions"),
  };
}

function actionState(
  decision: PermissionDecision,
  apiId: ApiEndpointId,
  actionCode: string,
  label: string,
): PageActionState {
  return {
    ...decision,
    apiId,
    actionCode,
    label,
  };
}

async function callPage<TRecord>(
  deps: ModuleConfigPageDeps,
  apiId: ApiEndpointId,
  options: { query?: PageQuery; pathParams?: Record<string, string | number | undefined> } = {},
): Promise<PageMutationResult<PageResult<TRecord>>> {
  return callData<PageResult<TRecord>, undefined, PageQuery>(deps, apiId, options);
}

async function callData<TData, TBody = unknown, TQuery = Record<string, unknown>>(
  deps: ModuleConfigPageDeps,
  apiId: ApiEndpointId,
  options: {
    pathParams?: Record<string, string | number | undefined>;
    query?: TQuery;
    body?: TBody;
    idempotencyKey?: string;
    context?: ApiContext;
  } = {},
): Promise<PageMutationResult<TData>> {
  const block = contextBlock(deps);
  if (block) {
    return {
      state: {
        loading: false,
        empty: true,
        errorMessage: block,
        retryable: false,
      },
    };
  }

  try {
    const response = await deps.apiClient.call<TData, TBody, TQuery>(apiId, {
      pathParams: deps.systemContext.toPathParams(options.pathParams),
      query: options.query,
      body: options.body,
      idempotencyKey: options.idempotencyKey,
      context: apiContext(deps, options.context?.requestId),
    });
    return {
      data: response.data,
      state: {
        loading: false,
        empty: isEmpty(response.data),
        requestId: response.requestId,
        retryable: false,
      },
      idempotencyReplay: response.meta.idempotencyReplay,
    };
  } catch (error) {
    const display = deps.error.capture(error);
    return {
      state: {
        loading: false,
        empty: true,
        errorMessage: `${display.code}: ${display.message}`,
        requestId: display.requestId,
        retryable: display.retryable,
      },
    };
  }
}

async function mutate<TData, TBody = unknown>(
  deps: ModuleConfigPageDeps,
  apiId: ApiEndpointId,
  options: {
    pathParams?: Record<string, string | number | undefined>;
    body?: TBody;
    idempotencyKey?: string;
  } = {},
): Promise<PageMutationResult<TData>> {
  return callData<TData, TBody>(deps, apiId, options);
}

async function refreshRuntimeAfterPublish(
  deps: ModuleConfigPageDeps,
  moduleId: EntityId,
): Promise<RuntimeRefreshState> {
  const menus = await callData<JsonValue[]>(deps, "RUN-001");
  const schema = await callData<RuntimeModuleSchemaVO>(deps, "RUN-002", {
    pathParams: { moduleId },
  });
  return {
    menusRefreshed: Boolean(menus.data),
    schemaRefreshed: Boolean(schema.data),
    menuRequestId: menus.state.requestId,
    schemaRequestId: schema.state.requestId,
    schema: schema.data,
  };
}

function contextBlock(deps: ModuleConfigPageDeps): string | undefined {
  const missing = deps.systemContext.validate(MODULE_CONFIG_CONTEXT);
  if (missing.length > 0) {
    return `SYS_CONTEXT_REQUIRED: ${missing.join(",")}`;
  }
  const state = deps.systemContext.getState();
  if (state.status === "disabled") {
    return state.disabledReason ?? "SYS_DISABLED";
  }
  return undefined;
}

function apiContext(deps: ModuleConfigPageDeps, requestId?: string): ApiContext {
  return {
    ...deps.auth.toApiContext(requestId),
    tenantId: deps.systemContext.toTenantHeader(),
  };
}

function validateSchemaRefs(refs: SchemaFieldRef[], fields: FieldDefinitionVO[]): SchemaValidationResult {
  const available = new Set(fields.filter((field) => field.status !== "DELETED").map((field) => field.fieldCode));
  const fieldCodes = refs.map((ref) => ref.fieldCode).filter(Boolean);
  const duplicateFieldCodeList = duplicateFieldCodes(fieldCodes);
  return {
    valid: duplicateFieldCodeList.length === 0 && fieldCodes.every((fieldCode) => available.has(fieldCode)),
    missingFieldCodes: fieldCodes.filter((fieldCode) => !available.has(fieldCode)),
    duplicateFieldCodes: duplicateFieldCodeList,
  };
}

function duplicateFieldCodes(fieldCodes: string[]): string[] {
  const seen = new Set<string>();
  const duplicates = new Set<string>();
  fieldCodes.forEach((fieldCode) => {
    if (seen.has(fieldCode)) {
      duplicates.add(fieldCode);
      return;
    }
    seen.add(fieldCode);
  });
  return [...duplicates];
}

function locatePublishIssue(issue: PublishCheckIssueVO): string {
  if (issue.schemaPath) {
    return issue.schemaPath;
  }
  if (issue.fieldCode) {
    return `fields.${issue.fieldCode}`;
  }
  if (issue.targetId) {
    return `${issue.targetType}.${issue.targetId}`;
  }
  return issue.targetType;
}

function isEmpty(value: unknown): boolean {
  if (Array.isArray(value)) {
    return value.length === 0;
  }
  if (isObject(value) && Array.isArray(value.records)) {
    return value.records.length === 0;
  }
  return value === undefined || value === null;
}

function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function createIdempotencyKey(apiId: ApiEndpointId): string {
  const endpoint = API_ENDPOINTS[apiId];
  const cryptoApi = (globalThis as { crypto?: { randomUUID?: () => string } }).crypto;
  const random =
    typeof cryptoApi?.randomUUID === "function"
      ? cryptoApi.randomUUID()
      : Math.random().toString(36).slice(2);
  return `${endpoint.id}:${Date.now()}:${random}`;
}
