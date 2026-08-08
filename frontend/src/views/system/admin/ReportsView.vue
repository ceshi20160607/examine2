<script setup lang="ts">
import { message } from 'ant-design-vue'
import { CheckCircle2, ChevronDown, ChevronUp, FileText, Plus, RefreshCw, RotateCcw, Save, Send, Trash2 } from 'lucide-vue-next'
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'

import AdminMobileNotice from '@/components/admin/AdminMobileNotice.vue'
import AdminPageHeader from '@/components/admin/AdminPageHeader.vue'
import ReportScheduleManager from '@/components/report/ReportScheduleManager.vue'
import { ApiRequestError } from '@/services/api'
import { dataSourceAdminApi, runtimeDataSourceApi } from '@/services/dataSource'
import { reportAdminApi } from '@/services/report'
import { configRecoveryApi } from '@/services/configRecovery'
import type { DataSourceSummary, RuntimeDataSourceField } from '@/types/dataSource'
import type {
  CreateReportInput,
  ReportCheckResult,
  ReportDetail,
  ReportFieldPin,
  ReportSummary,
  ReportVersion,
  SaveReportDraftInput,
} from '@/types/report'
import { moveReportField, reportFieldLabel, validateReportFields } from './reportEditorModel'
import { useAdminViewport } from '@/composables/useAdminViewport'

const { isMobile } = useAdminViewport()

type EditorState = 'clean' | 'dirty' | 'saving' | 'checked' | 'published' | 'error'

const route = useRoute()
const systemId = computed(() => String(route.params.systemId))
const reports = ref<ReportSummary[]>([])
const dataSources = ref<DataSourceSummary[]>([])
const selected = ref<ReportDetail | null>(null)
const versions = ref<ReportVersion[]>([])
const inspectedVersion = ref<ReportVersion | null>(null)
const sourceFields = ref<Array<ReportFieldPin & { readable?: boolean }>>([])
const loading = ref(false)
const detailLoading = ref(false)
const mutating = ref(false)
const capabilitiesLoading = ref(false)
const errorMessage = ref('')
const capabilityMessage = ref('')
const checkResult = ref<ReportCheckResult | null>(null)
const editorState = ref<EditorState>('clean')
const outputCandidate = ref('')
const createOpen = ref(false)
const creating = ref(false)
const createError = ref('')
const createFields = ref<RuntimeDataSourceField[]>([])
const createOutputCandidate = ref('')
const createOutputFieldCodes = ref<string[]>([])
let selectionGeneration = 0

const editor = reactive({ name: '', description: '', dataSourceId: '', outputFieldCodes: [] as string[] })
const createForm = reactive({ code: '', name: '', description: '', dataSourceId: '' })
const publishedSources = computed(() => dataSources.value.filter(source => source.activeVersionId))
const availableFields = computed(() => sourceFields.value.filter(field =>
  field.readable !== false && !editor.outputFieldCodes.includes(field.code)))
const selectedFields = computed(() => editor.outputFieldCodes.map(code =>
  sourceFields.value.find(field => field.code === code)
  ?? inspectedVersion.value?.fields.find(field => field.code === code)
  ?? { logicalFieldId: '', code, name: code, type: 'UNKNOWN', queryType: 'UNKNOWN' }))
const availableCreateFields = computed(() => createFields.value.filter(
  field => !createOutputFieldCodes.value.includes(field.fieldCode),
))
const selectedCreateFields = computed(() => createOutputFieldCodes.value.map(code =>
  createFields.value.find(field => field.fieldCode === code)
  ?? { fieldCode: code, fieldName: code, type: 'UNKNOWN' } as RuntimeDataSourceField))
const activeVersion = computed(() => versions.value.find(version => version.active
  || version.id === selected.value?.activeVersionId))
const blockerCount = computed(() => checkResult.value?.blockerCount
  ?? checkResult.value?.issues.filter(issue => issue.severity === 'BLOCKER').length
  ?? 0)
const stateText: Record<EditorState, string> = {
  clean: '草稿已同步', dirty: '有未保存修改', saving: '正在保存', checked: '检查已通过',
  published: '已发布', error: '操作失败',
}

