import type { DashboardGrid, DashboardWidgetBehavior, DashboardWidgetDraft, DashboardWidgetType } from '@/types/dashboard'
import type { DataSourceStatisticsRequest, StatisticsVisualization } from '@/types/statistics'
import { statisticsDraftFromRequest, validateStatisticsDraft, type StatisticsCapabilities } from './statisticsEditorModel'

export const DASHBOARD_GRID_COLUMNS = 12
export const DASHBOARD_GRID_ROWS = 100
export const DASHBOARD_MAX_WIDGETS = 20

export interface DashboardClientIssue {
  code: string
  path: string
  message: string
}

export function defaultWidgetGrid(type: DashboardWidgetType, widgets: DashboardWidgetDraft[]): DashboardGrid {
  const width = type === 'STAT_COUNT' || type === 'STAT_VALUE' || type === 'PROGRESS'
    ? 3 : type === 'LINE_TREND' || type === 'TODO_LIST' || type === 'QUICK_ENTRY' ? 8 : 6
  const height = type === 'STAT_COUNT' || type === 'STAT_VALUE' || type === 'PROGRESS' ? 2 : 5
  for (let y = 0; y <= DASHBOARD_GRID_ROWS - height; y += 1) {
    for (let x = 0; x <= DASHBOARD_GRID_COLUMNS - width; x += 1) {
      const candidate = { x, y, width, height }
      if (!widgets.some(widget => gridsOverlap(candidate, widget.grid))) return candidate
    }
  }
  return { x: 0, y: Math.min(DASHBOARD_GRID_ROWS - height, widgets.length * 2), width, height }
}

export function isStatisticsWidget(type: DashboardWidgetType) {
  return type === 'STAT_VALUE' || type === 'BAR_CHART' || type === 'PIE_CHART'
    || type === 'LINE_TREND' || type === 'RANKING' || type === 'PROGRESS'
}

export function statisticsVisualizationForWidget(type: DashboardWidgetType): StatisticsVisualization {
  if (type === 'RANKING') return 'BAR_CHART'
  if (type === 'PROGRESS') return 'STAT_VALUE'
  if (type === 'STAT_VALUE' || type === 'BAR_CHART' || type === 'PIE_CHART' || type === 'LINE_TREND') return type
  return 'STAT_VALUE'
}

export function isListWidget(type: DashboardWidgetType) {
  return type === 'DATA_LIST' || type === 'TODO_LIST' || type === 'QUICK_ENTRY'
}

export function defaultWidgetBehavior(): DashboardWidgetBehavior {
  return { refreshSeconds: 0, clickThrough: null, styleVariant: 'STANDARD' }
}

export function isKpiWidget(type: DashboardWidgetType): type is 'KPI_VALUE' {
  return type === 'KPI_VALUE'
}

export function defaultWidgetStatistics(type: DashboardWidgetType): DataSourceStatisticsRequest | null {
  if (!isStatisticsWidget(type)) return null
  const base: DataSourceStatisticsRequest = { aggregation: 'COUNT', measureFieldCode: null, grouping: null, trend: null }
  if (type === 'BAR_CHART' || type === 'PIE_CHART' || type === 'RANKING') {
    return { ...base, grouping: { fieldCode: '', bucketLimit: 10 } }
  }
  if (type === 'LINE_TREND') {
    const start = new Date()
    start.setUTCHours(0, 0, 0, 0)
    const end = new Date(start)
    end.setUTCDate(end.getUTCDate() + 1)
    return {
      ...base,
      trend: {
        fieldCode: '', grain: 'DAY',
        startInclusive: start.toISOString().slice(0, 10), endExclusive: end.toISOString().slice(0, 10),
      },
    }
  }
  return base
}

