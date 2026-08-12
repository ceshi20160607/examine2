<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'

import { flowApi } from '@/services/flow'
import type {
  FlowExtensionDependency,
  FlowExtensionDependencyType,
  FlowExtensionFieldMode,
  FlowExtensionGraph,
  FlowExtensionNode,
  FlowExtensionNodeCatalogEntry,
  FlowExtensionNodeType,
  FlowExtensionPublishImpact,
} from '@/types/flow'

const props = defineProps<{
  systemId: string
  definitionId: string
  revision: number
}>()

const emit = defineEmits<{
  saved: []
}>()

const catalog = ref<FlowExtensionNodeCatalogEntry[]>([])
const graph = ref<FlowExtensionGraph>(emptyGraph())
const selectedCode = ref('start')
const busy = ref(false)
const error = ref('')
const notice = ref('')
const impact = ref<FlowExtensionPublishImpact | null>(null)
const configText = ref('{}')
const expectedRevision = ref(props.revision)

const selectedNode = computed(() => (
  graph.value.nodes.find(node => node.code === selectedCode.value) ?? null
))
const groupedCatalog = computed(() => {
  const groups = new Map<string, FlowExtensionNodeCatalogEntry[]>()
  catalog.value.forEach((entry) => {
    groups.set(entry.family, [...(groups.get(entry.family) ?? []), entry])
  })
  return [...groups.entries()]
})

watch(selectedNode, (node) => {
  configText.value = JSON.stringify(node?.config ?? {}, null, 2)
}, { immediate: true })

watch(() => [props.systemId, props.definitionId], () => {
  graph.value = emptyGraph()
  selectedCode.value = 'start'
  impact.value = null
  notice.value = ''
  expectedRevision.value = props.revision
  void load()
})

watch(() => props.revision, value => { expectedRevision.value = value })

onMounted(load)

async function load() {
  error.value = ''
  try {
    const [entries, stored, currentImpact] = await Promise.all([
      flowApi.extensionNodeCatalog(props.systemId),
      flowApi.extensionDraft(props.systemId, props.definitionId),
      flowApi.extensionPublishImpact(props.systemId, props.definitionId),
    ])
    catalog.value = entries
    if (stored?.graph) graph.value = cloneGraph(stored.graph)
    impact.value = currentImpact
    selectedCode.value = graph.value.nodes.some(node => node.code === selectedCode.value)
      ? selectedCode.value
      : graph.value.nodes[0]?.code ?? ''
  } catch (cause) {
    error.value = message(cause)
  }
}

function emptyGraph(): FlowExtensionGraph {
  return {
    nodes: [
      {
        code: 'start', name: '开始', type: 'START', applicationCode: 'default',
        moduleCode: null, config: {}, fieldPolicies: [], next: ['end'],
      },
      {
        code: 'end', name: '结束', type: 'END', applicationCode: 'default',
        moduleCode: null, config: {}, fieldPolicies: [], next: [],
      },
    ],
    dependencies: [],
  }
}

function cloneGraph(value: FlowExtensionGraph): FlowExtensionGraph {
  return JSON.parse(JSON.stringify(value)) as FlowExtensionGraph
}

function nodeFor(type: FlowExtensionNodeType, ordinal: number, next?: string): FlowExtensionNode {
  const entry = catalog.value.find(item => item.type === type)
  const code = uniqueCode(type.toLowerCase(), ordinal)
  return {
    code,
    name: entry?.name ?? type,
    type,
    applicationCode: 'default',
    moduleCode: entry?.requiresBusinessRecord ? 'module_code' : null,
    config: defaultConfig(type),
    fieldPolicies: ['APPROVAL', 'CONDITIONAL_APPROVAL', 'FORM'].includes(type)
      ? [{ fieldCode: 'title', mode: type === 'FORM' ? 'REQUIRED' : 'VISIBLE' }]
      : [],
    next: type === 'END' ? [] : [next ?? 'end'],
  }
}

function uniqueCode(seed: string, ordinal = graph.value.nodes.length + 1) {
  const base = seed.replace(/[^a-z0-9_]/g, '_').slice(0, 50) || 'node'
  let value = `${base}_${ordinal}`
  let suffix = ordinal
  while (graph.value.nodes.some(node => node.code === value)) value = `${base}_${++suffix}`
  return value
}

