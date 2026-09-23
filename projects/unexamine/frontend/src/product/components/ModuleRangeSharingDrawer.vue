<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { Empty, message } from 'ant-design-vue'
import { api, ApiError } from '../api'
import { productDateTime, userFacingWorkspaceName } from '../presentation'
import { systemContext, systemTokens } from '../session'
import { conditionIssues, conditionSummary, emptyCondition, isConditionGroup, type ConditionField, type ConditionGroup, type ConditionNode } from '../conditions'
import type { ConfiguredAction, RuntimeModuleRangeShare, SaveRuntimeModuleRangeShareRequest, TenantShareTarget } from '../types'
import ConditionGroupEditor from './ConditionGroupEditor.vue'

const props = defineProps<{ open: boolean; enabled: boolean; moduleCode: string; moduleName?: string; actions: ConfiguredAction[] }>()
const emit = defineEmits<{ 'update:open': [value: boolean]; changed: [] }>()
const rows = ref<RuntimeModuleRangeShare[]>([])
const fields = ref<ConditionField[]>([])
const targets = ref<TenantShareTarget[]>([])
const loading = ref(false)
const saving = ref(false)
const revoking = ref(false)
const error = ref('')
const editorOpen = ref(false)
const editingId = ref<number>()
const editingVersion = ref<number>()
const revokeRow = ref<RuntimeModuleRangeShare>()
const revokeReason = ref('')
const form = reactive({ name: '', targetTenantId: undefined as number | undefined,
  allowedActions: [] as string[], condition: emptyCondition(), startTime: '', expireTime: '' })
let active = true
let generation = 0
let editorGeneration = 0
let revokeGeneration = 0
let originalStartTime = ''
let originalExpireTime = ''
const token = computed(() => systemTokens.value?.accessToken)
const identity = computed(() => {
  const context = systemContext.value
  return JSON.stringify([context?.platformId, context?.accountId, context?.systemId, context?.tenantId,
    context?.memberId, context?.tenantMemberId, context?.contextRevision, context?.roleIds,
    context?.permissions, context?.dataScopes, context?.tenantSwitchContext])
})
const actionOptions = computed(() => props.actions.filter(action => action.status === 'ACTIVE'
  && !['CREATE', 'SHARE', 'RESTORE'].includes(action.code)).map(action => ({ value: action.code, label: action.name })))
const baseActionCodes = ['LIST', 'DETAIL']
const extraActionOptions = computed(() => actionOptions.value.filter(action => !baseActionCodes.includes(action.value)))
const extraAllowedActions = computed({ get: () => form.allowedActions.filter(code => !baseActionCodes.includes(code)),
  set: (values: string[]) => { form.allowedActions = [...baseActionCodes, ...values] } })
const formActionOptions = computed(() => [...extraActionOptions.value,
  ...form.allowedActions.filter(code => !baseActionCodes.includes(code) && !actionOptions.value.some(action => action.value === code))
    .map(value => ({ value, label: '原有不可用操作（请取消）' }))])
const targetOptions = computed(() => targets.value.map(target => ({ value: target.tenantId, label: userFacingWorkspaceName(target.tenantName) })))
const formTargetOptions = computed(() => form.targetTenantId && !targets.value.some(target => target.tenantId === form.targetTenantId)
  ? [...targetOptions.value, { value: form.targetTenantId, label: '原目标已不可用，请重新选择', disabled: true }] : targetOptions.value)
const hasAllRecordsCondition = computed(() => form.condition.mode === 'ALL' && !form.condition.conditions.length)

