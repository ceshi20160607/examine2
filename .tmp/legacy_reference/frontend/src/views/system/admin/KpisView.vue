<script setup lang="ts">
import { message } from 'ant-design-vue'
import { CheckCircle2, Gauge, Plus, RefreshCw, RotateCcw, Save, Send } from 'lucide-vue-next'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'

import AdminMobileNotice from '@/components/admin/AdminMobileNotice.vue'
import KpiCalculationExplanation from '@/components/kpi/KpiCalculationExplanation.vue'
import KpiDefinitionEditor from '@/components/kpi/KpiDefinitionEditor.vue'
import KpiTargetTable from '@/components/kpi/KpiTargetTable.vue'
import { systemAdminApi } from '@/services/admin'
import { dataSourceAdminApi, runtimeDataSourceApi } from '@/services/dataSource'
import { kpiAdminApi } from '@/services/kpi'
import { configRecoveryApi } from '@/services/configRecovery'
import { memberDirectoryApi } from '@/services/memberDirectory'
import type { Department, Role } from '@/types/admin'
import type { DataSourceSummary } from '@/types/dataSource'
import type {
  KpiCalculation,
  KpiCheckResult,
  KpiDetail,
  KpiSubjectType,
  KpiSummary,
  KpiTarget,
  KpiVersion,
} from '@/types/kpi'
import type { RuntimeMemberOption } from '@/types/memberDirectory'
import type { DataSourceStatisticsCapabilities } from '@/types/statistics'
import {
  alignedPeriodStart,
  createKpiEditorDraft,
  defaultPeriodStart,
  normalizeNonNegativeDecimal,
  validateKpiEditorDraft,
  type KpiEditorDraft,
} from './kpiEditorModel'
import { useAdminViewport } from '@/composables/useAdminViewport'

const route = useRoute()
const { isMobile } = useAdminViewport()
const systemId = computed(() => String(route.params.systemId))
const loading = ref(false)
const detailLoading = ref(false)
const errorMessage = ref('')
const definitions = ref<KpiSummary[]>([])
const selected = ref<KpiDetail | null>(null)
let editor = reactive<KpiEditorDraft>(createKpiEditorDraft())
const sources = ref<DataSourceSummary[]>([])
const capabilities = ref<DataSourceStatisticsCapabilities | null>(null)
const capabilitiesLoading = ref(false)
const versions = ref<KpiVersion[]>([])
const targets = ref<KpiTarget[]>([])
const checkResult = ref<KpiCheckResult | null>(null)
const saveState = ref<'IDLE' | 'DIRTY' | 'SAVING' | 'CHECKED' | 'PUBLISHED' | 'ERROR'>('IDLE')
const createOpen = ref(false)
const creating = ref(false)
const createError = ref('')
const createCode = ref('')
let createEditor = reactive<KpiEditorDraft>(createKpiEditorDraft())
const createCapabilities = ref<DataSourceStatisticsCapabilities | null>(null)
const createCapabilitiesLoading = ref(false)
const targetOpen = ref(false)
const targetSaving = ref(false)
const targetError = ref('')
const editingTarget = ref<KpiTarget | null>(null)
const targetForm = reactive({ subjectId: '', periodStart: '', targetValue: '' })
const members = ref<RuntimeMemberOption[]>([])
const departments = ref<Department[]>([])
const roles = ref<Role[]>([])
const subjectLoading = ref(false)
const calculatingId = ref('')
const historyLoadingId = ref('')
const historyOpen = ref(false)
const historyTarget = ref<KpiTarget | null>(null)
const history = ref<KpiCalculation[]>([])
let detailGeneration = 0
let capabilityGeneration = 0
let applyingEditor = false

const clientIssues = computed(() => validateKpiEditorDraft(editor, capabilities.value))
const activeVersion = computed(() => versions.value.find(version => version.active) ?? versions.value.find(version => version.id === selected.value?.activeVersionId) ?? null)
const targetSubjectType = computed<KpiSubjectType>(() => activeVersion.value?.subjectType ?? editor.subjectType)
const subjectOptions = computed(() => {
  if (targetSubjectType.value === 'MEMBER') return members.value.map(item => ({ value: item.memberId, label: `${item.displayName}（${item.memberCode}）` }))
  if (targetSubjectType.value === 'DEPARTMENT') return departments.value.filter(item => item.status === 'ACTIVE').map(item => ({ value: item.id, label: `${item.name}（${item.code}）` }))
  return roles.value.filter(item => item.status === 'ACTIVE').map(item => ({ value: item.id, label: `${item.name}（${item.code}）` }))
})
const saveStateLabel = computed(() => ({ IDLE: '已同步', DIRTY: '待保存', SAVING: '保存中', CHECKED: '检查通过', PUBLISHED: '已发布', ERROR: '操作失败' })[saveState.value])