function requestError(error: unknown, fallback: string) {
  return error instanceof ApiRequestError
    ? error.message || error.code
    : error instanceof Error ? error.message : fallback
}

function replaceReport(report: ReportSummary) {
  const index = reports.value.findIndex(item => item.id === report.id)
  reports.value = index < 0 ? [report, ...reports.value]
    : reports.value.map(item => item.id === report.id ? report : item)
}

function applyDetail(detail: ReportDetail) {
  selected.value = detail
  Object.assign(editor, {
    name: detail.name,
    description: detail.description ?? '',
    dataSourceId: detail.draft.dataSourceId,
    outputFieldCodes: [...detail.draft.outputFieldCodes],
  })
  checkResult.value = null
  editorState.value = 'clean'
  replaceReport(detail)
}

async function load() {
  loading.value = true
  errorMessage.value = ''
  try {
    const [reportItems, sourceItems] = await Promise.all([
      reportAdminApi.list(systemId.value), dataSourceAdminApi.list(systemId.value),
    ])
    reports.value = reportItems
    dataSources.value = sourceItems
    if (selected.value) {
      const current = reportItems.find(item => item.id === selected.value?.id)
      if (current) await selectReport(current)
      else selected.value = null
    } else if (reportItems[0]) await selectReport(reportItems[0])
  } catch (error) {
    errorMessage.value = requestError(error, '报表目录加载失败')
    editorState.value = 'error'
  } finally {
    loading.value = false
  }
}

async function selectReport(report: ReportSummary) {
  const generation = ++selectionGeneration
  detailLoading.value = true
  errorMessage.value = ''
  capabilityMessage.value = ''
  try {
    const [detail, history] = await Promise.all([
      reportAdminApi.detail(systemId.value, report.id),
      reportAdminApi.versions(systemId.value, report.id),
    ])
    if (generation !== selectionGeneration) return
    applyDetail(detail)
    versions.value = history
    inspectedVersion.value = history.find(version => version.active
      || version.id === detail.activeVersionId) ?? history[0] ?? null
    await loadSourceFields(detail.draft.dataSourceId, generation)
  } catch (error) {
    if (generation !== selectionGeneration) return
    errorMessage.value = requestError(error, '报表详情加载失败')
    editorState.value = 'error'
  } finally {
    if (generation === selectionGeneration) detailLoading.value = false
  }
}

async function fieldsForSource(dataSourceId: string) {
  const source = dataSources.value.find(item => item.id === dataSourceId)
  if (!source?.activeVersionId) return []
  const metadata = await runtimeDataSourceApi.metadata(systemId.value, source.code)
  return metadata.outputFields ?? metadata.fields ?? []
}

async function loadSourceFields(dataSourceId = editor.dataSourceId, generation = selectionGeneration) {
  capabilitiesLoading.value = true
  capabilityMessage.value = ''
  try {
    const fields = await fieldsForSource(dataSourceId)
    if (generation !== selectionGeneration || dataSourceId !== editor.dataSourceId) return
    sourceFields.value = fields.map(field => ({
      logicalFieldId: '', code: field.fieldCode, name: field.fieldName,
      type: field.type, queryType: field.type,
    }))
  } catch (error) {
    if (generation !== selectionGeneration) return
    sourceFields.value = []
    capabilityMessage.value = requestError(error, '字段能力加载失败，请检查草稿后重试')
  } finally {
    if (generation === selectionGeneration) capabilitiesLoading.value = false
  }
}

async function sourceChanged() {
  editor.outputFieldCodes = []
  outputCandidate.value = ''
  markDirty()
  await loadSourceFields()
}

function markDirty() {
  if (!selected.value || editorState.value === 'saving') return
  editorState.value = 'dirty'
  checkResult.value = null
  inspectedVersion.value = activeVersion.value ?? inspectedVersion.value
  errorMessage.value = ''
}

function addField() {
  if (!outputCandidate.value || editor.outputFieldCodes.includes(outputCandidate.value)) return
  editor.outputFieldCodes.push(outputCandidate.value)
  outputCandidate.value = ''
  markDirty()
}

function moveField(index: number, offset: -1 | 1) {
  editor.outputFieldCodes = moveReportField(editor.outputFieldCodes, index, offset)
  markDirty()
}

