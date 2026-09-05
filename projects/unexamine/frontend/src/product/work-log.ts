import type { WorkLog, WorkLogRevision } from './types'

export function sortWorkLogs(logs: WorkLog[]) {
  return [...logs].sort((a, b) => b.workDate.localeCompare(a.workDate) || b.updatedAt.localeCompare(a.updatedAt))
}

export function revisionDifference(current: WorkLogRevision, previous?: WorkLogRevision) {
  if (!previous) return '初始版本'
  const fields = [
    'workDate', 'title', 'content', 'durationMinutes', 'status',
    'projectId', 'taskIds', 'businessType', 'businessId', 'businessTitle', 'configuredValues',
  ]
    .filter(field => JSON.stringify(current.snapshot[field]) !== JSON.stringify(previous.snapshot[field]))
  return fields.length ? `变更：${fields.join('、')}` : '仅记录状态操作'
}

export function canEditLog(status: WorkLog['status']) {
  return status !== 'SUBMITTED'
}
