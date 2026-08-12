import { apiRequest } from './api'
import { kpiDtoMapper } from './kpi'
import type {
  CreateDashboardInput,
  CreateScopedDashboardInput,
  DashboardCheckResult,
  DashboardDetail,
  DashboardPublishResult,
  DashboardSummary,
  DashboardVersion,
  RuntimeDashboard,
  SaveDashboardDraftInput,
} from '@/types/dashboard'

function normalizeRuntimeDashboard(input: RuntimeDashboard): RuntimeDashboard {
  return {
    ...input,
    widgets: (input.widgets ?? []).map(widget => ({
      ...widget,
      fields: widget.fields ?? [],
      rows: widget.rows ?? [],
      kpiTargets: kpiDtoMapper.targets(widget.kpiTargets ?? []),
    })),
  }
}

const adminBase = (systemId: string) =>
  `/api/v1/systems/${encodeURIComponent(systemId)}/admin/dashboards`
const runtimeBase = (systemId: string) =>
  `/api/v1/systems/${encodeURIComponent(systemId)}/dashboards`
const dashboardPath = (systemId: string, dashboardId: string) =>
  `${adminBase(systemId)}/${encodeURIComponent(dashboardId)}`
const scopedAdminPath = (systemId: string, placement: string, scopeKey: string) =>
  `/api/v1/systems/${encodeURIComponent(systemId)}/admin/dashboard-scopes/${encodeURIComponent(placement)}/${encodeURIComponent(scopeKey)}`
const personalPath = (systemId: string, dashboardId: string) =>
  `/api/v1/systems/${encodeURIComponent(systemId)}/dashboards/personal/${encodeURIComponent(dashboardId)}`

export const dashboardAdminApi = {
  async list(systemId: string) {
    const result = await apiRequest<DashboardSummary[] | { items: DashboardSummary[] }>(adminBase(systemId))
    return Array.isArray(result) ? result : result.items
  },
  create(systemId: string, input: CreateDashboardInput) {
    return apiRequest<DashboardDetail>(adminBase(systemId), {
      method: 'POST', body: input, idempotencyKey: crypto.randomUUID(),
    })
  },
  createScoped(systemId: string, input: CreateScopedDashboardInput) {
    const body = { code: input.code, name: input.name, description: input.description }
    const placement = encodeURIComponent(input.placement)
    const scopeKey = encodeURIComponent(input.scopeKey)
    const path = input.placement === 'PERSONAL_HOME'
      ? `/api/v1/systems/${encodeURIComponent(systemId)}/dashboards/personal/${scopeKey}`
      : `/api/v1/systems/${encodeURIComponent(systemId)}/admin/dashboard-scopes/${placement}/${scopeKey}`
    return apiRequest<DashboardDetail>(path, {
      method: 'POST', body, idempotencyKey: crypto.randomUUID(),
    })
  },
  async personal(systemId: string) {
    const result = await apiRequest<DashboardSummary[]>(
      `/api/v1/systems/${encodeURIComponent(systemId)}/dashboards/personal`,
    )
    return result ?? []
  },
  scopedList(systemId: string) {
    return apiRequest<DashboardSummary[]>(
      `/api/v1/systems/${encodeURIComponent(systemId)}/admin/dashboard-scopes`,
    )
  },
  scopedDetail(systemId: string, placement: string, scopeKey: string) {
    return apiRequest<DashboardDetail>(scopedAdminPath(systemId, placement, scopeKey))
  },
  scopedSaveDraft(systemId: string, placement: string, scopeKey: string, input: SaveDashboardDraftInput) {
    return apiRequest<DashboardDetail>(`${scopedAdminPath(systemId, placement, scopeKey)}/draft`, {
      method: 'PUT', body: input,
    })
  },
  scopedCheckDraft(systemId: string, placement: string, scopeKey: string) {
    return apiRequest<DashboardCheckResult>(`${scopedAdminPath(systemId, placement, scopeKey)}/draft:check`, {
      method: 'POST',
    })
  },
  scopedPublishDraft(systemId: string, placement: string, scopeKey: string, expectedVersion: number) {
    return apiRequest<DashboardPublishResult>(`${scopedAdminPath(systemId, placement, scopeKey)}/draft:publish`, {
      method: 'POST', body: { expectedVersion }, idempotencyKey: crypto.randomUUID(),
    })
  },
  scopedVersions(systemId: string, placement: string, scopeKey: string) {
    return apiRequest<DashboardVersion[]>(`${scopedAdminPath(systemId, placement, scopeKey)}/versions`)
  },
  personalDetail(systemId: string, dashboardId: string) {
    return apiRequest<DashboardDetail>(personalPath(systemId, dashboardId))
  },
  personalSaveDraft(systemId: string, dashboardId: string, input: SaveDashboardDraftInput) {
    return apiRequest<DashboardDetail>(`${personalPath(systemId, dashboardId)}/draft`, {
      method: 'PUT', body: input,
    })
  },
  personalCheckDraft(systemId: string, dashboardId: string) {
    return apiRequest<DashboardCheckResult>(`${personalPath(systemId, dashboardId)}/draft:check`, { method: 'POST' })
  },
  personalPublishDraft(systemId: string, dashboardId: string, expectedVersion: number) {
    return apiRequest<DashboardPublishResult>(`${personalPath(systemId, dashboardId)}/draft:publish`, {
      method: 'POST', body: { expectedVersion }, idempotencyKey: crypto.randomUUID(),
    })
  },
  personalVersions(systemId: string, dashboardId: string) {
    return apiRequest<DashboardVersion[]>(`${personalPath(systemId, dashboardId)}/versions`)
  },
  detail(systemId: string, dashboardId: string) {
    return apiRequest<DashboardDetail>(dashboardPath(systemId, dashboardId))
  },
  saveDraft(systemId: string, dashboardId: string, input: SaveDashboardDraftInput) {
    return apiRequest<DashboardDetail>(`${dashboardPath(systemId, dashboardId)}/draft`, {
      method: 'PUT', body: input,
    })
  },
  checkDraft(systemId: string, dashboardId: string) {
    return apiRequest<DashboardCheckResult>(`${dashboardPath(systemId, dashboardId)}/draft:check`, {
      method: 'POST',
    })
  },
  publishDraft(systemId: string, dashboardId: string, expectedVersion: number) {
    return apiRequest<DashboardPublishResult>(`${dashboardPath(systemId, dashboardId)}/draft:publish`, {
      method: 'POST', body: { expectedVersion }, idempotencyKey: crypto.randomUUID(),
    })
  },
  versions(systemId: string, dashboardId: string) {
    return apiRequest<DashboardVersion[]>(`${dashboardPath(systemId, dashboardId)}/versions`)
  },
}

export const runtimeDashboardApi = {
  async systemHome(systemId: string) {
    return normalizeRuntimeDashboard(await apiRequest<RuntimeDashboard>(`${runtimeBase(systemId)}/system-home`))
  },
  async byCode(systemId: string, code: string) {
    return normalizeRuntimeDashboard(await apiRequest<RuntimeDashboard>(`${runtimeBase(systemId)}/${encodeURIComponent(code)}`))
  },
  async scoped(systemId: string, placement: string, scopeKey: string) {
    return normalizeRuntimeDashboard(await apiRequest<RuntimeDashboard>(
      `${runtimeBase(systemId)}/scopes/${encodeURIComponent(placement)}/${encodeURIComponent(scopeKey)}`,
    ))
  },
}