function removeField(index: number) {
  editor.outputFieldCodes.splice(index, 1)
  markDirty()
}

function buildDraft(): SaveReportDraftInput {
  if (!selected.value) throw new Error('请先选择报表')
  if (!editor.name.trim()) throw new Error('报表名称不能为空')
  if (!editor.dataSourceId) throw new Error('请选择已发布数据源')
  const fieldError = validateReportFields(editor.outputFieldCodes)
  if (fieldError) throw new Error(fieldError)
  return {
    expectedVersion: selected.value.version,
    name: editor.name.trim(),
    description: editor.description.trim() || null,
    dataSourceId: editor.dataSourceId,
    outputFieldCodes: [...editor.outputFieldCodes],
  }
}

async function saveDraft() {
  if (!selected.value || mutating.value) return false
  mutating.value = true
  editorState.value = 'saving'
  errorMessage.value = ''
  try {
    const saved = await reportAdminApi.saveDraft(systemId.value, selected.value.id, buildDraft())
    applyDetail(saved)
    return true
  } catch (error) {
    errorMessage.value = requestError(error, '报表草稿保存失败')
    editorState.value = 'error'
    return false
  } finally {
    mutating.value = false
  }
}

async function checkDraft() {
  if (!selected.value) return false
  if (editorState.value === 'dirty' && !await saveDraft()) return false
  mutating.value = true
  errorMessage.value = ''
  try {
    const result = await reportAdminApi.checkDraft(systemId.value, selected.value.id)
    checkResult.value = result
    if (result.source?.dataSourceId === editor.dataSourceId) sourceFields.value = result.source.fields
    editorState.value = result.valid && blockerCount.value === 0 ? 'checked' : 'error'
    return editorState.value === 'checked'
  } catch (error) {
    errorMessage.value = requestError(error, '报表草稿检查失败')
    editorState.value = 'error'
    return false
  } finally {
    mutating.value = false
  }
}

async function publishDraft() {
  if (!selected.value) return
  if (editorState.value !== 'checked' && !await checkDraft()) return
  mutating.value = true
  errorMessage.value = ''
  try {
    const result = await reportAdminApi.publishDraft(
      systemId.value, selected.value.id, selected.value.version,
    )
    applyDetail(result.report)
    editorState.value = 'published'
    versions.value = await reportAdminApi.versions(systemId.value, result.report.id)
    inspectedVersion.value = await reportAdminApi.version(
      systemId.value, result.report.id, result.version.versionNumber,
    )
  } catch (error) {
    errorMessage.value = requestError(error, '报表发布失败')
    editorState.value = 'error'
  } finally {
    mutating.value = false
  }
}

async function inspectVersion(version: ReportVersion) {
  if (!selected.value) return
  errorMessage.value = ''
  try {
    inspectedVersion.value = await reportAdminApi.version(
      systemId.value, selected.value.id, version.versionNumber,
    )
  } catch (error) {
    errorMessage.value = requestError(error, '报表版本读取失败')
  }
}

async function restoreVersion(version: ReportVersion) {
  if (!selected.value || version.active) return
  if (editorState.value === 'dirty' || editorState.value === 'saving') {
    errorMessage.value = '请先保存或放弃当前本地修改，再恢复历史版本。'
    return
  }
  if (!window.confirm(`将报表 v${version.versionNumber} 恢复为新草稿？当前运行版本不会改变。`)) return
  mutating.value = true
  errorMessage.value = ''
  try {
    const current = selected.value
    await configRecoveryApi.report(systemId.value, current.id, version.versionNumber, {
      expectedVersion: current.draftVersion,
      reason: `恢复报表 v${version.versionNumber} 为新草稿`,
    })
    await selectReport(current)
    message.success('历史版本已恢复为新草稿；请检查后显式发布。')
  } catch (error) {
    errorMessage.value = requestError(error, '报表版本恢复失败')
    editorState.value = 'error'
  } finally {
    mutating.value = false
  }
}

