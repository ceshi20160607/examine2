import type { RuntimeDataSourceField, RuntimeDataSourceRow } from './dataSource'
import type { KpiPeriodType, KpiSubjectType, KpiTarget } from './kpi'
import type { DataSourceStatisticsRequest, DataSourceStatisticsResult } from './statistics'

export type DashboardPlacement = 'SYSTEM_HOME' | 'APPLICATION_HOME' | 'MODULE_HOME' | 'PERSONAL_HOME'
export type DashboardWidgetType = 'STAT_COUNT' | 'DATA_LIST' | 'STAT_VALUE' | 'BAR_CHART' | 'PIE_CHART' | 'LINE_TREND' | 'RANKING' | 'PROGRESS' | 'TODO_LIST' | 'QUICK_ENTRY' | 'KPI_VALUE'
export type DashboardCheckSeverity = 'BLOCKER' | 'WARNING'

export interface DashboardGrid {
  x: number
  y: number
  width: number
  height: number
}

export interface DashboardWidgetDraft {
  code: string
  type: DashboardWidgetType
  title: string
  dataSourceId?: string | null
  kpiId?: string | null
  rowLimit?: number | null
  statistics?: DataSourceStatisticsRequest | null
  behavior?: DashboardWidgetBehavior | null
  grid: DashboardGrid
}

export interface DashboardWidgetBehavior {
  refreshSeconds: number
  clickThrough?: string | null
  styleVariant: 'STANDARD' | 'COMPACT' | 'EMPHASIS'
}

export interface DashboardDraft {
  widgets: DashboardWidgetDraft[]
}

export interface DashboardSummary {
  id: string
  systemId: string
  tenantId: string
  code: string
  placement: DashboardPlacement
  scopeKey?: string | null
  name: string
  description: string | null
  draftVersion: number
  activeVersionId: string | null
  activeVersionNumber: number | null
  createdAt: string
  updatedAt: string
  version: number
}

export interface DashboardDetail extends DashboardSummary {
  draft: DashboardDraft
}

export interface CreateDashboardInput {
  code: string
  placement: DashboardPlacement
  name: string
  description: string | null
}

export interface CreateScopedDashboardInput {
  code: string
  placement: DashboardPlacement
  scopeKey: string
  name: string
  description: string | null
}

export interface SaveDashboardDraftInput extends DashboardDraft {
  expectedVersion: number
  name: string
  description?: string | null
}

export interface DashboardCheckIssue {
  severity: DashboardCheckSeverity
  code: string
  path: string
  message: string
}

export interface DashboardCheckResult {
  dashboardId: string
  checkedDraftVersion: number
  valid: boolean
  blockerCount: number
  warningCount: number
  issues: DashboardCheckIssue[]
}

export interface DashboardVersionWidget extends DashboardWidgetDraft {
  id: string
  ordinal: number
  dataSourceVersionNumber?: number | null
  dataSourceCode?: string | null
  dataSourceVersionId?: string | null
  moduleCode?: string | null
  schemaVersionId?: string | null
  kpiId?: string | null
  kpiVersionId?: string | null
  kpiVersionNumber?: number | null
  kpiCode?: string | null
  kpiName?: string | null
  kpiSubjectType?: KpiSubjectType | null
  kpiPeriodType?: KpiPeriodType | null
}

export interface DashboardVersion {
  id: string
  dashboardId: string
  versionNumber: number
  sourceDraftVersion: number
  code: string
  placement: DashboardPlacement
  name: string
  description?: string | null
  snapshot: DashboardDraft
  fingerprint: string
  widgetCount: number
  publishedBy: string
  publishedAt: string
  active: boolean
  widgets: DashboardVersionWidget[]
}

export interface DashboardPublishResult {
  dashboard: DashboardDetail
  version: DashboardVersion
}

export interface RuntimeDashboardWidgetError {
  code: string
  message: string
}

export interface RuntimeDashboardWidget {
  code: string
  type: DashboardWidgetType
  title: string
  ordinal: number
  grid: DashboardGrid
  dataSourceId?: string | null
  dataSourceVersionId?: string | null
  dataSourceVersionNumber?: number | null
  dataSourceCode?: string | null
  kpiId?: string | null
  kpiVersionId?: string | null
  kpiVersionNumber?: number | null
  kpiCode?: string | null
  kpiName?: string | null
  kpiSubjectType?: KpiSubjectType | null
  kpiPeriodType?: KpiPeriodType | null
  rowLimit?: number | null
  behavior?: DashboardWidgetBehavior | null
  status: 'OK' | 'ERROR'
  error: RuntimeDashboardWidgetError | null
  total: number | null
  statisticsResult?: DataSourceStatisticsResult | null
  fields: RuntimeDataSourceField[]
  rows: RuntimeDataSourceRow[]
  kpiTargets: KpiTarget[]
}

export interface RuntimeDashboard {
  id: string
  code: string
  name: string
  description: string | null
  placement: DashboardPlacement
  versionId: string
  versionNumber: number
  widgets: RuntimeDashboardWidget[]
}
