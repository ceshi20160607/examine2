import type {
  ApiClient,
  ApiContext,
  ApiEndpointId,
  ApiRequestOptions,
  AvailableAction,
  DynamicFieldValue,
  EntityId,
  FieldPermission,
  JsonValue,
  PageQuery,
  PageResult,
  RecordDetailVO,
  RecordListItemVO,
  RecordMutationResultVO,
  RecordStatus,
  RuntimeModuleSchemaVO,
} from "../../api";
import type { AuthStore, ErrorStore, PermissionDecision, PermissionStore, SystemContextStore } from "../../stores";
import {
  buildDynamicListQuery,
  createDynamicDetailModel,
  createDynamicFormModel,
  createDynamicHistoryModel,
  createDynamicListModel,
  mapApiFieldErrors,
  validateDynamicForm,
} from "../../components/dynamic-schema";
import type {
  DynamicDetailRenderModel,
  DynamicFieldError,
  DynamicFormRenderModel,
  DynamicListRenderModel,
  DynamicQueryDraft,
  DynamicSchemaInput,
} from "../../components/dynamic-schema";

export interface RuntimeWorkbenchDeps {
  apiClient: ApiClient;
  auth: AuthStore;
  systemContext: SystemContextStore;
  permission: PermissionStore;
  error: ErrorStore;
}

export interface RuntimeRequestState {
  loading: boolean;
  empty: boolean;
  requestId?: string;
  errorMessage?: string;
  retryable: boolean;
  fieldErrors?: DynamicFieldError[];
}

export interface RuntimePageResult<TData> {
  data?: TData;
  state: RuntimeRequestState;
}

export interface RuntimeMenuVO {
  menuId: EntityId;
  parentId?: EntityId;
  appId?: EntityId;
  moduleId?: EntityId;
  code: string;
  name: string;
  routePath?: string;
  icon?: string;
  children?: RuntimeMenuVO[];
}

export interface RuntimeRecordQueryBO {
  pageNo?: number;
  pageSize?: number;
  keyword?: string;
  filters?: JsonValue[];
  sorter?: JsonValue[];
}

export interface RuntimeRecordSaveBO {
  values: RuntimeRecordFieldValueBO[];
  remark?: string;
}

export interface RuntimeRecordUpdateBO extends RuntimeRecordSaveBO {
  recordVersion: number;
}

export interface RuntimeRecordSubmitBO {
  recordVersion: number;
  reason?: string;
}

export interface RuntimeRecordFieldValueBO {
  fieldCode: string;
  value: DynamicFieldValue["value"];
  displayValue?: JsonValue;
}

export interface RuntimeRecordHistoryVO {
  historyId: EntityId;
  recordVersion: number;
  operationType: string;
  beforeStatus?: RecordStatus;
  afterStatus?: RecordStatus;
  changedFields?: JsonValue;
  beforeSnapshot?: JsonValue;
  afterSnapshot?: JsonValue;
  requestId?: string;
  operatorMemberId?: EntityId;
  remark?: string;
  createdAt?: string;
}

export interface RuntimeRecordRelationVO {
  relationId: EntityId;
  fieldId: EntityId;
  targetModuleId?: EntityId;
  targetRecordId?: EntityId;
  relationType?: string;
  displaySnapshot?: JsonValue;
}

export interface RuntimeActionState extends PermissionDecision {
  actionCode: string;
  apiId: ApiEndpointId;
  label: string;
}

export interface RuntimeRecordListView {
  page: PageResult<RecordListItemVO>;
  listModel: DynamicListRenderModel;
  activeActions: RuntimeActionState[];
}

export interface RuntimeRecordDetailView {
  record: RecordDetailVO;
  detailModel: DynamicDetailRenderModel;
  formModel: DynamicFormRenderModel;
  activeActions: RuntimeActionState[];
  deleteDisabledReason?: string;
  submitDisabledReason?: string;
}

export interface RuntimeRecordHistoryView {
  history: RuntimeRecordHistoryVO[];
  snapshots: ReturnType<typeof createDynamicHistoryModel>;
}

const RUNTIME_CONTEXT = { system: true, member: true };

const RUNTIME_API_IDS = [
  "RUN-001",
  "RUN-002",
  "RUN-003",
  "RUN-004",
  "RUN-005",
  "RUN-006",
  "RUN-007",
  "RUN-008",
  "RUN-009",
  "RUN-010",
] as const satisfies readonly ApiEndpointId[];

const LOCKED_STATUSES: readonly RecordStatus[] = ["IN_APPROVAL", "APPROVED", "ARCHIVED", "DELETED"];

