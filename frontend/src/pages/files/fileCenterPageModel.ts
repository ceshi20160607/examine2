import type {
  ApiClient,
  ApiContext,
  ApiEndpointId,
  ApiRequestOptions,
  DynamicFieldValue,
  EntityId,
  FileInfoVO,
  FileStatus,
  IsoDateTimeString,
  JsonValue,
  PageQuery,
  PageResult,
} from "../../api";
import type { AuthStore, ErrorStore, PermissionDecision, PermissionStore, SystemContextStore } from "../../stores";

export interface FileCenterDeps {
  apiClient: ApiClient;
  auth: AuthStore;
  systemContext: SystemContextStore;
  permission: PermissionStore;
  error: ErrorStore;
}

export interface FileRequestState {
  loading: boolean;
  empty: boolean;
  requestId?: string;
  errorMessage?: string;
  retryable: boolean;
  validationErrors?: FileValidationError[];
}

export interface FilePageResult<TData> {
  data?: TData;
  state: FileRequestState;
}

export interface FileValidationError {
  field: string;
  message: string;
}

export interface FileQueryBO extends PageQuery {
  status?: FileStatus;
  bizType?: string;
  bizId?: EntityId;
  moduleId?: EntityId;
  recordId?: EntityId;
  fieldCode?: string;
}

export interface FileListItemVO {
  fileId: EntityId;
  fileName: string;
  extension?: string;
  contentType: string;
  size: number;
  status: FileStatus;
  previewable: boolean;
  previewableReason?: string;
  downloadable?: boolean;
  downloadableReason?: string;
  downloadUrl?: string;
  ownerMemberId?: EntityId;
  refCount?: number;
  createdAt?: IsoDateTimeString;
}

export interface FileDetailVO extends Omit<FileInfoVO, "references" | "status"> {
  status: FileStatus;
  downloadUrl?: string;
  ownerMemberId?: EntityId;
  refCount?: number;
  createdAt?: IsoDateTimeString;
  references?: FileReferenceVO[];
}

export interface FileReferenceVO {
  referenceId: EntityId;
  fileId: EntityId;
  bizType?: string;
  bizId?: EntityId;
  moduleId?: EntityId;
  recordId?: EntityId;
  fieldCode?: string;
  displayName?: string;
  sortOrder?: number;
  status?: string;
  boundAt?: IsoDateTimeString;
}

export interface FileAccessVO {
  fileName: string;
  contentType: string;
  size: number;
  inline: boolean;
  downloadUrl?: string;
}

export interface FileBindDTO {
  fileId: EntityId;
  bizType?: string;
  bizId?: EntityId;
  moduleId?: EntityId;
  recordId?: EntityId;
  fieldCode?: string;
  displayName?: string;
  sortOrder?: number;
}

export interface FileUploadDraft {
  file: Blob;
  fileName?: string;
  bizType?: string;
  moduleId?: EntityId;
  recordId?: EntityId;
  fieldCode?: string;
  idempotencyKey?: string;
}

export interface FileActionState extends PermissionDecision {
  actionCode: string;
  apiId: ApiEndpointId;
  label: string;
}

export interface FilePermissionState {
  upload: FileActionState;
  view: FileActionState;
  preview: FileActionState;
  download: FileActionState;
  delete: FileActionState;
}

export interface FileAttachmentFieldModel {
  fieldCode: string;
  value: FileBindDTO[];
  displayValue: JsonValue;
}

const FILE_CONTEXT = { system: true, member: true };

const FILE_API_IDS = ["FILE-001", "FILE-002", "FILE-003", "FILE-004", "FILE-005", "FILE-006"] as const satisfies readonly ApiEndpointId[];