function errorText(error: unknown, fallback: string) {
  return error instanceof Error && error.message ? error.message : fallback
}

function applyDetail(detail: KpiDetail) {
  selected.value = detail
  applyingEditor = true
  Object.assign(editor, createKpiEditorDraft(), detail.draft, {
    name: detail.name,
    description: detail.description ?? '',
  })
  applyingEditor = false
  const index = definitions.value.findIndex(item => item.id === detail.id)
  if (index >= 0) definitions.value[index] = detail
  else definitions.value.unshift(detail)
  saveState.value = 'IDLE'
  checkResult.value = null
}

async function load() {
  loading.value = true
  errorMessage.value = ''
  try {
    const [kpis, dataSources] = await Promise.all([kpiAdminApi.list(systemId.value), dataSourceAdminApi.list(systemId.value)])
    definitions.value = kpis
    sources.value = dataSources
    if (selected.value) {
      const retained = kpis.find(item => item.id === selected.value?.id)
      if (retained) await selectDefinition(retained.id)
      else clearSelection()
    } else if (kpis[0]) await selectDefinition(kpis[0].id)
  } catch (error) {
    errorMessage.value = errorText(error, 'KPI 配置加载失败')
  } finally {
    loading.value = false
  }
}

function clearSelection() {
  selected.value = null
  versions.value = []
  targets.value = []
  capabilities.value = null
  Object.assign(editor, createKpiEditorDraft())
}

async function selectDefinition(id: string) {
  const generation = ++detailGeneration
  detailLoading.value = true
  errorMessage.value = ''
  try {
    const [detail, publishedVersions, assignedTargets] = await Promise.all([
      kpiAdminApi.detail(systemId.value, id),
      kpiAdminApi.versions(systemId.value, id),
      kpiAdminApi.targets(systemId.value, id),
    ])
    if (generation !== detailGeneration) return
    applyDetail(detail)
    versions.value = publishedVersions
    targets.value = assignedTargets
    await loadCapabilities(detail.draft.dataSourceId)
  } catch (error) {
    if (generation === detailGeneration) errorMessage.value = errorText(error, 'KPI 详情加载失败')
  } finally {
    if (generation === detailGeneration) detailLoading.value = false
  }
}

async function loadCapabilities(dataSourceId: string) {
  const generation = ++capabilityGeneration
  capabilities.value = null
  if (!dataSourceId) return
  const source = sources.value.find(item => item.id === dataSourceId)
  if (!source?.activeVersionId) return
  capabilitiesLoading.value = true
  try {
    const result = await runtimeDataSourceApi.statisticsCapabilities(systemId.value, source.code)
    if (generation === capabilityGeneration) capabilities.value = result
  } catch {
    if (generation === capabilityGeneration) capabilities.value = null
  } finally {
    if (generation === capabilityGeneration) capabilitiesLoading.value = false
  }
}

function markDirty() {
  if (!applyingEditor && selected.value && saveState.value !== 'SAVING') {
    saveState.value = 'DIRTY'
    checkResult.value = null
  }
}

watch(editor, markDirty, { deep: true, flush: 'sync' })

async function saveDraft() {
  if (!selected.value) return false
  const issues = clientIssues.value
  if (issues.length) {
    saveState.value = 'ERROR'
    errorMessage.value = issues.map(issue => issue.message).join('；')
    return false
  }
  saveState.value = 'SAVING'
  errorMessage.value = ''
  try {
    const saved = await kpiAdminApi.saveDraft(systemId.value, selected.value.id, {
      expectedVersion: selected.value.draftVersion,
      name: editor.name.trim(),
      description: editor.description.trim() || null,
      dataSourceId: editor.dataSourceId,
      subjectType: editor.subjectType,
      periodType: editor.periodType,
      aggregation: editor.aggregation,
      measureFieldCode: editor.aggregation === 'COUNT' ? null : editor.measureFieldCode,
      timeFieldCode: editor.timeFieldCode,
      direction: editor.direction,
      warningThreshold: normalizeNonNegativeDecimal(editor.warningThreshold)!,
    })
    applyDetail(saved)
    message.success('KPI 草稿已保存')
    return true
  } catch (error) {
    saveState.value = 'ERROR'
    errorMessage.value = errorText(error, 'KPI 草稿保存失败')
    return false
  }
}

