import type { FlowBindingView } from './types'

export function activeBinding(bindings: FlowBindingView[]) {
  return bindings.find(binding => binding.status === 'ACTIVE')
}

export function bindingSourceLabel(source: 'DEFAULT' | 'TENANT_OVERRIDE') {
  return source === 'TENANT_OVERRIDE' ? '当前工作空间配置' : '系统公共配置'
}

export function publishedBindingPayload(input: {
  moduleId: number
  flowId: number
  triggerEvent: string
  executionMode: string
  priorityOrder: number
  conditionExpression?: string
  mutuallyExclusive: boolean
}, replaceExisting = false) {
  return { ...input, conditionExpression: input.conditionExpression?.trim() || undefined, replaceExisting }
}
