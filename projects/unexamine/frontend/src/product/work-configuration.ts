import type { WorkConfiguration, WorkConfigurationField } from './types'

export function sourceLabel(source: WorkConfigurationField['source']) {
  return source === 'PLATFORM_DEFAULT' ? '平台默认' : '系统覆盖'
}

export function effectiveField(config: WorkConfiguration | undefined, targetType: string, fieldCode: string) {
  return config?.effectivePublished.find(field => field.targetType === targetType && field.fieldCode === fieldCode)
}

export function parseFieldSettings(text: string) {
  if (!text.trim()) return {}
  const parsed = JSON.parse(text) as unknown
  if (!parsed || Array.isArray(parsed) || typeof parsed !== 'object') throw new Error('设置必须是 JSON 对象')
  return parsed as Record<string, unknown>
}

export function isoDateOffset(offset: number, base = new Date()) {
  const date = new Date(base)
  date.setDate(date.getDate() + offset)
  return date.toISOString().slice(0, 10)
}
