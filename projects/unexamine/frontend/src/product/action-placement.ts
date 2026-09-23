import type { ConfiguredAction, RuntimeRecord } from './types'

export type ActionLocation = 'MODULE_ENTRY' | 'LIST_TOOLBAR' | 'BATCH' | 'ROW' | 'DETAIL_HEADER' | 'DETAIL_MORE'
export const actionLocations: Array<{ value: ActionLocation; label: string }> = [
  { value: 'MODULE_ENTRY', label: '模块入口' }, { value: 'LIST_TOOLBAR', label: '列表工具栏' },
  { value: 'BATCH', label: '批量操作区' }, { value: 'ROW', label: '列表行' },
  { value: 'DETAIL_HEADER', label: '详情头部' }, { value: 'DETAIL_MORE', label: '详情更多操作' },
]
const recordCodes = new Set(['DETAIL', 'UPDATE', 'ARCHIVE', 'DELETE', 'RESTORE', 'TRANSFER', 'CONVERT', 'SHARE'])
const moduleCodes = new Set(['LIST', 'CREATE', 'IMPORT', 'EXPORT'])
type ActionIdentity = string | Pick<ConfiguredAction, 'code' | 'actionType'>
/** 配置消费方按动作类型判定；字符串入口保留旧 ACTION_ 调用兼容。 */
function actionIdentity(action: ActionIdentity) {
  return typeof action === 'string' ? { code: action, custom: action.startsWith('ACTION_') }
    : { code: action.code, custom: action.actionType === 'CUSTOM' }
}
export function requiresRecord(action: ActionIdentity) {
  const { code, custom } = actionIdentity(action)
  return custom || recordCodes.has(code)
}
export function actionsAtLocation(actions: ConfiguredAction[], location: ActionLocation, can: (code: string) => boolean) {
  // 模块导航提供列表入口，点击数据行提供详情入口，避免重复放置相同按钮。
  return actions.filter(action => action.status === 'ACTIVE' && action.location === location
    && (action.code !== 'LIST' || location !== 'MODULE_ENTRY')
    && (action.code !== 'DETAIL' || location !== 'ROW')
    && (recordCodes.has(action.code) || moduleCodes.has(action.code) || action.actionType === 'CUSTOM') && can(action.code))
}
export function actionAllowsLifecycle(action: ActionIdentity, state: 'ACTIVE' | 'ARCHIVED' | 'DELETED') {
  const { code, custom } = actionIdentity(action)
  if (custom || ['CREATE', 'IMPORT', 'UPDATE', 'ARCHIVE', 'TRANSFER', 'CONVERT'].includes(code)) return state === 'ACTIVE'
  if (code === 'DELETE') return state !== 'DELETED'
  if (code === 'RESTORE') return state !== 'ACTIVE'
  return true
}
export function actionAllowsRecord(action: ActionIdentity, record: Pick<RuntimeRecord, 'shared' | 'sharedActions' | 'ownedByCurrentTenant' | 'deleted' | 'archived'>, hasConversionTargets = true) {
  const { code } = actionIdentity(action)
  if (record.shared && !record.sharedActions.includes(code)) return false
  if (code === 'SHARE' && (!record.ownedByCurrentTenant || record.shared)) return false
  if (code === 'CONVERT' && !hasConversionTargets) return false
  return actionAllowsLifecycle(action, record.deleted ? 'DELETED' : record.archived ? 'ARCHIVED' : 'ACTIVE')
}
export function supportsBatchAction(action?: Pick<ConfiguredAction, 'code' | 'actionType' | 'location'>) {
  return action?.location === 'BATCH' && (action.actionType === 'CUSTOM' || ['DELETE', 'CONVERT'].includes(action.code))
}
export function selectionActionReason(action: ActionIdentity, selectedCount: number, batch = false) {
  if (!requiresRecord(action)) return ''
  return batch ? (selectedCount < 1 ? '请先勾选至少一条记录' : '') : (selectedCount !== 1 ? '请先选择一条记录' : '')
}