async function ensureSaved() {
  return saveState.value === 'DIRTY' || saveState.value === 'ERROR' ? saveDraft() : true
}

async function checkDraft() {
  if (!selected.value || !await ensureSaved()) return
  errorMessage.value = ''
  try {
    const checked = await kpiAdminApi.checkDraft(systemId.value, selected.value.id)
    checkResult.value = checked
    saveState.value = checked.blockerCount ? 'ERROR' : 'CHECKED'
    if (!checked.blockerCount) message.success('发布检查通过')
  } catch (error) {
    saveState.value = 'ERROR'
    errorMessage.value = errorText(error, 'KPI 发布检查失败')
  }
}

async function publishDraft() {
  if (!selected.value || !await ensureSaved()) return
  await checkDraft()
  if (!checkResult.value || checkResult.value.blockerCount) return
  try {
    const result = await kpiAdminApi.publishDraft(systemId.value, selected.value.id, selected.value.draftVersion)
    applyDetail(result.kpi)
    saveState.value = 'PUBLISHED'
    versions.value = await kpiAdminApi.versions(systemId.value, selected.value.id)
    message.success(`KPI v${result.version.versionNumber} 已发布`)
  } catch (error) {
    saveState.value = 'ERROR'
    errorMessage.value = errorText(error, 'KPI 发布失败')
  }
}

async function restoreVersion(version: KpiVersion) {
  if (!selected.value || version.active) return
  if (saveState.value === 'DIRTY' || saveState.value === 'SAVING') {
    errorMessage.value = '请先保存或放弃当前本地修改，再恢复历史版本。'
    return
  }
  if (!window.confirm(`将 KPI v${version.versionNumber} 恢复为新草稿？当前运行版本不会改变。`)) return
  detailLoading.value = true
  errorMessage.value = ''
  try {
    const id = selected.value.id
    await configRecoveryApi.kpi(systemId.value, id, version.versionNumber, {
      expectedVersion: selected.value.draftVersion,
      reason: `恢复 KPI v${version.versionNumber} 为新草稿`,
    })
    await selectDefinition(id)
    message.success('历史版本已恢复为新草稿；请检查后显式发布。')
  } catch (error) {
    saveState.value = 'ERROR'
    errorMessage.value = errorText(error, 'KPI 版本恢复失败')
  } finally {
    detailLoading.value = false
  }
}

function openCreate() {
  createCode.value = ''
  Object.assign(createEditor, createKpiEditorDraft())
  createCapabilities.value = null
  createError.value = ''
  createOpen.value = true
}

async function createDefinition() {
  const issues = validateKpiEditorDraft(createEditor, createCapabilities.value)
  if (!createCode.value.trim() || issues.length) {
    createError.value = !createCode.value.trim() ? '请输入 KPI 编码' : issues.map(issue => issue.message).join('；')
    return
  }
  creating.value = true
  createError.value = ''
  try {
    const created = await kpiAdminApi.create(systemId.value, {
      code: createCode.value.trim(), name: createEditor.name.trim(), description: createEditor.description.trim() || null,
      dataSourceId: createEditor.dataSourceId,
      subjectType: createEditor.subjectType,
      periodType: createEditor.periodType,
      aggregation: createEditor.aggregation,
      measureFieldCode: createEditor.aggregation === 'COUNT' ? null : createEditor.measureFieldCode,
      timeFieldCode: createEditor.timeFieldCode,
      direction: createEditor.direction,
      warningThreshold: normalizeNonNegativeDecimal(createEditor.warningThreshold)!,
    })
    definitions.value.unshift(created)
    createOpen.value = false
    await selectDefinition(created.id)
  } catch (error) {
    createError.value = errorText(error, 'KPI 创建失败')
  } finally {
    creating.value = false
  }
}

