import type {
  ApiClient,
  ApiContext,
  ApiEndpointId,
  ApiRequestOptions,
  EntityId,
  ExportFailureReason,
  ExportJobDetailVO,
  ExportJobStatus,
  IsoDateTimeString,
  JsonValue,
  PageQuery,
  PageResult,
} from "../../api";
import type { AuthStore, ErrorStore, PermissionDecision, PermissionStore, SystemContextStore } from "../../stores";

export interface ExportPageDeps {
  apiClient: ApiClient;
  auth: AuthStore;
  systemContext: SystemContextStore;
  permission: PermissionStore;
  error: ErrorStore;
}

export interface ExportRequestState {
  loading: boolean;
  empty: boolean;
  requestId?: string;
  errorMessage?: string;
  retryable: boolean;
  validationErrors?: ExportValidationError[];
}

export interface ExportPageResult<TData> {
  data?: TData;
  state: ExportRequestState;
}

export interface ExportValidationError {
  field: string;
  message: string;
}

export interface ExportTemplateVO {
  templateId: EntityId;
  moduleId: EntityId;
  templateCode: string;
  templateName: string;
  templateStatus?: string;
  fileNamePattern?: string;
  exportFormat?: string;
  includeHistory?: boolean;
  fields: ExportTemplateFieldVO[];
  updatedAt?: IsoDateTimeString;
}

export interface ExportTemplateFieldVO {
  templateFieldId?: EntityId;
  fieldId: EntityId;
  fieldCode?: string;
  headerName?: string;
  columnOrder?: number;
  plainRequired?: boolean;
  maskStrategy?: string;
}

export interface ExportTemplateSaveBO {
  moduleId: EntityId;
  templateCode: string;
  templateName: string;
  fileNamePattern?: string;
  exportFormat?: string;
  includeHistoryFlag?: 0 | 1;
  configJson?: string;
  fields: ExportTemplateFieldBO[];
}

export interface ExportTemplateFieldBO {
  fieldId: EntityId;
  headerName?: string;
  columnOrder?: number;
  plainRequiredFlag?: 0 | 1;
  maskStrategy?: string;
  formatJson?: string;
}

export interface ExportJobCreateBO {
  moduleId: EntityId;
  templateId?: EntityId;
  filters?: JsonValue[];
  sorter?: JsonValue[];
  selectedRecordIds?: EntityId[];
  fileName?: string;
  idempotencyKey: string;
}

export interface ExportJobQueryBO extends PageQuery {
  moduleId?: EntityId;
  status?: ExportJobStatus;
  keyword?: string;
}

export interface ExportJobActionBO {
  reason?: string;
  idempotencyKey: string;
}

export interface ExportJobListItemVO {
  jobId: EntityId;
  moduleId: EntityId;
  templateId?: EntityId;
  status: ExportJobStatus;
  progress: number;
  resultFileId?: EntityId;
  fileName?: string;
  failureReason?: ExportFailureReason;
  retryable: boolean;
  createdBy?: EntityId;
  createdAt?: IsoDateTimeString;
  finishedAt?: IsoDateTimeString;
  pollingIntervalMs?: number;
}

export interface ExportJobDetailView {
  job: ExportJobDetailVO & Partial<ExportJobListItemVO>;
  poll: ExportPollState;
  retryDisabledReason?: string;
  cancelDisabledReason?: string;
  resultDownloadFileId?: EntityId;
}

export interface ExportPollState {
  shouldPoll: boolean;
  nextDelayMs?: number;
  reason: "queued" | "processing" | "success" | "failed" | "canceled";
}

export interface ExportActionState extends PermissionDecision {
  actionCode: string;
  apiId: ApiEndpointId;
  label: string;
}

export interface ExportPermissionState {
  templateView: ExportActionState;
  templateCreate: ExportActionState;
  templateEdit: ExportActionState;
  jobCreate: ExportActionState;
  jobView: ExportActionState;
  jobRetry: ExportActionState;
  jobCancel: ExportActionState;
}

const EXPORT_CONTEXT = { system: true, member: true };

const EXPORT_API_IDS = ["EXP-001", "EXP-002", "EXP-003", "EXP-004", "EXP-005", "EXP-006", "EXP-007", "EXP-008"] as const satisfies readonly ApiEndpointId[];

