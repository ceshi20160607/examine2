<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter, onBeforeRouteLeave, onBeforeRouteUpdate } from 'vue-router'
import { Empty, Modal, message } from 'ant-design-vue'
import { ApiError, api } from '../api'
import { bindingSourceLabel, publishedBindingPayload } from '../flow-binding'
import { versionLabel } from '../presentation'
import { allowsPermission } from '../permissions'
import { systemContext, systemTokens } from '../session'
import ModuleContextBanner from '../components/ModuleContextBanner.vue'
import ConditionGroupEditor from '../components/ConditionGroupEditor.vue'
import ConditionValueInput from '../components/ConditionValueInput.vue'
import { bindingCondition, emptyCondition, conditionFieldCodes, conditionIssues, conditionSummary, type ConditionField } from '../conditions'
import { moduleAdminTarget, queryText, selectContextModule } from '../module-navigation'
import type { ConfiguredAction, FlowBindingEventDelivery, FlowBindingList, FlowBindingResolution, FlowBindingView, FlowDefinitionView, FlowScheduleConfig, RuntimeModuleCatalogItem, RuntimeModuleConfiguration, RuntimeRecord } from '../types'

const token = computed(() => systemTokens.value?.accessToken)
const route = useRoute()
const router = useRouter()
const modules = ref<RuntimeModuleCatalogItem[]>([])
const flows = ref<FlowDefinitionView[]>([])
const manualActions = ref<ConfiguredAction[]>([])
const bindings = ref<FlowBindingList>({ currentTenant: [], inheritedDefaults: [] })
const resolution = ref<FlowBindingResolution>()
const loading = ref(false)
const busy = ref('')
const form = reactive({
  moduleId: undefined as number | undefined,
  triggerEvent: ['CREATE','UPDATE','DELETE','TRANSFER','CONVERT','CONVERSION_UNLINK','STATUS_CHANGED','MANUAL','SCHEDULED','APPLICATION','IMPORT_COMPLETED','EXCEPTION'].includes(queryText(route.query.triggerEvent)) ? queryText(route.query.triggerEvent) : 'CREATE',
  actionId: undefined as number | undefined,
  flowId: undefined as number | undefined,
  executionMode: 'DIRECT',
  priorityOrder: 0,
  mutuallyExclusive: true,
  approvalStatusMapping: { COMPLETED: '', REJECTED: '', WITHDRAWN: '' } as Record<string, string>,
})
const schedule = reactive<FlowScheduleConfig>({
  scheduleMode: 'INTERVAL', intervalValue: 1, intervalUnit: 'DAY',
  dailyTime: '09:00', weekDay: 1, misfirePolicy: 'FIRE_ONCE',
})
const condition = ref(emptyCondition())
const conditionFields = ref<ConditionField[]>([])
const sampleValues = reactive<Record<string, unknown>>({})
const sampleRecords = ref<RuntimeRecord[]>([])
const eventDeliveries = ref<FlowBindingEventDelivery[]>([])
const sampleRecordId = ref<number>()
const pointLoading = ref(false)
const pointReady = ref(false)
const savedSnapshot = ref('')
const preview = ref<{ matched: boolean; conditions: Array<{ field: string; operator: string; matched: boolean }> }>()
function snapshot() { return JSON.stringify({ actionId: form.actionId, mode: form.executionMode, flowId: form.flowId, priority: form.priorityOrder, exclusive: form.mutuallyExclusive, condition: condition.value, schedule: form.triggerEvent === 'SCHEDULED' ? schedule : undefined, approvalStatusMapping: form.executionMode === 'AFTER_APPROVAL' && ['CREATE', 'UPDATE'].includes(form.triggerEvent) ? form.approvalStatusMapping : undefined }) }
const dirty = computed(() => Boolean(savedSnapshot.value && savedSnapshot.value !== snapshot()))
const reliableEvent = computed(() => ['SCHEDULED', 'IMPORT_COMPLETED', 'EXCEPTION'].includes(form.triggerEvent))
const testFields = computed(() => conditionFields.value.filter(field => conditionFieldCodes(condition.value).includes(field.code)))
const issues = computed(() => conditionIssues(condition.value, conditionFields.value))
const activePublished = computed(() => bindings.value.currentTenant.find(row => row.status === 'ACTIVE') || bindings.value.inheritedDefaults.find(row => row.status === 'ACTIVE'))
function testVariables() { return Object.fromEntries(testFields.value.filter(field => sampleValues[field.code] !== undefined).map(field => [field.code, sampleValues[field.code]])) }
function useSample(id?: number) {
  sampleRecordId.value = id
  Object.keys(sampleValues).forEach(key => delete sampleValues[key])
  const record = sampleRecords.value.find(record => record.id === id)
  for (const field of conditionFields.value) if (record?.fields[field.code] !== undefined) sampleValues[field.code] = record.fields[field.code]
  preview.value = undefined; resolution.value = undefined
}
function canLeave() { if (dirty.value) { message.warning('请先发布当前修改，或点击取消修改'); return false }; return true }
onBeforeRouteLeave(canLeave)
onBeforeRouteUpdate((to, from) => to.query.module === from.query.module && to.query.triggerEvent === from.query.triggerEvent || canLeave())
function changePoint(moduleId?: number, event = form.triggerEvent) {
  if (!canLeave()) return
  savedSnapshot.value = ''; form.moduleId = moduleId; form.triggerEvent = event; form.actionId = undefined
}