export function createFileCenterPageModel(deps: FileCenterDeps) {
  return {
    routeName: "files.center" as const,
    routePath: "/systems/:systemId/files",
    apiIds: FILE_API_IDS,
    actions: createFilePermissionState(deps.permission),
    files: {
      list: (query: FileQueryBO = {}) =>
        callData<PageResult<FileListItemVO>, undefined, FileQueryBO>(deps, "FILE-002", { query }),
      upload: (draft: FileUploadDraft) => {
        const normalizedDraft = {
          ...draft,
          idempotencyKey: draft.idempotencyKey ?? createIdempotencyKey("FILE-001"),
        };
        const validation = validateUploadDraft(normalizedDraft);
        return validation.length === 0
          ? callData<FileDetailVO, FormData>(deps, "FILE-001", {
              body: toUploadFormData(normalizedDraft),
              idempotencyKey: normalizedDraft.idempotencyKey,
            })
          : localValidationResult<FileDetailVO>(validation);
      },
      detail: (fileId: EntityId) => callData<FileDetailVO>(deps, "FILE-003", { pathParams: { fileId } }),
      preview: (file: Pick<FileListItemVO | FileDetailVO, "fileId" | "previewable" | "previewableReason">) => {
        const disabledReason = previewDisabledReason(file);
        return disabledReason
          ? localValidationResult<FileAccessVO>([{ field: "fileId", message: disabledReason }])
          : callData<FileAccessVO>(deps, "FILE-004", { pathParams: { fileId: file.fileId } });
      },
      download: (file: Pick<FileListItemVO | FileDetailVO, "fileId" | "downloadable" | "downloadableReason">) => {
        const disabledReason = downloadDisabledReason(file);
        return disabledReason
          ? localValidationResult<FileAccessVO>([{ field: "fileId", message: disabledReason }])
          : callData<FileAccessVO>(deps, "FILE-005", { pathParams: { fileId: file.fileId } });
      },
      delete: (file: Pick<FileListItemVO | FileDetailVO, "fileId" | "status" | "refCount">) => {
        const disabledReason = deleteDisabledReason(file);
        return disabledReason
          ? localValidationResult<FileDetailVO>([{ field: "fileId", message: disabledReason }])
          : callData<FileDetailVO>(deps, "FILE-006", { pathParams: { fileId: file.fileId } });
      },
      previewDisabledReason,
      downloadDisabledReason,
      deleteDisabledReason,
      toBindDTO,
      toAttachmentFieldModel,
      appendToDynamicFieldValue,
    },
    createUploadDraft: (file: Blob, fieldCode?: string, extra: Partial<FileUploadDraft> = {}): FileUploadDraft => ({
      ...extra,
      file,
      fieldCode,
      idempotencyKey: extra.idempotencyKey ?? createIdempotencyKey("FILE-001"),
    }),
  };
}

function createFilePermissionState(permission: PermissionStore): FilePermissionState {
  return {
    upload: actionState(permission.decide({ anyOperations: ["FILE_UPLOAD"] }), "FILE-001", "FILE_UPLOAD", "上传文件"),
    view: actionState(permission.decide({ anyOperations: ["FILE_VIEW"] }), "FILE-002", "FILE_VIEW", "查看文件"),
    preview: actionState(permission.decide({ anyOperations: ["FILE_REFERENCE_PERMISSION"] }), "FILE-004", "FILE_REFERENCE_PERMISSION", "预览文件"),
    download: actionState(permission.decide({ anyOperations: ["FILE_REFERENCE_PERMISSION"] }), "FILE-005", "FILE_REFERENCE_PERMISSION", "下载文件"),
    delete: actionState(permission.decide({ anyOperations: ["FILE_DELETE"] }), "FILE-006", "FILE_DELETE", "删除文件"),
  };
}

function actionState(decision: PermissionDecision, apiId: ApiEndpointId, actionCode: string, label: string): FileActionState {
  return {
    ...decision,
    apiId,
    actionCode,
    label,
  };
}

