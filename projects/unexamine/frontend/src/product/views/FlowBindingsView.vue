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
import type { ConfiguredAction, FlowBindingList, FlowBindingResolution, FlowBindingView, FlowDefinitionView, RuntimeModuleCatalogItem, RuntimeModuleConfiguration, RuntimeRecord } from '../types'

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
  triggerEvent: ['CREATE','UPDATE','DELETE','TRANSFER','CONVERT','STATUS_CHANGED','MANUAL','SCHEDULED','APPLICATION','IMPORT_COMPLETED','EXCEPTION'].includes(queryText(route.query.triggerEvent)) ? queryText(route.query.triggerEvent) : 'CREATE',
  actionId: undefined as number | undefined,
  flowId: undefined as number | undefined,
  executionMode: 'DIRECT',
  priorityOrder: 0,
  mutuallyExclusive: true,
})
const condition = ref(emptyCondition())
const conditionFields = ref<ConditionField[]>([])
const sampleValues = reactive<Record<string, unknown>>({})
const sampleRecords = ref<RuntimeRecord[]>([])
const sampleRecordId = ref<number>()
const pointLoading = ref(false)
const pointReady = ref(false)
const savedSnapshot = ref('')
const preview = ref<{ matched: boolean; conditions: Array<{ field: string; operator: string; matched: boolean }> }>()
function snapshot() { return JSON.stringify({ actionId: form.actionId, mode: form.executionMode, flowId: form.flowId, priority: form.priorityOrder, exclusive: form.mutuallyExclusive, condition: condition.value }) }
const dirty = computed(() => Boolean(savedSnapshot.value && savedSnapshot.value !== snapshot()))
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
  void loadBindings()
}