function changeManualAction(actionId?: number) {
  if (!canLeave()) return
  savedSnapshot.value = ''
  form.actionId = actionId
  if (manualActions.value.some(action => action.id === actionId && action.code === 'APPROVAL')) form.executionMode = 'FLOW_ONLY'
  void loadBindings()
}

const publishedFlows = computed(() => flows.value.filter(flow => {
  if (!flow.currentVersionId) return false
  if (!approvalActionSelected.value) return true
  const nodes = flow.versions.find(version => version.id === flow.currentVersionId)?.snapshot.nodes
  return Array.isArray(nodes) && nodes.some(node => node && typeof node === 'object'
    && 'nodeType' in node && node.nodeType === 'APPROVAL')
}))
const approvalActionSelected = computed(() => manualActions.value.some(action => action.id === form.actionId && action.code === 'APPROVAL'))
const rows = computed(() => [...bindings.value.currentTenant, ...bindings.value.inheritedDefaults])
const contextModule = computed(() => modules.value.find(module => module.moduleId === form.moduleId))
function returnToModule() {
  if (systemContext.value?.systemId) void router.push(moduleAdminTarget(systemContext.value.systemId, contextModule.value?.moduleCode || queryText(route.query.module), 'templates', 'FLOW'))
}
function syncModuleRoute() {
  if (contextModule.value && (queryText(route.query.module) !== contextModule.value.moduleCode || route.query.triggerEvent !== form.triggerEvent)) void router.replace({ query: { ...route.query, module: contextModule.value.moduleCode, moduleTask: 'FLOW', triggerEvent: form.triggerEvent } })
}

function can(action: string) {
  return allowsPermission(systemContext.value?.permissions, 'FLOW', 'SYSTEM', action)
    || allowsPermission(systemContext.value?.permissions, 'FLOW', '*', action)
}

function describeError(error: unknown) {
  if (error instanceof ApiError) return `${error.message}${error.traceId ? `（追踪号 ${error.traceId}）` : ''}`
  return error instanceof Error ? error.message : '操作失败'
}

function eventLabel(event: string) {
  return ({ CREATE: '新建时', UPDATE: '编辑时', DELETE: '删除前', TRANSFER: '转交负责人和部门时', CONVERT: '转化时', CONVERSION_UNLINK: '解除转化关系时', STATUS_CHANGED: '状态变化时', MANUAL: '手动触发',
    SCHEDULED: '定时触发', APPLICATION: '应用调用', IMPORT_COMPLETED: '导入完成', EXCEPTION: '异常时' } as Record<string, string>)[event] || event
}

function eventDeliveryStatus(row: FlowBindingEventDelivery) {
  if (row.status === 'FAILED') return '等待自动重试'
  if (row.status === 'PENDING') return row.eventType === 'SCHEDULED' && row.availableTime
    ? `计划于 ${new Date(row.availableTime).toLocaleString()}执行` : '等待投递'
  if (row.deliveryResult === 'MISFIRE_SKIPPED') return '错过后已按配置跳过'
  if (row.deliveryResult === 'STALE_SCHEDULE') return '旧计划已失效，未启动流程'
  return row.flowInstanceId ? `已启动流程实例 ${row.flowInstanceId}` : '没有命中绑定，已安全结束'
}

