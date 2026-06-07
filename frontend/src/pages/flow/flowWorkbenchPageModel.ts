import type {
  ApiClient,
  ApiContext,
  ApiEndpointId,
  ApiRequestOptions,
  AvailableAction,
  DynamicFieldValue,
  EntityId,
  FlowInstanceStatus,
  FlowTaskDetailVO,
  FlowTaskStatus,
  FlowTemplateStatus,
  IsoDateTimeString,
  JsonValue,
  PageQuery,
  PageResult,
} from "../../api";
import type { AuthStore, ErrorStore, PermissionDecision, PermissionStore, SystemContextStore } from "../../stores";

export interface FlowWorkbenchDeps {
  apiClient: ApiClient;
  auth: AuthStore;
  systemContext: SystemContextStore;
  permission: PermissionStore;
  error: ErrorStore;
}

export interface FlowRequestState {
  loading: boolean;
  empty: boolean;
  requestId?: string;
  errorMessage?: string;
  retryable: boolean;
  validationErrors?: FlowValidationError[];
}

export interface FlowPageResult<TData> {
  data?: TData;
  state: FlowRequestState;
}

export interface FlowValidationError {
  field: string;
  message: string;
}

export interface FlowTemplateVO {
  templateId: EntityId;
  templateName: string;
  moduleId?: EntityId;
  status: FlowTemplateStatus;
  version?: number;
  graphVersion?: number;
  updatedAt?: IsoDateTimeString;
  availableActions?: AvailableAction[];
}

export interface FlowTemplateSaveBO {
  templateName: string;
  moduleId?: EntityId;
  remark?: string;
}

export interface FlowTemplateGraphBO {
  graph: JsonValue;
  graphVersion?: number;
}

export interface FlowTemplateStatusBO {
  status: FlowTemplateStatus;
  version?: number;
  reason?: string;
}

export interface FlowBindingSaveBO {
  templateId: EntityId;
  moduleVersionId?: EntityId;
  enabled: boolean;
}

export interface FlowPublishCheckVO {
  passed: boolean;
  errors: FlowValidationError[];
  warnings: FlowValidationError[];
  requestId?: string;
}

export interface FlowTaskQuery extends PageQuery {
  status?: FlowTaskStatus;
  moduleId?: EntityId;
  keyword?: string;
}

export interface FlowTaskListItemVO {
  taskId: EntityId;
  taskVersion: number;
  instanceId: EntityId;
  moduleId: EntityId;
  recordId: EntityId;
  nodeId: EntityId;
  nodeName: string;
  recordTitle?: string;
  recordSummary?: JsonValue;
  taskStatus: FlowTaskStatus;
  assigneeMemberId?: EntityId;
  assigneeName?: string;
  claimedByMemberId?: EntityId;
  claimedByName?: string;
  createdAt?: IsoDateTimeString;
  dueAt?: IsoDateTimeString;
  availableActions?: AvailableAction[];
}

export interface FlowCcItemVO {
  ccId: EntityId;
  instanceId: EntityId;
  moduleId: EntityId;
  recordId: EntityId;
  recordTitle?: string;
  nodeName?: string;
  read: boolean;
  createdAt?: IsoDateTimeString;
}

export interface FlowInstanceListItemVO {
  instanceId: EntityId;
  templateId?: EntityId;
  moduleId: EntityId;
  recordId: EntityId;
  recordTitle?: string;
  starterMemberId?: EntityId;
  starterName?: string;
  status: FlowInstanceStatus;
  currentNodeName?: string;
  createdAt?: IsoDateTimeString;
  updatedAt?: IsoDateTimeString;
  availableActions?: AvailableAction[];
}

export interface FlowInstanceDetailVO extends FlowInstanceListItemVO {
  values?: DynamicFieldValue[];
  summary?: JsonValue;
  history?: FlowHistoryItemVO[];
  diagram?: FlowDiagramRenderModel;
}

export interface FlowHistoryItemVO {
  historyId: EntityId;
  instanceId: EntityId;
  taskId?: EntityId;
  nodeId?: EntityId;
  nodeName?: string;
  action: FlowTaskAction;
  comment?: string;
  operatorMemberId?: EntityId;
  operatorName?: string;
  createdAt?: IsoDateTimeString;
  requestId?: string;
}