export function createExportPageModel(deps: ExportPageDeps) {
  return {
    routeName: "exports.jobs" as const,
    routePath: "/systems/:systemId/exports",
    apiIds: EXPORT_API_IDS,
    actions: createExportPermissionState(deps.permission),
    templates: {
      list: (moduleId?: EntityId) =>
        callData<ExportTemplateVO[], undefined, { moduleId?: EntityId }>(deps, "EXP-001", { query: { moduleId } }),
      create: (body: ExportTemplateSaveBO) => {
        const validation = validateTemplate(body);
        return validation.length === 0
          ? callData<ExportTemplateVO, ExportTemplateSaveBO>(deps, "EXP-002", { body })
          : localValidationResult<ExportTemplateVO>(validation);
      },
      update: (templateId: EntityId, body: ExportTemplateSaveBO) => {
        const validation = validateTemplate(body);
        return validation.length === 0
          ? callData<ExportTemplateVO, ExportTemplateSaveBO>(deps, "EXP-003", { pathParams: { templateId }, body })
          : localValidationResult<ExportTemplateVO>(validation);
      },
    },
    jobs: {
      create: (body: ExportJobCreateBO) => {
        const validation = validateJobCreate(body);
        return validation.length === 0
          ? callData<ExportJobDetailVO, ExportJobCreateBO>(deps, "EXP-004", {
              body,
              idempotencyKey: body.idempotencyKey,
            })
          : localValidationResult<ExportJobDetailVO>(validation);
      },
      list: (query: ExportJobQueryBO = {}) =>
        callData<PageResult<ExportJobListItemVO>, undefined, ExportJobQueryBO>(deps, "EXP-005", { query }),
      detail: async (jobId: EntityId) => {
        const result = await callData<ExportJobDetailVO>(deps, "EXP-006", { pathParams: { jobId } });
        return result.data
          ? {
              ...result,
              data: toJobDetailView(result.data),
            }
          : result;
      },
      retry: (job: Pick<ExportJobListItemVO | ExportJobDetailVO, "jobId" | "status" | "retryable">, body = createActionBody("EXP-007")) => {
        const disabledReason = retryDisabledReason(job);
        return disabledReason
          ? localValidationResult<ExportJobDetailVO>([{ field: "jobId", message: disabledReason }])
          : callData<ExportJobDetailVO, ExportJobActionBO>(deps, "EXP-007", {
              pathParams: { jobId: job.jobId },
              body,
              idempotencyKey: body.idempotencyKey,
            });
      },
      cancel: (job: Pick<ExportJobListItemVO | ExportJobDetailVO, "jobId" | "status">, body = createActionBody("EXP-008")) => {
        const disabledReason = cancelDisabledReason(job);
        return disabledReason
          ? localValidationResult<ExportJobDetailVO>([{ field: "jobId", message: disabledReason }])
          : callData<ExportJobDetailVO, ExportJobActionBO>(deps, "EXP-008", {
              pathParams: { jobId: job.jobId },
              body,
              idempotencyKey: body.idempotencyKey,
            });
      },
      pollState,
      retryDisabledReason,
      cancelDisabledReason,
      toJobDetailView,
    },
    createJobBody: (moduleId: EntityId, draft: Partial<ExportJobCreateBO> = {}): ExportJobCreateBO => ({
      ...draft,
      moduleId,
      idempotencyKey: draft.idempotencyKey ?? createIdempotencyKey("EXP-004"),
    }),
    createActionBody,
    toTemplateSaveBody,
  };
}

function createExportPermissionState(permission: PermissionStore): ExportPermissionState {
  return {
    templateView: actionState(permission.decide({ anyOperations: ["EXPORT_TEMPLATE_VIEW"] }), "EXP-001", "EXPORT_TEMPLATE_VIEW", "查看导出模板"),
    templateCreate: actionState(permission.decide({ anyOperations: ["EXPORT_TEMPLATE_CREATE"] }), "EXP-002", "EXPORT_TEMPLATE_CREATE", "新建导出模板"),
    templateEdit: actionState(permission.decide({ anyOperations: ["EXPORT_TEMPLATE_EDIT"] }), "EXP-003", "EXPORT_TEMPLATE_EDIT", "编辑导出模板"),
    jobCreate: actionState(permission.decide({ anyOperations: ["RECORD_EXPORT"] }), "EXP-004", "RECORD_EXPORT", "创建导出任务"),
    jobView: actionState(permission.decide({ anyOperations: ["EXPORT_JOB_VIEW"] }), "EXP-005", "EXPORT_JOB_VIEW", "查看导出任务"),
    jobRetry: actionState(permission.decide({ anyOperations: ["EXPORT_JOB_RETRY"] }), "EXP-007", "EXPORT_JOB_RETRY", "重试导出任务"),
    jobCancel: actionState(permission.decide({ anyOperations: ["EXPORT_JOB_CANCEL"] }), "EXP-008", "EXPORT_JOB_CANCEL", "取消导出任务"),
  };
}

function actionState(decision: PermissionDecision, apiId: ApiEndpointId, actionCode: string, label: string): ExportActionState {
  return {
    ...decision,
    apiId,
    actionCode,
    label,
  };
}

async function callData<TData, TBody = unknown, TQuery = Record<string, unknown>>(
  deps: ExportPageDeps,
  apiId: ApiEndpointId,
  options: ApiRequestOptions<TBody, TQuery> = {},
): Promise<ExportPageResult<TData>> {
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
        validationErrors: [],
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
        validationErrors: [],
      },
    };
  }
}

function apiContext(deps: ExportPageDeps, requestId?: string): ApiContext {
  return {
    ...deps.auth.toApiContext(requestId),
    tenantId: deps.systemContext.toTenantHeader(),
  };
}

