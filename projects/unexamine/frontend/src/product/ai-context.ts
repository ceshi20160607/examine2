import type { AiQueryResult } from './types'

export function aiOutcomePresentation(outcome?: AiQueryResult['outcome']) {
  if (outcome === 'SUCCEEDED') return { color: 'green', label: '已按权限完成', alert: 'success' as const }
  if (outcome === 'REFUSED') return { color: 'red', label: '已拒绝越权请求', alert: 'error' as const }
  if (outcome === 'DEGRADED') return { color: 'orange', label: '模型不可用，未生成结果', alert: 'warning' as const }
  return { color: 'default', label: '等待提问', alert: 'info' as const }
}

export function aiScopeSummary(scope?: AiQueryResult['scope']) {
  if (!scope) return '尚未建立查询范围'
  const tenant = scope.tenantScope === 'OWN' ? '仅本租户' : scope.tenantScope === 'SHARED' ? '仅共享给我' : '本租户及获权共享'
  const filter = scope.filters.length ? `${scope.filters.length} 项筛选` : '无组合筛选'
  return `系统 #${scope.systemId} · 租户 #${scope.tenantId} · ${scope.moduleCode}/${scope.actionCode} · ${tenant} · ${scope.lifecycleState} · ${filter}`
}