function requestContext() {
  return { generation, token: token.value, identity: identity.value, moduleCode: props.moduleCode }
}
function requestCurrent(context: ReturnType<typeof requestContext>) {
  return active && props.open && props.enabled && context.generation === generation
    && context.token === token.value && context.identity === identity.value && context.moduleCode === props.moduleCode
}
function cancelEditor() {
  editorGeneration++
  originalStartTime = originalExpireTime = ''
  editorOpen.value = false
  editingId.value = editingVersion.value = undefined
  Object.assign(form, { name: '', targetTenantId: undefined, allowedActions: [], condition: emptyCondition(), startTime: '', expireTime: '' })
  saving.value = false
}
function cancelRevoke() {
  revokeGeneration++
  revokeRow.value = undefined
  revokeReason.value = ''
  revoking.value = false
}
function clear() {
  // 源范围条件不缓存；关闭、换模块或身份失效立即释放条件和授权历史。
  generation++
  rows.value = []
  fields.value = []
  targets.value = []
  loading.value = false
  error.value = ''
  cancelEditor()
  cancelRevoke()
}
function close() { clear(); emit('update:open', false) }
function readable(reason: unknown) { return reason instanceof Error ? reason.message : '暂时无法管理范围共享，请稍后重试。' }
function denied(reason: unknown) { return reason instanceof ApiError && [401, 403, 404].includes(reason.httpStatus || 0) }
function loseAccess() {
  close()
  message.error('当前身份无法管理这个模块的范围共享，已清除原授权内容。')
}
async function load() {
  if (!props.open || !props.enabled || !props.moduleCode || !token.value || !active) return
  clear()
  const context = requestContext()
  loading.value = true
  try {
    const root = `/api/runtime/modules/${encodeURIComponent(context.moduleCode)}/records`
    const [shares, conditionFields, tenantTargets] = await Promise.all([
      api<RuntimeModuleRangeShare[]>(`${root}/range-shares`, {}, context.token),
      api<ConditionField[]>(`${root}/range-share-fields`, {}, context.token),
      api<TenantShareTarget[]>(`${root}/share-targets`, {}, context.token),
    ])
    if (!requestCurrent(context)) return
    rows.value = shares
    // SECRET 不参与共享条件；字段与选项以服务端当前来源发布目录为准。
    fields.value = conditionFields.filter(field => field.fieldType !== 'SECRET')
    targets.value = tenantTargets
  } catch (reason) {
    if (!requestCurrent(context)) return
    if (denied(reason)) loseAccess()
    else error.value = readable(reason)
  } finally { if (requestCurrent(context)) loading.value = false }
}
function createShare() {
  if (loading.value || saving.value || revoking.value || !props.enabled || error.value) return
  cancelEditor()
  form.allowedActions = [...baseActionCodes]
  form.targetTenantId = targets.value[0]?.tenantId
  editorOpen.value = true
}
/** DatePicker 输入固定为北京时间；响应偏移不借用浏览器时区，原小数精度留给未修改的保存。 */
function beijingTime(value?: string | null) {
  if (!value) return ''
  const parts = /^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2})(\.\d{1,9})?(Z|[+-]\d{2}:\d{2})?$/.exec(value)
  if (!parts) return value
  if (!parts[3] || parts[3] === '+08:00') return parts[1]! + (parts[2] || '')
  const instant = Date.parse(value)
  if (!Number.isFinite(instant)) return value
  return new Date(instant + 8 * 60 * 60 * 1000).toISOString().slice(0, 19) + (parts[2] || '')
}
function timeInput(value: string) { return value.replace(/\.\d+$/, '') }
function timeForSave(value: string | null | undefined, original: string) {
  return value && value === timeInput(original) ? original : value || ''
}
function beijingTimestamp(value: string) { return Date.parse(`${value}+08:00`) }
function editShare(row: RuntimeModuleRangeShare) {
  if (saving.value || revoking.value || !props.enabled || row.status === 'REVOKED'
    || row.sourceTenantId !== systemContext.value?.tenantId) return
  cancelEditor()
  originalStartTime = beijingTime(row.startTime)
  originalExpireTime = beijingTime(row.expireTime)
  editingId.value = row.id
  editingVersion.value = row.version
  Object.assign(form, { name: row.name, targetTenantId: row.targetTenantId, allowedActions: [...row.allowedActions],
    condition: JSON.parse(JSON.stringify(row.condition)) as ConditionGroup,
    startTime: timeInput(originalStartTime), expireTime: timeInput(originalExpireTime) })
  editorOpen.value = true
}
function formIssue() {
  if (!form.name.trim()) return '请填写范围授权名称。'
  if (!targets.value.some(target => target.tenantId === form.targetTenantId)) return '请选择当前可共享的目标工作空间。'
  if (!form.allowedActions.length) return '请至少选择一个允许动作。'
  // 列表和详情是固定共享基础项，不以来源操作者的动作目录裁剪结果否决；额外动作仍须当前可用。
  if (extraAllowedActions.value.some(code => !extraActionOptions.value.some(action => action.value === code))) return '原有操作已不可用，请取消后重新选择允许动作。'
  const issues = conditionIssues(form.condition, fields.value)
  if (issues.length) return issues[0]!
  const startTime = timeForSave(form.startTime, originalStartTime)
  const expireTime = timeForSave(form.expireTime, originalExpireTime)
  if (startTime && !Number.isFinite(beijingTimestamp(startTime)) || expireTime && !Number.isFinite(beijingTimestamp(expireTime))) return '请重新选择有效的生效或失效时间。'
  if (startTime && expireTime && beijingTimestamp(expireTime) <= beijingTimestamp(startTime)) return '失效时间必须晚于生效时间。'
  return ''
}
async function conflict(context: ReturnType<typeof requestContext>) {
  if (!requestCurrent(context)) return
  const pending = load()
  const refreshed = requestContext()
  await pending
  if (requestCurrent(refreshed)) message.warning('范围授权已被其他操作修改，已重新读取最新内容，请重新打开编辑或撤销。')
}
async function save() {
  if (saving.value || revoking.value || !editorOpen.value || !props.enabled) return
  const issue = formIssue()
  if (issue) { message.warning(issue); return }
  const context = requestContext()
  const editor = editorGeneration
  const current = () => requestCurrent(context) && editor === editorGeneration
  const startTime = timeForSave(form.startTime, originalStartTime)
  const expireTime = timeForSave(form.expireTime, originalExpireTime)
  const body: SaveRuntimeModuleRangeShareRequest = {
    name: form.name.trim(), targetTenantId: form.targetTenantId!, allowedActions: [...new Set([...baseActionCodes, ...form.allowedActions])],
    condition: JSON.parse(JSON.stringify(form.condition)) as ConditionGroup,
    ...(editingId.value ? { id: editingId.value, version: editingVersion.value } : {}),
    ...(startTime ? { startTime } : {}), ...(expireTime ? { expireTime } : {}),
  }
  saving.value = true
  try {
    const result = await api<RuntimeModuleRangeShare>(`/api/runtime/modules/${encodeURIComponent(context.moduleCode)}/records/range-shares`,
      { method: 'POST', body: JSON.stringify(body) }, context.token)
    if (!current()) return
    rows.value = rows.value.some(row => row.id === result.id) ? rows.value.map(row => row.id === result.id ? result : row) : [result, ...rows.value]
    cancelEditor()
    emit('changed')
    message.success(result.status === 'SCHEDULED' ? '范围共享已保存，将在指定时间生效。' : '范围共享已保存，目标访问仍受其当前权限约束。')
  } catch (reason) {
    if (!current()) return
    if (denied(reason)) loseAccess()
    else if (reason instanceof ApiError && reason.httpStatus === 409) await conflict(context)
    else message.error(readable(reason))
  } finally { if (current()) saving.value = false }
}
function openRevoke(row: RuntimeModuleRangeShare) {
  if (saving.value || revoking.value || !props.enabled || row.sourceTenantId !== systemContext.value?.tenantId
    || !['ACTIVE', 'SCHEDULED', 'INVALID'].includes(row.status)) return
  cancelRevoke()
  revokeRow.value = row
}
async function revoke() {
  if (!revokeRow.value || !revokeReason.value.trim() || revoking.value || !props.enabled) return
  const context = requestContext()
  const request = revokeGeneration
  const current = () => requestCurrent(context) && request === revokeGeneration
  const row = revokeRow.value
  revoking.value = true
  try {
    const result = await api<RuntimeModuleRangeShare>(`/api/runtime/modules/${encodeURIComponent(context.moduleCode)}/records/range-shares/${row.id}/revoke`,
      { method: 'POST', body: JSON.stringify({ version: row.version, reason: revokeReason.value.trim() }) }, context.token)
    if (!current()) return
    rows.value = rows.value.map(item => item.id === result.id ? result : item)
    cancelRevoke()
    emit('changed')
    message.success('范围共享已撤销，来源数据和共享历史保留。')
  } catch (reason) {
    if (!current()) return
    if (denied(reason)) loseAccess()
    else if (reason instanceof ApiError && reason.httpStatus === 409) await conflict(context)
    else message.error(readable(reason))
  } finally { if (current()) revoking.value = false }
}
function actionNames(codes: string[]) { return codes.map(code => code === 'LIST' ? '列表查看' : code === 'DETAIL' ? '详情查看'
  : actionOptions.value.find(action => action.value === code)?.label || '已不可用操作').join('、') }