export function createRuntimeWorkbenchPageModel(deps: RuntimeWorkbenchDeps) {
  return {
    routeNames: ["runtime.home", "runtime.module.records", "runtime.record.detail"] as const,
    apiIds: RUNTIME_API_IDS,
    actions: createRuntimeActions(deps.permission),
    menus: {
      load: () => callData<RuntimeMenuVO[]>(deps, "RUN-001"),
      flatten: flattenMenus,
    },
    schema: {
      load: async (moduleId: EntityId) => {
        const result = await callData<RuntimeModuleSchemaVO>(deps, "RUN-002", { pathParams: { moduleId } });
        return result.data
          ? {
              ...result,
              data: normalizeRuntimeSchema(result.data),
            }
          : result;
      },
      toInput: normalizeRuntimeSchema,
    },
    records: {
      query: async (moduleId: EntityId, schema: RuntimeModuleSchemaVO, draft: DynamicQueryDraft & PageQuery = {}) => {
        const schemaInput = normalizeRuntimeSchema(schema);
        const dynamicQuery = buildDynamicListQuery(schemaInput, draft);
        const query = toRecordQuery(draft, dynamicQuery);
        const result = await callData<PageResult<RecordListItemVO>, undefined, RuntimeRecordQueryBO>(deps, "RUN-003", {
          pathParams: { moduleId },
          query,
        });
        return result.data
          ? {
              ...result,
              data: {
                page: result.data,
                listModel: createDynamicListModel(schemaInput, firstRecordValues(result.data)),
                activeActions: pageActions(deps.permission, result.data.records.flatMap((item) => item.availableActions ?? [])),
              },
            }
          : result;
      },
      createForm: (schema: RuntimeModuleSchemaVO, values: Record<string, unknown> = {}) =>
        createDynamicFormModel(normalizeRuntimeSchema(schema), values),
      create: (moduleId: EntityId, schema: RuntimeModuleSchemaVO, values: Record<string, unknown>, remark?: string) => {
        const body = toSaveBody(normalizeRuntimeSchema(schema), values, remark);
        return body.valid
          ? callData<RecordMutationResultVO, RuntimeRecordSaveBO>(deps, "RUN-004", {
              pathParams: { moduleId },
              body: body.body,
              idempotencyKey: createIdempotencyKey("RUN-004"),
            })
          : localValidationResult<RecordMutationResultVO>(body.errors);
      },
      detail: async (moduleId: EntityId, recordId: EntityId, schema: RuntimeModuleSchemaVO) => {
        const schemaInput = normalizeRuntimeSchema(schema);
        const result = await callData<RecordDetailVO>(deps, "RUN-005", { pathParams: { moduleId, recordId } });
        return result.data
          ? {
              ...result,
              data: toDetailView(deps.permission, schemaInput, result.data),
            }
          : result;
      },
      update: (
        moduleId: EntityId,
        recordId: EntityId,
        schema: RuntimeModuleSchemaVO,
        recordVersion: number,
        values: Record<string, unknown>,
        remark?: string,
      ) => {
        const body = toSaveBody(normalizeRuntimeSchema(schema), values, remark);
        return body.valid
          ? callData<RecordMutationResultVO, RuntimeRecordUpdateBO>(deps, "RUN-006", {
              pathParams: { moduleId, recordId },
              body: {
                ...body.body,
                recordVersion,
              },
            })
          : localValidationResult<RecordMutationResultVO>(body.errors);
      },
      delete: (moduleId: EntityId, recordId: EntityId) =>
        callData<RecordMutationResultVO>(deps, "RUN-007", { pathParams: { moduleId, recordId } }),
      submit: (moduleId: EntityId, recordId: EntityId, body: RuntimeRecordSubmitBO) =>
        callData<RecordMutationResultVO, RuntimeRecordSubmitBO>(deps, "RUN-008", {
          pathParams: { moduleId, recordId },
          body,
        }),
      history: async (moduleId: EntityId, recordId: EntityId, schema: RuntimeModuleSchemaVO) => {
        const schemaInput = normalizeRuntimeSchema(schema);
        const result = await callData<RuntimeRecordHistoryVO[]>(deps, "RUN-009", { pathParams: { moduleId, recordId } });
        return result.data
          ? {
              ...result,
              data: {
                history: result.data,
                snapshots: createDynamicHistoryModel(schemaInput, toHistorySnapshots(result.data)),
              },
            }
          : result;
      },
      relations: (moduleId: EntityId, recordId: EntityId) =>
        callData<RuntimeRecordRelationVO[]>(deps, "RUN-010", { pathParams: { moduleId, recordId } }),
      deleteDisabledReason,
      submitDisabledReason,
    },
  };
}

