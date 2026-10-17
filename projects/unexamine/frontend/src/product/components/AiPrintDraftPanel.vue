<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { api, ApiError } from '../api'
import { systemTokens } from '../session'

interface AgentOption { id: number; name: string }
interface FieldOption { code: string; name: string; fieldType: string }
interface Options {
  agents: AgentOption[]; moduleId: number; moduleCode: string; moduleName: string
  draftRevision: number; publishedVersionId: number; fields: FieldOption[]
}
interface Layout {
  header: string | null; footer: string | null; signatureLabel: string | null
  fieldCodes: string[]; detailFieldCodes: string[]; rowsPerPage: number
  offsetXmm: number; offsetYmm: number
}
interface Candidate {
  pendingWriteId: number; status: string; outcome: string; message: string; errorCode: string | null
  code: string; name: string; pageSize: 'A4' | 'A5'; orientation: 'PORTRAIT' | 'LANDSCAPE'
  layout: Layout | null; confidence: number; businessWritten: boolean; version: number
  moduleId: number; moduleCode: string; agentId: number; templateId: number | null
}

const props = defineProps<{ moduleId: number }>()
const emit = defineEmits<{ saved: [templateId: number] }>()
const token = computed(() => systemTokens.value?.accessToken || '')
const open = ref(false)
const options = ref<Options>()
const agentId = ref<number>()
const instruction = ref('')
const candidate = ref<Candidate>()
const code = ref('')
const name = ref('')
const pageSize = ref<'A4' | 'A5'>('A4')
const orientation = ref<'PORTRAIT' | 'LANDSCAPE'>('PORTRAIT')
const layout = ref<Layout>({ header: null, footer: null, signatureLabel: null,
  fieldCodes: [], detailFieldCodes: [], rowsPerPage: 10, offsetXmm: 0, offsetYmm: 0 })
const confirmed = ref(false)
const reviewedLowConfidence = ref(false)
const loading = ref(false)
const recognizing = ref(false)
const saving = ref(false)
const error = ref('')
const allowedCodes = computed(() => new Set(options.value?.fields.map(field => field.code) || []))
const selectedCodes = computed(() => [...layout.value.fieldCodes, ...layout.value.detailFieldCodes])
const lowConfidence = computed(() => Boolean(candidate.value && candidate.value.confidence < 0.7))
const canSave = computed(() => Boolean(candidate.value && candidate.value.status === 'PENDING_CONFIRMATION'
  && !candidate.value.businessWritten && confirmed.value && (!lowConfidence.value || reviewedLowConfidence.value)
  && /^[a-z][a-z0-9_]{1,99}$/.test(code.value.trim()) && name.value.trim().length > 0
  && name.value.trim().length <= 200 && layout.value.fieldCodes.length > 0
  && layout.value.fieldCodes.length <= 40 && layout.value.detailFieldCodes.length <= 20
  && selectedCodes.value.every(value => allowedCodes.value.has(value))
  && new Set(selectedCodes.value).size === selectedCodes.value.length
  && Number.isInteger(layout.value.rowsPerPage) && layout.value.rowsPerPage >= 4 && layout.value.rowsPerPage <= 24
  && Math.abs(layout.value.offsetXmm) <= 30 && Math.abs(layout.value.offsetYmm) <= 30
  && (layout.value.header?.length || 0) <= 200 && (layout.value.footer?.length || 0) <= 500
  && (layout.value.signatureLabel?.length || 0) <= 200))

function readable(reason: unknown, fallback: string) {
  return reason instanceof ApiError ? `${reason.message}（${reason.code}）` : reason instanceof Error ? reason.message : fallback
}

async function loadOptions() {
  if (!token.value) return
  loading.value = true
  error.value = ''
  try {
    const result = await api<Options>(
      `/api/admin/system/ai/print-drafts/options?moduleId=${props.moduleId}`, {}, token.value)
    if (result.moduleId !== props.moduleId) return
    options.value = result
    agentId.value = result.agents.some(agent => agent.id === agentId.value) ? agentId.value : result.agents[0]?.id
  } catch (reason) {
    options.value = undefined
    error.value = readable(reason, '打印建议配置加载失败')
  } finally { loading.value = false }
}

function fieldMode(code: string) {
  if (layout.value.fieldCodes.includes(code)) return 'field'
  if (layout.value.detailFieldCodes.includes(code)) return 'detail'
  return 'unused'
}

/** 字段归属只能在当前模块发布字段中选择，并保留用户调整后的输出顺序。 */
function setFieldMode(code: string, mode: string) {
  if (!allowedCodes.value.has(code)) return
  layout.value.fieldCodes = layout.value.fieldCodes.filter(value => value !== code)
  layout.value.detailFieldCodes = layout.value.detailFieldCodes.filter(value => value !== code)
  if (mode === 'field') layout.value.fieldCodes.push(code)
  if (mode === 'detail') layout.value.detailFieldCodes.push(code)
}