function eventSourceLabel(row: FlowBindingEventDelivery) {
  if (row.eventType === 'SCHEDULED') return '周期计划'
  if (row.sourceType === 'MODULE_VALIDATION') return row.actionCode === 'UPDATE' ? '模块编辑校验' : '模块新建校验'
  return `导入批次 ${row.sourceId}`
}

function eventBusinessSummary(row: FlowBindingEventDelivery) {
  if (row.sourceType !== 'MODULE_VALIDATION') return ''
  const action = row.actionCode === 'UPDATE' ? '编辑' : '新建'
  const record = row.recordId ? ` · 记录 ${row.recordId}` : ''
  return `${action} · ${row.ruleName || '已发布业务规则'} · ${row.errorCategory || '业务校验未通过'}${record}`
}

function modeLabel(mode: string) {
  return ({ DIRECT: '直接保存', AFTER_EXECUTION: '保存后启动流程', AFTER_APPROVAL: '审批通过后保存', FLOW_ONLY: '仅启动流程' } as Record<string, string>)[mode] || mode
}

function flowName(id?: number) {
  const flow = flows.value.find(item => item.id === id)
  return flow ? flow.name : '业务流程'
}

function actionName(id?: number) {
  return manualActions.value.find(item => item.id === id)?.name || '手动动作'
}

function scheduleSummary(value?: FlowScheduleConfig) {
  if (!value) return ''
  const misfire = value.misfirePolicy === 'SKIP' ? '错过后跳过' : '错过后补执行一次'
  if (value.scheduleMode === 'DAILY') return `每天 ${value.dailyTime} · ${misfire}`
  if (value.scheduleMode === 'WEEKLY') return `每周${['一','二','三','四','五','六','日'][(value.weekDay || 1) - 1]} ${value.dailyTime} · ${misfire}`
  return `每隔 ${value.intervalValue} ${({ MINUTE: '分钟', HOUR: '小时', DAY: '天' } as Record<string,string>)[value.intervalUnit || 'DAY']} · ${misfire}`
}

async function loadCatalog() {
  if (!token.value) return
  loading.value = true
  try {
    const [moduleRows, flowSummaries] = await Promise.all([
      api<RuntimeModuleCatalogItem[]>('/api/runtime/modules', {}, token.value),
      api<FlowDefinitionView[]>('/api/flows', {}, token.value),
    ])
    const flowRows = await Promise.all(flowSummaries.map(flow =>
      api<FlowDefinitionView>(`/api/flows/${flow.id}`, {}, token.value)))
    // 共享记录由来源模块控制触发规则，接收工作空间不能发布来源绑定。
    const configurable = moduleRows.filter(module => module.configurationMode !== 'SHARED_RECORDS')
    modules.value = configurable
    flows.value = flowRows
    form.moduleId = selectContextModule(configurable, queryText(route.query.module))?.moduleId
    await loadBindings()
    syncModuleRoute()
  } catch (error) {
    message.error(describeError(error))
  } finally {
    loading.value = false
  }
}