export interface FlowDiagramVO {
  instanceId?: EntityId;
  templateId?: EntityId;
  graph: JsonValue;
  activeNodeIds?: EntityId[];
  completedNodeIds?: EntityId[];
  rejectedNodeIds?: EntityId[];
}

export interface FlowDiagramRenderModel {
  nodes: FlowDiagramNode[];
  edges: FlowDiagramEdge[];
  activeNodeIds: EntityId[];
  completedNodeIds: EntityId[];
  rejectedNodeIds: EntityId[];
}

export interface FlowDiagramNode {
  nodeId: EntityId;
  label: string;
  type?: string;
  status: "pending" | "active" | "completed" | "rejected";
  raw?: JsonValue;
}

export interface FlowDiagramEdge {
  edgeId: string;
  sourceNodeId: EntityId;
  targetNodeId: EntityId;
  label?: string;
  raw?: JsonValue;
}

export type FlowTaskAction = "APPROVE" | "REJECT" | "TRANSFER" | "RETURN" | "TERMINATE";

export interface FlowActionBO {
  action: FlowTaskAction;
  comment?: string;
  targetNodeId?: EntityId;
  returnStrategy?: string;
  transferToMemberId?: EntityId;
  taskVersion: number;
  recordVersion?: number;
  idempotencyKey: string;
}

export interface FlowTaskVersionBO {
  taskVersion: number;
  idempotencyKey: string;
}

export interface FlowWithdrawBO {
  comment: string;
  idempotencyKey: string;
}

export interface FlowMutationResultVO {
  instanceId: EntityId;
  taskId?: EntityId;
  status: FlowInstanceStatus | FlowTaskStatus;
  idempotencyReplay?: boolean;
  availableActions?: AvailableAction[];
}

export interface FlowActionState extends PermissionDecision {
  actionCode: string;
  apiId: ApiEndpointId;
  label: string;
}

export interface FlowTemplateDetailView {
  template: FlowTemplateVO;
  graph: FlowDiagramRenderModel;
  actions: FlowActionState[];
  publishDisabledReason?: string;
}

export interface FlowTaskDetailView {
  task: FlowTaskDetailVO;
  diagram: FlowDiagramRenderModel;
  actions: FlowActionState[];
  claimDisabledReason?: string;
  unclaimDisabledReason?: string;
}

export interface FlowInstanceDetailView {
  instance: FlowInstanceDetailVO;
  diagram: FlowDiagramRenderModel;
  history: FlowHistoryItemVO[];
  withdrawDisabledReason?: string;
}

export interface FlowPermissionState {
  templateView: FlowActionState;
  templateCreate: FlowActionState;
  templateEdit: FlowActionState;
  templatePublish: FlowActionState;
  bindingEdit: FlowActionState;
  todoView: FlowActionState;
  taskHandle: FlowActionState;
  instanceWithdraw: FlowActionState;
  instanceView: FlowActionState;
}

const FLOW_CONTEXT = { system: true, member: true };

const FLOW_API_IDS = [
  "FLOW-001",
  "FLOW-002",
  "FLOW-003",
  "FLOW-004",
  "FLOW-005",
  "FLOW-006",
  "FLOW-007",
  "FLOW-008",
  "FLOW-009",
  "FLOW-010",
  "FLOW-011",
  "FLOW-012",
  "FLOW-013",
  "FLOW-014",
  "FLOW-015",
  "FLOW-016",
  "FLOW-017",
  "FLOW-018",
  "FLOW-019",
  "FLOW-020",
  "FLOW-021",
] as const satisfies readonly ApiEndpointId[];

const FINISHED_TASK_STATUSES: readonly FlowTaskStatus[] = ["DONE", "CANCELED", "TRANSFERRED", "RETURNED"];
const FINISHED_INSTANCE_STATUSES: readonly FlowInstanceStatus[] = ["APPROVED", "REJECTED", "WITHDRAWN", "TERMINATED"];
const COMMENT_REQUIRED_ACTIONS: readonly FlowTaskAction[] = ["REJECT", "RETURN", "TERMINATE"];