function conditionWithLabels(node: ConditionNode): ConditionNode {
  if (isConditionGroup(node)) return { ...node, conditions: node.conditions.map(conditionWithLabels) }
  const field = fields.value.find(field => field.code === node.field)
  let options: Array<{ value?: string; code?: string; label?: string }> = []
  try { options = JSON.parse(field?.configJson || '{}').options || [] } catch { /* 已失效配置不推断选项名称。 */ }
  const label = (value: unknown) => options.find(option => String(option.value ?? option.code) === String(value))?.label || value
  return { ...node, ...('value' in node ? { value: Array.isArray(node.value) ? node.value.map(label) : label(node.value) } : {}) }
}
function statusName(status: RuntimeModuleRangeShare['status']) {
  return ({ ACTIVE: '有效', SCHEDULED: '待生效', EXPIRED: '已到期', REVOKED: '已撤销', INVALID: '已失效' })[status]
}
watch(() => [props.open, props.moduleCode, props.enabled, token.value, identity.value], (values, previous) => {
  clear()
  if (!props.open) return
  if (!props.enabled || previous?.[0] && values.slice(1).some((value, index) => value !== previous[index + 1])) {
    emit('update:open', false)
    return
  }
  void load()
}, { immediate: true, flush: 'sync' })
onBeforeUnmount(() => { active = false; clear() })
</script>

