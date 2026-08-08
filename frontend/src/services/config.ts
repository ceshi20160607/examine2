import { apiRequest } from './api'
import type { CheckReport, ConfigAction, ConfigComponent, ConfigDictionary, ConfigDictionaryItem, ConfigField, ConfigGroup, ConfigModule, ConfigPage, ConfigRoot, ConfigRule, ConfigVersion, PermissionPreview, PublishResult, RuntimeAutosaveRecordInput, RuntimeAutosaveRecordResponse, RuntimeBatchCommandInput, RuntimeBatchCommandResponse, RuntimeBatchEditInput, RuntimeBatchEditResponse, RuntimeBatchTransferInput, RuntimeBatchTransferResponse, RuntimeCreateRecordInput, RuntimeDefinition, RuntimeDeleteSavedViewResponse, RuntimeLifecycleCommand, RuntimeMyDraftsQuery, RuntimeNavigation, RuntimeRecordDetail, RuntimeRecordFlowState, RuntimeRecordMutationResponse, RuntimeRecordNeighborInput, RuntimeRecordNeighborResponse, RuntimeRecordPage, RuntimeRecordQuery, RuntimeRecordSchema, RuntimeReferenceRetryResponse, RuntimeRelationCandidatePage, RuntimeRelationMutationInput, RuntimeRelationPage, RuntimeSavedView, RuntimeSavedViewList, RuntimeSubtableMutationInput, RuntimeSubtablePage, RuntimeUpdateRecordInput, VersionDiff } from '@/types/config'

const key = () => crypto.randomUUID()
const base = (systemId: string) => `/api/v1/systems/${systemId}/admin/config`
const moduleBase = (systemId: string, moduleId: string) => `${base(systemId)}/modules/${moduleId}`

