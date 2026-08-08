import { apiRequest } from './api'
import type {
  CreateDataSourceInput,
  DataSourceCatalog,
  DataSourceCheckResult,
  DataSourceConnectionCheckInput,
  DataSourceConnectionCheckResult,
  DataSourceDetail,
  DataSourceDraftRowsPreviewInput,
  DataSourceDraftRowsPreviewResult,
  DataSourcePublishResult,
  DataSourceSchemaDiscoveryInput,
  DataSourceSchemaDiscoveryResult,
  DataSourceSummary,
  DataSourceVersion,
  PublishDataSourceInput,
  PublishedHttpDataSourceRows,
  PublishedJdbcDataSourceRows,
  RuntimeDataSourceMetadata,
  RuntimeDataSourceRows,
  SaveDataSourceDraftInput,
} from '@/types/dataSource'
import type { DataSourceStatisticsCapabilities, DataSourceStatisticsRequest, DataSourceStatisticsResult } from '@/types/statistics'

const adminBase = (systemId: string) =>
  `/api/v1/systems/${encodeURIComponent(systemId)}/admin/data-sources`
const runtimeBase = (systemId: string) =>
  `/api/v1/systems/${encodeURIComponent(systemId)}/data-sources`
const sourcePath = (systemId: string, id: string) =>
  `${adminBase(systemId)}/${encodeURIComponent(id)}`

export const dataSourceAdminApi = {
  catalog(systemId: string) {
    return apiRequest<DataSourceCatalog>(`${adminBase(systemId)}/catalog`)
  },
  async list(systemId: string) {
    const result = await apiRequest<DataSourceSummary[] | { items: DataSourceSummary[] }>(adminBase(systemId))
    return Array.isArray(result) ? result : result.items
  },
  create(systemId: string, input: CreateDataSourceInput) {
    return apiRequest<DataSourceDetail>(adminBase(systemId), {
      method: 'POST',
      body: input,
      idempotencyKey: crypto.randomUUID(),
    })
  },
  detail(systemId: string, id: string) {
    return apiRequest<DataSourceDetail>(sourcePath(systemId, id))
  },
  saveDraft(systemId: string, id: string, input: SaveDataSourceDraftInput) {
    return apiRequest<DataSourceDetail>(`${sourcePath(systemId, id)}/draft`, {
      method: 'PUT',
      body: input,
    })
  },
  checkDraft(systemId: string, id: string) {
    return apiRequest<DataSourceCheckResult>(`${sourcePath(systemId, id)}/draft:check`, {
      method: 'POST',
    })
  },
  checkDraftConnection(systemId: string, id: string, input: DataSourceConnectionCheckInput) {
    return apiRequest<DataSourceConnectionCheckResult>(
      `${sourcePath(systemId, id)}/draft:connection-check`,
      { method: 'POST', body: input },
    )
  },
  discoverDraftSchema(systemId: string, id: string, input: DataSourceSchemaDiscoveryInput) {
    return apiRequest<DataSourceSchemaDiscoveryResult>(
      `${sourcePath(systemId, id)}/draft:schema-discovery`,
      { method: 'POST', body: input },
    )
  },
  previewDraftRows(systemId: string, id: string, input: DataSourceDraftRowsPreviewInput) {
    return apiRequest<DataSourceDraftRowsPreviewResult>(
      `${sourcePath(systemId, id)}/draft:rows-preview`,
      { method: 'POST', body: input },
    )
  },
  publishDraft(systemId: string, id: string, input: PublishDataSourceInput) {
    return apiRequest<DataSourcePublishResult>(`${sourcePath(systemId, id)}/draft:publish`, {
      method: 'POST',
      body: input,
      idempotencyKey: crypto.randomUUID(),
    })
  },
  versions(systemId: string, id: string) {
    return apiRequest<DataSourceVersion[]>(`${sourcePath(systemId, id)}/versions`)
  },
}

export const runtimeDataSourceApi = {
  jdbcRows(systemId: string, code: string) {
    return apiRequest<PublishedJdbcDataSourceRows>(
      `${runtimeBase(systemId)}/${encodeURIComponent(code)}/jdbc-rows`,
    )
  },
  httpRows(systemId: string, code: string) {
    return apiRequest<PublishedHttpDataSourceRows>(
      `${runtimeBase(systemId)}/${encodeURIComponent(code)}/http-rows`,
    )
  },
  metadata(systemId: string, code: string) {
    return apiRequest<RuntimeDataSourceMetadata>(
      `${runtimeBase(systemId)}/${encodeURIComponent(code)}`,
    )
  },
  rows(systemId: string, code: string, page = 1, size = 20) {
    const query = new URLSearchParams({ page: String(page), size: String(size) })
    return apiRequest<RuntimeDataSourceRows>(
      `${runtimeBase(systemId)}/${encodeURIComponent(code)}/rows?${query}`,
    )
  },
  statisticsCapabilities(systemId: string, code: string) {
    return apiRequest<DataSourceStatisticsCapabilities>(
      `${runtimeBase(systemId)}/${encodeURIComponent(code)}/statistics-capabilities`,
    )
  },
  statistics(systemId: string, code: string, input: DataSourceStatisticsRequest) {
    return apiRequest<DataSourceStatisticsResult>(
      `${runtimeBase(systemId)}/${encodeURIComponent(code)}:statistics`,
      { method: 'POST', body: input },
    )
  },
}
