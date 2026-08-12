import { apiRequest } from './api'
import type {
  AddSignFlowInstanceInput,
  ApprovalTaskPage,
  ApprovalTaskQuery,
  CancelFlowClaimInput,
  ClaimableTaskPage,
  ClaimFlowInstanceInput,
  CompleteFlowExternalTaskInput,
  CopyFlowInstanceInput,
  CreateFlowDefinitionInput,
  CreateFlowDelegationInput,
  CreateFlowCommentInput,
  FlowComment,
  FlowCommentPage,
  FlowCopy,
  FlowCopyPage,
  FlowDefinitionDraft,
  FlowDraftCheck,
  FlowDraftSimulation,
  FlowDefinitionPage,
  FlowDefinitionVersion,
  FlowDefinitionVersionPage,
  FlowDelegationPage,
  FlowDelegationRule,
  FlowDecisionCommentTemplate,
  FlowDecisionCommentTemplatePage,
  FlowDecisionCommentTemplateStatus,
  FlowDecisionEvidenceInput,
  FlowCompletionExecution,
  FlowCompensationExecution,
  FlowExternalTaskClaim,
  FlowExternalTaskPage,
  FlowHistory,
  FlowExtensionGraph,
  FlowExtensionNodeCatalogEntry,
  FlowExtensionPublishImpact,
  FlowExtensionStoredGraph,
  FlowInstance,
  FlowInstanceListQuery,
  FlowInstancePage,
  FlowPeriodicScheduleState,
  FlowNodeExecution,
  FlowNodeExecutionEvent,
  FlowNodeForm,
  FlowNodeFormHistory,
  FlowNodeFormWriteResult,
  FlowRecordMemberFieldCatalog,
  FlowStartableDefinitionPage,
  FlowUrge,
  FlowUrgePage,
  FailFlowExternalTaskInput,
  ReduceSignFlowInstanceInput,
  ReturnFlowInstanceInput,
  SaveFlowDecisionCommentTemplateInput,
  StartFlowInstanceInput,
  SimulateFlowDraftInput,
  TerminateFlowInstanceInput,
  TransferFlowInstanceInput,
  UrgeFlowInstanceInput,
  WithdrawFlowInstanceInput,
} from '@/types/flow'

function root(systemId: string) {
  return `/api/v1/systems/${encodeURIComponent(systemId)}/flow`
}