export const configApi = {
  root: (systemId: string) => apiRequest<ConfigRoot>(base(systemId)),
  groups: (systemId: string) => apiRequest<ConfigGroup[]>(`${base(systemId)}/module-groups`),
  createGroup: (systemId: string, body: unknown) => apiRequest<ConfigGroup>(`${base(systemId)}/module-groups`, { method: 'POST', body, idempotencyKey: key() }),
  updateGroup: (systemId: string, id: string, body: unknown) => apiRequest<ConfigGroup>(`${base(systemId)}/module-groups/${id}`, { method: 'PUT', body }),
  deleteGroup: (systemId: string, id: string, body: unknown) => apiRequest<void>(`${base(systemId)}/module-groups/${id}:delete`, { method: 'POST', body }),
  modules: (systemId: string) => apiRequest<ConfigModule[]>(`${base(systemId)}/modules`),
  createModule: (systemId: string, body: unknown) => apiRequest<ConfigModule>(`${base(systemId)}/modules`, { method: 'POST', body, idempotencyKey: key() }),
  copyModule: (systemId: string, sourceId: string, body: unknown) => apiRequest<ConfigModule>(`${base(systemId)}/modules/${sourceId}:copy`, { method: 'POST', body, idempotencyKey: key() }),
  updateModule: (systemId: string, id: string, body: unknown) => apiRequest<ConfigModule>(`${base(systemId)}/modules/${id}`, { method: 'PUT', body }),
  deleteModule: (systemId: string, id: string, body: unknown) => apiRequest<void>(`${base(systemId)}/modules/${id}:delete`, { method: 'POST', body }),
  dictionaries: (systemId: string) => apiRequest<ConfigDictionary[]>(`${base(systemId)}/dictionaries`),
  createDictionary: (systemId: string, body: unknown) => apiRequest<ConfigDictionary>(`${base(systemId)}/dictionaries`, { method: 'POST', body, idempotencyKey: key() }),
  updateDictionary: (systemId: string, id: string, body: unknown) => apiRequest<ConfigDictionary>(`${base(systemId)}/dictionaries/${id}`, { method: 'PUT', body }),
  deleteDictionary: (systemId: string, id: string, body: unknown) => apiRequest<void>(`${base(systemId)}/dictionaries/${id}:delete`, { method: 'POST', body }),
  dictionaryItems: (systemId: string, dictionaryId: string) => apiRequest<ConfigDictionaryItem[]>(`${base(systemId)}/dictionaries/${dictionaryId}/items`),
  createDictionaryItem: (systemId: string, dictionaryId: string, body: unknown) => apiRequest<ConfigDictionaryItem>(`${base(systemId)}/dictionaries/${dictionaryId}/items`, { method: 'POST', body, idempotencyKey: key() }),
  updateDictionaryItem: (systemId: string, dictionaryId: string, id: string, body: unknown) => apiRequest<ConfigDictionaryItem>(`${base(systemId)}/dictionaries/${dictionaryId}/items/${id}`, { method: 'PUT', body }),
  deleteDictionaryItem: (systemId: string, dictionaryId: string, id: string, body: unknown) => apiRequest<void>(`${base(systemId)}/dictionaries/${dictionaryId}/items/${id}:delete`, { method: 'POST', body }),
  fields: (systemId: string, moduleId: string) => apiRequest<ConfigField[]>(`${moduleBase(systemId, moduleId)}/fields`),
  createField: (systemId: string, moduleId: string, body: unknown) => apiRequest<ConfigField>(`${moduleBase(systemId, moduleId)}/fields`, { method: 'POST', body, idempotencyKey: key() }),
  updateField: (systemId: string, moduleId: string, id: string, body: unknown) => apiRequest<ConfigField>(`${moduleBase(systemId, moduleId)}/fields/${id}`, { method: 'PUT', body }),
  deleteField: (systemId: string, moduleId: string, id: string, body: unknown) => apiRequest<void>(`${moduleBase(systemId, moduleId)}/fields/${id}:delete`, { method: 'POST', body }),
  pages: (systemId: string, moduleId: string) => apiRequest<ConfigPage[]>(`${moduleBase(systemId, moduleId)}/pages`),
  createPage: (systemId: string, moduleId: string, body: unknown) => apiRequest<ConfigPage>(`${moduleBase(systemId, moduleId)}/pages`, { method: 'POST', body, idempotencyKey: key() }),
  updatePage: (systemId: string, moduleId: string, id: string, body: unknown) => apiRequest<ConfigPage>(`${moduleBase(systemId, moduleId)}/pages/${id}`, { method: 'PUT', body }),
  deletePage: (systemId: string, moduleId: string, id: string, body: unknown) => apiRequest<void>(`${moduleBase(systemId, moduleId)}/pages/${id}:delete`, { method: 'POST', body }),
  components: (systemId: string, moduleId: string, pageId: string) => apiRequest<ConfigComponent[]>(`${moduleBase(systemId, moduleId)}/pages/${pageId}/components`),
  createComponent: (systemId: string, moduleId: string, pageId: string, body: unknown) => apiRequest<ConfigComponent>(`${moduleBase(systemId, moduleId)}/pages/${pageId}/components`, { method: 'POST', body, idempotencyKey: key() }),
  updateComponent: (systemId: string, moduleId: string, pageId: string, id: string, body: unknown) => apiRequest<ConfigComponent>(`${moduleBase(systemId, moduleId)}/pages/${pageId}/components/${id}`, { method: 'PUT', body }),
  deleteComponent: (systemId: string, moduleId: string, pageId: string, id: string, body: unknown) => apiRequest<void>(`${moduleBase(systemId, moduleId)}/pages/${pageId}/components/${id}:delete`, { method: 'POST', body }),
  actions: (systemId: string, moduleId: string) => apiRequest<ConfigAction[]>(`${moduleBase(systemId, moduleId)}/actions`),
  createAction: (systemId: string, moduleId: string, body: unknown) => apiRequest<ConfigAction>(`${moduleBase(systemId, moduleId)}/actions`, { method: 'POST', body, idempotencyKey: key() }),
  updateAction: (systemId: string, moduleId: string, id: string, body: unknown) => apiRequest<ConfigAction>(`${moduleBase(systemId, moduleId)}/actions/${id}`, { method: 'PUT', body }),
  deleteAction: (systemId: string, moduleId: string, id: string, body: unknown) => apiRequest<void>(`${moduleBase(systemId, moduleId)}/actions/${id}:delete`, { method: 'POST', body }),
  rules: (systemId: string, moduleId: string) => apiRequest<ConfigRule[]>(`${moduleBase(systemId, moduleId)}/rules`),
  createRule: (systemId: string, moduleId: string, body: unknown) => apiRequest<ConfigRule>(`${moduleBase(systemId, moduleId)}/rules`, { method: 'POST', body, idempotencyKey: key() }),
  updateRule: (systemId: string, moduleId: string, id: string, body: unknown) => apiRequest<ConfigRule>(`${moduleBase(systemId, moduleId)}/rules/${id}`, { method: 'PUT', body }),
  deleteRule: (systemId: string, moduleId: string, id: string, body: unknown) => apiRequest<void>(`${moduleBase(systemId, moduleId)}/rules/${id}:delete`, { method: 'POST', body }),
  check: (systemId: string, draftRevision: string) => apiRequest<CheckReport>(`${base(systemId)}/checks`, { method: 'POST', body: { draftRevision } }),
  checkReport: (systemId: string, checkId: string) => apiRequest<CheckReport>(`${base(systemId)}/checks/${checkId}`),
  publish: (systemId: string, body: unknown) => apiRequest<PublishResult>(`${base(systemId)}:publish`, { method: 'POST', body, idempotencyKey: key() }),
  versions: (systemId: string) => apiRequest<ConfigVersion[]>(`${base(systemId)}/versions`),
  diff: (systemId: string, fromId: string, toId: string) => apiRequest<VersionDiff>(`${base(systemId)}/versions/${fromId}:diff/${toId}`),
  rollback: (systemId: string, versionId: string, body: unknown) => apiRequest<PublishResult>(`${base(systemId)}/versions/${versionId}:rollback`, { method: 'POST', body, idempotencyKey: key() }),
  preview: (systemId: string, memberId: string, tenantId?: string) => {
    const params = new URLSearchParams({ memberId })
    if (tenantId) params.set('tenantId', tenantId)
    return apiRequest<PermissionPreview>(`${base(systemId)}/preview?${params}`)
  },
}