async function callData<TData, TBody = unknown, TQuery = Record<string, unknown>>(
  deps: FileCenterDeps,
  apiId: ApiEndpointId,
  options: ApiRequestOptions<TBody, TQuery> = {},
): Promise<FilePageResult<TData>> {
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

function apiContext(deps: FileCenterDeps, requestId?: string): ApiContext {
  return {
    ...deps.auth.toApiContext(requestId),
    tenantId: deps.systemContext.toTenantHeader(),
  };
}

function contextBlock(deps: FileCenterDeps): string | undefined {
  const missing = deps.systemContext.validate(FILE_CONTEXT);
  if (missing.length > 0) {
    return `SYS_CONTEXT_REQUIRED: ${missing.join(",")}`;
  }
  const state = deps.systemContext.getState();
  if (state.status === "disabled") {
    return state.disabledReason ?? "SYS_DISABLED";
  }
  return undefined;
}

function validateUploadDraft(draft: FileUploadDraft): FileValidationError[] {
  const errors: FileValidationError[] = [];
  if (!draft.file) {
    errors.push({ field: "file", message: "上传文件必填" });
  }
  if (!draft.idempotencyKey) {
    errors.push({ field: "idempotencyKey", message: "幂等键必填" });
  }
  return errors;
}

function toUploadFormData(draft: FileUploadDraft): FormData {
  const formData = new FormData();
  formData.append("file", draft.file, draft.fileName);
  appendOptional(formData, "bizType", draft.bizType);
  appendOptional(formData, "moduleId", draft.moduleId);
  appendOptional(formData, "recordId", draft.recordId);
  appendOptional(formData, "fieldCode", draft.fieldCode);
  appendOptional(formData, "idempotencyKey", draft.idempotencyKey);
  return formData;
}

function appendOptional(formData: FormData, key: string, value: string | number | undefined): void {
  if (value !== undefined && value !== "") {
    formData.append(key, String(value));
  }
}

function previewDisabledReason(file: Pick<FileListItemVO | FileDetailVO, "previewable" | "previewableReason">): string | undefined {
  if (!file.previewable) {
    return file.previewableReason ?? "FILE_PREVIEW_UNSUPPORTED";
  }
  return undefined;
}

function downloadDisabledReason(file: Pick<FileListItemVO | FileDetailVO, "downloadable" | "downloadableReason">): string | undefined {
  if (!file.downloadable) {
    return file.downloadableReason ?? "FILE_DOWNLOAD_DENIED";
  }
  return undefined;
}

function deleteDisabledReason(file: Pick<FileListItemVO | FileDetailVO, "status" | "refCount">): string | undefined {
  if (file.status === "DELETED" || file.status === "EXPIRED") {
    return `FILE_STATUS_CONFLICT:${file.status}`;
  }
  if ((file.refCount ?? 0) > 0) {
    return "FILE_REFERENCED_DELETE_DENIED";
  }
  return undefined;
}

function toBindDTO(file: Pick<FileListItemVO | FileDetailVO, "fileId" | "fileName">, fieldCode: string, sortOrder = 0, extra: Partial<FileBindDTO> = {}): FileBindDTO {
  return {
    ...extra,
    fileId: file.fileId,
    fieldCode,
    displayName: extra.displayName ?? file.fileName,
    sortOrder,
    bizType: extra.bizType ?? "MODULE_RECORD_FIELD",
  };
}

function toAttachmentFieldModel(fieldCode: string, files: FileBindDTO[] = []): FileAttachmentFieldModel {
  return {
    fieldCode,
    value: files,
    displayValue: files.map((file) => ({
      fileId: file.fileId,
      displayName: file.displayName,
      sortOrder: file.sortOrder,
      bizType: file.bizType,
    })) as unknown as JsonValue,
  };
}

function appendToDynamicFieldValue(
  current: DynamicFieldValue<FileBindDTO[]>,
  file: Pick<FileListItemVO | FileDetailVO, "fileId" | "fileName">,
  extra: Partial<FileBindDTO> = {},
): DynamicFieldValue<FileBindDTO[]> {
  const currentFiles = Array.isArray(current.value) ? current.value : [];
  const next = toBindDTO(file, current.fieldCode, currentFiles.length, extra);
  const value = [...currentFiles.filter((item) => item.fileId !== next.fileId), next];
  return {
    ...current,
    value,
    displayValue: toAttachmentFieldModel(current.fieldCode, value).displayValue,
  };
}

function localValidationResult<TData>(errors: FileValidationError[]): Promise<FilePageResult<TData>> {
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
