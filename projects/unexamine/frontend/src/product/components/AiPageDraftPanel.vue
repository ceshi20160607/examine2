<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { api, ApiError } from '../api'
import { systemTokens } from '../session'
import type { ConfiguredField } from '../types'

interface AgentOption { id: number; name: string }
interface DraftOptions { agents: AgentOption[]; moduleId: number; moduleCode: string; moduleName: string; draftRevision: number }
interface SuggestedPage {
  pageType: 'FORM' | 'LIST' | 'DETAIL'
  fieldCodes: string[]
  title: string
  columns: number
  density: 'comfortable' | 'compact'
  pageVersion: number
  confidence: number
}
interface Candidate {
  pendingWriteId: number; status: string; outcome: string; message: string; errorCode: string | null
  pages: SuggestedPage[]; confidence: number; businessWritten: boolean; version: number
  moduleId: number; moduleCode: string; agentId: number; resultPageIds: number[]
}

const props = defineProps<{ moduleId: number; fields: ConfiguredField[] }>()
const emit = defineEmits<{ saved: [] }>()
const token = computed(() => systemTokens.value?.accessToken || '')
const availableFields = computed(() => props.fields.filter(field => field.status === 'ACTIVE' && !field.parentFieldId))
const fieldNames = computed(() => new Map(availableFields.value.map(field => [field.code, field.name])))
const open = ref(false)
const options = ref<DraftOptions>()
const agentId = ref<number>()
const instruction = ref('')
const candidate = ref<Candidate>()
const pages = ref<SuggestedPage[]>([])
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const confirmed = ref(false)
const reviewed = ref<Record<string, boolean>>({})
const pageNames = { FORM: '表单页', LIST: '列表页', DETAIL: '详情页' }
const canSave = computed(() => Boolean(candidate.value && !candidate.value.businessWritten && confirmed.value
  && pages.value.length === 3
  && new Set(pages.value.map(page => page.pageType)).size === 3
  && pages.value.every(page => page.title.trim() && page.fieldCodes.length
    && new Set(page.fieldCodes).size === page.fieldCodes.length
    && page.fieldCodes.every(code => fieldNames.value.has(code))
    && Number.isInteger(page.columns) && page.columns >= 1 && page.columns <= 3
    && ['comfortable', 'compact'].includes(page.density)
    && (page.confidence >= 0.7 && candidate.value!.confidence >= 0.7 || reviewed.value[page.pageType]))))

function errorMessage(reason: unknown, fallback: string) {
  return reason instanceof ApiError ? reason.message : fallback
}

async function loadOptions() {
  if (!token.value) return
  error.value = ''
  try {
    const result = await api<DraftOptions>(`/api/admin/system/ai/page-drafts/options?moduleId=${props.moduleId}`, {}, token.value)
    if (result.moduleId !== props.moduleId) return
    options.value = result
    agentId.value = result.agents.some(agent => agent.id === agentId.value) ? agentId.value : result.agents[0]?.id
  } catch (reason) {
    options.value = undefined
    error.value = errorMessage(reason, '配置 Agent 加载失败')
  }
}

async function recognize() {
  if (!token.value || !agentId.value || !instruction.value.trim()) return
  loading.value = true
  error.value = ''
  candidate.value = undefined
  pages.value = []
  confirmed.value = false
  reviewed.value = {}
  try {
    const result = await api<Candidate>('/api/admin/system/ai/page-drafts/recognize', {
      method: 'POST', body: JSON.stringify({ agentId: agentId.value, moduleId: props.moduleId,
        instruction: instruction.value.trim() }),
    }, token.value)
    if (result.moduleId !== props.moduleId) return
    candidate.value = result
    pages.value = result.pages.map(page => ({ ...page, fieldCodes: [...page.fieldCodes] }))
  } catch (reason) {
    error.value = errorMessage(reason, '页面建议生成失败')
  } finally {
    loading.value = false
  }
}

function addField(page: SuggestedPage, value: unknown) {
  const code = String(value || '')
  if (fieldNames.value.has(code) && !page.fieldCodes.includes(code)) page.fieldCodes.push(code)
}

function moveField(page: SuggestedPage, index: number, offset: number) {
  const target = index + offset
  if (target < 0 || target >= page.fieldCodes.length) return
  const next = [...page.fieldCodes]
  ;[next[index], next[target]] = [next[target]!, next[index]!]
  page.fieldCodes = next
}

async function save() {
  if (!token.value || !candidate.value || !canSave.value) return
  saving.value = true
  error.value = ''
  try {
    const result = await api<Candidate>(`/api/admin/system/ai/page-drafts/${candidate.value.pendingWriteId}/confirm`, {
      method: 'POST', body: JSON.stringify({ expectedVersion: candidate.value.version, confirmed: true,
        reviewedPageTypes: pages.value.filter(page => reviewed.value[page.pageType]).map(page => page.pageType),
        pages: pages.value.map(page => ({ pageType: page.pageType, fieldCodes: page.fieldCodes,
          title: page.title.trim(), columns: page.columns, density: page.density, pageVersion: page.pageVersion })) }),
    }, token.value)
    candidate.value = result
    if (result.businessWritten) emit('saved')
  } catch (reason) {
    error.value = errorMessage(reason, '页面草稿保存失败，请核对模块字段和页面版本')
  } finally {
    saving.value = false
  }
}

watch(() => props.moduleId, () => {
  options.value = undefined; candidate.value = undefined; pages.value = []
  confirmed.value = false; reviewed.value = {}
  if (open.value) void loadOptions()
})
watch(open, value => { if (value) void loadOptions() })
</script>