async function loadCreateCapabilities(dataSourceId: string) {
  createCapabilities.value = null
  const source = sources.value.find(item => item.id === dataSourceId)
  if (!source?.activeVersionId) return
  createCapabilitiesLoading.value = true
  try {
    createCapabilities.value = await runtimeDataSourceApi.statisticsCapabilities(systemId.value, source.code)
  } catch {
    createCapabilities.value = null
  } finally {
    createCapabilitiesLoading.value = false
  }
}

async function loadSubjects() {
  subjectLoading.value = true
  targetError.value = ''
  try {
    if (targetSubjectType.value === 'MEMBER' && !members.value.length) {
      members.value = (await memberDirectoryApi.list(systemId.value, '', 1, 200)).items
    } else if (targetSubjectType.value === 'DEPARTMENT' && !departments.value.length) {
      departments.value = (await systemAdminApi.listDepartments(systemId.value, { page: 1, size: 200 })).items
    } else if (targetSubjectType.value === 'ROLE' && !roles.value.length) {
      roles.value = (await systemAdminApi.listRoles(systemId.value, { page: 1, size: 200 })).items
    }
  } catch (error) {
    targetError.value = errorText(error, '考核对象目录加载失败')
  } finally {
    subjectLoading.value = false
  }
}

async function openCreateTarget() {
  if (!selected.value?.activeVersionId) {
    message.warning('请先发布 KPI 定义')
    return
  }
  editingTarget.value = null
  Object.assign(targetForm, { subjectId: '', periodStart: defaultPeriodStart(activeVersion.value?.periodType ?? editor.periodType), targetValue: '' })
  targetError.value = ''
  targetOpen.value = true
  await loadSubjects()
}

function openEditTarget(target: KpiTarget) {
  editingTarget.value = target
  Object.assign(targetForm, { subjectId: target.subjectId, periodStart: target.periodStart, targetValue: target.targetValue })
  targetError.value = ''
  targetOpen.value = true
}

async function saveTarget() {
  if (!selected.value) return
  const targetValue = normalizeNonNegativeDecimal(targetForm.targetValue)
  const period = alignedPeriodStart(activeVersion.value?.periodType ?? editor.periodType, targetForm.periodStart)
  if (!targetValue) {
    targetError.value = '目标值必须是非负十进制字符串'
    return
  }
  if (!period) {
    targetError.value = '周期开始日期未按月、季度或年度边界对齐'
    return
  }
  if (!editingTarget.value && !targetForm.subjectId) {
    targetError.value = '请选择考核对象'
    return
  }
  targetSaving.value = true
  targetError.value = ''
  try {
    const saved = editingTarget.value
      ? await kpiAdminApi.updateTarget(systemId.value, editingTarget.value.id, { targetValue, expectedVersion: editingTarget.value.version })
      : await kpiAdminApi.createTarget(systemId.value, selected.value.id, {
        subjectId: targetForm.subjectId, periodStart: period, targetValue,
      })
    const index = targets.value.findIndex(item => item.id === saved.id)
    if (index >= 0) targets.value[index] = saved
    else targets.value.unshift(saved)
    targetOpen.value = false
    message.success(editingTarget.value ? '目标已更新' : '目标已创建')
  } catch (error) {
    targetError.value = errorText(error, '目标保存失败')
  } finally {
    targetSaving.value = false
  }
}

async function calculateTarget(target: KpiTarget) {
  calculatingId.value = target.id
  errorMessage.value = ''
  try {
    const calculation = await kpiAdminApi.recalculate(systemId.value, target.id)
    const index = targets.value.findIndex(item => item.id === target.id)
    const current = targets.value[index]
    if (index >= 0 && current) targets.value[index] = { ...current, latestCalculation: calculation }
    if (calculation.status === 'CALCULATION_FAILED') message.error(`计算失败：${calculation.errorCode || 'CALCULATION_FAILED'}`)
    else message.success('KPI 计算完成')
  } catch (error) {
    errorMessage.value = errorText(error, 'KPI 计算请求失败')
  } finally {
    calculatingId.value = ''
  }
}

