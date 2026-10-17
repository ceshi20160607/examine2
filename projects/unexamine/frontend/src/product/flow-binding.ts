import type { FlowBindingView, FlowScheduleConfig } from './types'
import type { ConditionGroup } from './conditions'

export function activeBinding(bindings: FlowBindingView[]) {
  return bindings.find(binding => binding.status === 'ACTIVE')
}

export function bindingSourceLabel(source: 'DEFAULT' | 'TENANT_OVERRIDE') {
  return source === 'TENANT_OVERRIDE' ? '当前工作空间配置' : '系统公共配置'
}

export function publishedBindingPayload(input: {
  moduleId: number
  actionId?: number
  flowId?: number
  triggerEvent: string
  executionMode: string
  priorityOrder: number
  conditionExpression?: string
  condition?: ConditionGroup
  mutuallyExclusive: boolean
  schedule?: FlowScheduleConfig
  approvalStatusMapping?: Record<string, string>
}, replaceExisting = false) {
  return { ...input, flowId: input.executionMode === 'DIRECT' ? undefined : input.flowId,
    conditionExpression: input.conditionExpression?.trim() || undefined,
    approvalStatusMapping: input.executionMode === 'AFTER_APPROVAL' && ['CREATE', 'UPDATE'].includes(input.triggerEvent)
      ? Object.fromEntries(Object.entries(input.approvalStatusMapping || {}).filter(([, value]) => value.trim()).map(([key, value]) => [key, value.trim().toUpperCase()]))
      : undefined,
    replaceExisting }
}