<template>
  <a-drawer :open="open" :title="`${moduleName || '当前模块'} · 范围共享`" :width="860" @close="close">
    <a-alert type="info" show-icon message="共享当前来源数据，不复制记录或改变所有权"
      description="条件持续匹配来源记录，未来满足条件的新记录也在范围内。目标仍须同时具有对应角色、动作、字段和数据范围权限，不能再次共享来源数据。" />
    <div class="range-sharing-toolbar"><strong>本租户发出的范围授权</strong><a-space>
      <a-button :disabled="saving || revoking" :loading="loading" @click="load">刷新授权</a-button>
      <a-button type="primary" :disabled="loading || saving || revoking || Boolean(error) || !targets.length" @click="createShare">新增范围共享</a-button>
    </a-space></div>
    <a-alert v-if="error" type="error" show-icon :message="error" />
    <a-spin :spinning="loading">
      <template v-if="!error">
        <a-alert v-if="!loading && !targets.length" type="warning" show-icon message="当前没有可共享的同系统目标工作空间。" />
        <a-table size="small" :data-source="rows" row-key="id" :pagination="false" :scroll="{ x: 740 }">
          <a-table-column title="授权名称" data-index="name" :width="150" />
          <a-table-column title="目标工作空间" :width="140"><template #default="{ record }">{{ userFacingWorkspaceName(record.targetTenantName) }}</template></a-table-column>
          <a-table-column title="允许动作" :width="150"><template #default="{ record }">{{ actionNames(record.allowedActions) }}</template></a-table-column>
          <a-table-column title="状态" :width="80"><template #default="{ record }"><a-tag>{{ statusName(record.status) }}</a-tag></template></a-table-column>
          <a-table-column title="有效时间" :width="170"><template #default="{ record }">{{ record.startTime ? productDateTime(record.startTime) : '授权后生效' }}<br />{{ record.expireTime ? `至 ${productDateTime(record.expireTime)}` : '长期有效' }}</template></a-table-column>
          <a-table-column title="管理" :width="100"><template #default="{ record }">
            <a-button v-if="record.status !== 'REVOKED'" type="link" size="small" :disabled="saving || revoking || loading" @click="editShare(record)">编辑</a-button>
            <a-button v-if="['ACTIVE', 'SCHEDULED', 'INVALID'].includes(record.status)" danger type="link" size="small" :disabled="saving || revoking || loading" @click="openRevoke(record)">撤销</a-button>
          </template></a-table-column>
          <template #expandedRowRender="{ record }"><p>{{ conditionSummary(conditionWithLabels(record.condition), fields) }}</p><small>授予时间：{{ productDateTime(record.grantTime) }}<template v-if="record.revokeTime"> · 撤销时间：{{ productDateTime(record.revokeTime) }}</template></small></template>
          <template #emptyText><a-empty :image="Empty.PRESENTED_IMAGE_SIMPLE" description="本租户尚未发出范围授权" /></template>
        </a-table>
      </template>
    </a-spin>
    <section v-if="editorOpen" class="range-sharing-editor">
      <h3>{{ editingId ? '编辑范围授权' : '新增范围授权' }}</h3>
      <a-form layout="vertical">
        <a-form-item label="授权名称" required><a-input v-model:value="form.name" :disabled="saving" :maxlength="200" placeholder="例如：共享已签约客户" /></a-form-item>
        <a-form-item label="目标工作空间" required><a-select v-model:value="form.targetTenantId" :disabled="saving" :options="formTargetOptions" show-search option-filter-prop="label" /></a-form-item>
        <a-form-item label="基础查看权限" extra="共享固定包含列表与详情查看，目标用户仍需对应角色权限。"><a-space><a-checkbox checked disabled>列表查看</a-checkbox><a-checkbox checked disabled>详情查看</a-checkbox></a-space></a-form-item>
        <a-form-item label="额外允许动作" extra="编辑、转移、导出或其他操作需单独选择，仍取目标用户当前权限交集。"><a-checkbox-group v-model:value="extraAllowedActions" :disabled="saving" :options="formActionOptions" /></a-form-item>
        <div class="range-sharing-times">
          <a-form-item label="生效时间（北京时间）" extra="留空表示授权后生效。"><a-date-picker v-model:value="form.startTime" :disabled="saving" show-time value-format="YYYY-MM-DDTHH:mm:ss" style="width:100%" /></a-form-item>
          <a-form-item label="失效时间（北京时间）" extra="留空表示长期有效。"><a-date-picker v-model:value="form.expireTime" :disabled="saving" show-time value-format="YYYY-MM-DDTHH:mm:ss" style="width:100%" /></a-form-item>
        </div>
        <a-form-item label="来源数据范围" extra="仅使用当前来源已发布且有权配置的安全字段；条件不会在目标页面暴露。密码和密钥不会出现在条件目录中，可选字段以当前来源权限为准。">
          <fieldset :disabled="saving" class="range-sharing-condition"><ConditionGroupEditor v-model="form.condition" :fields="fields" /></fieldset>
        </a-form-item>
        <a-alert v-if="hasAllRecordsCondition" type="warning" show-icon message="没有条件时，共享覆盖当前共享权限允许的全部来源记录，未来符合权限的新记录也会进入范围。" />
      </a-form>
      <div class="range-sharing-footer"><a-button @click="cancelEditor">取消编辑</a-button><a-button type="primary" :loading="saving" :disabled="revoking" @click="save">保存范围共享</a-button></div>
    </section>
    <template #footer><a-button @click="close">关闭</a-button></template>
  </a-drawer>
  <a-modal :open="Boolean(revokeRow)" title="撤销范围共享" :confirm-loading="revoking" :ok-button-props="{ danger: true, disabled: !revokeReason.trim() }" ok-text="确认撤销" @cancel="cancelRevoke" @ok="revoke">
    <a-alert type="warning" show-icon :message="`撤销对“${userFacingWorkspaceName(revokeRow?.targetTenantName || '')}”的范围授权`" description="目标将失去本授权的访问关系。来源数据、共享历史、审计和已经启动的流程保留。" />
    <a-form layout="vertical"><a-form-item label="撤销原因" required><a-textarea v-model:value="revokeReason" :disabled="revoking" :maxlength="1000" :rows="3" placeholder="填写可追溯的撤销原因" /></a-form-item></a-form>
  </a-modal>
</template>

<style scoped>
.range-sharing-toolbar, .range-sharing-footer { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 12px; margin: 16px 0; }
.range-sharing-editor { border-top: 1px solid #dbe3ee; margin-top: 20px; padding-top: 16px; }
.range-sharing-times { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; }
.range-sharing-condition { min-width: 0; border: 0; margin: 0; padding: 0; }
@media (max-width: 640px) { .range-sharing-times { grid-template-columns: 1fr; gap: 0; } }
</style>