async function openCreate() {
  const source = publishedSources.value[0]
  Object.assign(createForm, { code: '', name: '', description: '', dataSourceId: source?.id ?? '' })
  createOutputCandidate.value = ''
  createOutputFieldCodes.value = []
  createFields.value = []
  createError.value = ''
  createOpen.value = true
  await loadCreateFields()
}

async function loadCreateFields() {
  createOutputCandidate.value = ''
  createOutputFieldCodes.value = []
  createError.value = ''
  try {
    createFields.value = await fieldsForSource(createForm.dataSourceId)
    createOutputCandidate.value = createFields.value[0]?.fieldCode ?? ''
  } catch (error) {
    createFields.value = []
    createError.value = requestError(error, '数据源字段加载失败')
  }
}

function addCreateField() {
  const code = createOutputCandidate.value
  if (!code || createOutputFieldCodes.value.includes(code)
      || createOutputFieldCodes.value.length >= 100) return
  createOutputFieldCodes.value.push(code)
  createOutputCandidate.value = availableCreateFields.value[0]?.fieldCode ?? ''
  createError.value = ''
}

function moveCreateField(index: number, offset: -1 | 1) {
  createOutputFieldCodes.value = moveReportField(createOutputFieldCodes.value, index, offset)
}

function removeCreateField(index: number) {
  createOutputFieldCodes.value.splice(index, 1)
  if (!createOutputCandidate.value) {
    createOutputCandidate.value = availableCreateFields.value[0]?.fieldCode ?? ''
  }
}

async function createReport() {
  const input: CreateReportInput = {
    code: createForm.code.trim(),
    name: createForm.name.trim(),
    description: createForm.description.trim() || null,
    dataSourceId: createForm.dataSourceId,
    outputFieldCodes: [...createOutputFieldCodes.value],
  }
  const fieldError = validateReportFields(input.outputFieldCodes)
  if (!input.code || !input.name || !input.dataSourceId || fieldError) {
    createError.value = fieldError || '编码、名称和数据源均为必填项'
    return
  }
  creating.value = true
  createError.value = ''
  try {
    const created = await reportAdminApi.create(systemId.value, input)
    createOpen.value = false
    replaceReport(created)
    await selectReport(created)
  } catch (error) {
    createError.value = requestError(error, '报表创建失败')
  } finally {
    creating.value = false
  }
}

function formatTime(value?: string) {
  if (!value) return '—'
  const date = new Date(value)
  return Number.isNaN(date.valueOf()) ? value : date.toLocaleString('zh-CN')
}

onMounted(load)
</script>