function defaultConfig(type: FlowExtensionNodeType): Record<string, unknown> {
  switch (type) {
    case 'APPROVAL': return { approvalMode: 'SEQUENTIAL' }
    case 'CONDITIONAL_APPROVAL': return { condition: { field: 'amount', operator: 'GT', value: 0 } }
    case 'COPY': return { recipients: [1] }
    case 'CONDITIONAL_BRANCH':
    case 'PARALLEL_GATEWAY':
    case 'INCLUSIVE_GATEWAY': return { routes: [{ code: 'route_a' }, { code: 'route_b' }] }
    case 'SUBFLOW': return { definitionId: 1 }
    case 'AUTOMATION': return { operation: 'RUN' }
    case 'FORM': return { formAction: 'EDIT' }
    case 'TASK': return { taskKind: 'WORK_TASK', assigneeMemberId: 1, title: '待办任务', description: '' }
    case 'NOTIFICATION': return { templateCode: 'template_code', recipients: [1] }
    case 'FIELD_UPDATE': return { values: {} }
    case 'DATA_CREATE_UPDATE': return { operation: 'CREATE', targetModuleCode: 'module_code', values: {} }
    case 'WAIT': return { eventKey: 'event.key' }
    case 'TIMER': return { delaySeconds: 60 }
    case 'MESSAGE': return { templateCode: 'template_code', recipients: [1] }
    case 'WEBHOOK': return { endpointCode: 'endpoint', url: 'https://example.test/hook', timeoutSeconds: 10, maxAttempts: 3, baseBackoffSeconds: 5 }
    case 'EXTERNAL': return { topic: 'flow.task', leaseSeconds: 60, maxAttempts: 3, resultJsonLimitBytes: 4096 }
    case 'AI_ASSIST': return { modelPolicyCode: 'default', fieldCode: 'summary', confirmationRequired: true }
    default: return {}
  }
}

function dependencyFor(node: FlowExtensionNode): FlowExtensionDependency | null {
  const type: Partial<Record<FlowExtensionNodeType, FlowExtensionDependencyType>> = {
    SUBFLOW: 'FLOW_DEFINITION',
    NOTIFICATION: 'MESSAGE_TEMPLATE',
    MESSAGE: 'MESSAGE_TEMPLATE',
    WEBHOOK: 'OPENAPI_APPLICATION',
    AI_ASSIST: 'AI_POLICY',
  }
  const dependencyType = type[node.type]
  if (!dependencyType) return null
  return {
    sourceNodeCode: node.code,
    type: dependencyType,
    targetSystemId: Number(props.systemId),
    targetTenantId: null,
    targetKey: node.type === 'SUBFLOW' ? '1' : `${node.code}_dependency`,
    requiredVersion: 1,
    versionMode: node.type === 'SUBFLOW' ? 'EXACT' : 'MINIMUM',
  }
}

function addNode(entry: FlowExtensionNodeCatalogEntry) {
  if (entry.type === 'START' && graph.value.nodes.some(node => node.type === 'START')) return
  const end = graph.value.nodes.find(node => node.type === 'END')
  if (entry.type === 'END' && end) return
  const newNode = nodeFor(entry.type, graph.value.nodes.length + 1, end?.code)
  if (['CONDITIONAL_BRANCH', 'PARALLEL_GATEWAY', 'INCLUSIVE_GATEWAY'].includes(entry.type)) {
    newNode.next = end ? [end.code, end.code] : []
  }
  const predecessor = end
    ? [...graph.value.nodes].reverse().find(node => node.type !== 'END' && node.next.includes(end.code))
    : null
  if (predecessor) predecessor.next = predecessor.next.map(code => code === end!.code ? newNode.code : code)
  graph.value.nodes.splice(end ? graph.value.nodes.indexOf(end) : graph.value.nodes.length, 0, newNode)
  const dependency = dependencyFor(newNode)
  if (dependency) graph.value.dependencies.push(dependency)
  selectedCode.value = newNode.code
  impact.value = null
}

function removeSelected() {
  const node = selectedNode.value
  if (!node || node.type === 'START' || node.type === 'END') return
  const fallback = node.next[0] ?? graph.value.nodes.find(item => item.type === 'END')?.code
  graph.value.nodes.forEach((candidate) => {
    candidate.next = candidate.next.map(code => code === node.code ? fallback : code)
      .filter((code): code is string => Boolean(code))
  })
  graph.value.nodes = graph.value.nodes.filter(candidate => candidate.code !== node.code)
  graph.value.dependencies = graph.value.dependencies.filter(item => item.sourceNodeCode !== node.code)
  selectedCode.value = graph.value.nodes[0]?.code ?? ''
  impact.value = null
}