const publishedFlows = computed(() => flows.value.filter(flow => !!flow.currentVersionId))
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
  return ({ CREATE: '新建时', UPDATE: '编辑时', DELETE: '删除前', TRANSFER: '转交负责人和部门时', CONVERT: '转化时', STATUS_CHANGED: '状态变化时', MANUAL: '手动触发',
    SCHEDULED: '定时触发', APPLICATION: '应用调用', IMPORT_COMPLETED: '导入完成', EXCEPTION: '异常时' } as Record<string, string>)[event] || event
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
    modules.value = moduleRows
    flows.value = flowRows
    form.moduleId = selectContextModule(moduleRows, queryText(route.query.module))?.moduleId
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
  conditionFields.value = []; sampleRecords.value = []; savedSnapshot.value = ''
  useSample(); condition.value = emptyCondition()
  form.flowId = undefined; form.executionMode = 'DIRECT'; form.priorityOrder = 0; form.mutuallyExclusive = true
  if (!token.value || !form.moduleId || !can('VIEW')) return
  const moduleId = form.moduleId, triggerEvent = form.triggerEvent
  pointLoading.value = true
  try {
    const module = modules.value.find(item => item.moduleId === moduleId)
    if (triggerEvent === 'MANUAL' && module) {
      const runtime = await api<RuntimeModuleConfiguration>(`/api/runtime/modules/${module.moduleCode}/configuration`, {}, token.value)
      manualActions.value = runtime.configuration.actions.filter(action => action.actionType === 'CUSTOM' && action.status === 'ACTIVE')
      if (!manualActions.value.some(action => action.id === form.actionId)) form.actionId = manualActions.value[0]?.id
    } else form.actionId = undefined
    const scopedActionId = form.actionId
    const actionQuery = triggerEvent === 'MANUAL' && form.actionId ? `&actionId=${form.actionId}` : ''
    const [result, fields] = await Promise.all([
      triggerEvent === 'MANUAL' && !form.actionId
        ? Promise.resolve({ currentTenant: [], inheritedDefaults: [] } as FlowBindingList)
        : api<FlowBindingList>(`/api/flow-bindings?moduleId=${moduleId}&triggerEvent=${encodeURIComponent(triggerEvent)}${actionQuery}`, {}, token.value),
      api<ConditionField[]>(`/api/flow-bindings/condition-fields?moduleId=${moduleId}&triggerEvent=${encodeURIComponent(triggerEvent)}`, {}, token.value),
    ])
    if (form.moduleId !== moduleId || form.triggerEvent !== triggerEvent || form.actionId !== scopedActionId) return
    bindings.value = result; conditionFields.value = fields
    pointReady.value = true
    const current = activePublished.value
    if (current) {
      form.flowId = current.flowId; form.executionMode = current.executionMode; form.priorityOrder = current.priorityOrder; form.mutuallyExclusive = current.mutuallyExclusive
      condition.value = bindingCondition(current.condition, current.conditionExpression)
    }
    savedSnapshot.value = snapshot()
    if (module && allowsPermission(systemContext.value?.permissions, 'MODULE', module.moduleCode, 'LIST')) {
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
      }, replaceExisting)),
    }, token.value)
    message.success(saved.executionMode === 'DIRECT' ? '已发布直接保存配置，不启动流程' : `已发布${eventLabel(saved.triggerEvent)}配置，固定使用流程${versionLabel(saved.flowVersionNumber)}`)
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
            <a-select :value="form.triggerEvent" @update:value="changePoint(form.moduleId,$event)" :options="['CREATE','UPDATE','DELETE','TRANSFER','CONVERT','STATUS_CHANGED','MANUAL','SCHEDULED','APPLICATION','IMPORT_COMPLETED','EXCEPTION'].map(value => ({ value, label: eventLabel(value) }))" />
          </a-form-item>
          <a-form-item v-if="form.triggerEvent === 'MANUAL'" label="手动动作" required extra="动作来自模块的已发布配置；每个动作可独立设置条件和审批方式。">
            <a-select :value="form.actionId" placeholder="选择手动动作" :options="manualActions.map(item => ({ value: item.id, label: item.name }))" @update:value="changeManualAction" />
          </a-form-item>
          <a-alert v-if="form.triggerEvent === 'MANUAL' && pointReady && !manualActions.length" type="warning" show-icon class="section-alert" message="当前模块没有可配置的已发布手动动作" description="请先在模块配置中用字段选择器新增手动动作并发布。" />
          <a-form-item v-if="form.executionMode !== 'DIRECT'" label="已发布流程" required>
            <a-select v-model:value="form.flowId" placeholder="选择流程" :options="publishedFlows.map(item => ({ value: item.id, label: `${item.name} · ${versionLabel(item.versions.find(v => v.id === item.currentVersionId)?.versionNumber)}` }))" />
          </a-form-item>
          <div class="flow-binding-form__row">
            <a-form-item label="执行方式"><a-select v-model:value="form.executionMode" :options="['AFTER_EXECUTION','DIRECT','AFTER_APPROVAL','FLOW_ONLY'].map(value => ({ value, label: modeLabel(value) }))" /></a-form-item>
            <a-form-item label="优先级"><a-input-number v-model:value="form.priorityOrder" :min="0" style="width:100%" /></a-form-item>
          </div>
          <a-alert class="section-alert" type="info" show-icon :message="({DIRECT:'按正常业务保存，不启动流程。', AFTER_EXECUTION:'先保存业务数据，成功后启动流程；流程投递失败可以重试。', AFTER_APPROVAL:'先启动审批，通过前不保存业务数据。', FLOW_ONLY:'只启动流程，不保存本次业务变更。'} as Record<string,string>)[form.executionMode]" />
          <a-form-item label="命中条件" extra="字段来自当前模块的发布版本，并受页面与流程字段权限限制。">
            <ConditionGroupEditor v-model="condition" :fields="conditionFields" />
          </a-form-item>
          <a-alert v-if="issues.length" type="warning" show-icon :message="issues[0]" />
          <a-checkbox v-model:checked="form.mutuallyExclusive">命中后不再寻找其他绑定</a-checkbox>
          <a-button v-if="can('BIND_PUBLISH')" type="primary" block class="flow-binding-submit" :disabled="!pointReady || pointLoading || !form.moduleId || form.triggerEvent === 'MANUAL' && !form.actionId || form.executionMode !== 'DIRECT' && !form.flowId || issues.length > 0" :loading="busy === 'publish'" @click="publish(false)">发布动作配置</a-button>
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
              <small>条件：{{ conditionSummary(bindingCondition(binding.condition,binding.conditionExpression), conditionFields) }} · 已发布</small>
            </article>
          </div>
          <a-empty v-else :image="Empty.PRESENTED_IMAGE_SIMPLE" description="当前触发点尚未发布绑定" />
        </section>

        <section class="panel-card flow-binding-resolver">
          <div class="panel-title"><span><strong>真实解析验证</strong><small>按当前工作空间、触发点和业务条件验证最终流程</small></span></div>
          <a-select :value="sampleRecordId" allow-clear placeholder="选择已有记录填入测试值" style="width:100%;margin-bottom:12px" :options="sampleRecords.map(record=>({value:record.id,label:record.title}))" @update:value="useSample" />
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
      </main>
    </div>
  </a-spin>
</template>
