<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { Empty, Modal, message } from 'ant-design-vue'
import { ApiError, api } from '../api'
import { bindingSourceLabel, publishedBindingPayload } from '../flow-binding'
import { allowsPermission } from '../permissions'
import { systemContext, systemTokens } from '../session'
import type { FlowBindingList, FlowBindingResolution, FlowBindingView, FlowDefinitionView, RuntimeModuleCatalogItem } from '../types'

const token = computed(() => systemTokens.value?.accessToken)
const modules = ref<RuntimeModuleCatalogItem[]>([])
const flows = ref<FlowDefinitionView[]>([])
const bindings = ref<FlowBindingList>({ currentTenant: [], inheritedDefaults: [] })
const resolution = ref<FlowBindingResolution>()
const loading = ref(false)
const busy = ref('')
const form = reactive({
  moduleId: undefined as number | undefined,
  triggerEvent: 'CREATE',
  flowId: undefined as number | undefined,
  executionMode: 'AFTER_EXECUTION',
  priorityOrder: 0,
  conditionExpression: 'amount > 10',
  mutuallyExclusive: true,
})
const resolveAmount = ref(8000)

const publishedFlows = computed(() => flows.value.filter(flow => !!flow.currentVersionId))
const rows = computed(() => [...bindings.value.currentTenant, ...bindings.value.inheritedDefaults])

function can(action: string) {
  return allowsPermission(systemContext.value?.permissions, 'FLOW', 'SYSTEM', action)
    || allowsPermission(systemContext.value?.permissions, 'FLOW', '*', action)
}

function describeError(error: unknown) {
  if (error instanceof ApiError) return `${error.message}${error.traceId ? `（追踪号 ${error.traceId}）` : ''}`
  return error instanceof Error ? error.message : '操作失败'
}

function eventLabel(event: string) {
  return ({ CREATE: '新建后', UPDATE: '更新后', DELETE: '删除前', STATUS_CHANGED: '状态变化', MANUAL: '手动触发',
    SCHEDULED: '定时触发', APPLICATION: '应用调用', IMPORT_COMPLETED: '导入完成', EXCEPTION: '异常时' } as Record<string, string>)[event] || event
}

function modeLabel(mode: string) {
  return ({ DIRECT: '直接执行', AFTER_EXECUTION: '业务成功后执行', AFTER_APPROVAL: '审批通过后执行', FLOW_ONLY: '仅执行流程' } as Record<string, string>)[mode] || mode
}

function flowName(id: number) {
  const flow = flows.value.find(item => item.id === id)
  return flow ? flow.name : '业务流程'
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
    form.moduleId ||= moduleRows[0]?.moduleId
    form.flowId ||= publishedFlows.value[0]?.id
    await loadBindings()
  } catch (error) {
    message.error(describeError(error))
  } finally {
    loading.value = false
  }
}

async function loadBindings() {
  if (!token.value || !form.moduleId || !can('VIEW')) return
  try {
    bindings.value = await api<FlowBindingList>(
      `/api/flow-bindings?moduleId=${form.moduleId}&triggerEvent=${encodeURIComponent(form.triggerEvent)}`, {}, token.value)
  } catch (error) {
    message.error(describeError(error))
  }
}

async function publish(replaceExisting = false) {
  if (!token.value || !form.moduleId || !form.flowId) return
  busy.value = 'publish'
  try {
    const saved = await api<FlowBindingView>('/api/flow-bindings/publish', {
      method: 'POST', body: JSON.stringify(publishedBindingPayload({
        moduleId: form.moduleId, flowId: form.flowId, triggerEvent: form.triggerEvent,
        executionMode: form.executionMode, priorityOrder: form.priorityOrder,
        conditionExpression: form.conditionExpression, mutuallyExclusive: form.mutuallyExclusive,
      }, replaceExisting)),
    }, token.value)
    message.success(`已发布${eventLabel(saved.triggerEvent)}绑定，固定使用流程版本 V${saved.flowVersionNumber}`)
    resolution.value = undefined
    await loadBindings()
  } catch (error) {
    if (error instanceof ApiError && error.code === 'FLOW_BINDING_REPLACEMENT_REQUIRED') {
      Modal.confirm({
        title: '确认替换当前启用绑定？',
        content: '同一模块和触发事件只能有一个启用绑定。确认后旧绑定会保留为“已停用”，不会删除历史记录。',
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
        moduleId: form.moduleId, triggerEvent: form.triggerEvent, variables: { amount: resolveAmount.value },
      }),
    }, token.value)
    message.success(`已解析到${bindingSourceLabel(resolution.value.source)}流程版本 V${resolution.value.flowVersionNumber}`)
  } catch (error) {
    resolution.value = undefined
    message.error(describeError(error))
  } finally {
    busy.value = ''
  }
}

watch(() => [form.moduleId, form.triggerEvent], () => {
  resolution.value = undefined
  void loadBindings()
})
onMounted(loadCatalog)
</script>

