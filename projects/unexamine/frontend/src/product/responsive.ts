export const MOBILE_BREAKPOINT_PX = 900
export const MINIMUM_TOUCH_TARGET_PX = 48

const HEAVY_CONFIGURATION_SECTIONS = new Set([
  'organization', 'authorization', 'roles', 'dictionary', 'dashboard', 'print', 'templates', 'parameters',
  'flow', 'applications', 'ai', 'operations',
])

const STATUS_TONES = {
  neutral: new Set(['DRAFT', 'PENDING', 'TODO', 'NOT_STARTED', 'SUBMITTED']),
  processing: new Set(['PROCESSING', 'RUNNING', 'IN_PROGRESS', 'APPROVING', 'CURRENT', 'OPEN']),
  attention: new Set(['WAITING', 'WAITING_CONFIRMATION', 'WAITING_ACCEPTANCE', 'PENDING_APPROVAL', 'DUE_SOON', 'WARNING']),
  positive: new Set(['COMPLETED', 'PASSED', 'SUCCESS', 'SUCCEEDED', 'ACTIVE', 'VALID', 'HEALTHY', 'APPROVED', 'PUBLISHED', 'READY', 'VERIFIED']),
  critical: new Set(['ERROR', 'FAILED', 'REJECTED', 'OVERDUE', 'DISABLED', 'PAUSED', 'DELETED', 'BLOCKED', 'VERIFICATION_FAILED']),
} as const

export type StatusTone = keyof typeof STATUS_TONES

export function isHeavyConfigurationSection(section: string) {
  return HEAVY_CONFIGURATION_SECTIONS.has(section)
}

export function responsiveMode(viewportWidth: number) {
  return viewportWidth <= MOBILE_BREAKPOINT_PX ? 'mobile' : 'desktop'
}

export function statusTone(status: string): StatusTone {
  const normalized = status.trim().toUpperCase()
  return (Object.entries(STATUS_TONES) as [StatusTone, ReadonlySet<string>][]) 
    .find(([, values]) => values.has(normalized))?.[0] || 'neutral'
}
