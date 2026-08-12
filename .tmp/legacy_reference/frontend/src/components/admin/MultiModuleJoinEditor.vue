<script setup lang="ts">
import { computed, onMounted, reactive } from 'vue'

import { dataSourceAdminApi } from '@/services/dataSource'
import type {
  DataSourceJoinEdge,
  DataSourceJoinInput,
  DataSourceJoinProjection,
  DataSourceMultiModuleJoin,
  DataSourceSummary,
  DataSourceVersion,
} from '@/types/dataSource'

const props = defineProps<{
  systemId: string
  currentSourceId: string
  anchorModuleId: string
  sources: DataSourceSummary[]
  modelValue: DataSourceMultiModuleJoin
}>()
const emit = defineEmits<{ 'update:modelValue': [value: DataSourceMultiModuleJoin] }>()

const versions = reactive<Record<number, DataSourceVersion[]>>({})
const loading = reactive<Record<number, boolean>>({})
const error = reactive<Record<number, string>>({})
const candidates = computed(() => props.sources.filter(source =>
  source.id !== props.currentSourceId
  && Boolean(source.activeVersionId)
  && (source.sourceKind ?? source.draft?.sourceKind ?? 'NATIVE_MODULE') !== 'MULTI_MODULE_JOIN',
))

function candidateSources(index: number) {
  return index === 0
    ? candidates.value.filter(source => source.moduleId === props.anchorModuleId)
    : candidates.value
}

function copyPlan(): DataSourceMultiModuleJoin {
  return {
    inputs: props.modelValue.inputs.map(item => ({ ...item })),
    edges: props.modelValue.edges.map(item => ({ ...item })),
    projections: props.modelValue.projections.map(item => ({ ...item })),
    failureMode: props.modelValue.failureMode,
    timeoutSeconds: props.modelValue.timeoutSeconds,
    rowLimit: props.modelValue.rowLimit,
  }
}

function normalizeEdges(inputs: DataSourceJoinInput[], current: DataSourceJoinEdge[]) {
  return inputs.slice(1).map((input, index) => {
    const existing = current[index]
    const leftAlias = inputs.slice(0, index + 1).some(item => item.alias === existing?.leftAlias)
      ? existing!.leftAlias : inputs[index]?.alias ?? ''
    return {
      leftAlias,
      leftFieldCode: existing?.leftFieldCode ?? '',
      rightAlias: input.alias,
      rightFieldCode: existing?.rightFieldCode ?? '',
      joinType: existing?.joinType ?? 'LEFT',
      cardinality: existing?.cardinality ?? 'MANY_TO_ONE',
    } satisfies DataSourceJoinEdge
  })
}

function update(mutator: (value: DataSourceMultiModuleJoin) => void) {
  const next = copyPlan()
  mutator(next)
  next.edges = normalizeEdges(next.inputs, next.edges)
  emit('update:modelValue', next)
}

async function loadVersions(index: number) {
  const input = props.modelValue.inputs[index]
  if (!input?.dataSourceId) {
    versions[index] = []
    return
  }
  loading[index] = true
  error[index] = ''
  try {
    versions[index] = await dataSourceAdminApi.versions(props.systemId, input.dataSourceId)
  } catch (failure) {
    versions[index] = []
    error[index] = failure instanceof Error ? failure.message : '版本加载失败'
  } finally {
    loading[index] = false
  }
}

async function sourceChanged(index: number, dataSourceId: string) {
  const source = candidateSources(index).find(item => item.id === dataSourceId)
  update(plan => {
    const input = plan.inputs[index]
    if (!input) return
    input.dataSourceId = dataSourceId
    input.dataSourceVersionId = source?.activeVersionId ?? ''
  })
  await loadVersions(index)
}

function addInput() {
  if (props.modelValue.inputs.length >= 8) return
  const index = props.modelValue.inputs.length
  const source = candidateSources(index).find(item =>
    !props.modelValue.inputs.some(input => input.dataSourceId === item.id),
  ) ?? candidates.value[0]
  update(plan => plan.inputs.push({
    alias: `source${index + 1}`,
    dataSourceId: source?.id ?? '',
    dataSourceVersionId: source?.activeVersionId ?? '',
  }))
  void loadVersions(index)
}