function createRuntimeActions(permission: PermissionStore) {
  return {
    menuVisible: actionState(permission.decide({ anyOperations: ["SYSTEM_MEMBER"] }), "RUN-001", "SYSTEM_MEMBER", "View runtime menus"),
    schemaVisible: actionState(permission.decide({ menu: "MENU_VISIBLE" }), "RUN-002", "MENU_VISIBLE", "View runtime schema"),
    recordView: actionState(permission.decide({ anyOperations: ["RECORD_VIEW"] }), "RUN-003", "RECORD_VIEW", "View records"),
    recordCreate: actionState(permission.decide({ anyOperations: ["RECORD_CREATE"] }), "RUN-004", "RECORD_CREATE", "Create record"),
    recordEdit: actionState(permission.decide({ anyOperations: ["RECORD_EDIT"] }), "RUN-006", "RECORD_EDIT", "Edit record"),
    recordDelete: actionState(permission.decide({ anyOperations: ["RECORD_DELETE"] }), "RUN-007", "RECORD_DELETE", "Delete record"),
    recordSubmit: actionState(permission.decide({ anyOperations: ["RECORD_SUBMIT"] }), "RUN-008", "RECORD_SUBMIT", "Submit record"),
    recordHistory: actionState(
      permission.decide({ anyOperations: ["RECORD_HISTORY_VIEW"] }),
      "RUN-009",
      "RECORD_HISTORY_VIEW",
      "View record history",
    ),
  };
}

function actionState(decision: PermissionDecision, apiId: ApiEndpointId, actionCode: string, label: string): RuntimeActionState {
  return {
    ...decision,
    apiId,
    actionCode,
    label,
  };
}

async function callData<TData, TBody = unknown, TQuery = Record<string, unknown>>(
  deps: RuntimeWorkbenchDeps,
  apiId: ApiEndpointId,
  options: ApiRequestOptions<TBody, TQuery> = {},
): Promise<RuntimePageResult<TData>> {
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
    const requestId = options.context?.requestId;
    const response = await deps.apiClient.call<TData, TBody, TQuery>(apiId, {
      ...options,
      pathParams: deps.systemContext.toPathParams(options.pathParams),
      context: apiContext(deps, requestId),
    });
    return {
      data: response.data,
      state: {
        loading: false,
        empty: isEmpty(response.data),
        requestId: response.requestId,
        retryable: false,
        fieldErrors: [],
      },
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
        fieldErrors: mapApiFieldErrors(display.details, display.requestId),
      },
    };
  }
}

function apiContext(deps: RuntimeWorkbenchDeps, requestId?: string): ApiContext {
  return {
    ...deps.auth.toApiContext(requestId),
    tenantId: deps.systemContext.toTenantHeader(),
  };
}

function contextBlock(deps: RuntimeWorkbenchDeps): string | undefined {
  const missing = deps.systemContext.validate(RUNTIME_CONTEXT);
  if (missing.length > 0) {
    return `SYS_CONTEXT_REQUIRED: ${missing.join(",")}`;
  }
  const state = deps.systemContext.getState();
  if (state.status === "disabled") {
    return state.disabledReason ?? "SYS_DISABLED";
  }
  return undefined;
}

function normalizeRuntimeSchema(schema: RuntimeModuleSchemaVO): DynamicSchemaInput & RuntimeModuleSchemaVO {
  return {
    ...schema,
    fieldDefinitions: Array.isArray(schema.fieldDefinitions) ? schema.fieldDefinitions : [],
    availableActions: normalizeActions(schema.availableActions),
    fieldPermissions: readFieldPermissions(schema),
  };
}

function readFieldPermissions(schema: RuntimeModuleSchemaVO): FieldPermission[] {
  return schema.fieldDefinitions.flatMap((field) => field.fieldPermissions ?? []);
}

function toRecordQuery(draft: PageQuery, dynamicQuery: DynamicQueryDraft): RuntimeRecordQueryBO {
  return {
    pageNo: draft.pageNo ?? 1,
    pageSize: draft.pageSize ?? 20,
    keyword: dynamicQuery.keyword,
    filters: (dynamicQuery.filters ?? []) as unknown as JsonValue[],
    sorter: (dynamicQuery.sorter ?? []) as unknown as JsonValue[],
  };
}

function toSaveBody(
  schema: DynamicSchemaInput,
  values: Record<string, unknown>,
  remark?: string,
): { valid: true; body: RuntimeRecordSaveBO } | { valid: false; errors: DynamicFieldError[] } {
  const validation = validateDynamicForm(schema, values);
  if (!validation.valid) {
    return {
      valid: false,
      errors: validation.errors,
    };
  }
  return {
    valid: true,
    body: {
      values: validation.values.map((value) => ({
        fieldCode: value.fieldCode,
        value: value.value,
        displayValue: value.displayValue,
      })),
      remark,
    },
  };
}