function renameSelected(value: string) {
  const node = selectedNode.value
  const nextCode = value.trim()
  if (!node || !nextCode || nextCode === node.code) return
  if (!/^[a-z][a-z0-9_]{0,59}$/.test(nextCode)) {
    error.value = '节点代码需以小写字母开头，仅含小写字母、数字和下划线，最长 60 位'
    return
  }
  if (graph.value.nodes.some(candidate => candidate !== node && candidate.code === nextCode)) {
    error.value = '节点代码不能重复'
    return
  }
  const previous = node.code
  graph.value.nodes.forEach((candidate) => {
    candidate.next = candidate.next.map(code => code === previous ? nextCode : code)
  })
  graph.value.dependencies.forEach((dependency) => {
    if (dependency.sourceNodeCode === previous) dependency.sourceNodeCode = nextCode
  })
  node.code = nextCode
  selectedCode.value = nextCode
  error.value = ''
}

function applyConfig() {
  if (!selectedNode.value) return
  try {
    const parsed = JSON.parse(configText.value) as unknown
    if (!parsed || Array.isArray(parsed) || typeof parsed !== 'object') {
      throw new Error('节点配置必须是 JSON 对象')
    }
    selectedNode.value.config = parsed as Record<string, unknown>
    error.value = ''
    notice.value = '节点配置已应用到当前草稿'
  } catch (cause) {
    error.value = message(cause)
  }
}

function addFieldPolicy() {
  selectedNode.value?.fieldPolicies.push({ fieldCode: '', mode: 'VISIBLE' })
}

function removeFieldPolicy(index: number) {
  selectedNode.value?.fieldPolicies.splice(index, 1)
}

function addDependency() {
  const node = selectedNode.value
  if (!node) return
  graph.value.dependencies.push(dependencyFor(node) ?? {
    sourceNodeCode: node.code,
    type: 'MODULE_CONFIGURATION',
    targetSystemId: Number(props.systemId),
    targetTenantId: null,
    targetKey: 'module_code',
    requiredVersion: 1,
    versionMode: 'MINIMUM',
  })
}

async function save() {
  busy.value = true
  error.value = ''
  notice.value = ''
  try {
    applyConfig()
    if (error.value) return
    const stored = await flowApi.saveExtensionDraft(
      props.systemId,
      props.definitionId,
      expectedRevision.value,
      cloneGraph(graph.value),
    )
    graph.value = cloneGraph(stored.graph)
    expectedRevision.value = stored.sourceRevision
    impact.value = await flowApi.extensionPublishImpact(props.systemId, props.definitionId)
    notice.value = `扩展草稿已保存（校验和 ${stored.checksum.slice(0, 12)}…）`
    emit('saved')
  } catch (cause) {
    error.value = message(cause)
  } finally {
    busy.value = false
  }
}

async function refreshImpact() {
  busy.value = true
  error.value = ''
  try {
    impact.value = await flowApi.extensionPublishImpact(props.systemId, props.definitionId)
  } catch (cause) {
    error.value = message(cause)
  } finally {
    busy.value = false
  }
}

function message(cause: unknown) {
  return cause instanceof Error ? cause.message : '流程扩展操作失败'
}
</script>

