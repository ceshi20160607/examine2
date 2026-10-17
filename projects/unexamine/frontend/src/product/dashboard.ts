import type { DashboardComponentInput, DashboardSourceType } from './types'

export const dashboardSourceLabels: Record<DashboardSourceType, string> = {
  PLATFORM_SYSTEMS: '已授权系统',
  MODULE_RECORDS: '模块业务记录',
  MODULE_REPORT: '结构化模块报表',
  TODO_ITEMS: '待办事项',
  MESSAGE_ITEMS: '未读消息',
  WORK_PROJECTS: '工作项目',
}

const CORE_WORKSPACE_SUMMARIES = new Set(['待处理事项', '未读消息', '可进入系统', '可用业务模块'])

export function isCoreWorkspaceSummary(title: string) {
  return CORE_WORKSPACE_SUMMARIES.has(title.trim())
}

export function moveDashboardComponent(
  components: DashboardComponentInput[], from: number, to: number,
): DashboardComponentInput[] {
  if (from < 0 || to < 0 || from >= components.length || to >= components.length || from === to) {
    return components.map((item, index) => ({ ...item, sortOrder: index * 10 }))
  }
  const result = [...components]
  const [moved] = result.splice(from, 1)
  if (!moved) return components.map((item, index) => ({ ...item, sortOrder: index * 10 }))
  result.splice(to, 0, moved)
  return result.map((item, index) => ({ ...item, sortOrder: index * 10 }))
}

export function dashboardComponentSpan(layout: Record<string, unknown>): number {
  const width = Number(layout.width ?? layout.w ?? 1)
  return Number.isFinite(width) ? Math.max(1, Math.min(4, Math.round(width))) : 1
}

/** 统计值保留有效小数位，避免数据库定点数的补零占满窄卡片。 */
export function formatDashboardMetric(value: unknown): string {
  if (value === null || value === undefined || value === '') return '—'
  if (typeof value !== 'number' && typeof value !== 'string') return String(value)
  const raw = String(value).trim()
  if (!/^-?\d+(?:\.\d+)?$/.test(raw)) return raw
  const [integer = '0', fraction = ''] = raw.split('.')
  const grouped = integer.replace(/\B(?=(\d{3})+(?!\d))/g, ',')
  const significant = fraction.replace(/0+$/, '')
  return significant ? `${grouped}.${significant}` : grouped
}

/** 运行页只显示业务统计口径，技术字段编码留在数据源配置页。 */
export function dashboardMetricSummary(definition: string): string {
  const kpi = definition.match(/^实际\s+(-?\d+(?:\.\d+)?)\s*\/\s*目标\s+(GTE|LTE)\s+(-?\d+(?:\.\d+)?)\s*\/\s*达成率\s+(-?\d+(?:\.\d+)?)%/)
  if (kpi) return `目标 ${kpi[2] === 'LTE' ? '≤' : '≥'} ${formatDashboardMetric(kpi[3])} · 达成率 ${formatDashboardMetric(kpi[4])}%`
  const aggregate = definition.match(/^(COUNT|SUM|AVG|MAX|MIN)\([^)]*\).*?对\s*(\d+)\s*行授权结果聚合/)
  if (aggregate) {
    const label: Record<string, string> = {
      COUNT: '数量', SUM: '求和', AVG: '平均值', MAX: '最大值', MIN: '最小值',
    }
    return `${label[aggregate[1]!] || '统计'} · ${aggregate[2]} 行可查看业务数据`
  }
  return definition.replace(/\s*\/ 数据源版本 #\d+/, '')
}

export function sourceDefinition(
  sourceType: DashboardSourceType, moduleCode: string, limit: number,
): Record<string, unknown> {
  const definition: Record<string, unknown> = { limit: Math.max(1, Math.min(20, Math.round(limit || 5))) }
  if (sourceType === 'MODULE_RECORDS') definition.moduleCode = moduleCode.trim()
  return definition
}