async function loadBindings() {
  pointReady.value = false
  bindings.value = { currentTenant: [], inheritedDefaults: [] }
  manualActions.value = []
  conditionFields.value = []; sampleRecords.value = []; eventDeliveries.value = []; savedSnapshot.value = ''
  useSample(); condition.value = emptyCondition()
  form.flowId = undefined; form.executionMode = reliableEvent.value ? 'FLOW_ONLY' : 'DIRECT'; form.priorityOrder = 0; form.mutuallyExclusive = true
  form.approvalStatusMapping = { COMPLETED: '', REJECTED: '', WITHDRAWN: '' }
  Object.assign(schedule, { scheduleMode: 'INTERVAL', intervalValue: 1, intervalUnit: 'DAY', dailyTime: '09:00', weekDay: 1, misfirePolicy: 'FIRE_ONCE' })
  if (!token.value || !form.moduleId || !can('VIEW')) return
  const moduleId = form.moduleId, triggerEvent = form.triggerEvent
  pointLoading.value = true
  try {
    const module = modules.value.find(item => item.moduleId === moduleId)
    if (triggerEvent === 'MANUAL' && module) {
      const runtime = await api<RuntimeModuleConfiguration>(`/api/runtime/modules/${module.moduleCode}/configuration`, {}, token.value)
      manualActions.value = runtime.configuration.actions.filter(action =>
        (action.actionType === 'CUSTOM' || action.code === 'APPROVAL') && action.status === 'ACTIVE')
      if (!manualActions.value.some(action => action.id === form.actionId)) form.actionId = manualActions.value[0]?.id
    } else form.actionId = undefined
    const scopedActionId = form.actionId
    const actionQuery = triggerEvent === 'MANUAL' && form.actionId ? `&actionId=${form.actionId}` : ''
    const [result, fields, deliveries] = await Promise.all([
      triggerEvent === 'MANUAL' && !form.actionId
        ? Promise.resolve({ currentTenant: [], inheritedDefaults: [] } as FlowBindingList)
        : api<FlowBindingList>(`/api/flow-bindings?moduleId=${moduleId}&triggerEvent=${encodeURIComponent(triggerEvent)}${actionQuery}`, {}, token.value),
      api<ConditionField[]>(`/api/flow-bindings/condition-fields?moduleId=${moduleId}&triggerEvent=${encodeURIComponent(triggerEvent)}`, {}, token.value),
      ['SCHEDULED', 'IMPORT_COMPLETED', 'EXCEPTION'].includes(triggerEvent)
        ? api<FlowBindingEventDelivery[]>(`/api/flow-bindings/events?moduleId=${moduleId}&triggerEvent=${encodeURIComponent(triggerEvent)}`, {}, token.value)
        : Promise.resolve([]),
    ])
    if (form.moduleId !== moduleId || form.triggerEvent !== triggerEvent || form.actionId !== scopedActionId) return
    bindings.value = result; conditionFields.value = fields; eventDeliveries.value = deliveries
    pointReady.value = true
    const current = activePublished.value
    if (current) {
      form.flowId = current.flowId
      form.executionMode = reliableEvent.value ? 'FLOW_ONLY' : current.executionMode
      form.priorityOrder = current.priorityOrder; form.mutuallyExclusive = current.mutuallyExclusive
      form.approvalStatusMapping = { COMPLETED: current.approvalStatusMapping?.COMPLETED || '', REJECTED: current.approvalStatusMapping?.REJECTED || '', WITHDRAWN: current.approvalStatusMapping?.WITHDRAWN || '' }
      condition.value = bindingCondition(current.condition, current.conditionExpression)
      if (current.schedule) Object.assign(schedule, current.schedule)
    }
    if (approvalActionSelected.value) form.executionMode = 'FLOW_ONLY'
    savedSnapshot.value = snapshot()
    if (!reliableEvent.value && module && allowsPermission(systemContext.value?.permissions, 'MODULE', module.moduleCode, 'LIST')) {
      const result = await api<{ records: RuntimeRecord[] }>(`/api/runtime/modules/${module.moduleCode}/records?page=1&pageSize=20`, {}, token.value)
      if (form.moduleId === moduleId && form.triggerEvent === triggerEvent) sampleRecords.value = result.records
    }
  } catch (error) { message.error(describeError(error)) }
  finally { if (form.moduleId === moduleId && form.triggerEvent === triggerEvent) pointLoading.value = false }
}

async function publish(replaceExisting = false) {
  if (!token.value || !form.moduleId || form.executionMode !== 'DIRECT' && !form.flowId) return
  if (form.triggerEvent === 'MANUAL' && !form.actionId) return message.warning('请先选择需要绑定的手动动作')
  if (issues.value.length) return message.warning(issues.value[0]!)
  busy.value = 'publish'
  try {
    const saved = await api<FlowBindingView>('/api/flow-bindings/publish', {
      method: 'POST', body: JSON.stringify(publishedBindingPayload({
        moduleId: form.moduleId, actionId: form.triggerEvent === 'MANUAL' ? form.actionId : undefined,
        flowId: form.flowId, triggerEvent: form.triggerEvent,
        executionMode: form.executionMode, priorityOrder: form.priorityOrder,
        condition: condition.value, mutuallyExclusive: form.mutuallyExclusive,
        approvalStatusMapping: form.approvalStatusMapping,
        schedule: form.triggerEvent === 'SCHEDULED' ? { ...schedule } : undefined,
      }, replaceExisting)),
    }, token.value)
    message.success(saved.executionMode === 'DIRECT' ? '已发布直接保存配置，不启动流程' : `已发布${eventLabel(saved.triggerEvent)}配置，当前使用流程${versionLabel(saved.flowVersionNumber)}；新动作读取最新发布版本`)
    resolution.value = undefined
    await loadBindings()
  } catch (error) {
    if (error instanceof ApiError && error.code === 'FLOW_BINDING_REPLACEMENT_REQUIRED') {
      Modal.confirm({
        title: '确认替换当前启用绑定？',
        content: '当前业务触发点已有启用绑定。确认后旧绑定会保留为“已停用”，不会删除历史记录。',
        okText: '停用旧绑定并发布', cancelText: '取消',
        onOk: () => publish(true),
      })
    } else message.error(describeError(error))
  } finally {
    busy.value = ''
  }
}