export function validateWidgetStatistics(
  widget: DashboardWidgetDraft,
  capabilities?: StatisticsCapabilities,
): DashboardClientIssue[] {
  const path = 'statistics'
  if (!isStatisticsWidget(widget.type)) {
    return widget.statistics
      ? [{ code: 'STATISTICS_FORBIDDEN', path, message: `${widget.type} 不接受统计配置` }]
      : []
  }
  if (!widget.statistics) return [{ code: 'STATISTICS_REQUIRED', path, message: `${widget.type} 必须配置统计查询` }]
  const fallbackFields = [
    widget.statistics.measureFieldCode,
    widget.statistics.grouping?.fieldCode,
    widget.statistics.trend?.fieldCode,
  ].filter((code): code is string => Boolean(code)).map(fieldCode => ({
    fieldCode, fieldName: fieldCode, type: 'NUMBER', numeric: true, temporal: true, groupable: true,
  }))
  const effectiveCapabilities: StatisticsCapabilities = capabilities ?? {
    fields: fallbackFields, measureFields: fallbackFields, groupFields: fallbackFields, timeFields: fallbackFields,
  }
  return validateStatisticsDraft(
    statisticsDraftFromRequest(widget.statistics), effectiveCapabilities, statisticsVisualizationForWidget(widget.type),
  ).map(issue => ({ ...issue, path: `${path}.${issue.path}` }))
}

export function gridsOverlap(left: DashboardGrid, right: DashboardGrid) {
  return left.x < right.x + right.width
    && left.x + left.width > right.x
    && left.y < right.y + right.height
    && left.y + left.height > right.y
}

export function gridStyle(grid: DashboardGrid) {
  return {
    gridColumn: `${grid.x + 1} / span ${grid.width}`,
    gridRow: `${grid.y + 1} / span ${grid.height}`,
  }
}