function contextBlock(deps: ExportPageDeps): string | undefined {
  const missing = deps.systemContext.validate(EXPORT_CONTEXT);
  if (missing.length > 0) {
    return `SYS_CONTEXT_REQUIRED: ${missing.join(",")}`;
  }
  const state = deps.systemContext.getState();
  if (state.status === "disabled") {
    return state.disabledReason ?? "SYS_DISABLED";
  }
  return undefined;
}

function validateTemplate(body: ExportTemplateSaveBO): ExportValidationError[] {
  const errors: ExportValidationError[] = [];
  if (!body.moduleId) {
    errors.push({ field: "moduleId", message: "模块 ID 必填" });
  }
  if (!body.templateCode?.trim()) {
    errors.push({ field: "templateCode", message: "模板编码必填" });
  }
  if (!body.templateName?.trim()) {
    errors.push({ field: "templateName", message: "模板名称必填" });
  }
  if (!body.fields?.length) {
    errors.push({ field: "fields", message: "导出字段至少选择一项" });
  }
  return errors;
}

function validateJobCreate(body: ExportJobCreateBO): ExportValidationError[] {
  const errors: ExportValidationError[] = [];
  if (!body.moduleId) {
    errors.push({ field: "moduleId", message: "模块 ID 必填" });
  }
  if (!body.idempotencyKey) {
    errors.push({ field: "idempotencyKey", message: "幂等键必填" });
  }
  return errors;
}

function toTemplateSaveBody(template: ExportTemplateVO): ExportTemplateSaveBO {
  return {
    moduleId: template.moduleId,
    templateCode: template.templateCode,
    templateName: template.templateName,
    fileNamePattern: template.fileNamePattern,
    exportFormat: template.exportFormat ?? "CSV",
    includeHistoryFlag: template.includeHistory ? 1 : 0,
    fields: [...template.fields]
      .sort((left, right) => (left.columnOrder ?? 0) - (right.columnOrder ?? 0))
      .map((field, index) => ({
        fieldId: field.fieldId,
        headerName: field.headerName,
        columnOrder: field.columnOrder ?? index,
        plainRequiredFlag: field.plainRequired ? 1 : 0,
        maskStrategy: field.maskStrategy,
      })),
  };
}

function createActionBody(apiId: "EXP-007" | "EXP-008", reason?: string): ExportJobActionBO {
  return {
    reason,
    idempotencyKey: createIdempotencyKey(apiId),
  };
}

function toJobDetailView(job: ExportJobDetailVO & Partial<ExportJobListItemVO>): ExportJobDetailView {
  return {
    job,
    poll: pollState(job),
    retryDisabledReason: retryDisabledReason(job),
    cancelDisabledReason: cancelDisabledReason(job),
    resultDownloadFileId: job.status === "SUCCESS" ? job.resultFileId : undefined,
  };
}

function pollState(job: Pick<ExportJobListItemVO | ExportJobDetailVO, "status" | "pollingIntervalMs">): ExportPollState {
  if (job.status === "QUEUED") {
    return { shouldPoll: true, nextDelayMs: job.pollingIntervalMs ?? 2000, reason: "queued" };
  }
  if (job.status === "PROCESSING") {
    return { shouldPoll: true, nextDelayMs: job.pollingIntervalMs ?? 2000, reason: "processing" };
  }
  return {
    shouldPoll: false,
    reason: job.status === "SUCCESS" ? "success" : job.status === "FAILED" ? "failed" : "canceled",
  };
}

function retryDisabledReason(job: Pick<ExportJobListItemVO | ExportJobDetailVO, "status" | "retryable">): string | undefined {
  if (job.status !== "FAILED") {
    return `EXPORT_RETRY_STATUS_CONFLICT:${job.status}`;
  }
  if (!job.retryable) {
    return "EXPORT_JOB_RETRY_DENIED";
  }
  return undefined;
}

function cancelDisabledReason(job: Pick<ExportJobListItemVO | ExportJobDetailVO, "status">): string | undefined {
  if (job.status === "SUCCESS" || job.status === "CANCELED") {
    return `EXPORT_CANCEL_STATUS_CONFLICT:${job.status}`;
  }
  return undefined;
}

function localValidationResult<TData>(errors: ExportValidationError[]): Promise<ExportPageResult<TData>> {
  return Promise.resolve({
    state: {
      loading: false,
      empty: true,
      errorMessage: "COMMON_PARAM_INVALID",
      retryable: false,
      validationErrors: errors,
    },
  });
}

function isEmpty(data: unknown): boolean {
  if (data === undefined || data === null) {
    return true;
  }
  if (Array.isArray(data)) {
    return data.length === 0;
  }
  if (typeof data === "object" && "records" in data) {
    return Array.isArray((data as PageResult<unknown>).records) && (data as PageResult<unknown>).records.length === 0;
  }
  return false;
}

function createIdempotencyKey(apiId: ApiEndpointId): string {
  return `${apiId}_${Date.now()}_${Math.random().toString(36).slice(2, 10)}`;
}