<template>
  <section class="admin-page reports-page">
    <AdminPageHeader title="报表工作室" :description="`管理 ${reports.length} 个租户报表；发布后固定数据源版本与字段顺序。`">
      <template v-if="!isMobile" #actions>
        <a-button aria-label="刷新报表" :loading="loading" :disabled="loading" @click="load"><RefreshCw :size="16" />刷新</a-button>
        <a-button class="report-create" type="primary" @click="openCreate"><Plus :size="16" />新建报表</a-button>
      </template>
    </AdminPageHeader>

    <a-alert v-if="errorMessage" class="report-error" type="error" show-icon role="alert" aria-live="assertive" :message="errorMessage" />

    <div v-if="isMobile" class="report-mobile-gate">
      <AdminMobileNotice />
      <a-empty description="报表字段排序、版本固定和调度配置需要桌面工作区，请在宽度大于 720px 的窗口中管理。" />
    </div>
    <a-spin v-else :spinning="loading || detailLoading" :aria-busy="loading || detailLoading">
      <div class="report-workspace">
        <aside class="report-list-panel">
          <header><strong>报表目录</strong><span>{{ reports.length }}</span></header>
          <button v-for="report in reports" :key="report.id" class="report-list-item" :class="{ active: selected?.id === report.id }" type="button" @click="selectReport(report)">
            <FileText :size="17" /><span><strong>{{ report.name }}</strong><small>{{ report.code }} · {{ report.activeVersionNumber ? `v${report.activeVersionNumber}` : '未发布' }}</small></span>
          </button>
          <a-empty v-if="!loading && !reports.length" class="report-list-empty" description="暂无报表，请先创建" />
        </aside>

        <main v-if="selected" class="report-editor">
          <header class="editor-heading">
            <div><span class="report-code">{{ selected.code }}</span><strong>{{ selected.name }}</strong></div>
            <a-tag :color="editorState === 'error' ? 'red' : editorState === 'dirty' ? 'orange' : 'green'">{{ stateText[editorState] }}</a-tag>
          </header>

          <section class="editor-card definition-section">
            <h2>基础定义</h2>
            <div class="form-grid">
              <label>名称<input v-model="editor.name" class="report-name-input" maxlength="160" @input="markDirty"></label>
              <label>数据源<select v-model="editor.dataSourceId" class="report-source-select" @change="sourceChanged"><option value="" disabled>请选择已发布数据源</option><option v-for="source in publishedSources" :key="source.id" :value="source.id">{{ source.name }} · {{ source.code }} · v{{ source.activeVersionNumber }}</option></select></label>
              <label class="wide">描述<textarea v-model="editor.description" class="report-description-input" rows="2" @input="markDirty" /></label>
            </div>
          </section>

          <section class="editor-card field-section">
            <div class="section-heading"><div><h2>输出字段</h2><p>字段顺序就是运行表格的列顺序，支持 1–100 个字段。</p></div><span>{{ editor.outputFieldCodes.length }}/100</span></div>
            <a-alert v-if="capabilityMessage" class="report-capability-error" type="warning" show-icon :message="capabilityMessage" />
            <div class="field-picker"><select v-model="outputCandidate" class="report-field-candidate" :disabled="capabilitiesLoading"><option value="">选择字段</option><option v-for="field in availableFields" :key="field.code" :value="field.code">{{ reportFieldLabel(field) }}</option></select><a-button class="report-field-add" :disabled="!outputCandidate" @click="addField"><Plus :size="15" />添加</a-button></div>
            <div v-if="selectedFields.length" class="report-field-list">
              <div v-for="(field, index) in selectedFields" :key="`${field.code}-${index}`" class="report-field-row">
                <span class="ordinal">{{ index + 1 }}</span><span><strong>{{ field.name || field.code }}</strong><small>{{ field.code }} · {{ field.type }}</small></span>
                <button type="button" title="上移" :disabled="index === 0" @click="moveField(index, -1)"><ChevronUp :size="15" /></button>
                <button type="button" title="下移" :disabled="index === selectedFields.length - 1" @click="moveField(index, 1)"><ChevronDown :size="15" /></button>
                <button type="button" title="移除" @click="removeField(index)"><Trash2 :size="15" /></button>
              </div>
            </div>
            <a-empty v-else class="report-field-empty" description="至少选择一个输出字段" />
          </section>

          <div class="editor-actions">
            <a-button class="report-save" :loading="mutating && editorState === 'saving'" @click="saveDraft"><Save :size="16" />保存草稿</a-button>
            <a-button class="report-check" :loading="mutating" @click="checkDraft"><CheckCircle2 :size="16" />检查</a-button>
            <a-button class="report-publish" type="primary" :loading="mutating" @click="publishDraft"><Send :size="16" />发布</a-button>
          </div>

          <section v-if="checkResult" class="editor-card check-section">
            <h2>检查结果</h2><p>{{ checkResult.valid ? '检查通过，可以发布。' : `存在 ${blockerCount} 个阻断项。` }}</p>
            <ul v-if="checkResult.issues.length"><li v-for="issue in checkResult.issues" :key="`${issue.code}-${issue.path}`"><strong>{{ issue.severity }}</strong> {{ issue.path }}：{{ issue.message }}</li></ul>
          </section>

          <section class="editor-card version-section">
            <div class="section-heading"><div><h2>发布版本与精确 Pin</h2><p>历史版本按版本号读取，不跟随后续数据源或报表发布移动。</p></div><span>{{ versions.length }} 个版本</span></div>
            <div v-if="versions.length" class="version-layout">
              <div class="version-list"><button v-for="version in versions" :key="version.id" type="button" :class="{ active: inspectedVersion?.id === version.id }" @click="inspectVersion(version)"><strong>v{{ version.versionNumber }}</strong><span>{{ version.active ? '当前发布' : formatTime(version.publishedAt) }}</span></button></div>
              <div v-if="inspectedVersion" class="pin-detail">
                <header><strong>{{ inspectedVersion.name }} · v{{ inspectedVersion.versionNumber }}</strong><span><a-tag v-if="inspectedVersion.active" color="green">当前</a-tag><a-button v-else size="small" :disabled="mutating || editorState === 'dirty' || editorState === 'saving'" @click="restoreVersion(inspectedVersion)"><RotateCcw :size="14" />恢复为草稿</a-button></span></header>
                <dl><div><dt>数据源</dt><dd>{{ inspectedVersion.dataSourceName }} · {{ inspectedVersion.dataSourceCode }}</dd></div><div><dt>精确版本</dt><dd>v{{ inspectedVersion.dataSourceVersionNumber }} · {{ inspectedVersion.dataSourceVersionId }}</dd></div><div><dt>模块 / Schema</dt><dd>{{ inspectedVersion.moduleCode }} · {{ inspectedVersion.schemaVersionId }}</dd></div><div><dt>发布时间</dt><dd>{{ formatTime(inspectedVersion.publishedAt) }}</dd></div></dl>
                <ol><li v-for="field in inspectedVersion.fields" :key="field.code"><strong>{{ field.name }}</strong><span>{{ field.code }} · {{ field.type }}</span></li></ol>
              </div>
            </div>
            <a-empty v-else class="report-version-empty" description="尚未发布版本" />
          </section>
          <ReportScheduleManager :system-id="systemId" :report-id="selected.id" :report-code="selected.code" :report-active="Boolean(selected.activeVersionId)" />
        </main>
        <a-empty v-else-if="!loading" class="report-editor-empty" description="选择或创建一个报表开始配置" />
      </div>
    </a-spin>

    <a-modal v-model:open="createOpen" title="新建报表" :confirm-loading="creating" @ok="createReport">
      <div class="create-form">
        <label>编码<input v-model="createForm.code" class="report-create-code" placeholder="例如 monthly_orders"></label>
        <label>名称<input v-model="createForm.name" class="report-create-name"></label>
        <label>描述<textarea v-model="createForm.description" rows="2" /></label>
        <label>已发布数据源<select v-model="createForm.dataSourceId" class="report-create-source" @change="loadCreateFields"><option v-for="source in publishedSources" :key="source.id" :value="source.id">{{ source.name }} · {{ source.code }}</option></select></label>
        <div class="report-create-fields">
          <div class="section-heading"><div><strong>输出字段</strong><p>按提交顺序选择 1–100 个唯一字段。</p></div><span>{{ createOutputFieldCodes.length }}/100</span></div>
          <div class="field-picker">
            <select v-model="createOutputCandidate" class="report-create-field-candidate" :disabled="createOutputFieldCodes.length >= 100"><option value="">选择字段</option><option v-for="field in availableCreateFields" :key="field.fieldCode" :value="field.fieldCode">{{ field.fieldName }} · {{ field.fieldCode }}</option></select>
            <a-button class="report-create-field-add" :disabled="!createOutputCandidate || createOutputFieldCodes.length >= 100" @click="addCreateField"><Plus :size="15" />添加</a-button>
          </div>
          <div v-if="selectedCreateFields.length" class="report-field-list report-create-field-list">
            <div v-for="(field, index) in selectedCreateFields" :key="field.fieldCode" class="report-field-row report-create-field-row">
              <span class="ordinal">{{ index + 1 }}</span><span><strong>{{ field.fieldName || field.fieldCode }}</strong><small>{{ field.fieldCode }} · {{ field.type }}</small></span>
              <button class="report-create-field-up" type="button" title="上移" :disabled="index === 0" @click="moveCreateField(index, -1)"><ChevronUp :size="15" /></button>
              <button class="report-create-field-down" type="button" title="下移" :disabled="index === selectedCreateFields.length - 1" @click="moveCreateField(index, 1)"><ChevronDown :size="15" /></button>
              <button class="report-create-field-remove" type="button" title="移除" @click="removeCreateField(index)"><Trash2 :size="15" /></button>
            </div>
          </div>
          <a-empty v-else class="report-create-field-empty" description="至少选择一个输出字段" />
        </div>
        <a-alert v-if="createError" type="error" show-icon :message="createError" />
      </div>
    </a-modal>
  </section>