async function resolveBinding() {
  if (!token.value || !form.moduleId) return
  busy.value = 'resolve'
  try {
    resolution.value = await api<FlowBindingResolution>('/api/flow-bindings/resolve', {
      method: 'POST', body: JSON.stringify({
        moduleId: form.moduleId, actionId: form.triggerEvent === 'MANUAL' ? form.actionId : undefined,
        triggerEvent: form.triggerEvent, variables: testVariables(),
      }),
    }, token.value)
    message.success(resolution.value.executionMode === 'DIRECT' ? '已匹配直接保存配置' : `已解析到${bindingSourceLabel(resolution.value.source)}的流程${versionLabel(resolution.value.flowVersionNumber)}`)
  } catch (error) {
    resolution.value = undefined
    message.error(describeError(error))
  } finally {
    busy.value = ''
  }
}

async function previewCondition() {
  if (!token.value || !form.moduleId) return
  if (issues.value.length) return message.warning(issues.value[0]!)
  busy.value = 'preview'
  try {
    preview.value = await api('/api/flow-bindings/preview-condition', { method: 'POST', body: JSON.stringify({ moduleId: form.moduleId, triggerEvent: form.triggerEvent, condition: condition.value, variables: testVariables() }) }, token.value)
  } catch (error) { message.error(describeError(error)) }
  finally { busy.value = '' }
}
watch(() => [form.moduleId, form.triggerEvent], () => {
  resolution.value = undefined; preview.value = undefined
  if (!loading.value) { syncModuleRoute(); void loadBindings() }
})
watch(() => [route.query.module, route.query.triggerEvent], ([value, event]) => {
  if (!loading.value) {
    const moduleId = selectContextModule(modules.value, queryText(value))?.moduleId
    const triggerEvent = queryText(event) || 'CREATE'
    if (form.moduleId !== moduleId || form.triggerEvent !== triggerEvent) changePoint(moduleId, triggerEvent)
  }
})
watch(condition, () => { preview.value = undefined; resolution.value = undefined }, { deep: true })
onMounted(loadCatalog)
</script>