export const flowApi = {
  listRecordMemberFields(systemId: string, moduleCode: string) {
    const search = new URLSearchParams({ moduleCode })
    return apiRequest<FlowRecordMemberFieldCatalog>(
      `${root(systemId)}/approver-sources/record-member-fields?${search}`,
    )
  },
  listDefinitions(systemId: string, page = 1, size = 20) {
    return apiRequest<FlowDefinitionPage>(
      `${root(systemId)}/definitions?page=${page}&size=${size}`,
    )
  },
  createDefinition(systemId: string, input: CreateFlowDefinitionInput) {
    return apiRequest<FlowDefinitionDraft>(`${root(systemId)}/definitions`, {
      method: 'POST',
      body: input,
      idempotencyKey: crypto.randomUUID(),
    })
  },
  reviseDefinition(systemId: string, definitionId: string, input: CreateFlowDefinitionInput) {
    return apiRequest<FlowDefinitionDraft>(
      `${root(systemId)}/definitions/${encodeURIComponent(definitionId)}/draft`,
      {
        method: 'PUT',
        body: input,
        idempotencyKey: crypto.randomUUID(),
      },
    )
  },
  publishDefinition(systemId: string, definitionId: string) {
    return apiRequest<FlowDefinitionVersion>(
      `${root(systemId)}/definitions/${encodeURIComponent(definitionId)}:publish`,
      {
        method: 'POST',
        idempotencyKey: crypto.randomUUID(),
      },
    )
  },
  checkDefinitionDraft(systemId: string, definitionId: string) {
    return apiRequest<FlowDraftCheck>(
      `${root(systemId)}/definitions/${encodeURIComponent(definitionId)}/draft:check`,
      {
        method: 'POST',
        idempotencyKey: crypto.randomUUID(),
      },
    )
  },
  simulateDefinitionDraft(
    systemId: string,
    definitionId: string,
    input: SimulateFlowDraftInput = {},
  ) {
    return apiRequest<FlowDraftSimulation>(
      `${root(systemId)}/definitions/${encodeURIComponent(definitionId)}/draft:simulate`,
      {
        method: 'POST',
        body: input,
        idempotencyKey: crypto.randomUUID(),
      },
    )
  },
  listDefinitionVersions(systemId: string, definitionId: string, page = 1, size = 10) {
    return apiRequest<FlowDefinitionVersionPage>(
      `${root(systemId)}/definitions/${encodeURIComponent(definitionId)}/versions?page=${page}&size=${size}`,
    )
  },
  restoreDefinitionVersion(systemId: string, definitionId: string, version: number) {
    return apiRequest<FlowDefinitionDraft>(
      `${root(systemId)}/definitions/${encodeURIComponent(definitionId)}/versions/${version}:restore`,
      {
        method: 'POST',
        idempotencyKey: crypto.randomUUID(),
      },
    )
  },
  periodicSchedule(systemId: string, definitionId: string) {
    return apiRequest<FlowPeriodicScheduleState>(
      `${root(systemId)}/definitions/${encodeURIComponent(definitionId)}/periodic-schedule`,
    )
  },
  listDecisionCommentTemplates(
    systemId: string,
    status?: FlowDecisionCommentTemplateStatus,
    page = 1,
    size = 100,
  ) {
    const search = new URLSearchParams({
      ...(status ? { status } : {}),
      page: String(page),
      size: String(size),
    })
    return apiRequest<FlowDecisionCommentTemplatePage>(
      `${root(systemId)}/decision-comment-templates?${search}`,
    )
  },
  createDecisionCommentTemplate(
    systemId: string,
    input: SaveFlowDecisionCommentTemplateInput,
  ) {
    return apiRequest<FlowDecisionCommentTemplate>(
      `${root(systemId)}/decision-comment-templates`,
      {
        method: 'POST',
        body: input,
        idempotencyKey: crypto.randomUUID(),
      },
    )
  },
  updateDecisionCommentTemplate(
    systemId: string,
    templateId: string,
    input: SaveFlowDecisionCommentTemplateInput,
  ) {
    return apiRequest<FlowDecisionCommentTemplate>(
      `${root(systemId)}/decision-comment-templates/${encodeURIComponent(templateId)}`,
      {
        method: 'PUT',
        body: input,
        idempotencyKey: crypto.randomUUID(),
      },
    )
  },
  activateDecisionCommentTemplate(systemId: string, templateId: string) {
    return apiRequest<FlowDecisionCommentTemplate>(
      `${root(systemId)}/decision-comment-templates/${encodeURIComponent(templateId)}:activate`,
      {
        method: 'POST',
        idempotencyKey: crypto.randomUUID(),
      },
    )
  },
  deactivateDecisionCommentTemplate(systemId: string, templateId: string) {
    return apiRequest<FlowDecisionCommentTemplate>(
      `${root(systemId)}/decision-comment-templates/${encodeURIComponent(templateId)}:deactivate`,
      {
        method: 'POST',
        idempotencyKey: crypto.randomUUID(),
      },
    )
  },
  listStartableDefinitions(systemId: string, page = 1, size = 100) {
    return apiRequest<FlowStartableDefinitionPage>(
      `${root(systemId)}/startable-definitions?page=${page}&size=${size}`,
    )
  },
  startInstance(
    systemId: string,
    definitionId: string,
    input: StartFlowInstanceInput,
    idempotencyKey: string,
  ) {
    return apiRequest<FlowInstance>(
      `${root(systemId)}/definitions/${encodeURIComponent(definitionId)}/instances`,
      {
        method: 'POST',
        body: input,
        idempotencyKey,
      },
    )
  },
  listInstances(
    systemId: string,
    pageOrQuery: number | FlowInstanceListQuery = 1,
    size = 20,
  ) {
    const query = typeof pageOrQuery === 'number'
      ? { page: pageOrQuery, size }
      : pageOrQuery
    const search = new URLSearchParams({
      page: String(query.page ?? 1),
      size: String(query.size ?? 20),
    })
    if (query.status) search.set('status', query.status)
    if (query.from) search.set('from', query.from)
    if (query.to) search.set('to', query.to)
    return apiRequest<FlowInstancePage>(
      `${root(systemId)}/instances?${search.toString()}`,
    )
  },
  listApprovalTasks(systemId: string, query: ApprovalTaskQuery = {}) {
    const search = new URLSearchParams({
      status: query.status ?? 'PENDING',
      page: String(query.page ?? 1),
      size: String(query.size ?? 20),
    })
    return apiRequest<ApprovalTaskPage>(`${root(systemId)}/tasks?${search}`)
  },
  listClaimableTasks(systemId: string, page = 1, size = 20) {
    const search = new URLSearchParams({ page: String(page), size: String(size) })
    return apiRequest<ClaimableTaskPage>(`${root(systemId)}/claimable-tasks?${search}`)
  },
  listExternalTasks(systemId: string, topic = '', page = 1, size = 20) {
    const search = new URLSearchParams({
      ...(topic ? { topic } : {}),
      page: String(page),
      size: String(size),
    })
    return apiRequest<FlowExternalTaskPage>(`${root(systemId)}/external-tasks?${search}`)
  },
  claimExternalTask(systemId: string, executionId: string) {
    return apiRequest<FlowExternalTaskClaim>(
      `${root(systemId)}/external-tasks/${encodeURIComponent(executionId)}:claim`,
      {
        method: 'POST',
        idempotencyKey: crypto.randomUUID(),
      },
    )
  },
  heartbeatExternalTask(systemId: string, executionId: string, leaseToken: string) {
    return apiRequest<FlowCompletionExecution>(
      `${root(systemId)}/external-tasks/${encodeURIComponent(executionId)}:heartbeat`,
      {
        method: 'POST',
        body: { leaseToken },
        idempotencyKey: crypto.randomUUID(),
      },
    )
  },
  completeExternalTask(
    systemId: string,
    executionId: string,
    input: CompleteFlowExternalTaskInput,
  ) {
    return apiRequest<FlowCompletionExecution>(
      `${root(systemId)}/external-tasks/${encodeURIComponent(executionId)}:complete`,
      {
        method: 'POST',
        body: input,
        idempotencyKey: crypto.randomUUID(),
      },
    )
  },
  failExternalTask(
    systemId: string,
    executionId: string,
    input: FailFlowExternalTaskInput,
  ) {
    return apiRequest<FlowCompletionExecution>(
      `${root(systemId)}/external-tasks/${encodeURIComponent(executionId)}:fail`,
      {
        method: 'POST',
        body: input,
        idempotencyKey: crypto.randomUUID(),
      },
    )
  },
  listDelegations(
    systemId: string,
    delegatorMemberId?: string,
    page = 1,
    size = 20,
  ) {
    const search = new URLSearchParams({
      ...(delegatorMemberId ? { delegatorMemberId } : {}),
      page: String(page),
      size: String(size),
    })
    return apiRequest<FlowDelegationPage>(`${root(systemId)}/delegations?${search}`)
  },
  createDelegation(systemId: string, input: CreateFlowDelegationInput) {
    return apiRequest<FlowDelegationRule>(`${root(systemId)}/delegations`, {
      method: 'POST',
      body: input,
      idempotencyKey: crypto.randomUUID(),
    })
  },
  revokeDelegation(systemId: string, delegationRuleId: string) {
    return apiRequest<FlowDelegationRule>(
      `${root(systemId)}/delegations/${encodeURIComponent(delegationRuleId)}/revoke`,
      {
        method: 'POST',
        idempotencyKey: crypto.randomUUID(),
      },
    )
  },
  instance(systemId: string, instanceId: string) {
    return apiRequest<FlowInstance>(
      `${root(systemId)}/instances/${encodeURIComponent(instanceId)}`,
    )
  },
  approve(
    systemId: string,
    instanceId: string,
    comment: string,
    representedMemberId?: string,
    evidence: FlowDecisionEvidenceInput = {},
  ) {
    return apiRequest<FlowInstance>(`${root(systemId)}/instances/${encodeURIComponent(instanceId)}:approve`, {
      method: 'POST',
      body: {
        ...(evidence.commentTemplateId ? {} : { comment }),
        ...(representedMemberId ? { representedMemberId } : {}),
        ...evidence,
      },
      idempotencyKey: crypto.randomUUID(),
    })
  },
  reject(
    systemId: string,
    instanceId: string,
    reason: string,
    representedMemberId?: string,
    evidence: FlowDecisionEvidenceInput = {},
  ) {
    return apiRequest<FlowInstance>(`${root(systemId)}/instances/${encodeURIComponent(instanceId)}:reject`, {
      method: 'POST',
      body: {
        ...(evidence.commentTemplateId ? {} : { reason }),
        ...(representedMemberId ? { representedMemberId } : {}),
        ...evidence,
      },
      idempotencyKey: crypto.randomUUID(),
    })
  },
  approveBranch(
    systemId: string,
    instanceId: string,
    branchCode: string,
    comment: string,
    representedMemberId?: string,
    evidence: FlowDecisionEvidenceInput = {},
  ) {
    return apiRequest<FlowInstance>(
      `${root(systemId)}/instances/${encodeURIComponent(instanceId)}`
        + `/branches/${encodeURIComponent(branchCode)}:approve`,
      {
        method: 'POST',
        body: {
          ...(evidence.commentTemplateId ? {} : { comment }),
          ...(representedMemberId ? { representedMemberId } : {}),
          ...evidence,
        },
        idempotencyKey: crypto.randomUUID(),
      },
    )
  },
  rejectBranch(
    systemId: string,
    instanceId: string,
    branchCode: string,
    reason: string,
    representedMemberId?: string,
    evidence: FlowDecisionEvidenceInput = {},
  ) {
    return apiRequest<FlowInstance>(
      `${root(systemId)}/instances/${encodeURIComponent(instanceId)}`
        + `/branches/${encodeURIComponent(branchCode)}:reject`,
      {
        method: 'POST',
        body: {
          ...(evidence.commentTemplateId ? {} : { reason }),
          ...(representedMemberId ? { representedMemberId } : {}),
          ...evidence,
        },
        idempotencyKey: crypto.randomUUID(),
      },
    )
  },
  withdraw(systemId: string, instanceId: string, input: WithdrawFlowInstanceInput) {
    return apiRequest<FlowInstance>(
      `${root(systemId)}/instances/${encodeURIComponent(instanceId)}:withdraw`,
      {
        method: 'POST',
        body: input,
        idempotencyKey: crypto.randomUUID(),
      },
    )
  },
  terminate(systemId: string, instanceId: string, input: TerminateFlowInstanceInput) {
    return apiRequest<FlowInstance>(
      `${root(systemId)}/instances/${encodeURIComponent(instanceId)}:terminate`,
      {
        method: 'POST',
        body: input,
        idempotencyKey: crypto.randomUUID(),
      },
    )
  },
  listUrges(systemId: string, instanceId: string, page = 1, size = 20) {
    const search = new URLSearchParams({ page: String(page), size: String(size) })
    return apiRequest<FlowUrgePage>(
      `${root(systemId)}/instances/${encodeURIComponent(instanceId)}/urges?${search}`,
    )
  },
  urge(systemId: string, instanceId: string, input: UrgeFlowInstanceInput) {
    return apiRequest<FlowUrge>(
      `${root(systemId)}/instances/${encodeURIComponent(instanceId)}:urge`,
      {
        method: 'POST',
        body: input,
        idempotencyKey: crypto.randomUUID(),
      },
    )
  },
  listComments(systemId: string, instanceId: string, page = 1, size = 20) {
    const search = new URLSearchParams({ page: String(page), size: String(size) })
    return apiRequest<FlowCommentPage>(
      `${root(systemId)}/instances/${encodeURIComponent(instanceId)}/comments?${search}`,
    )
  },
  createComment(systemId: string, instanceId: string, input: CreateFlowCommentInput) {
    return apiRequest<FlowComment>(
      `${root(systemId)}/instances/${encodeURIComponent(instanceId)}/comments`,
      {
        method: 'POST',
        body: input,
        idempotencyKey: crypto.randomUUID(),
      },
    )
  },
  transfer(systemId: string, instanceId: string, input: TransferFlowInstanceInput) {
    return apiRequest<FlowInstance>(
      `${root(systemId)}/instances/${encodeURIComponent(instanceId)}:transfer`,
      {
        method: 'POST',
        body: input,
        idempotencyKey: crypto.randomUUID(),
      },
    )
  },
  addSign(systemId: string, instanceId: string, input: AddSignFlowInstanceInput) {
    return apiRequest<FlowInstance>(
      `${root(systemId)}/instances/${encodeURIComponent(instanceId)}:add-sign`,
      {
        method: 'POST',
        body: input,
        idempotencyKey: crypto.randomUUID(),
      },
    )
  },
  reduceSign(systemId: string, instanceId: string, input: ReduceSignFlowInstanceInput) {
    return apiRequest<FlowInstance>(
      `${root(systemId)}/instances/${encodeURIComponent(instanceId)}:reduce-sign`,
      {
        method: 'POST',
        body: input,
        idempotencyKey: crypto.randomUUID(),
      },
    )
  },
  listCopies(systemId: string, instanceId: string, page = 1, size = 20) {
    const search = new URLSearchParams({ page: String(page), size: String(size) })
    return apiRequest<FlowCopyPage>(
      `${root(systemId)}/instances/${encodeURIComponent(instanceId)}/copies?${search}`,
    )
  },
  copy(systemId: string, instanceId: string, input: CopyFlowInstanceInput) {
    return apiRequest<FlowCopy>(
      `${root(systemId)}/instances/${encodeURIComponent(instanceId)}/copies`,
      {
        method: 'POST',
        body: input,
        idempotencyKey: crypto.randomUUID(),
      },
    )
  },
  returnInstance(systemId: string, instanceId: string, input: ReturnFlowInstanceInput) {
    return apiRequest<FlowInstance>(
      `${root(systemId)}/instances/${encodeURIComponent(instanceId)}:return`,
      {
        method: 'POST',
        body: input,
        idempotencyKey: crypto.randomUUID(),
      },
    )
  },
  cancelClaim(systemId: string, instanceId: string, input: CancelFlowClaimInput) {
    return apiRequest<FlowInstance>(
      `${root(systemId)}/instances/${encodeURIComponent(instanceId)}:cancel-claim`,
      {
        method: 'POST',
        body: input,
        idempotencyKey: crypto.randomUUID(),
      },
    )
  },
  claim(systemId: string, instanceId: string, input: ClaimFlowInstanceInput) {
    return apiRequest<FlowInstance>(
      `${root(systemId)}/instances/${encodeURIComponent(instanceId)}:claim`,
      {
        method: 'POST',
        body: input,
        idempotencyKey: crypto.randomUUID(),
      },
    )
  },
  extensionNodeCatalog(systemId: string) {
    return apiRequest<FlowExtensionNodeCatalogEntry[]>(`${root(systemId)}/node-catalog`)
  },
  extensionDraft(systemId: string, definitionId: string) {
    return apiRequest<FlowExtensionStoredGraph | null>(
      `${root(systemId)}/definitions/${encodeURIComponent(definitionId)}/extension-draft`,
    )
  },
  saveExtensionDraft(
    systemId: string,
    definitionId: string,
    expectedRevision: number,
    graph: FlowExtensionGraph,
  ) {
    return apiRequest<FlowExtensionStoredGraph>(
      `${root(systemId)}/definitions/${encodeURIComponent(definitionId)}/extension-draft`,
      { method: 'PUT', body: { expectedRevision, graph } },
    )
  },
  extensionPublishImpact(systemId: string, definitionId: string) {
    return apiRequest<FlowExtensionPublishImpact>(
      `${root(systemId)}/definitions/${encodeURIComponent(definitionId)}/publish-impact`,
    )
  },
  nodeForm(systemId: string, instanceId: string, nodeCode: string) {
    return apiRequest<FlowNodeForm>(
      `${root(systemId)}/instances/${encodeURIComponent(instanceId)}`
        + `/nodes/${encodeURIComponent(nodeCode)}/form`,
    )
  },
  writeNodeForm(
    systemId: string,
    instanceId: string,
    nodeCode: string,
    input: {
      expectedSnapshotVersion: number
      expectedRecordVersion: number
      values: Record<string, unknown>
    },
    idempotencyKey: string,
  ) {
    return apiRequest<FlowNodeFormWriteResult>(
      `${root(systemId)}/instances/${encodeURIComponent(instanceId)}`
        + `/nodes/${encodeURIComponent(nodeCode)}/form`,
      { method: 'PUT', body: input, idempotencyKey },
    )
  },
  nodeFormHistory(systemId: string, instanceId: string, nodeCode: string) {
    return apiRequest<FlowNodeFormHistory[]>(
      `${root(systemId)}/instances/${encodeURIComponent(instanceId)}`
        + `/nodes/${encodeURIComponent(nodeCode)}/form/history`,
    )
  },
  executeExtensionNode(
    systemId: string,
    instanceId: string,
    nodeCode: string,
    expectedVersion: number | null,
    input: Record<string, unknown>,
    idempotencyKey: string,
  ) {
    return apiRequest<FlowNodeExecution>(
      `${root(systemId)}/instances/${encodeURIComponent(instanceId)}`
        + `/nodes/${encodeURIComponent(nodeCode)}:execute`,
      { method: 'POST', body: { expectedVersion, input }, idempotencyKey },
    )
  },
  resumeExtensionNode(
    systemId: string,
    instanceId: string,
    nodeCode: string,
    expectedVersion: number,
    input: Record<string, unknown>,
    idempotencyKey: string,
  ) {
    return apiRequest<FlowNodeExecution>(
      `${root(systemId)}/instances/${encodeURIComponent(instanceId)}`
        + `/nodes/${encodeURIComponent(nodeCode)}:resume`,
      { method: 'POST', body: { expectedVersion, input }, idempotencyKey },
    )
  },
  extensionNodeHistory(systemId: string, instanceId: string, nodeCode: string) {
    return apiRequest<FlowNodeExecutionEvent[]>(
      `${root(systemId)}/instances/${encodeURIComponent(instanceId)}`
        + `/nodes/${encodeURIComponent(nodeCode)}/execution-history`,
    )
  },
  history(systemId: string, instanceId: string) {
    return apiRequest<FlowHistory>(`${root(systemId)}/instances/${encodeURIComponent(instanceId)}/history`)
  },
  retryCompletionExecution(
    systemId: string,
    instanceId: string,
    executionId: string,
  ) {
    return apiRequest<FlowCompletionExecution>(
      `${root(systemId)}/instances/${encodeURIComponent(instanceId)}`
        + `/completion-executions/${encodeURIComponent(executionId)}:retry`,
      {
        method: 'POST',
        idempotencyKey: crypto.randomUUID(),
      },
    )
  },
  retryCompensationExecution(
    systemId: string,
    instanceId: string,
    compensationExecutionId: string,
  ) {
    return apiRequest<FlowCompensationExecution>(
      `${root(systemId)}/instances/${encodeURIComponent(instanceId)}`
        + `/compensation-executions/${encodeURIComponent(compensationExecutionId)}:retry`,
      {
        method: 'POST',
        idempotencyKey: crypto.randomUUID(),
      },
    )
  },
}
