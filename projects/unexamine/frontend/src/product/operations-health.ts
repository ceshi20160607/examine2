import type { OperationsHealth, OperationsObservation } from './types'

export function readinessLabel(health?: OperationsHealth) {
  if (!health) return '尚未体检'
  return health.ready ? '可接收业务流量' : '禁止宣告就绪'
}

export function observedStageCount(observation?: OperationsObservation) {
  return observation?.stages.filter(item => item.observed).length ?? 0
}

export function safeRequestId(value: string) {
  return /^[A-Za-z0-9_-]{8,64}$/.test(value.trim())
}
