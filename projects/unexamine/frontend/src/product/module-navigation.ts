export type ModuleTask = 'INFO' | 'FIELDS' | 'ACTIONS' | 'FLOW' | 'APPLICATION'
export function queryText(value: unknown) { return typeof value === 'string' ? value : '' }
export function moduleTask(value: unknown): ModuleTask {
  return ['INFO', 'FIELDS', 'ACTIONS', 'FLOW', 'APPLICATION'].includes(queryText(value)) ? value as ModuleTask : 'INFO'
}
export function moduleAdminTarget(systemId: number, code: string, section: 'templates' | 'flow' | 'applications', task: ModuleTask) {
  return { path: `/systems/${systemId}/admin`, query: { section, module: code, moduleTask: task, ...(section === 'flow' ? { flowSection: 'bindings' } : {}) } }
}
export function selectContextModule<T extends { moduleCode: string }>(rows: T[], requested: string) {
  return requested ? rows.find(row => row.moduleCode === requested) : rows[0]
}