function removeInput(index: number) {
  if (props.modelValue.inputs.length <= 2) return
  const removed = props.modelValue.inputs[index]?.alias
  update(plan => {
    plan.inputs.splice(index, 1)
    plan.projections = plan.projections.filter(item => item.sourceAlias !== removed)
  })
  delete versions[index]
}

function addProjection() {
  if (props.modelValue.projections.length >= 50) return
  const alias = props.modelValue.inputs[0]?.alias ?? ''
  update(plan => plan.projections.push({ sourceAlias: alias, sourceFieldCode: '', fieldCode: `${alias}__` }))
}

function projectionAliasChanged(index: number, alias: string) {
  update(plan => {
    const item = plan.projections[index]
    if (!item) return
    item.sourceAlias = alias
    if (!item.fieldCode || !item.fieldCode.includes('__')) item.fieldCode = `${alias}__${item.sourceFieldCode}`
  })
}

function inputAliasChanged(index: number, alias: string) {
  const previous = props.modelValue.inputs[index]?.alias
  update(plan => {
    const input = plan.inputs[index]
    if (!input) return
    input.alias = alias
    plan.edges.forEach(edge => {
      if (edge.leftAlias === previous) edge.leftAlias = alias
      if (edge.rightAlias === previous) edge.rightAlias = alias
    })
    plan.projections.forEach(item => {
      if (item.sourceAlias === previous) item.sourceAlias = alias
    })
  })
}

function inputValue(index: number, field: keyof DataSourceJoinInput, value: string) {
  update(plan => {
    const input = plan.inputs[index]
    if (input) input[field] = value
  })
}

function edgeValue<K extends keyof DataSourceJoinEdge>(index: number, field: K, value: DataSourceJoinEdge[K]) {
  update(plan => {
    const edge = plan.edges[index]
    if (edge) edge[field] = value
  })
}

function projectionValue<K extends keyof DataSourceJoinProjection>(index: number, field: K, value: DataSourceJoinProjection[K]) {
  update(plan => {
    const item = plan.projections[index]
    if (item) item[field] = value
  })
}

onMounted(() => props.modelValue.inputs.forEach((_item, index) => void loadVersions(index)))
</script>

