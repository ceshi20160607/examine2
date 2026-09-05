import { statusTone, type StatusTone } from './responsive'

const STATUS_LABELS: Record<string, string> = {
  ACTIVE: '启用',
  APPROVED: '已通过',
  ARCHIVED: '已归档',
  BLOCKED: '受阻',
  CANCELLED: '已取消',
  CLOSED: '已关闭',
  COMPLETED: '已完成',
  COMPLETED_WITH_ERRORS: '部分完成',
  CURRENT: '当前版本',
  DELETED: '已删除',
  DISABLED: '已停用',
  DRAFT: '草稿',
  DUE_SOON: '即将到期',
  ERROR: '异常',
  FAILED: '失败',
  HEALTHY: '正常',
  IN_PROGRESS: '进行中',
  NOT_STARTED: '未开始',
  OPEN: '进行中',
  PASSED: '已通过',
  PAUSED: '已暂停',
  PENDING: '待处理',
  PENDING_APPROVAL: '等待审批',
  PROCESSING: '处理中',
  PREVIEWED: '预演完成',
  PUBLISHED: '已发布',
  READY: '就绪',
  REJECTED: '已拒绝',
  REVOKED: '已撤销',
  RUNNING: '运行中',
  QUEUED: '等待执行',
  ROLLED_BACK: '已回滚',
  ROLLBACK_PARTIAL: '部分回滚',
  SUBMITTED: '已提交',
  SUCCEEDED: '成功',
  SUCCESS: '成功',
  TODO: '待办',
  VALID: '有效',
  VERIFICATION_FAILED: '验证失败',
  VERIFIED: '已验证',
  WAITING: '等待中',
  WAITING_ACCEPTANCE: '等待验收',
  WAITING_CONFIRMATION: '等待确认',
  WARNING: '需关注',
}

const STATUS_COLORS: Record<StatusTone, string> = {
  neutral: 'default',
  processing: 'processing',
  attention: 'warning',
  positive: 'success',
  critical: 'error',
}

const DASHBOARD_COMPONENT_LABELS: Record<string, string> = {
  CHART: '趋势图表',
  KPI: '关键指标',
  LIST: '业务列表',
  METRIC: '关键指标',
  PROGRESS: '进度',
  QUICK_ENTRY: '快捷入口',
  RANKING: '业务排行',
}

const FIELD_TYPE_LABELS: Record<string, string> = {
  TEXT: '单行文本',
  MULTILINE_TEXT: '多行文本',
  LONG_TEXT: '多行文本',
  NUMBER: '数字',
  DECIMAL: '小数',
  INTEGER: '整数',
  LONG: '整数',
  MONEY: '金额',
  PERCENT: '百分比',
  DATE: '日期',
  DATETIME: '日期时间',
  BOOLEAN: '是/否',
  DICTIONARY: '选项',
  REFERENCE: '关联记录',
  ATTACHMENT: '附件',
  JSON: '结构化内容',
}

const ACTION_LABELS: Record<string, string> = {
  LIST: '查看列表', DETAIL: '查看详情', CREATE: '新建', UPDATE: '编辑',
  ARCHIVE: '归档', DELETE: '删除', EXPORT: '导出', IMPORT: '导入',
  PRINT: '打印', TRIGGER: '发起流程', APPROVE: '审批', REJECT: '驳回',
}

export interface ProductStatusPresentation {
  label: string
  tone: StatusTone
  color: string
}

export function productStatus(status?: string | null): ProductStatusPresentation {
  const source = String(status || '').trim()
  const normalized = source.toUpperCase()
  const tone = statusTone(normalized)
  const label = STATUS_LABELS[normalized]
    || (/\p{Script=Han}/u.test(source) ? source : '状态待确认')
  return { label, tone, color: STATUS_COLORS[tone] }
}

export function dashboardComponentLabel(componentType?: string | null) {
  const normalized = String(componentType || '').trim().toUpperCase()
  return DASHBOARD_COMPONENT_LABELS[normalized] || '信息卡片'
}

export function fieldTypeLabel(fieldType?: string | null) {
  const normalized = String(fieldType || '').trim().toUpperCase()
  return FIELD_TYPE_LABELS[normalized] || '通用内容'
}

export function actionLabel(action?: string | null) {
  const source = String(action || '').trim()
  const normalized = source.toUpperCase()
  return ACTION_LABELS[normalized] || (/\p{Script=Han}/u.test(source) ? source : '业务操作')
}

export function versionLabel(version?: number | null) {
  return version && version > 0 ? `第 ${version} 版` : '尚未发布'
}

export function tenantModeLabel(mode?: string | null) {
  return String(mode || '').toUpperCase() === 'MULTI' ? '多工作空间' : '单工作空间'
}

export function userFacingWorkspaceName(name?: string | null) {
  const source = String(name || '').trim()
  if (!source || /^(默认)?主租户$/.test(source) || source === '默认租户') return '主工作空间'
  return source
}

export function isVerificationArtifactName(name?: string | null) {
  const source = String(name || '').trim()
  return /^(?:C\d+|CYCLE[-_ ]?\d+|周期[一二三四五六七八九十百千万零〇两\d]+)/i.test(source)
    || /(?:验收|探测)系统$/.test(source)
}

export function userFacingRecordNumber(value?: string | null, fallback = '业务记录') {
  const source = String(value || '').trim()
  if (!source || /^(?:C\d{2,}|CYCLE[-_ ]?\d+|周期[一二三四五六七八九十百千万零〇两\d]+)/i.test(source)) return fallback
  return source
}

export function productDateTime(value?: string | null) {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '—'
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', hour12: false,
  }).format(date)
}

export function userFacingDateTime(value?: string | null) {
  const formatted = productDateTime(value)
  return formatted === '—' ? '更新时间未知' : `更新于 ${formatted}`
}
