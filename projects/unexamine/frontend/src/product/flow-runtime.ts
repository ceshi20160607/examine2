import type { FlowRuntimeInstance } from './types'

export function activeRuntimeTask(instance?: FlowRuntimeInstance) {
  return instance?.tasks.find(task => task.status === 'PENDING')
}

export function runtimeStatusLabel(status: string) {
  return ({ RUNNING: '运行中', WAITING: '待审批', COMPLETED: '已完成', REJECTED: '已拒绝',
    RETURNED: '已退回', WITHDRAWN: '已撤回', TERMINATED: '已终止', EXCEPTION: '异常待处理' } as Record<string, string>)[status] || status
}

export function parseRuntimeVariables(text: string) {
  if (!text.trim()) return {}
  const value = JSON.parse(text) as unknown
  if (!value || Array.isArray(value) || typeof value !== 'object') throw new Error('变量必须是 JSON 对象')
  return value as Record<string, unknown>
}

export function parseManualStatusMappings(text: string) {
  if (!text.trim()) return {}
  const value = JSON.parse(text) as unknown
  if (!value || Array.isArray(value) || typeof value !== 'object') throw new Error('状态映射必须是 JSON 对象')
  const result: Record<string, Record<string, string>> = {}
  for (const [action, mapping] of Object.entries(value)) {
    if (!mapping || Array.isArray(mapping) || typeof mapping !== 'object') throw new Error(`${action} 的状态映射必须是对象`)
    result[action.toUpperCase()] = Object.fromEntries(Object.entries(mapping).map(([key, entry]) => [key, String(entry)]))
  }
  return result
}

export function runtimeIdempotency(prefix: string) {
  return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`
}