<template>
  <a-alert v-if="!can('VIEW')" type="warning" show-icon message="当前系统没有流程绑定查看权限" />
  <a-spin v-else :spinning="loading">
    <div class="flow-binding-layout">
      <section class="panel-card flow-binding-form">
        <div class="panel-title"><span><strong>业务触发点</strong><small>选择已发布模块和流程，不改变两者的配置层级</small></span></div>
        <a-form layout="vertical">
          <a-form-item label="已发布模块" required>
            <a-select v-model:value="form.moduleId" placeholder="选择模块" :options="modules.map(item => ({ value: item.moduleId, label: `${item.moduleName}（${item.moduleCode}）` }))" />
          </a-form-item>
          <a-form-item label="触发事件" required>
            <a-select v-model:value="form.triggerEvent" :options="['CREATE','UPDATE','DELETE','STATUS_CHANGED','MANUAL','SCHEDULED','APPLICATION','IMPORT_COMPLETED','EXCEPTION'].map(value => ({ value, label: eventLabel(value) }))" />
          </a-form-item>
          <a-form-item label="已发布流程" required>
            <a-select v-model:value="form.flowId" placeholder="选择流程" :options="publishedFlows.map(item => ({ value: item.id, label: `${item.name} · 当前 V${item.versions.find(v => v.id === item.currentVersionId)?.versionNumber || '-'}` }))" />
          </a-form-item>
          <div class="flow-binding-form__row">
            <a-form-item label="执行方式"><a-select v-model:value="form.executionMode" :options="['AFTER_EXECUTION','DIRECT','AFTER_APPROVAL','FLOW_ONLY'].map(value => ({ value, label: modeLabel(value) }))" /></a-form-item>
            <a-form-item label="优先级"><a-input-number v-model:value="form.priorityOrder" :min="0" style="width:100%" /></a-form-item>
          </div>
          <a-form-item label="命中条件" extra="示例：amount > 10；留空表示始终命中"><a-input v-model:value="form.conditionExpression" placeholder="amount > 10" /></a-form-item>
          <a-checkbox v-model:checked="form.mutuallyExclusive">命中后不再寻找其他绑定</a-checkbox>
          <a-button v-if="can('BIND_PUBLISH')" type="primary" block class="flow-binding-submit" :disabled="!form.moduleId || !form.flowId" :loading="busy === 'publish'" @click="publish(false)">发布触发绑定</a-button>
        </a-form>
      </section>

      <main class="flow-binding-main">
        <section class="panel-card">
          <div class="panel-title"><span><strong>当前生效范围</strong><small>当前租户配置优先；没有覆盖时继承主租户默认</small></span><a-tag>{{ rows.filter(row => row.status === 'ACTIVE').length }} 个启用</a-tag></div>
          <div v-if="rows.length" class="flow-binding-list">
            <article v-for="binding in rows" :key="binding.id" :class="{ disabled: binding.status !== 'ACTIVE' }">
              <div><a-tag :color="binding.scopeType === 'TENANT_OVERRIDE' ? 'purple' : 'blue'">{{ binding.scopeType === 'TENANT_OVERRIDE' ? '租户覆盖' : '主租户默认' }}</a-tag><a-tag :color="binding.status === 'ACTIVE' ? 'green' : 'default'">{{ binding.status === 'ACTIVE' ? '启用' : '已停用' }}</a-tag></div>
              <strong>{{ eventLabel(binding.triggerEvent) }} → {{ flowName(binding.flowId) }}</strong>
              <p>{{ modeLabel(binding.executionMode) }} · 流程版本 V{{ binding.flowVersionNumber }} · 优先级 {{ binding.priorityOrder }}</p>
              <small>条件：{{ binding.conditionExpression || '始终命中' }} · 已发布</small>
            </article>
          </div>
          <a-empty v-else :image="Empty.PRESENTED_IMAGE_SIMPLE" description="当前触发点尚未发布绑定" />
        </section>

        <section class="panel-card flow-binding-resolver">
          <div class="panel-title"><span><strong>真实解析验证</strong><small>按当前租户、触发点和业务变量解析最终版本快照</small></span></div>
          <div class="flow-binding-resolver__input"><a-input-number v-model:value="resolveAmount" addon-before="amount" style="width:100%" /><a-button v-if="can('TRIGGER')" :loading="busy === 'resolve'" @click="resolveBinding">解析绑定</a-button></div>
          <a-result v-if="resolution" status="success" :title="`${bindingSourceLabel(resolution.source)} · 流程版本 V${resolution.flowVersionNumber}`" :sub-title="resolution.resolutionReason">
            <template #extra><a-descriptions bordered size="small" :column="2">
              <a-descriptions-item label="绑定结果">已命中当前有效配置</a-descriptions-item>
              <a-descriptions-item label="工作空间范围">{{ resolution.sourceTenantId === resolution.requestedTenantId ? '当前工作空间' : '继承系统默认' }}</a-descriptions-item>
              <a-descriptions-item label="流程版本">V{{ resolution.flowVersionNumber }}</a-descriptions-item>
              <a-descriptions-item label="快照节点">{{ ((resolution.definitionSnapshot.nodes as unknown[]) || []).length }} 个</a-descriptions-item>
              <a-descriptions-item label="定义哈希" :span="2"><code>{{ resolution.definitionHash }}</code></a-descriptions-item>
            </a-descriptions></template>
          </a-result>
        </section>
      </main>
    </div>
  </a-spin>
</template>