<template>
  <section class="ai-page-panel">
    <div class="ai-page-heading">
      <div><strong>AI 辅助设计页面</strong><small>根据当前模块字段生成表单、列表和详情页建议；核对后保存到页面草稿。</small></div>
      <a-button size="small" @click="open = !open">{{ open ? '收起' : '生成页面建议' }}</a-button>
    </div>
    <div v-if="open" class="ai-page-body">
      <a-alert v-if="error" type="error" show-icon :message="error" />
      <a-alert v-if="!availableFields.length" type="info" show-icon message="请先为模块添加业务字段。" />
      <a-alert v-if="options && !options.agents.length" type="info" show-icon message="当前模块尚无已发布且授权的配置 Agent，请先在 AI 配置中发布。" />
      <template v-if="availableFields.length && options?.agents.length">
        <label>配置 Agent
          <a-select v-model:value="agentId" :options="options.agents.map(agent => ({ value: agent.id, label: agent.name }))" />
        </label>
        <label>页面要求
          <a-textarea v-model:value="instruction" :rows="2" :maxlength="12000" placeholder="例如：列表突出客户名称和最近跟进时间，表单先填写基本信息，详情展示完整信息" />
        </label>
        <div class="ai-page-actions"><a-button :loading="loading" :disabled="!agentId || !instruction.trim()" @click="recognize">生成建议</a-button></div>
      </template>
      <div v-if="candidate" class="ai-page-candidate">
        <a-alert :type="candidate.outcome === 'READY' ? 'info' : 'warning'" show-icon :message="candidate.message" />
        <p>整体识别置信度 {{ Math.round(candidate.confidence * 100) }}%。可调整字段和顺序；保存后仍需单独发布模块。</p>
        <div v-for="page in pages" :key="page.pageType" class="ai-page-row">
          <div class="ai-page-row-title"><strong>{{ pageNames[page.pageType] }}</strong><span>置信度 {{ Math.round(page.confidence * 100) }}%</span></div>
          <div class="ai-page-options">
            <label>区块标题<a-input v-model:value="page.title" :maxlength="100" :disabled="candidate.businessWritten" /></label>
            <label>每行列数<a-select v-model:value="page.columns" :options="[1, 2, 3].map(value => ({ value, label: `${value} 列` }))" :disabled="candidate.businessWritten" /></label>
            <label>间距<a-select v-model:value="page.density" :options="[{ value: 'comfortable', label: '舒适' }, { value: 'compact', label: '紧凑' }]" :disabled="candidate.businessWritten" /></label>
          </div>
          <div class="ai-page-fields">
            <span>显示字段及顺序</span>
            <div v-for="(code, index) in page.fieldCodes" :key="code" class="ai-page-field">
              <span>{{ fieldNames.get(code) || code }}</span>
              <a-space v-if="!candidate.businessWritten" size="small">
                <a-button size="small" :disabled="index === 0" @click="moveField(page, index, -1)">上移</a-button>
                <a-button size="small" :disabled="index === page.fieldCodes.length - 1" @click="moveField(page, index, 1)">下移</a-button>
                <a-button size="small" @click="page.fieldCodes.splice(index, 1)">移除</a-button>
              </a-space>
            </div>
            <a-select v-if="!candidate.businessWritten" placeholder="添加字段" :value="undefined"
              :options="availableFields.filter(field => !page.fieldCodes.includes(field.code)).map(field => ({ value: field.code, label: field.name }))"
              @change="addField(page, $event)" />
          </div>
          <a-checkbox v-if="(page.confidence < 0.7 || candidate.confidence < 0.7) && !candidate.businessWritten"
            v-model:checked="reviewed[page.pageType]">此页面建议置信度较低，我已逐项核对</a-checkbox>
        </div>
        <template v-if="!candidate.businessWritten">
          <a-checkbox v-model:checked="confirmed">我已核对三个页面的字段与布局，确认保存到当前模块草稿</a-checkbox>
          <div class="ai-page-actions"><a-button type="primary" :loading="saving" :disabled="!canSave" @click="save">确认保存页面</a-button></div>
        </template>
        <a-alert v-else type="success" show-icon :message="`已保存 ${candidate.resultPageIds.length} 个页面草稿`" />
      </div>
    </div>
  </section>
</template>

<style scoped>
.ai-page-panel { margin: 16px; padding: 14px 16px; border: 1px solid #dbeafe; border-radius: 10px; background: #f8fbff; }
.ai-page-heading { display: flex; justify-content: space-between; align-items: center; gap: 16px; }
.ai-page-heading > div, .ai-page-body, .ai-page-candidate, .ai-page-body label { display: grid; gap: 8px; }
.ai-page-heading small { color: #64748b; }
.ai-page-body, .ai-page-candidate { margin-top: 14px; }
.ai-page-candidate p { margin: 0; color: #475569; }
.ai-page-actions { display: flex; justify-content: flex-end; }
.ai-page-row { display: grid; gap: 12px; padding: 14px; border: 1px solid #e2e8f0; border-radius: 8px; background: white; }
.ai-page-row-title, .ai-page-field { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.ai-page-row-title span { color: #64748b; font-size: 12px; }
.ai-page-options { display: grid; grid-template-columns: 2fr 1fr 1fr; gap: 10px; }
.ai-page-fields { display: grid; gap: 6px; }
.ai-page-field { padding: 6px 8px; border: 1px solid #e2e8f0; border-radius: 6px; }
@media (max-width: 800px) { .ai-page-options { grid-template-columns: 1fr; }.ai-page-field { flex-wrap: wrap; } }
</style>