export function createFlowWorkbenchPageModel(deps: FlowWorkbenchDeps) {
  return {
    routeNames: ["flow.templates", "flow.workbench"] as const,
    apiIds: FLOW_API_IDS,
    actions: createFlowPermissionState(deps.permission),
    templates: {
      list: (query: PageQuery = {}) =>
        callData<PageResult<FlowTemplateVO>, undefined, PageQuery>(deps, "FLOW-001", { query }),
      create: (body: FlowTemplateSaveBO) =>
        callData<FlowTemplateVO, FlowTemplateSaveBO>(deps, "FLOW-002", { body }),
      detail: async (templateId: EntityId) => {
        const result = await callData<FlowTemplateVO>(deps, "FLOW-019", { pathParams: { templateId } });
        if (!result.data) {
          return result;
        }
        const graph = await callData<FlowDiagramVO>(deps, "FLOW-020", { pathParams: { templateId } });
        return {
          ...result,
          data: {
            template: result.data,
            graph: toDiagramModel(graph.data?.graph),
            actions: pageActions(deps.permission, result.data.availableActions),
            publishDisabledReason: publishDisabledReason(result.data),
          },
        } satisfies FlowPageResult<FlowTemplateDetailView>;
      },
      saveGraph: (templateId: EntityId, body: FlowTemplateGraphBO) =>
        callData<FlowTemplateVO, FlowTemplateGraphBO>(deps, "FLOW-003", {
          pathParams: { templateId },
          body,
        }),
      publishCheck: (templateId: EntityId) =>
        callData<FlowPublishCheckVO>(deps, "FLOW-004", { pathParams: { templateId } }),
      publish: (templateId: EntityId, idempotencyKey = createIdempotencyKey("FLOW-005")) =>
        callData<FlowTemplateVO>(deps, "FLOW-005", {
          pathParams: { templateId },
          idempotencyKey,
        }),
      bindModule: (moduleId: EntityId, body: FlowBindingSaveBO) =>
        callData<FlowTemplateVO, FlowBindingSaveBO>(deps, "FLOW-006", {
          pathParams: { moduleId },
          body,
        }),
      changeStatus: (templateId: EntityId, body: FlowTemplateStatusBO) =>
        callData<FlowTemplateVO, FlowTemplateStatusBO>(deps, "FLOW-021", {
          pathParams: { templateId },
          body,
        }),
      publishDisabledReason,
    },
    workbench: {
      todo: (query: FlowTaskQuery = {}) =>
        callData<PageResult<FlowTaskListItemVO>, undefined, FlowTaskQuery>(deps, "FLOW-007", { query }),
      cc: (query: PageQuery = {}, idempotencyKey = createIdempotencyKey("FLOW-013")) =>
        callData<PageResult<FlowCcItemVO>, undefined, PageQuery>(deps, "FLOW-013", { query, idempotencyKey }),
      started: (query: PageQuery = {}) =>
        callData<PageResult<FlowInstanceListItemVO>, undefined, PageQuery>(deps, "FLOW-014", { query }),
      instances: (query: PageQuery = {}) =>
        callData<PageResult<FlowInstanceListItemVO>, undefined, PageQuery>(deps, "FLOW-017", { query }),
      taskDetail: async (taskId: EntityId) => {
        const result = await callData<FlowTaskDetailVO>(deps, "FLOW-008", { pathParams: { taskId } });
        return result.data
          ? {
              ...result,
              data: toTaskDetailView(deps.permission, result.data),
            }
          : result;
      },
      handleTask: (taskId: EntityId, body: FlowActionBO) => {
        const validation = validateActionBody(body);
        return validation.length === 0
          ? callData<FlowMutationResultVO, FlowActionBO>(deps, "FLOW-009", {
              pathParams: { taskId },
              body,
              idempotencyKey: body.idempotencyKey,
            })
          : localValidationResult<FlowMutationResultVO>(validation);
      },
      claim: (taskId: EntityId, body: FlowTaskVersionBO) =>
        callData<FlowMutationResultVO, FlowTaskVersionBO>(deps, "FLOW-015", {
          pathParams: { taskId },
          body,
          idempotencyKey: body.idempotencyKey,
        }),
      unclaim: (taskId: EntityId, body: FlowTaskVersionBO) =>
        callData<FlowMutationResultVO, FlowTaskVersionBO>(deps, "FLOW-016", {
          pathParams: { taskId },
          body,
          idempotencyKey: body.idempotencyKey,
        }),
      withdraw: (instanceId: EntityId, body: FlowWithdrawBO) => {
        const validation = body.comment?.trim() ? [] : [{ field: "comment", message: "撤回原因必填" }];
        return validation.length === 0
          ? callData<FlowMutationResultVO, FlowWithdrawBO>(deps, "FLOW-010", {
              pathParams: { instanceId },
              body,
              idempotencyKey: body.idempotencyKey,
            })
          : localValidationResult<FlowMutationResultVO>(validation);
      },
      instanceDetail: async (instanceId: EntityId) => {
        const result = await callData<FlowInstanceDetailVO>(deps, "FLOW-011", { pathParams: { instanceId } });
        if (!result.data) {
          return result;
        }
        const [diagram, history] = await Promise.all([
          callData<FlowDiagramVO>(deps, "FLOW-012", { pathParams: { instanceId } }),
          callData<FlowHistoryItemVO[]>(deps, "FLOW-018", { pathParams: { instanceId } }),
        ]);
        return {
          ...result,
          data: {
            instance: result.data,
            diagram: toDiagramModel(diagram.data?.graph, diagram.data),
            history: history.data ?? [],
            withdrawDisabledReason: withdrawDisabledReason(result.data),
          },
        } satisfies FlowPageResult<FlowInstanceDetailView>;
      },
      instanceDiagram: async (instanceId: EntityId) => {
        const result = await callData<FlowDiagramVO>(deps, "FLOW-012", { pathParams: { instanceId } });
        return result.data
          ? {
              ...result,
              data: toDiagramModel(result.data.graph, result.data),
            }
          : result;
      },
      instanceHistory: (instanceId: EntityId) =>
        callData<FlowHistoryItemVO[]>(deps, "FLOW-018", { pathParams: { instanceId } }),
      taskActionDisabledReason,
      claimDisabledReason,
      unclaimDisabledReason,
      withdrawDisabledReason,
    },
    createActionBody: (action: FlowTaskAction, taskVersion: number, draft: Partial<FlowActionBO> = {}): FlowActionBO => ({
      ...draft,
      action,
      taskVersion,
      idempotencyKey: draft.idempotencyKey ?? createIdempotencyKey("FLOW-009"),
    }),
    createTaskVersionBody: (apiId: "FLOW-015" | "FLOW-016", taskVersion: number): FlowTaskVersionBO => ({
      taskVersion,
      idempotencyKey: createIdempotencyKey(apiId),
    }),
    createWithdrawBody: (comment: string): FlowWithdrawBO => ({
      comment,
      idempotencyKey: createIdempotencyKey("FLOW-010"),
    }),
  };
}