export function validateDashboardWidgets(
  widgets: DashboardWidgetDraft[],
  statisticsCapabilitiesBySource: Record<string, StatisticsCapabilities> = {},
): DashboardClientIssue[] {
  const issues: DashboardClientIssue[] = []
  if (widgets.length < 1) issues.push({ code: 'WIDGETS_REQUIRED', path: 'widgets', message: '仪表盘至少需要一个组件' })
  if (widgets.length > DASHBOARD_MAX_WIDGETS) issues.push({ code: 'WIDGET_COUNT_INVALID', path: 'widgets', message: '仪表盘最多包含 20 个组件' })
  const seen = new Set<string>()
  widgets.forEach((widget, index) => {
    const path = `widgets[${index}]`
    const code = widget.code.trim()
    if (!code) issues.push({ code: 'WIDGET_CODE_REQUIRED', path: `${path}.code`, message: '组件编码不能为空' })
    else if (seen.has(code)) issues.push({ code: 'WIDGET_CODE_DUPLICATE', path: `${path}.code`, message: `组件编码 ${code} 重复` })
    seen.add(code)
    if (!widget.title.trim()) issues.push({ code: 'WIDGET_TITLE_REQUIRED', path: `${path}.title`, message: '组件标题不能为空' })
    if (isKpiWidget(widget.type)) {
      if (!widget.kpiId) issues.push({ code: 'WIDGET_KPI_REQUIRED', path: `${path}.kpiId`, message: '必须选择已发布 KPI' })
      if (widget.dataSourceId) issues.push({ code: 'KPI_DATA_SOURCE_FORBIDDEN', path: `${path}.dataSourceId`, message: 'KPI 组件不能提交数据源' })
      if (widget.rowLimit != null) issues.push({ code: 'KPI_ROW_LIMIT_FORBIDDEN', path: `${path}.rowLimit`, message: 'KPI 组件不能提交行数上限' })
      if (widget.statistics) issues.push({ code: 'KPI_STATISTICS_FORBIDDEN', path: `${path}.statistics`, message: 'KPI 组件不能提交统计配置' })
    } else {
      if (!widget.dataSourceId) issues.push({ code: 'WIDGET_SOURCE_REQUIRED', path: `${path}.dataSourceId`, message: '必须选择已发布数据源' })
      if (widget.kpiId) issues.push({ code: 'DATA_SOURCE_KPI_FORBIDDEN', path: `${path}.kpiId`, message: '数据源组件不能提交 KPI' })
    }
    if (widget.type === 'STAT_COUNT' && widget.rowLimit != null) {
      issues.push({ code: 'STAT_COUNT_ROW_LIMIT_FORBIDDEN', path: `${path}.rowLimit`, message: '统计卡不能设置行数上限' })
    }
    if (isListWidget(widget.type) && (!Number.isInteger(widget.rowLimit) || Number(widget.rowLimit) < 1 || Number(widget.rowLimit) > 20)) {
      issues.push({ code: 'DATA_LIST_ROW_LIMIT_INVALID', path: `${path}.rowLimit`, message: '数据列表行数必须为 1 到 20' })
    }
    if (isStatisticsWidget(widget.type) && widget.rowLimit != null) {
      issues.push({ code: 'STATISTICS_ROW_LIMIT_FORBIDDEN', path: `${path}.rowLimit`, message: '统计组件不能设置数据列表行数' })
    }
    validateWidgetStatistics(widget, widget.dataSourceId ? statisticsCapabilitiesBySource[widget.dataSourceId] : undefined)
      .forEach(issue => issues.push({ ...issue, path: `${path}.${issue.path}` }))
    const behavior = widget.behavior ?? defaultWidgetBehavior()
    if (!Number.isInteger(behavior.refreshSeconds)
        || behavior.refreshSeconds !== 0 && (behavior.refreshSeconds < 15 || behavior.refreshSeconds > 3600)) {
      issues.push({ code: 'WIDGET_REFRESH_INVALID', path: `${path}.behavior.refreshSeconds`, message: '自动刷新必须关闭或设置为 15 到 3600 秒' })
    }
    const clickThrough = behavior.clickThrough?.trim() ?? ''
    if (clickThrough && (!clickThrough.startsWith('/') || clickThrough.startsWith('//')
        || clickThrough.includes('\\') || clickThrough.includes('://') || clickThrough.length > 300)) {
      issues.push({ code: 'WIDGET_CLICK_THROUGH_INVALID', path: `${path}.behavior.clickThrough`, message: '点击跳转只能使用站内绝对路径' })
    }
    const grid = widget.grid
    if (![grid.x, grid.y, grid.width, grid.height].every(Number.isInteger)
      || grid.x < 0 || grid.y < 0 || grid.width < 1 || grid.height < 1
      || grid.x + grid.width > DASHBOARD_GRID_COLUMNS
      || grid.y + grid.height > DASHBOARD_GRID_ROWS) {
      issues.push({ code: 'WIDGET_GRID_OUT_OF_BOUNDS', path: `${path}.grid`, message: '组件桌面网格必须位于 12 × 100 的有效范围内' })
    }
    widgets.slice(0, index).forEach((other, otherIndex) => {
      if (gridsOverlap(grid, other.grid)) issues.push({
        code: 'WIDGET_GRID_OVERLAP', path: `${path}.grid`, message: `组件与第 ${otherIndex + 1} 个组件重叠`,
      })
    })
  })
  return issues
}

export function cloneWidget(widget: DashboardWidgetDraft): DashboardWidgetDraft {
  const common = {
    code: widget.code,
    type: widget.type,
    title: widget.title,
    grid: { ...widget.grid },
    behavior: widget.behavior ? { ...widget.behavior } : defaultWidgetBehavior(),
  }
  if (isKpiWidget(widget.type)) return { ...common, kpiId: widget.kpiId ?? '' }
  return {
    ...common,
    dataSourceId: widget.dataSourceId ?? '',
    rowLimit: widget.rowLimit ?? null,
    statistics: widget.statistics
      ? {
          ...widget.statistics,
          grouping: widget.statistics.grouping ? { ...widget.statistics.grouping } : null,
          trend: widget.statistics.trend ? { ...widget.statistics.trend } : null,
        }
      : null,
  }
}