export const runtimeApi = {
  navigation: (systemId: string) => apiRequest<RuntimeNavigation>(`/api/v1/systems/${systemId}/runtime/navigation`),
  definition: (systemId: string, moduleCode: string) => apiRequest<RuntimeDefinition>(`/api/v1/systems/${systemId}/runtime/modules/${moduleCode}/definition`),
  recordSchema: (systemId: string, moduleCode: string) => apiRequest<RuntimeRecordSchema>(`/api/v1/systems/${systemId}/runtime/modules/${moduleCode}/record-schema`),
  records: (systemId: string, moduleCode: string, query: { page: number; size: number; status?: string; sort?: string }) => {
    const params = new URLSearchParams({ page: String(query.page), size: String(query.size) })
    if (query.status) params.set('status', query.status)
    if (query.sort) params.set('sort', query.sort)
    return apiRequest<RuntimeRecordPage>(`/api/v1/systems/${systemId}/runtime/modules/${moduleCode}/records?${params}`)
  },
  queryRecords: (systemId: string, moduleCode: string, body: RuntimeRecordQuery) => apiRequest<RuntimeRecordPage>(`/api/v1/systems/${systemId}/runtime/modules/${moduleCode}/records:query`, { method: 'POST', body }),
  myDrafts: (systemId: string, moduleCode: string, body: RuntimeMyDraftsQuery) => apiRequest<RuntimeRecordPage>(`/api/v1/systems/${systemId}/runtime/modules/${moduleCode}/records:my-drafts-query`, { method: 'POST', body }),
  recordNeighbor: (systemId: string, moduleCode: string, recordId: string, body: RuntimeRecordNeighborInput) => apiRequest<RuntimeRecordNeighborResponse>(`/api/v1/systems/${systemId}/runtime/modules/${moduleCode}/records/${recordId}:neighbors`, { method: 'POST', body }),
  batchArchiveRecords: (systemId: string, moduleCode: string, body: RuntimeBatchCommandInput) => apiRequest<RuntimeBatchCommandResponse>(`/api/v1/systems/${systemId}/runtime/modules/${moduleCode}/records:batch-archive`, { method: 'POST', body, idempotencyKey: key() }),
  batchTrashRecords: (systemId: string, moduleCode: string, body: RuntimeBatchCommandInput) => apiRequest<RuntimeBatchCommandResponse>(`/api/v1/systems/${systemId}/runtime/modules/${moduleCode}/records:batch-trash`, { method: 'POST', body, idempotencyKey: key() }),
  batchTransferRecords: (systemId: string, moduleCode: string, body: RuntimeBatchTransferInput) => apiRequest<RuntimeBatchTransferResponse>(`/api/v1/systems/${systemId}/runtime/modules/${moduleCode}/records:batch-transfer`, { method: 'POST', body, idempotencyKey: key() }),
  batchEditRecords: (systemId: string, moduleCode: string, body: RuntimeBatchEditInput) => apiRequest<RuntimeBatchEditResponse>(`/api/v1/systems/${systemId}/runtime/modules/${moduleCode}/records:batch-edit`, { method: 'POST', body, idempotencyKey: key() }),
  savedViews: (systemId: string, moduleCode: string) => apiRequest<RuntimeSavedViewList>(`/api/v1/systems/${systemId}/runtime/saved-views?moduleCode=${encodeURIComponent(moduleCode)}`),
  createSavedView: (systemId: string, body: { moduleCode: string; name: string; query: RuntimeRecordQuery; columns: string[] }) => apiRequest<RuntimeSavedView>(`/api/v1/systems/${systemId}/runtime/saved-views`, { method: 'POST', body, idempotencyKey: key() }),
  updateSavedView: (systemId: string, viewId: string, body: { expectedVersion: number; name: string; query: RuntimeRecordQuery; columns: string[] }) => apiRequest<RuntimeSavedView>(`/api/v1/systems/${systemId}/runtime/saved-views/${viewId}`, { method: 'PUT', body, idempotencyKey: key() }),
  deleteSavedView: (systemId: string, viewId: string, expectedVersion: number) => apiRequest<RuntimeDeleteSavedViewResponse>(`/api/v1/systems/${systemId}/runtime/saved-views/${viewId}`, { method: 'DELETE', body: { expectedVersion }, idempotencyKey: key() }),
  record: (systemId: string, moduleCode: string, recordId: string) => apiRequest<RuntimeRecordDetail>(`/api/v1/systems/${systemId}/runtime/modules/${moduleCode}/records/${recordId}`),
  flowState: (systemId: string, moduleCode: string, recordId: string) =>
    apiRequest<RuntimeRecordFlowState | null>(
      `/api/v1/systems/${encodeURIComponent(systemId)}/runtime/modules/${encodeURIComponent(moduleCode)}/records/${encodeURIComponent(recordId)}/flow-state`,
    ),
  flowStates: (systemId: string, moduleCode: string, recordId: string) =>
    apiRequest<RuntimeRecordFlowState[]>(
      `/api/v1/systems/${encodeURIComponent(systemId)}/runtime/modules/${encodeURIComponent(moduleCode)}/records/${encodeURIComponent(recordId)}/flow-states`,
    ),
  relationCandidates: (systemId: string, moduleCode: string, fieldCode: string, q = '', page = 1, size = 20) => {
    const params = new URLSearchParams({ page: String(page), size: String(size) })
    if (q.trim()) params.set('q', q.trim())
    return apiRequest<RuntimeRelationCandidatePage>(`/api/v1/systems/${systemId}/runtime/modules/${moduleCode}/relations/${fieldCode}/candidates?${params}`)
  },
  relations: (systemId: string, moduleCode: string, recordId: string, fieldCode: string, page = 1, size = 100) => apiRequest<RuntimeRelationPage>(`/api/v1/systems/${systemId}/runtime/modules/${moduleCode}/records/${recordId}/relations/${fieldCode}?page=${page}&size=${size}`),
  mutateRelation: (systemId: string, moduleCode: string, recordId: string, fieldCode: string, body: RuntimeRelationMutationInput) => apiRequest<RuntimeRecordMutationResponse>(`/api/v1/systems/${systemId}/runtime/modules/${moduleCode}/records/${recordId}/relations/${fieldCode}:mutate`, { method: 'POST', body, idempotencyKey: key() }),
  subtable: (systemId: string, moduleCode: string, recordId: string, fieldCode: string, page = 1, size = 100) => apiRequest<RuntimeSubtablePage>(`/api/v1/systems/${systemId}/runtime/modules/${moduleCode}/records/${recordId}/subtables/${fieldCode}?page=${page}&size=${size}`),
  mutateSubtable: (systemId: string, moduleCode: string, recordId: string, fieldCode: string, body: RuntimeSubtableMutationInput) => apiRequest<RuntimeRecordMutationResponse>(`/api/v1/systems/${systemId}/runtime/modules/${moduleCode}/records/${recordId}/subtables/${fieldCode}:mutate`, { method: 'POST', body, idempotencyKey: key() }),
  retryReference: (systemId: string, moduleCode: string, recordId: string, fieldCode: string, expectedVersion: number) => apiRequest<RuntimeReferenceRetryResponse>(`/api/v1/systems/${systemId}/runtime/modules/${moduleCode}/records/${recordId}/references/${fieldCode}:retry`, { method: 'POST', body: { expectedVersion }, idempotencyKey: key() }),
  retryDerived: (systemId: string, moduleCode: string, recordId: string, fieldCode: string, expectedVersion: number) => apiRequest<RuntimeReferenceRetryResponse>(`/api/v1/systems/${systemId}/runtime/modules/${moduleCode}/records/${recordId}/references/${fieldCode}:retry`, { method: 'POST', body: { expectedVersion }, idempotencyKey: key() }),
  createRecord: (systemId: string, moduleCode: string, body: RuntimeCreateRecordInput) => apiRequest<RuntimeRecordDetail>(`/api/v1/systems/${systemId}/runtime/modules/${moduleCode}/records`, { method: 'POST', body, idempotencyKey: key() }),
  updateRecord: (systemId: string, moduleCode: string, recordId: string, body: RuntimeUpdateRecordInput) => apiRequest<RuntimeRecordDetail>(`/api/v1/systems/${systemId}/runtime/modules/${moduleCode}/records/${recordId}`, { method: 'PUT', body, idempotencyKey: key() }),
  autosaveRecord: (systemId: string, moduleCode: string, recordId: string, body: RuntimeAutosaveRecordInput) => apiRequest<RuntimeAutosaveRecordResponse>(`/api/v1/systems/${systemId}/runtime/modules/${moduleCode}/records/${recordId}:autosave`, { method: 'POST', body, idempotencyKey: key() }),
  activateRecord: (systemId: string, moduleCode: string, recordId: string, expectedVersion: number) => apiRequest<RuntimeRecordDetail>(`/api/v1/systems/${systemId}/runtime/modules/${moduleCode}/records/${recordId}:activate`, { method: 'POST', body: { expectedVersion }, idempotencyKey: key() }),
  lifecycleRecord: (systemId: string, moduleCode: string, recordId: string, command: RuntimeLifecycleCommand, expectedVersion: number) => apiRequest<RuntimeRecordDetail>(`/api/v1/systems/${systemId}/runtime/modules/${moduleCode}/records/${recordId}:${command}`, { method: 'POST', body: { expectedVersion }, idempotencyKey: key() }),
}