function onFieldModeChange(code: string, value: unknown) {
  setFieldMode(code, String(value))
}

function moveField(code: string, direction: -1 | 1) {
  const values = layout.value.fieldCodes.includes(code) ? layout.value.fieldCodes : layout.value.detailFieldCodes
  const index = values.indexOf(code)
  const next = index + direction
  if (index < 0 || next < 0 || next >= values.length) return
  const reordered = [...values]
  const currentCode = reordered[index]!
  reordered[index] = reordered[next]!
  reordered[next] = currentCode
  if (values === layout.value.fieldCodes) layout.value.fieldCodes = reordered
  else layout.value.detailFieldCodes = reordered
}

async function recognize() {
  if (!token.value || !agentId.value || !instruction.value.trim()) return
  recognizing.value = true
  error.value = ''
  candidate.value = undefined
  confirmed.value = false
  reviewedLowConfidence.value = false
  try {
    const result = await api<Candidate>('/api/admin/system/ai/print-drafts/recognize', {
      method: 'POST', body: JSON.stringify({ agentId: agentId.value, moduleId: props.moduleId,
        instruction: instruction.value.trim() }),
    }, token.value)
    if (result.moduleId !== props.moduleId) return
    candidate.value = result
    code.value = result.code
    name.value = result.name
    pageSize.value = result.pageSize
    orientation.value = result.orientation
    if (result.layout) layout.value = { ...result.layout,
      fieldCodes: [...result.layout.fieldCodes], detailFieldCodes: [...result.layout.detailFieldCodes] }
  } catch (reason) { error.value = readable(reason, '打印模板建议生成失败') }
  finally { recognizing.value = false }
}

async function save() {
  if (!token.value || !candidate.value || !canSave.value) return
  saving.value = true
  error.value = ''
  try {
    const result = await api<Candidate>(
      `/api/admin/system/ai/print-drafts/${candidate.value.pendingWriteId}/confirm`, {
        method: 'POST', body: JSON.stringify({ expectedVersion: candidate.value.version, confirmed: true,
          code: code.value.trim(), name: name.value.trim(), pageSize: pageSize.value,
          orientation: orientation.value, layout: { ...layout.value,
            header: layout.value.header?.trim() || null, footer: layout.value.footer?.trim() || null,
            signatureLabel: layout.value.signatureLabel?.trim() || null },
          reviewedLowConfidence: reviewedLowConfidence.value }),
      }, token.value)
    candidate.value = result
    if (result.businessWritten && result.templateId) emit('saved', result.templateId)
  } catch (reason) { error.value = readable(reason, '打印模板草稿保存失败') }
  finally { saving.value = false }
}

watch(() => props.moduleId, () => {
  options.value = undefined
  candidate.value = undefined
  confirmed.value = false
  reviewedLowConfidence.value = false
  if (open.value) void loadOptions()
})
watch(open, value => { if (value) void loadOptions() })
</script>