function createFlowPermissionState(permission: PermissionStore): FlowPermissionState {
  return {
    templateView: actionState(permission.decide({ anyOperations: ["FLOW_TEMPLATE_VIEW"] }), "FLOW-001", "FLOW_TEMPLATE_VIEW", "查看流程模板"),
    templateCreate: actionState(permission.decide({ anyOperations: ["FLOW_TEMPLATE_CREATE"] }), "FLOW-002", "FLOW_TEMPLATE_CREATE", "新建流程模板"),
    templateEdit: actionState(permission.decide({ anyOperations: ["FLOW_TEMPLATE_EDIT"] }), "FLOW-003", "FLOW_TEMPLATE_EDIT", "编辑流程图"),
    templatePublish: actionState(permission.decide({ anyOperations: ["FLOW_TEMPLATE_PUBLISH"] }), "FLOW-005", "FLOW_TEMPLATE_PUBLISH", "发布流程模板"),
    bindingEdit: actionState(permission.decide({ anyOperations: ["FLOW_BINDING_EDIT"] }), "FLOW-006", "FLOW_BINDING_EDIT", "绑定模块流程"),
    todoView: actionState(permission.decide({ anyOperations: ["APPROVER"] }), "FLOW-007", "APPROVER", "查看待办"),
    taskHandle: actionState(permission.decide({ anyOperations: ["FLOW_TASK_HANDLE"] }), "FLOW-009", "FLOW_TASK_HANDLE", "处理流程任务"),
    instanceWithdraw: actionState(permission.decide({ anyOperations: ["STARTER_OR_AUTHORIZED"] }), "FLOW-010", "STARTER_OR_AUTHORIZED", "撤回流程实例"),
    instanceView: actionState(permission.decide({ anyOperations: ["FLOW_INSTANCE_VIEW"] }), "FLOW-012", "FLOW_INSTANCE_VIEW", "查看流程实例"),
  };
}