async function openHistory(target: KpiTarget) {
  historyTarget.value = target
  history.value = []
  historyOpen.value = true
  historyLoadingId.value = target.id
  try {
    history.value = await kpiAdminApi.history(systemId.value, target.id)
  } catch (error) {
    errorMessage.value = errorText(error, '计算历史加载失败')
  } finally {
    historyLoadingId.value = ''
  }
}

function formatTime(value: string) {
  if (!value) return '—'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString()
}

onMounted(load)
</script>

<template>
  <section class="kpi-admin-page">
    <header class="kpi-page-heading">
      <div><h1><Gauge :size="22" />KPI 管理</h1><p>发布可复用 KPI 定义，为成员、部门或角色配置周期目标并保留可解释计算快照。</p></div>
      <div v-if="!isMobile" class="heading-actions"><a-button aria-label="刷新 KPI" :loading="loading" :disabled="loading" @click="load"><RefreshCw :size="15" />刷新</a-button><a-button type="primary" @click="openCreate"><Plus :size="15" />新建 KPI</a-button></div>
    </header>
    <a-alert v-if="errorMessage" class="page-error" type="error" show-icon closable role="alert" aria-live="assertive" :message="errorMessage" @close="errorMessage = ''" />

    <div v-if="isMobile" class="kpi-mobile-gate">
      <AdminMobileNotice />
      <a-empty description="KPI 定义、目标和版本编辑器包含多列关联配置，请在宽度大于 720px 的桌面窗口中管理。" />
    </div>
    <div v-else class="kpi-studio" :aria-busy="loading || detailLoading">
      <aside class="kpi-list">
        <header><strong>KPI 定义</strong><span>{{ definitions.length }}</span></header>
        <button v-for="definition in definitions" :key="definition.id" type="button" :class="{ active: selected?.id === definition.id }" @click="selectDefinition(definition.id)">
          <Gauge :size="17" /><span><strong>{{ definition.name }}</strong><small>{{ definition.code }}</small></span><a-tag :color="definition.activeVersionId ? 'green' : 'default'">{{ definition.activeVersionId ? `v${definition.activeVersionNumber}` : '草稿' }}</a-tag>
        </button>
        <a-empty v-if="!loading && !definitions.length" description="尚无 KPI 定义" />
      </aside>

      <main class="kpi-editor" :class="{ loading: detailLoading }">
        <div v-if="!selected" class="kpi-editor-empty"><Gauge :size="38" /><strong>选择或创建一个 KPI</strong><span>先实现定义、目标和手动计算闭环。</span></div>
        <template v-else>
          <header class="kpi-toolbar">
            <div><h2>{{ selected.name }}</h2><p>{{ selected.code }} · 草稿代次 {{ selected.draftVersion }} · {{ saveStateLabel }}</p></div>
            <div class="kpi-actions"><a-button class="kpi-save" :loading="saveState === 'SAVING'" @click="saveDraft"><Save :size="15" />保存草稿</a-button><a-button class="kpi-check" @click="checkDraft"><CheckCircle2 :size="15" />检查</a-button><a-button class="kpi-publish" type="primary" @click="publishDraft"><Send :size="15" />发布</a-button></div>
          </header>
          <div class="kpi-editor-content">
            <section class="kpi-card"><div class="card-heading"><div><strong>定义草稿</strong><p>能力控件仅展示已发布、可读且类型匹配的字段；MONEY 不参与数值聚合。</p></div><a-tag>{{ saveStateLabel }}</a-tag></div><KpiDefinitionEditor v-model="editor" :sources="sources" :capabilities="capabilities" :capabilities-loading="capabilitiesLoading" @source-change="loadCapabilities" /><ul v-if="clientIssues.length" class="client-issues"><li v-for="issue in clientIssues" :key="`${issue.code}:${issue.path}`"><code>{{ issue.code }}</code><span>{{ issue.message }}</span></li></ul></section>

            <section v-if="checkResult" class="kpi-card check-result" :class="{ blocked: checkResult.blockerCount }"><div class="card-heading"><div><strong>发布检查</strong><p>{{ checkResult.blockerCount }} 个阻断，{{ checkResult.warningCount }} 个警告</p></div><a-tag :color="checkResult.blockerCount ? 'red' : 'green'">{{ checkResult.valid ? '可以发布' : '不可发布' }}</a-tag></div><ul v-if="checkResult.issues.length"><li v-for="issue in checkResult.issues" :key="`${issue.code}:${issue.path}`"><a-tag :color="issue.severity === 'BLOCKER' ? 'red' : 'orange'">{{ issue.severity }}</a-tag><strong>{{ issue.code }}</strong><code>{{ issue.path }}</code><span>{{ issue.message }}</span></li></ul></section>

            <section class="kpi-card target-section"><div class="card-heading"><div><strong>周期目标</strong><p>目标固定当前不可变 KPI 版本；实际值与状态只读取后端计算结果。</p></div><a-button class="kpi-create-target" type="primary" :disabled="!selected.activeVersionId" @click="openCreateTarget"><Plus :size="15" />创建目标</a-button></div><KpiTargetTable :targets="targets" :calculating-id="calculatingId" :history-loading-id="historyLoadingId" @edit="openEditTarget" @calculate="calculateTarget" @history="openHistory" /></section>

            <section class="kpi-card version-section"><div class="card-heading"><div><strong>发布版本</strong><p>历史目标继续使用固定的数据源、Schema、度量和时间字段元数据；恢复只创建草稿。</p></div></div><table v-if="versions.length"><thead><tr><th>版本</th><th>对象 / 周期</th><th>统计</th><th>固定数据源</th><th>固定字段</th><th>发布时间</th><th>状态</th><th>操作</th></tr></thead><tbody><tr v-for="version in versions" :key="version.id"><td>v{{ version.versionNumber }}</td><td>{{ version.subjectType }} / {{ version.periodType }}</td><td>{{ version.aggregation }} · {{ version.direction }}</td><td>{{ version.dataSourceCode }} v{{ version.dataSourceVersionNumber }}<small>{{ version.schemaVersionId }}</small></td><td><span>{{ version.measureField?.name || 'COUNT' }}</span><small>{{ version.timeField.name || version.timeField.code }}</small></td><td>{{ formatTime(version.publishedAt) }}</td><td><a-tag :color="version.active ? 'green' : 'default'">{{ version.active ? '当前' : '历史' }}</a-tag></td><td><a-button v-if="!version.active" size="small" :disabled="saveState === 'DIRTY' || saveState === 'SAVING'" @click="restoreVersion(version)"><RotateCcw :size="14" />恢复草稿</a-button></td></tr></tbody></table><a-empty v-else description="暂无发布版本" /></section>
          </div>
        </template>
      </main>
    </div>

    <a-modal v-model:open="createOpen" title="新建 KPI" width="760px" :confirm-loading="creating" @ok="createDefinition"><a-form layout="vertical"><a-form-item label="编码" required><a-input v-model:value="createCode" class="create-kpi-code" maxlength="64" placeholder="例如 monthly_order_amount" /></a-form-item></a-form><KpiDefinitionEditor v-model="createEditor" :sources="sources" :capabilities="createCapabilities" :capabilities-loading="createCapabilitiesLoading" @source-change="loadCreateCapabilities" /><a-alert v-if="createError" class="create-kpi-error" type="error" show-icon :message="createError" /></a-modal>

    <a-modal v-model:open="targetOpen" :title="editingTarget ? '编辑 KPI 目标' : '创建 KPI 目标'" :confirm-loading="targetSaving" @ok="saveTarget"><a-form layout="vertical"><a-form-item label="考核对象" required><a-select v-model:value="targetForm.subjectId" class="target-subject-select" show-search :loading="subjectLoading" :disabled="Boolean(editingTarget)" :options="subjectOptions" option-filter-prop="label" placeholder="请选择当前有效对象" /></a-form-item><a-form-item label="周期开始" required><a-input v-model:value="targetForm.periodStart" type="date" :disabled="Boolean(editingTarget)" /><small>月度为每月 1 日，季度为 1/4/7/10 月 1 日，年度为 1 月 1 日。</small></a-form-item><a-form-item label="目标值" required><a-input v-model:value="targetForm.targetValue" class="target-value-input" inputmode="decimal" placeholder="非负十进制字符串" /></a-form-item></a-form><a-alert v-if="targetError" type="error" show-icon :message="targetError" /></a-modal>

    <a-drawer v-model:open="historyOpen" title="KPI 计算历史" width="760"><header v-if="historyTarget" class="history-heading"><strong>{{ historyTarget.subjectName }} · {{ historyTarget.periodStart }}</strong><span>目标 {{ historyTarget.targetValue }}</span></header><a-spin :spinning="Boolean(historyLoadingId)"><div v-if="history.length" class="history-list"><section v-for="item in history" :key="item.id"><KpiCalculationExplanation :calculation="item" /></section></div><a-empty v-else description="暂无计算历史" /></a-spin></a-drawer>
  </section>