<template>
  <section class="ai-print-panel panel-card">
    <div class="ai-print-heading">
      <div><strong>AI 辅助生成打印模板</strong><small>根据当前模块字段生成建议，人工核对后保存为草稿。</small></div>
      <a-button size="small" @click="open = !open">{{ open ? '收起' : '生成模板建议' }}</a-button>
    </div>
    <div v-if="open" class="ai-print-body">
      <a-alert v-if="error" type="error" show-icon :message="error" />
      <a-spin :spinning="loading">
        <template v-if="options">
          <p class="ai-print-hint">目标模块：{{ options.moduleName }}。可使用 {{ options.fields.length }} 个当前发布字段。</p>
          <a-alert v-if="!options.agents.length" type="info" show-icon message="当前模块没有已发布且授权的打印配置 Agent，请先在 AI 配置中发布。" />
          <div v-else class="ai-print-inputs">
            <label>配置 Agent<a-select v-model:value="agentId" :options="options.agents.map(agent => ({ value: agent.id, label: agent.name }))" /></label>
            <label>打印需求<a-textarea v-model:value="instruction" :rows="3" :maxlength="10000" placeholder="例如：生成售后工单打印单，正文包含客户、状态，明细列出费用，页脚注明联系方式" /></label>
            <div class="ai-print-actions"><a-button :loading="recognizing" :disabled="!agentId || !instruction.trim()" @click="recognize">生成建议</a-button></div>
          </div>
        </template>
      </a-spin>

      <div v-if="candidate" class="ai-print-candidate">
        <a-alert :type="candidate.outcome === 'READY' ? 'info' : 'warning'" show-icon :message="candidate.message" />
        <template v-if="candidate.layout">
          <p class="ai-print-hint">识别置信度 {{ Math.round(candidate.confidence * 100) }}%。请核对模板名称、字段归属和版面；确认后仍需真实记录预览与单独发布。</p>
          <div class="ai-print-grid">
            <label>模板标识<a-input v-model:value="code" :maxlength="100" :disabled="candidate.businessWritten" /></label>
            <label>模板名称<a-input v-model:value="name" :maxlength="200" :disabled="candidate.businessWritten" /></label>
            <label>纸张<a-select v-model:value="pageSize" :disabled="candidate.businessWritten" :options="[{value:'A4',label:'A4'},{value:'A5',label:'A5'}]" /></label>
            <label>方向<a-select v-model:value="orientation" :disabled="candidate.businessWritten" :options="[{value:'PORTRAIT',label:'纵向'},{value:'LANDSCAPE',label:'横向'}]" /></label>
          </div>
          <strong>字段归属与顺序</strong>
          <div class="ai-print-fields">
            <div v-for="field in options?.fields" :key="field.code" class="ai-print-field">
              <span>{{ field.name }} <small>{{ field.code }}</small></span>
              <a-select :value="fieldMode(field.code)" :disabled="candidate.businessWritten" :options="[
                {value:'unused',label:'不使用'},{value:'field',label:'正文'},{value:'detail',label:'明细'}]"
                @change="onFieldModeChange(field.code, $event)" />
              <span v-if="fieldMode(field.code) !== 'unused'" class="ai-print-order">
                <a-button size="small" :disabled="candidate.businessWritten" @click="moveField(field.code, -1)">上移</a-button>
                <a-button size="small" :disabled="candidate.businessWritten" @click="moveField(field.code, 1)">下移</a-button>
              </span>
            </div>
          </div>
          <div class="ai-print-grid">
            <label>每页字段行数<a-input-number v-model:value="layout.rowsPerPage" :min="4" :max="24" :disabled="candidate.businessWritten" style="width:100%" /></label>
            <label>水平偏移（毫米）<a-input-number v-model:value="layout.offsetXmm" :min="-30" :max="30" :disabled="candidate.businessWritten" style="width:100%" /></label>
            <label>垂直偏移（毫米）<a-input-number v-model:value="layout.offsetYmm" :min="-30" :max="30" :disabled="candidate.businessWritten" style="width:100%" /></label>
            <label>页眉<a-input v-model:value="layout.header" :maxlength="200" :disabled="candidate.businessWritten" /></label>
            <label>页脚<a-input v-model:value="layout.footer" :maxlength="500" :disabled="candidate.businessWritten" /></label>
            <label>签章区<a-input v-model:value="layout.signatureLabel" :maxlength="200" :disabled="candidate.businessWritten" /></label>
          </div>
          <template v-if="!candidate.businessWritten">
            <a-checkbox v-if="lowConfidence" v-model:checked="reviewedLowConfidence">识别置信度较低，我已逐项核对模板和字段</a-checkbox>
            <a-checkbox v-model:checked="confirmed">我已核对以上内容，确认保存为当前模块的打印草稿</a-checkbox>
            <div class="ai-print-actions"><a-button type="primary" :loading="saving" :disabled="!canSave" @click="save">确认保存草稿</a-button></div>
          </template>
          <a-alert v-else type="success" show-icon message="打印模板草稿已保存，可在下方继续编辑和预览。" />
        </template>
      </div>
    </div>
  </section>
</template>

<style scoped>
.ai-print-panel { margin-bottom: 18px; padding: 16px; border: 1px solid #dbeafe; background: #f8fbff; }
.ai-print-heading, .ai-print-field, .ai-print-actions { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.ai-print-heading > div, .ai-print-body, .ai-print-inputs, .ai-print-candidate, .ai-print-grid label { display: grid; gap: 10px; }
.ai-print-heading small, .ai-print-hint, .ai-print-field small { color: #64748b; }
.ai-print-body, .ai-print-candidate { margin-top: 14px; }
.ai-print-hint { margin: 0; }
.ai-print-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; }
.ai-print-fields { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px; }
.ai-print-field { padding: 8px; border: 1px solid #e2e8f0; border-radius: 6px; background: white; }
.ai-print-field > span:first-child { min-width: 0; }
.ai-print-field small { display: block; overflow: hidden; text-overflow: ellipsis; }
.ai-print-field .ant-select { min-width: 90px; }
.ai-print-order { display: flex; gap: 4px; }
@media (max-width: 900px) { .ai-print-grid, .ai-print-fields { grid-template-columns: 1fr; } .ai-print-field { flex-wrap: wrap; } }
</style>