function toDetailView(
  permission: PermissionStore,
  schema: DynamicSchemaInput,
  record: RecordDetailVO,
): RuntimeRecordDetailView {
  const currentValues = Object.fromEntries(record.values.map((value) => [value.fieldCode, value.value]));
  return {
    record,
    detailModel: createDynamicDetailModel(schema, record.values),
    formModel: createDynamicFormModel(schema, currentValues),
    activeActions: pageActions(permission, record.availableActions ?? []),
    deleteDisabledReason: deleteDisabledReason(record),
    submitDisabledReason: submitDisabledReason(record),
  };
}

function pageActions(permission: PermissionStore, actions: AvailableAction[]): RuntimeActionState[] {
  return normalizeActions(actions).map((action) => {
    const decision = permission.action(action.actionCode);
    return actionState(
      {
        visible: action.visible && decision.visible,
        enabled: action.enabled && decision.enabled,
        disabledReason: action.disabledReason ?? decision.disabledReason,
        matchedPermission: action.requiredPermission ?? decision.matchedPermission,
      },
      actionToApiId(action.actionCode),
      action.actionCode,
      action.label,
    );
  });
}

function normalizeActions(actions: AvailableAction[] = []): AvailableAction[] {
  return actions.map((action) => ({
    ...action,
    label: action.label ?? (action as unknown as { actionName?: string }).actionName ?? action.actionCode,
    visible: action.visible ?? true,
    enabled: action.enabled ?? true,
  }));
}

function actionToApiId(actionCode: string): ApiEndpointId {
  if (actionCode === "RECORD_CREATE") {
    return "RUN-004";
  }
  if (actionCode === "RECORD_EDIT") {
    return "RUN-006";
  }
  if (actionCode === "RECORD_DELETE") {
    return "RUN-007";
  }
  if (actionCode === "RECORD_SUBMIT") {
    return "RUN-008";
  }
  return "RUN-003";
}

function deleteDisabledReason(record: Pick<RecordDetailVO | RecordListItemVO, "recordStatus">): string | undefined {
  if (LOCKED_STATUSES.includes(record.recordStatus)) {
    return `RECORD_STATUS_LOCKED:${record.recordStatus}`;
  }
  return undefined;
}

function submitDisabledReason(record: Pick<RecordDetailVO | RecordListItemVO, "recordStatus">): string | undefined {
  if (!["DRAFT", "REJECTED", "WITHDRAWN"].includes(record.recordStatus)) {
    return `RECORD_STATUS_CONFLICT:${record.recordStatus}`;
  }
  return undefined;
}

function firstRecordValues(page: PageResult<RecordListItemVO>): DynamicFieldValue[] {
  return page.records[0]?.values ?? [];
}

function toHistorySnapshots(history: RuntimeRecordHistoryVO[]) {
  return history.map((item) => ({
    title: `${item.operationType} #${item.recordVersion}`,
    changedAt: item.createdAt,
    changedBy: item.operatorMemberId,
    values: snapshotValues(item.afterSnapshot),
    requestId: item.requestId,
  }));
}

function snapshotValues(snapshot: JsonValue | undefined): DynamicFieldValue[] {
  if (!snapshot || typeof snapshot !== "object" || Array.isArray(snapshot)) {
    return [];
  }
  return Object.entries(snapshot).map(([fieldCode, value]) => ({
    fieldCode,
    value: value as DynamicFieldValue["value"],
  }));
}

function flattenMenus(menus: RuntimeMenuVO[] = []): RuntimeMenuVO[] {
  return menus.flatMap((menu) => [menu, ...flattenMenus(menu.children ?? [])]);
}

function localValidationResult<TData>(errors: DynamicFieldError[]): Promise<RuntimePageResult<TData>> {
  return Promise.resolve({
    state: {
      loading: false,
      empty: true,
      errorMessage: errors.map((error) => `${error.fieldCode}:${error.code}`).join("; "),
      retryable: false,
      fieldErrors: errors,
    },
  });
}

function isEmpty(value: unknown): boolean {
  if (Array.isArray(value)) {
    return value.length === 0;
  }
  if (isRecord(value) && Array.isArray(value.records)) {
    return value.records.length === 0;
  }
  return value === undefined || value === null;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function createIdempotencyKey(apiId: ApiEndpointId): string {
  const randomApi = (globalThis as { crypto?: { randomUUID?: () => string } }).crypto;
  const random =
    typeof randomApi?.randomUUID === "function" ? randomApi.randomUUID() : Math.random().toString(36).slice(2);
  return `${apiId}:${Date.now()}:${random}`;
}