<template>
  <section class="flow-extension-editor">
    <header class="flow-extension-heading">
      <div>
        <h3>扩展节点编排</h3>
        <p>后端目录提供 {{ catalog.length }} 类节点；草稿按定义修订号 {{ expectedRevision }} 乐观锁保存。</p>
      </div>
      <button type="button" :disabled="busy" @click="refreshImpact">刷新发布影响</button>
    </header>

    <p v-if="error" class="flow-extension-error" role="alert">{{ error }}</p>
    <p v-if="notice" class="flow-extension-notice" role="status">{{ notice }}</p>

    <div class="flow-extension-workspace">
      <aside class="flow-extension-catalog">
        <h4>节点目录（{{ catalog.length }}/22）</h4>
        <section v-for="[family, entries] in groupedCatalog" :key="family">
          <strong>{{ family }}</strong>
          <button
            v-for="entry in entries"
            :key="entry.type"
            class="flow-extension-catalog-item"
            type="button"
            :disabled="busy || (entry.type === 'START' || entry.type === 'END')"
            :title="`${entry.executor} · ${entry.requiresBusinessRecord ? '需要业务记录' : '无需业务记录'}`"
            @click="addNode(entry)"
          >
            {{ entry.name }} <small>{{ entry.type }}</small>
          </button>
        </section>
      </aside>

      <main class="flow-extension-graph">
        <h4>草稿图（{{ graph.nodes.length }} 个节点）</h4>
        <ol>
          <li
            v-for="node in graph.nodes"
            :key="node.code"
            :class="{ selected: node.code === selectedCode }"
          >
            <button type="button" @click="selectedCode = node.code">
              <strong>{{ node.name }}</strong>
              <small>{{ node.code }} · {{ node.type }} → {{ node.next.join(', ') || '结束' }}</small>
            </button>
          </li>
        </ol>
      </main>

      <aside v-if="selectedNode" class="flow-extension-inspector">
        <header>
          <h4>节点配置</h4>
          <button
            v-if="!['START', 'END'].includes(selectedNode.type)"
            class="danger"
            type="button"
            @click="removeSelected"
          >删除</button>
        </header>
        <label>节点代码<input :value="selectedNode.code" maxlength="60" @change="renameSelected(($event.target as HTMLInputElement).value)"></label>
        <label>名称<input v-model.trim="selectedNode.name" maxlength="128"></label>
        <label>应用代码<input v-model.trim="selectedNode.applicationCode" maxlength="80"></label>
        <label v-if="catalog.find(item => item.type === selectedNode?.type)?.requiresBusinessRecord">
          模块代码<input v-model.trim="selectedNode.moduleCode" maxlength="80">
        </label>
        <label>后继节点
          <select v-model="selectedNode.next" multiple :disabled="selectedNode.type === 'END'">
            <option v-for="candidate in graph.nodes.filter(item => item.code !== selectedNode?.code)" :key="candidate.code" :value="candidate.code">
              {{ candidate.name }}（{{ candidate.code }}）
            </option>
          </select>
        </label>
        <label>配置 JSON<textarea v-model="configText" rows="9" spellcheck="false"></textarea></label>
        <button type="button" @click="applyConfig">应用配置</button>

        <section v-if="['APPROVAL', 'CONDITIONAL_APPROVAL', 'FORM'].includes(selectedNode.type)" class="flow-extension-policies">
          <header><h4>字段策略</h4><button type="button" @click="addFieldPolicy">添加字段</button></header>
          <div v-for="(policy, index) in selectedNode.fieldPolicies" :key="index">
            <input v-model.trim="policy.fieldCode" placeholder="字段代码">
            <select v-model="policy.mode">
              <option v-for="mode in ['VISIBLE', 'EDITABLE', 'REQUIRED', 'HIDDEN'] as FlowExtensionFieldMode[]" :key="mode" :value="mode">{{ mode }}</option>
            </select>
            <button type="button" @click="removeFieldPolicy(index)">移除</button>
          </div>
        </section>
      </aside>
    </div>

    <section class="flow-extension-dependencies">
      <header><h4>发布依赖（{{ graph.dependencies.length }}）</h4><button type="button" @click="addDependency">添加当前节点依赖</button></header>
      <div v-for="(dependency, index) in graph.dependencies" :key="index" class="flow-extension-dependency">
        <select v-model="dependency.sourceNodeCode"><option v-for="node in graph.nodes" :key="node.code" :value="node.code">{{ node.code }}</option></select>
        <select v-model="dependency.type"><option v-for="type in ['FLOW_DEFINITION', 'MODULE_CONFIGURATION', 'OPENAPI_APPLICATION', 'MESSAGE_TEMPLATE', 'AI_POLICY'] as FlowExtensionDependencyType[]" :key="type" :value="type">{{ type }}</option></select>
        <input v-model.trim="dependency.targetKey" placeholder="目标键">
        <input v-model.number="dependency.requiredVersion" type="number" min="1" aria-label="依赖版本">
        <select v-model="dependency.versionMode"><option value="EXACT">EXACT</option><option value="MINIMUM">MINIMUM</option></select>
        <button type="button" @click="graph.dependencies.splice(index, 1)">移除</button>
      </div>
    </section>

    <section v-if="impact" class="flow-extension-impact" :class="{ ready: impact.ready }">
      <header><h4>发布影响</h4><strong>{{ impact.ready ? '可以发布' : '存在阻断' }}</strong></header>
      <p>出站依赖 {{ impact.outboundDependencies.length }} · 入站消费者 {{ impact.inboundConsumers.length }}</p>
      <ul v-if="impact.issues.length">
        <li v-for="issue in impact.issues" :key="`${issue.code}:${issue.path}`">
          <b>{{ issue.severity }}</b> {{ issue.code }}：{{ issue.message }}
        </li>
      </ul>
      <p v-else>没有依赖阻断或跨应用兼容性警告。</p>
    </section>

    <footer><button class="primary" type="button" :disabled="busy" @click="save">{{ busy ? '保存中…' : '保存扩展草稿并检查影响' }}</button></footer>
  </section>
