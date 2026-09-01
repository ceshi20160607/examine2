import type { DashboardComponentInput, DashboardSourceType } from './types'

export const dashboardSourceLabels: Record<DashboardSourceType, string> = {
  PLATFORM_SYSTEMS: '已授权系统',
  MODULE_RECORDS: '模块业务记录',
  MODULE_REPORT: '结构化模块报表',
  TODO_ITEMS: '待办事项',
  MESSAGE_ITEMS: '未读消息',
  WORK_PROJECTS: '工作项目',
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

export function sourceDefinition(
  sourceType: DashboardSourceType, moduleCode: string, limit: number,
): Record<string, unknown> {
  const definition: Record<string, unknown> = { limit: Math.max(1, Math.min(20, Math.round(limit || 5))) }
  if (sourceType === 'MODULE_RECORDS') definition.moduleCode = moduleCode.trim()
  return definition
}