function actionState(decision: PermissionDecision, apiId: ApiEndpointId, actionCode: string, label: string): FlowActionState {
  return {
    ...decision,
    apiId,
    actionCode,
    label,
  };
}

async function callData<TData, TBody = unknown, TQuery = Record<string, unknown>>(
  deps: FlowWorkbenchDeps,
  apiId: ApiEndpointId,
  options: ApiRequestOptions<TBody, TQuery> = {},
): Promise<FlowPageResult<TData>> {
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

function apiContext(deps: FlowWorkbenchDeps, requestId?: string): ApiContext {
  return {
    ...deps.auth.toApiContext(requestId),
    tenantId: deps.systemContext.toTenantHeader(),
  };
}

function contextBlock(deps: FlowWorkbenchDeps): string | undefined {
  const missing = deps.systemContext.validate(FLOW_CONTEXT);
  if (missing.length > 0) {
    return `SYS_CONTEXT_REQUIRED: ${missing.join(",")}`;
  }
  const state = deps.systemContext.getState();
  if (state.status === "disabled") {
    return state.disabledReason ?? "SYS_DISABLED";
  }
  return undefined;
}

function toTaskDetailView(permission: PermissionStore, task: FlowTaskDetailVO): FlowTaskDetailView {
  return {
    task,
    diagram: toDiagramModel(task.diagram),
    actions: pageActions(permission, task.availableActions),
    claimDisabledReason: claimDisabledReason(task),
    unclaimDisabledReason: unclaimDisabledReason(task),
  };
}

function pageActions(permission: PermissionStore, actions: AvailableAction[] = []): FlowActionState[] {
  return normalizeActions(actions).map((action) => {
    const decision = permission.action(action.actionCode);
    return actionState(
      {
        visible: action.visible && decision.visible,
        enabled: action.enabled && decision.enabled,
        disabledReason: action.disabledReason ?? action.stateReason ?? decision.disabledReason,
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
  if (actionCode === "CLAIM") {
    return "FLOW-015";
  }
  if (actionCode === "UNCLAIM") {
    return "FLOW-016";
  }
  if (actionCode === "WITHDRAW") {
    return "FLOW-010";
  }
  if (["APPROVE", "REJECT", "TRANSFER", "RETURN", "TERMINATE"].includes(actionCode)) {
    return "FLOW-009";
  }
  return "FLOW-008";
}

function publishDisabledReason(template: Pick<FlowTemplateVO, "status">): string | undefined {
  if (template.status === "PUBLISHED") {
    return "FLOW_TEMPLATE_ALREADY_PUBLISHED";
  }
  if (template.status === "DISABLED") {
    return "FLOW_TEMPLATE_DISABLED";
  }
  return undefined;
}

function taskActionDisabledReason(task: Pick<FlowTaskListItemVO | FlowTaskDetailVO, "taskVersion"> & { taskStatus?: FlowTaskStatus }, body: FlowActionBO): string | undefined {
  if (task.taskStatus && FINISHED_TASK_STATUSES.includes(task.taskStatus)) {
    return `FLOW_TASK_ALREADY_HANDLED:${task.taskStatus}`;
  }
  const errors = validateActionBody(body);
  return errors[0]?.message;
}

function claimDisabledReason(task: Pick<FlowTaskListItemVO, "taskStatus" | "claimedByMemberId"> | FlowTaskDetailVO): string | undefined {
  const status = readTaskStatus(task);
  if (status && FINISHED_TASK_STATUSES.includes(status)) {
    return `FLOW_TASK_ALREADY_HANDLED:${status}`;
  }
  if ("claimedByMemberId" in task && task.claimedByMemberId) {
    return "FLOW_TASK_ALREADY_CLAIMED";
  }
  return undefined;
}

function unclaimDisabledReason(task: Pick<FlowTaskListItemVO, "taskStatus" | "claimedByMemberId"> | FlowTaskDetailVO): string | undefined {
  const status = readTaskStatus(task);
  if (status && FINISHED_TASK_STATUSES.includes(status)) {
    return `FLOW_TASK_ALREADY_HANDLED:${status}`;
  }
  if ("claimedByMemberId" in task && !task.claimedByMemberId) {
    return "FLOW_TASK_NOT_CLAIMED";
  }
  return undefined;
}

function withdrawDisabledReason(instance: Pick<FlowInstanceListItemVO | FlowInstanceDetailVO, "status">): string | undefined {
  if (FINISHED_INSTANCE_STATUSES.includes(instance.status)) {
    return `FLOW_INSTANCE_ALREADY_FINISHED:${instance.status}`;
  }
  if (instance.status !== "IN_APPROVAL") {
    return `FLOW_INSTANCE_STATUS_CONFLICT:${instance.status}`;
  }
  return undefined;
}

function validateActionBody(body: FlowActionBO): FlowValidationError[] {
  const errors: FlowValidationError[] = [];
  if (!body.idempotencyKey) {
    errors.push({ field: "idempotencyKey", message: "幂等键必填" });
  }
  if (body.taskVersion === undefined || body.taskVersion === null) {
    errors.push({ field: "taskVersion", message: "任务版本必填" });
  }
  if (COMMENT_REQUIRED_ACTIONS.includes(body.action) && !body.comment?.trim()) {
    errors.push({ field: "comment", message: `${body.action} 原因必填` });
  }
  if (body.action === "TRANSFER" && !body.transferToMemberId) {
    errors.push({ field: "transferToMemberId", message: "转交成员必填" });
  }
  if (body.action === "RETURN" && !body.targetNodeId && !body.returnStrategy) {
    errors.push({ field: "targetNodeId", message: "退回节点或退回策略必填" });
  }
  return errors;
}

function readTaskStatus(task: Pick<FlowTaskListItemVO, "taskStatus"> | FlowTaskDetailVO): FlowTaskStatus | undefined {
  return "taskStatus" in task ? task.taskStatus : undefined;
}

function toDiagramModel(graph: JsonValue | undefined, status?: Partial<FlowDiagramVO>): FlowDiagramRenderModel {
  const raw = readObject(graph);
  const nodes = readArray(raw?.nodes)
    .map(readObject)
    .filter(isJsonObject)
    .map((node, index) => toDiagramNode(node, index, status));
  const edges = readArray(raw?.edges)
    .map(readObject)
    .filter(isJsonObject)
    .map((edge, index) => toDiagramEdge(edge, index));

  return {
    nodes,
    edges,
    activeNodeIds: status?.activeNodeIds ?? [],
    completedNodeIds: status?.completedNodeIds ?? [],
    rejectedNodeIds: status?.rejectedNodeIds ?? [],
  };
}

function toDiagramNode(node: Record<string, JsonValue>, index: number, status?: Partial<FlowDiagramVO>): FlowDiagramNode {
  const nodeId = String(node.nodeId ?? node.id ?? `node_${index}`);
  const active = status?.activeNodeIds?.includes(nodeId);
  const completed = status?.completedNodeIds?.includes(nodeId);
  const rejected = status?.rejectedNodeIds?.includes(nodeId);
  return {
    nodeId,
    label: String(node.label ?? node.name ?? node.nodeName ?? nodeId),
    type: typeof node.type === "string" ? node.type : undefined,
    status: rejected ? "rejected" : active ? "active" : completed ? "completed" : "pending",
    raw: node,
  };
}

function toDiagramEdge(edge: Record<string, JsonValue>, index: number): FlowDiagramEdge {
  const sourceNodeId = String(edge.sourceNodeId ?? edge.source ?? "");
  const targetNodeId = String(edge.targetNodeId ?? edge.target ?? "");
  return {
    edgeId: String(edge.edgeId ?? edge.id ?? `edge_${index}`),
    sourceNodeId,
    targetNodeId,
    label: typeof edge.label === "string" ? edge.label : undefined,
    raw: edge,
  };
}

function readObject(value: JsonValue | undefined): Record<string, JsonValue> | undefined {
  return value && typeof value === "object" && !Array.isArray(value) ? (value as Record<string, JsonValue>) : undefined;
}

function isJsonObject(value: Record<string, JsonValue> | undefined): value is Record<string, JsonValue> {
  return Boolean(value);
}

function readArray(value: JsonValue | undefined): JsonValue[] {
  return Array.isArray(value) ? value : [];
}

function localValidationResult<TData>(errors: FlowValidationError[]): Promise<FlowPageResult<TData>> {
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