</template>

<style scoped>
.flow-extension-editor { display: grid; gap: 14px; }
.flow-extension-heading,.flow-extension-heading>div,.flow-extension-catalog,.flow-extension-catalog section,.flow-extension-graph,.flow-extension-inspector,.flow-extension-policies,.flow-extension-dependencies,.flow-extension-impact { display: grid; gap: 9px; }
.flow-extension-heading,.flow-extension-inspector>header,.flow-extension-policies>header,.flow-extension-dependencies>header,.flow-extension-impact>header { grid-auto-flow: column; align-items: start; justify-content: space-between; }
h3,h4,p { margin: 0; }
.flow-extension-heading p,.flow-extension-catalog small,.flow-extension-graph small { color: #64748b; }
.flow-extension-workspace { display: grid; grid-template-columns: 220px minmax(240px,1fr) 300px; min-height: 480px; overflow: hidden; border: 1px solid #dbe4ee; border-radius: 12px; }
.flow-extension-catalog,.flow-extension-inspector { align-content: start; padding: 14px; overflow: auto; background: #f8fafc; }
.flow-extension-catalog { border-right: 1px solid #e2e8f0; }
.flow-extension-catalog-item,.flow-extension-graph li>button { display: grid; gap: 2px; width: 100%; padding: 8px; border: 1px solid #cbd5e1; border-radius: 7px; background: #fff; text-align: left; cursor: pointer; }
.flow-extension-catalog-item:disabled { opacity: .5; cursor: not-allowed; }
.flow-extension-graph { align-content: start; padding: 14px; background: linear-gradient(#eef2f7 1px,transparent 1px),linear-gradient(90deg,#eef2f7 1px,transparent 1px); background-size: 20px 20px; }
.flow-extension-graph ol { display: grid; gap: 8px; margin: 0; padding: 0; list-style: none; }
.flow-extension-graph li.selected>button { border-color: #2563eb; box-shadow: 0 0 0 3px rgb(37 99 235 / 12%); }
.flow-extension-inspector { border-left: 1px solid #e2e8f0; }
.flow-extension-inspector label { display: grid; gap: 5px; color: #475569; }
input,select,textarea,button { min-height: 34px; padding: 6px 8px; border: 1px solid #cbd5e1; border-radius: 7px; background: #fff; }
textarea { resize: vertical; font: 12px/1.5 ui-monospace,SFMono-Regular,Consolas,monospace; }
button { cursor: pointer; }
button.primary { border-color: #2563eb; background: #2563eb; color: #fff; }
button.danger { color: #b91c1c; }
.flow-extension-policies>div,.flow-extension-dependency { display: grid; grid-template-columns: minmax(100px,1fr) minmax(130px,1fr) auto; gap: 6px; }
.flow-extension-dependency { grid-template-columns: repeat(5,minmax(100px,1fr)) auto; }
.flow-extension-dependencies,.flow-extension-impact { padding: 12px; border: 1px solid #e2e8f0; border-radius: 10px; }
.flow-extension-impact { border-color: #fecaca; background: #fff7f7; }
.flow-extension-impact.ready { border-color: #bbf7d0; background: #f0fdf4; }
.flow-extension-error { padding: 9px; border-radius: 7px; background: #fef2f2; color: #b91c1c; }
.flow-extension-notice { padding: 9px; border-radius: 7px; background: #eff6ff; color: #1d4ed8; }
@media (max-width: 1050px) { .flow-extension-workspace { grid-template-columns: 190px minmax(220px,1fr); } .flow-extension-inspector { grid-column: 1/-1; border-top: 1px solid #e2e8f0; border-left: 0; } }
</style>