</template>

<style scoped>
.reports-page{display:grid;min-width:0;gap:16px}.report-mobile-gate{min-width:0;padding:4px 0 28px}.report-mobile-gate .ant-empty{padding:30px 12px;border:1px solid #dce5e9;border-radius:8px;background:#fff}.report-workspace{display:grid;grid-template-columns:250px minmax(0,1fr);min-width:0;gap:16px;align-items:start}.report-list-panel,.report-editor,.editor-card{min-width:0;border:1px solid #dce5e9;border-radius:10px;background:#fff}.report-list-panel{overflow:hidden}.report-list-panel>header,.section-heading,.editor-heading,.editor-actions,.field-picker,.report-field-row,.pin-detail header{display:flex;align-items:center}.report-list-panel>header{justify-content:space-between;padding:14px;border-bottom:1px solid #e7edef}.report-list-item{display:flex;width:100%;gap:10px;padding:12px 14px;border:0;border-bottom:1px solid #eef2f3;background:#fff;text-align:left;cursor:pointer}.report-list-item.active{background:#edf7f5;color:#12695f}.report-list-item span{display:grid;min-width:0;gap:3px}.report-list-item strong,.report-list-item small{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.report-list-item small,.section-heading p,.report-field-row small,.version-list span,.pin-detail span{color:#697981;font-size:12px}.report-editor{display:grid;gap:14px;padding:16px}.editor-heading{justify-content:space-between}.editor-heading>div{display:grid;gap:4px}.report-code{font:12px ui-monospace;color:#64748b}.editor-card{padding:16px}.editor-card h2{margin:0 0 12px;font-size:16px}.section-heading{justify-content:space-between;gap:12px}.section-heading h2,.section-heading p{margin:0}.form-grid{display:grid;grid-template-columns:1fr 1fr;gap:12px}.form-grid label,.create-form label{display:grid;gap:6px;color:#52626a;font-size:13px}.form-grid .wide{grid-column:1/-1}input,textarea,select{width:100%;box-sizing:border-box;padding:8px 10px;border:1px solid #cbd5da;border-radius:7px;background:#fff;color:#17252d}.field-picker{gap:10px}.report-field-list{display:grid;gap:8px;margin-top:12px}.report-field-row{display:grid;grid-template-columns:30px minmax(0,1fr) 32px 32px 32px;gap:7px;padding:9px;border:1px solid #e3e9eb;border-radius:8px}.report-field-row>span:nth-child(2){display:grid}.report-field-row button{display:grid;place-items:center;border:0;background:transparent}.ordinal{display:grid;place-items:center;border-radius:50%;background:#edf4f3}.editor-actions{justify-content:flex-end;gap:10px}.check-section ul{margin:10px 0 0;padding-left:20px}.version-layout{display:grid;grid-template-columns:150px minmax(0,1fr);gap:12px;margin-top:14px}.version-list{display:grid;align-content:start;gap:7px}.version-list button{display:grid;gap:3px;padding:9px;border:1px solid #dde6e8;border-radius:7px;background:#fff;text-align:left}.version-list button.active{border-color:#279486;background:#eff9f7}.pin-detail{min-width:0;padding:12px;border:1px solid #e1e8ea;border-radius:8px;overflow:auto}.pin-detail header{justify-content:space-between}.pin-detail dl{display:grid;grid-template-columns:1fr 1fr;gap:8px}.pin-detail dl div{display:grid;gap:2px}.pin-detail dt{color:#718088;font-size:12px}.pin-detail dd{margin:0;overflow-wrap:anywhere}.pin-detail ol{display:grid;gap:6px;padding-left:24px}.pin-detail li span{display:block}.create-form{display:grid;gap:12px}.report-list-empty,.report-editor-empty,.report-field-empty,.report-version-empty{padding:28px 12px}@media(max-width:1100px){.report-workspace{grid-template-columns:210px minmax(0,1fr)}.editor-heading{align-items:flex-start;flex-direction:column}.editor-actions{width:100%;flex-wrap:wrap}.version-layout{grid-template-columns:120px minmax(0,1fr)}}
</style>