<template>
  <section class="join-editor" data-testid="multi-module-join-editor">
    <header>
      <div><strong>多模块精确版本 Join</strong><p>按左深顺序连接 2 至 8 个已发布数据源；每个输入固定到不可变版本。</p></div>
      <button type="button" :disabled="modelValue.inputs.length >= 8" @click="addInput">添加输入</button>
    </header>

    <div class="join-settings">
      <label>失败策略<select :value="modelValue.failureMode" @change="update(plan => plan.failureMode = ($event.target as HTMLSelectElement).value as DataSourceMultiModuleJoin['failureMode'])"><option value="FAIL_FAST">任一源失败即终止</option><option value="ALLOW_PARTIAL_LEFT">LEFT Join 允许右源部分失败</option></select></label>
      <label>超时（秒）<input :value="modelValue.timeoutSeconds" type="number" min="1" max="10" @input="update(plan => plan.timeoutSeconds = Number(($event.target as HTMLInputElement).value))"></label>
      <label>结果行上限<input :value="modelValue.rowLimit" type="number" min="1" max="100" @input="update(plan => plan.rowLimit = Number(($event.target as HTMLInputElement).value))"></label>
    </div>

    <ol class="join-inputs">
      <li v-for="(input, index) in modelValue.inputs" :key="index">
        <div class="input-title"><strong>输入 {{ index + 1 }}</strong><button type="button" :disabled="modelValue.inputs.length <= 2" @click="removeInput(index)">移除</button></div>
        <div class="input-grid">
          <label>别名<input :value="input.alias" maxlength="20" placeholder="orders" @input="inputAliasChanged(index, ($event.target as HTMLInputElement).value)"></label>
          <label>已发布数据源<select :value="input.dataSourceId" @change="sourceChanged(index, ($event.target as HTMLSelectElement).value)"><option value="">请选择</option><option v-for="source in candidateSources(index)" :key="source.id" :value="source.id">{{ source.name }}（{{ source.code }}）</option></select></label>
          <label>精确版本<select :value="input.dataSourceVersionId" :disabled="loading[index]" @change="inputValue(index, 'dataSourceVersionId', ($event.target as HTMLSelectElement).value)"><option value="">请选择</option><option v-for="version in versions[index] ?? []" :key="version.id" :value="version.id">v{{ version.versionNumber }} · {{ version.active ? '当前' : '历史' }}</option></select><small v-if="error[index]">{{ error[index] }}</small></label>
        </div>
      </li>
    </ol>

    <div class="join-block">
      <header><div><strong>连接条件</strong><p>每个后续输入通过一条显式边连接到已引入输入。</p></div></header>
      <div v-for="(edge, index) in modelValue.edges" :key="index" class="edge-grid">
        <label>左输入<select :value="edge.leftAlias" @change="edgeValue(index, 'leftAlias', ($event.target as HTMLSelectElement).value)"><option v-for="input in modelValue.inputs.slice(0, index + 1)" :key="input.alias" :value="input.alias">{{ input.alias }}</option></select></label>
        <label>左字段<input :value="edge.leftFieldCode" maxlength="64" @input="edgeValue(index, 'leftFieldCode', ($event.target as HTMLInputElement).value)"></label>
        <label>连接<select :value="edge.joinType" @change="edgeValue(index, 'joinType', ($event.target as HTMLSelectElement).value as DataSourceJoinEdge['joinType'])"><option value="INNER">INNER</option><option value="LEFT">LEFT</option></select></label>
        <label>右输入<input :value="edge.rightAlias" disabled></label>
        <label>右字段<input :value="edge.rightFieldCode" maxlength="64" @input="edgeValue(index, 'rightFieldCode', ($event.target as HTMLInputElement).value)"></label>
        <label>基数<select :value="edge.cardinality" @change="edgeValue(index, 'cardinality', ($event.target as HTMLSelectElement).value as DataSourceJoinEdge['cardinality'])"><option value="ONE_TO_ONE">1:1</option><option value="ONE_TO_MANY">1:N</option><option value="MANY_TO_ONE">N:1</option><option value="MANY_TO_MANY">N:N</option></select></label>
      </div>
    </div>

    <div class="join-block">
      <header><div><strong>输出字段命名空间</strong><p>输出编码必须使用“别名__字段”形式，避免不同源字段碰撞。</p></div><button type="button" :disabled="modelValue.projections.length >= 50" @click="addProjection">添加输出</button></header>
      <div v-for="(item, index) in modelValue.projections" :key="index" class="projection-grid">
        <label>来源<select :value="item.sourceAlias" @change="projectionAliasChanged(index, ($event.target as HTMLSelectElement).value)"><option v-for="input in modelValue.inputs" :key="input.alias" :value="input.alias">{{ input.alias }}</option></select></label>
        <label>来源字段<input :value="item.sourceFieldCode" maxlength="64" @input="projectionValue(index, 'sourceFieldCode', ($event.target as HTMLInputElement).value)"></label>
        <label>输出编码<input :value="item.fieldCode" maxlength="64" :placeholder="`${item.sourceAlias}__field`" @input="projectionValue(index, 'fieldCode', ($event.target as HTMLInputElement).value)"></label>
        <button type="button" @click="update(plan => plan.projections.splice(index, 1))">删除</button>
      </div>
    </div>
  </section>
</template>

<style scoped>
.join-editor,.join-block{display:grid;gap:12px}.join-editor>header,.join-block>header,.input-title{display:flex;justify-content:space-between;align-items:flex-start;gap:12px}.join-editor p,.join-block p{margin:3px 0 0;color:#6b7881;font-size:12px}.join-settings,.input-grid,.edge-grid,.projection-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:10px}.join-inputs{display:grid;gap:10px;margin:0;padding:0;list-style:none}.join-inputs li,.join-block{padding:12px;border:1px solid #dce4e8;border-radius:8px;background:#fff}.join-editor label{display:grid;gap:5px;color:#53616b;font-size:12px}.join-editor input,.join-editor select{width:100%;min-height:36px;padding:6px 8px;border:1px solid #cbd5da;border-radius:6px;background:#fff}.join-editor button{min-height:32px;padding:5px 10px;border:1px solid #b9c8cf;border-radius:6px;background:#fff;cursor:pointer}.join-editor button:disabled{opacity:.45;cursor:not-allowed}.projection-grid{grid-template-columns:1fr 1fr 1fr auto;align-items:end}.input-grid small{color:#ad3232}@media(max-width:980px){.join-settings,.input-grid,.edge-grid{grid-template-columns:repeat(2,minmax(0,1fr))}.projection-grid{grid-template-columns:1fr 1fr}.projection-grid button{align-self:end}}
</style>