</template>

<style scoped>
.kpi-admin-page{display:grid;min-width:0;gap:16px}.kpi-page-heading,.kpi-page-heading h1,.heading-actions,.kpi-actions{display:flex;align-items:center}.kpi-page-heading{justify-content:space-between;gap:20px}.kpi-page-heading h1{gap:9px;margin:0}.kpi-page-heading p{margin:5px 0 0;color:#697781}.heading-actions,.kpi-actions{gap:8px}.page-error{margin:0}.kpi-mobile-gate{min-width:0;padding:4px 0 28px}.kpi-mobile-gate .ant-empty{padding:30px 12px;border:1px solid #dce3e7;border-radius:8px;background:#fff}.kpi-studio{display:grid;grid-template-columns:280px minmax(0,1fr);min-width:0;min-height:760px;border:1px solid #dce3e7;border-radius:10px;overflow:hidden;background:#fff}.kpi-list{min-width:0;border-right:1px solid #dce3e7;background:#f7f9fa}.kpi-list>header{display:flex;justify-content:space-between;padding:16px;border-bottom:1px solid #dce3e7}.kpi-list>button{width:100%;display:grid;grid-template-columns:24px minmax(0,1fr) auto;gap:9px;align-items:center;padding:13px;border:0;border-bottom:1px solid #e4e9ec;background:transparent;text-align:left;cursor:pointer}.kpi-list>button.active{background:#e8f2f7;box-shadow:inset 3px 0 #24638c}.kpi-list button>span{display:flex;min-width:0;flex-direction:column;gap:3px}.kpi-list small,.version-section small{display:block;color:#74818a}.kpi-editor{min-width:0}.kpi-editor.loading{opacity:.7}.kpi-editor-empty{min-height:520px;display:grid;place-content:center;justify-items:center;gap:8px;color:#74818a}.kpi-toolbar{position:sticky;top:0;z-index:3;display:flex;justify-content:space-between;align-items:center;gap:16px;padding:14px 17px;border-bottom:1px solid #dce3e7;background:rgba(255,255,255,.97)}.kpi-toolbar h2,.kpi-toolbar p{margin:0}.kpi-toolbar p{margin-top:4px;color:#6c7a83;font-size:12px}.kpi-editor-content{min-width:0;padding:15px;background:#f4f7f8}.kpi-card{min-width:0;margin-bottom:14px;padding:16px;border:1px solid #dce3e7;border-radius:9px;background:#fff}.card-heading{display:flex;justify-content:space-between;align-items:center;gap:16px;margin-bottom:13px}.card-heading p{margin:3px 0 0;color:#697781;font-size:12px}.client-issues,.check-result ul{margin:12px 0 0;padding:0;list-style:none}.client-issues li{display:flex;gap:9px;padding:6px 0;color:#a33d3d}.check-result{border-left:4px solid #2b8550}.check-result.blocked{border-left-color:#b43d3d}.check-result li{display:grid;grid-template-columns:auto auto minmax(100px,.5fr) minmax(0,1fr);gap:9px;align-items:center;padding:8px 0;border-top:1px solid #e3e8ea}.version-section{overflow-x:auto}.version-section table{min-width:820px;width:100%;border-collapse:collapse}.version-section th,.version-section td{padding:9px;border:1px solid #dfe6e9;text-align:left}.version-section th{background:#f5f7f8;color:#52616b;font-size:12px}.history-heading{display:flex;justify-content:space-between;margin-bottom:14px}.history-list{display:grid;gap:14px}.history-list>section{padding:14px;border:1px solid #dce5e9;border-radius:8px}@media(max-width:1100px){.kpi-studio{grid-template-columns:220px minmax(0,1fr)}.kpi-toolbar{align-items:flex-start;flex-direction:column}.kpi-actions{width:100%;flex-wrap:wrap}}
</style>