<template>
  <ModuleContextBanner v-if="route.query.module" :module-name="contextModule?.moduleName" :loading="loading" task=" Flow" @back="returnToModule" />
  <a-alert v-if="!can('VIEW')" type="warning" show-icon message="当前系统没有流程绑定查看权限" />
  <a-spin v-else :spinning="loading || pointLoading">
    <div class="flow-binding-layout">
      <section class="panel-card flow-binding-form">
        <div class="panel-title"><span><strong>业务触发点</strong><small>选择已发布模块和流程，不改变两者的配置层级</small></span></div>
        <a-form layout="vertical">
          <a-form-item label="已发布模块" required>
            <a-select :value="form.moduleId" @update:value="changePoint($event)" placeholder="选择模块" :options="modules.map(item => ({ value: item.moduleId, label: item.moduleName }))" />
          </a-form-item>
          <a-form-item label="触发事件" required>
            <a-select :value="form.triggerEvent" @update:value="changePoint(form.moduleId,$event)" :options="['CREATE','UPDATE','DELETE','TRANSFER','CONVERT','CONVERSION_UNLINK','STATUS_CHANGED','MANUAL','SCHEDULED','APPLICATION','IMPORT_COMPLETED','EXCEPTION'].map(value => ({ value, label: eventLabel(value) }))" />
          </a-form-item>
          <a-alert v-if="form.triggerEvent === 'IMPORT_COMPLETED'" class="section-alert" type="info" show-icon message="每个导入批次结束后触发一次" description="导入结果先持久保存，再可靠启动流程；流程暂不可用不会回滚已完成的导入。" />
          <a-alert v-else-if="form.triggerEvent === 'EXCEPTION'" class="section-alert" type="info" show-icon message="导入失败或模块业务校验未通过时触发" description="模块新建、编辑只记录已发布业务规则和唯一规则的拒绝；权限、并发和系统错误不会触发。原业务写入仍会回滚，流程暂不可用时由系统自动重试。" />
          <template v-else-if="form.triggerEvent === 'SCHEDULED'">
            <a-alert class="section-alert" type="info" show-icon message="按固定周期启动流程" description="系统保存下一次执行计划；停用或替换配置后，旧计划不会再启动流程。" />
            <a-form-item label="执行周期" required>
              <a-select v-model:value="schedule.scheduleMode" :options="[{value:'INTERVAL',label:'每隔一段时间'},{value:'DAILY',label:'每天'},{value:'WEEKLY',label:'每周'}]" />
            </a-form-item>
            <div v-if="schedule.scheduleMode === 'INTERVAL'" class="flow-binding-form__row">
              <a-form-item label="间隔数" required><a-input-number v-model:value="schedule.intervalValue" :min="1" :precision="0" style="width:100%" /></a-form-item>
              <a-form-item label="间隔单位" required><a-select v-model:value="schedule.intervalUnit" :options="[{value:'MINUTE',label:'分钟'},{value:'HOUR',label:'小时'},{value:'DAY',label:'天'}]" /></a-form-item>
            </div>
            <div v-else class="flow-binding-form__row">
              <a-form-item v-if="schedule.scheduleMode === 'WEEKLY'" label="星期" required><a-select v-model:value="schedule.weekDay" :options="['一','二','三','四','五','六','日'].map((label,index)=>({value:index+1,label:`星期${label}`}))" /></a-form-item>
              <a-form-item label="执行时间" required><a-input v-model:value="schedule.dailyTime" type="time" /></a-form-item>
            </div>
            <a-form-item label="服务中断期间错过执行" required>
              <a-select v-model:value="schedule.misfirePolicy" :options="[{value:'FIRE_ONCE',label:'恢复后补执行一次'},{value:'SKIP',label:'跳过错过的执行'}]" />
            </a-form-item>
          </template>
          <a-form-item v-if="form.triggerEvent === 'MANUAL'" label="模块动作" required extra="动作来自模块的已发布配置；审批动作只支持“仅启动流程”。">
            <a-select :value="form.actionId" placeholder="选择模块动作" :options="manualActions.map(item => ({ value: item.id, label: item.name }))" @update:value="changeManualAction" />
          </a-form-item>
          <a-alert v-if="form.triggerEvent === 'MANUAL' && pointReady && !manualActions.length" type="warning" show-icon class="section-alert" message="当前模块没有可配置的已发布手动动作" description="请先在模块配置中用字段选择器新增手动动作并发布。" />
          <a-form-item v-if="form.executionMode !== 'DIRECT'" label="已发布流程" required>
            <a-select v-model:value="form.flowId" placeholder="选择流程" :options="publishedFlows.map(item => ({ value: item.id, label: `${item.name} · ${versionLabel(item.versions.find(v => v.id === item.currentVersionId)?.versionNumber)}` }))" />
          </a-form-item>
          <div class="flow-binding-form__row">
            <a-form-item label="执行方式"><a-select v-model:value="form.executionMode" :disabled="reliableEvent || approvalActionSelected" :options="(reliableEvent || approvalActionSelected ? ['FLOW_ONLY'] : ['AFTER_EXECUTION','DIRECT','AFTER_APPROVAL','FLOW_ONLY']).map(value => ({ value, label: modeLabel(value) }))" /></a-form-item>
            <a-form-item label="优先级"><a-input-number v-model:value="form.priorityOrder" :min="0" style="width:100%" /></a-form-item>
          </div>
          <a-alert class="section-alert" type="info" show-icon :message="reliableEvent ? '事件事实保存后可靠启动流程；重复投递不会重复创建实例。' : ({DIRECT:'按正常业务保存，不启动流程。', AFTER_EXECUTION:'先保存业务数据，成功后启动流程；流程投递失败可以重试。', AFTER_APPROVAL:'先启动审批，通过前不保存业务数据。', FLOW_ONLY:'只启动流程，不保存本次业务变更。'} as Record<string,string>)[form.executionMode]" />
          <template v-if="form.executionMode === 'AFTER_APPROVAL' && ['CREATE', 'UPDATE'].includes(form.triggerEvent)">
            <a-alert class="section-alert" type="info" show-icon message="审批结果与业务状态" description="审批中的已有记录暂停普通编辑；下面的状态编码随绑定发布，并由本次申请固定。留空表示该结果不修改记录状态。" />
            <div class="flow-binding-form__row">
              <a-form-item label="审批通过后状态"><a-input v-model:value="form.approvalStatusMapping.COMPLETED" placeholder="例如 APPROVED" /></a-form-item>
              <a-form-item label="审批拒绝后状态"><a-input v-model:value="form.approvalStatusMapping.REJECTED" placeholder="例如 REJECTED" /></a-form-item>
            </div>
            <a-form-item label="发起人撤回后状态"><a-input v-model:value="form.approvalStatusMapping.WITHDRAWN" placeholder="例如 DRAFT" /></a-form-item>
          </template>
          <a-form-item label="命中条件" :extra="form.triggerEvent === 'SCHEDULED' ? '可按计划时间、周期方式和是否补执行组合条件。' : form.triggerEvent === 'EXCEPTION' ? '可先按异常来源区分导入失败和模块校验，再按动作、规则或导入统计组合条件。' : reliableEvent ? '可按导入结果、总行数、成功行数和失败行数组合条件。' : '字段来自当前模块的发布版本，并受页面与流程字段权限限制。'">
            <ConditionGroupEditor v-model="condition" :fields="conditionFields" />
          </a-form-item>
          <a-alert v-if="issues.length" type="warning" show-icon :message="issues[0]" />
          <a-checkbox v-model:checked="form.mutuallyExclusive">命中后不再寻找其他绑定</a-checkbox>
          <a-button v-if="can('BIND_PUBLISH')" type="primary" block class="flow-binding-submit" :disabled="!pointReady || pointLoading || !form.moduleId || form.triggerEvent === 'MANUAL' && !form.actionId || form.executionMode !== 'DIRECT' && !form.flowId || issues.length > 0" :loading="busy === 'publish'" @click="publish(false)">{{ reliableEvent ? '发布事件触发配置' : '发布动作配置' }}</a-button>
          <a-button v-if="dirty" block class="flow-binding-submit" @click="loadBindings">取消修改</a-button>
        </a-form>
      </section>

      <main class="flow-binding-main">
        <section class="panel-card">
          <div class="panel-title"><span><strong>当前生效范围</strong><small>当前工作空间配置优先；没有专属配置时使用系统公共配置</small></span><a-tag>{{ rows.filter(row => row.status === 'ACTIVE').length }} 个启用</a-tag></div>
          <div v-if="rows.length" class="flow-binding-list">
            <article v-for="binding in rows" :key="binding.id" :class="{ disabled: binding.status !== 'ACTIVE' }">
              <div><a-tag :color="binding.scopeType === 'TENANT_OVERRIDE' ? 'purple' : 'blue'">{{ binding.scopeType === 'TENANT_OVERRIDE' ? '当前工作空间' : '系统公共' }}</a-tag><a-tag :color="binding.status === 'ACTIVE' ? 'green' : 'default'">{{ binding.status === 'ACTIVE' ? '启用' : '已停用' }}</a-tag></div>
              <strong>{{ binding.triggerEvent === 'MANUAL' ? actionName(binding.actionId) : eventLabel(binding.triggerEvent) }} → {{ binding.executionMode === 'DIRECT' ? '直接保存' : flowName(binding.flowId) }}</strong>
              <p>{{ modeLabel(binding.executionMode) }} · {{ binding.flowVersionNumber ? versionLabel(binding.flowVersionNumber) : '不启动流程' }} · 优先级 {{ binding.priorityOrder }}</p>
              <small v-if="Object.keys(binding.approvalStatusMapping || {}).length">状态联动：{{ Object.entries(binding.approvalStatusMapping).map(([result, status]) => `${({ COMPLETED:'通过', REJECTED:'拒绝', WITHDRAWN:'撤回' } as Record<string,string>)[result] || result} → ${status}`).join('，') }}<br /></small>
              <small v-if="binding.schedule">周期：{{ scheduleSummary(binding.schedule) }}<br /></small>
              <small>条件：{{ conditionSummary(bindingCondition(binding.condition,binding.conditionExpression), conditionFields) }} · 已发布</small>
            </article>
          </div>
          <a-empty v-else :image="Empty.PRESENTED_IMAGE_SIMPLE" description="当前触发点尚未发布绑定" />
        </section>

        <section class="panel-card flow-binding-resolver">
          <div class="panel-title"><span><strong>真实解析验证</strong><small>按当前工作空间、触发点和业务条件验证最终流程</small></span></div>
          <a-select v-if="!reliableEvent" :value="sampleRecordId" allow-clear placeholder="选择已有记录填入测试值" style="width:100%;margin-bottom:12px" :options="sampleRecords.map(record=>({value:record.id,label:record.title}))" @update:value="useSample" />
          <a-form layout="vertical">
            <a-form-item v-for="field in testFields" :key="field.code" :label="field.name">
              <ConditionValueInput :field="field" :model-value="sampleValues[field.code]" @update:model-value="sampleValues[field.code]=$event; preview=undefined; resolution=undefined" />
            </a-form-item>
          </a-form>
          <a-space wrap><a-button v-if="can('TRIGGER')" :disabled="!pointReady || pointLoading || issues.length > 0" :loading="busy==='preview'" @click="previewCondition">测试当前条件</a-button><a-button v-if="can('TRIGGER')" :disabled="!pointReady || pointLoading || dirty || !activePublished" :loading="busy==='resolve'" @click="resolveBinding">验证已发布配置</a-button></a-space>
          <a-alert v-if="preview" class="section-alert" :type="preview.matched?'success':'warning'" show-icon :message="preview.matched?'当前测试值满足条件':'当前测试值不满足条件，业务操作将正常保存'" />
          <ul v-if="preview?.conditions.length"><li v-for="(result,index) in preview.conditions" :key="index">{{ conditionFields.find(field=>field.code===result.field)?.name || '不可用字段' }}：{{ result.matched?'满足':'不满足' }}</li></ul>
          <a-result v-if="resolution" status="success" :title="`${bindingSourceLabel(resolution.source)} · ${resolution.executionMode==='DIRECT'?'直接保存':versionLabel(resolution.flowVersionNumber)}`" :sub-title="resolution.resolutionReason">
            <template #extra><a-descriptions bordered size="small" :column="2">
              <a-descriptions-item label="绑定结果">已命中当前有效配置</a-descriptions-item>
              <a-descriptions-item label="工作空间范围">{{ resolution.sourceTenantId === resolution.requestedTenantId ? '当前工作空间' : '继承系统默认' }}</a-descriptions-item>
              <a-descriptions-item label="流程版本">{{ resolution.flowVersionNumber ? versionLabel(resolution.flowVersionNumber) : '不启动流程' }}</a-descriptions-item>
              <a-descriptions-item label="快照节点">{{ ((resolution.definitionSnapshot.nodes as unknown[]) || []).length }} 个</a-descriptions-item>
            </a-descriptions></template>
          </a-result>
        </section>

        <section v-if="reliableEvent" class="panel-card">
          <div class="panel-title"><span><strong>{{ form.triggerEvent === 'SCHEDULED' ? '下一次执行与近期结果' : '近期事件触发结果' }}</strong><small>{{ form.triggerEvent === 'SCHEDULED' ? '显示下一次计划时间和实际启动结果' : form.triggerEvent === 'EXCEPTION' ? '显示导入失败和模块校验异常的脱敏投递结果，不展示字段原值' : '显示真实导入事件的可靠投递结果，不展示导入原始字段和值' }}</small></span><a-tag>{{ eventDeliveries.length }} 条</a-tag></div>
          <div v-if="eventDeliveries.length" class="flow-binding-list">
            <article v-for="row in eventDeliveries" :key="row.id">
              <div><a-tag :color="row.status === 'PUBLISHED' ? 'green' : row.status === 'FAILED' ? 'orange' : 'blue'">{{ eventDeliveryStatus(row) }}</a-tag><a-tag>{{ eventSourceLabel(row) }}</a-tag></div>
              <strong>{{ eventLabel(row.eventType) }}</strong>
              <p v-if="eventBusinessSummary(row)">{{ eventBusinessSummary(row) }}</p>
              <p>{{ row.occurredTime ? new Date(row.occurredTime).toLocaleString() : '等待记录时间' }}<template v-if="row.retryCount"> · 已重试 {{ row.retryCount }} 次</template></p>
              <small v-if="row.lastError">{{ row.lastError }}</small>
            </article>
          </div>
          <a-empty v-else :image="Empty.PRESENTED_IMAGE_SIMPLE" :description="form.triggerEvent === 'SCHEDULED' ? '发布周期配置后，这里会显示下一次执行计划' : form.triggerEvent === 'EXCEPTION' ? '还没有导入失败或模块业务校验异常' : '还没有实际导入事件；完成一次导入后可在这里查看结果'" />
        </section>
      </main>
    </div>
  </a-spin>
</template>
