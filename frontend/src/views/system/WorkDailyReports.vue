<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import MemberPicker from '@/components/runtime/MemberPicker.vue'
import { ApiRequestError } from '@/services/api'
import { workApi } from '@/services/work'
import { useSessionStore } from '@/stores/session'
import type {
  WorkDailyReport,
  WorkDailyReportScope,
  WorkDailyReportStatusFilter,
  WorkDailyReportSummary,
} from '@/types/work'

const props = defineProps<{
  systemId: string
  tenantId: string
}>()

const session = useSessionStore()
const reports = ref<WorkDailyReport[]>([])
const reportTotal = ref(0)
const reportPage = ref(1)
const reportPageSize = 20
const reportScope = ref<WorkDailyReportScope>('SELF')
const reportMemberId = ref('')
const reportStatus = ref<WorkDailyReportStatusFilter>('ALL')
const selectedDate = ref(localDate(new Date()))
const dateFrom = ref(localDate(addDays(new Date(), -6)))
const dateTo = ref(localDate(new Date()))
const selectedReport = ref<WorkDailyReport | null>(null)
const completedWork = ref('')
const plannedWork = ref('')
const blockers = ref('')
const loading = ref(false)
const summaryLoading = ref(false)
const mutation = ref('')
const error = ref('')
const summaryError = ref('')
const summary = ref<WorkDailyReportSummary | null>(null)
let loadGeneration = 0
let summaryGeneration = 0

const currentMemberId = computed(() => session.context?.memberId ?? '')
const canManage = computed(() => session.hasPermission('work.report.manage'))
const canCreate = computed(() => session.hasPermission('work.report.create'))
const canAccess = computed(() => (
  session.hasPermission('work.report.access') || canCreate.value || canManage.value
))
const editingOwnReport = computed(() => (
  !selectedReport.value
    ? reportScope.value === 'SELF'
    : selectedReport.value.authorMemberId === currentMemberId.value
))
const canEditDraft = computed(() => (
  canCreate.value
  && editingOwnReport.value
  && selectedReport.value?.status !== 'SUBMITTED'
  && selectedDate.value <= localDate(new Date())
))

function addDays(value: Date, days: number) {
  const next = new Date(value)
  next.setDate(next.getDate() + days)
  return next
}

function localDate(value: Date) {
  const local = new Date(value.valueOf() - value.getTimezoneOffset() * 60_000)
  return local.toISOString().slice(0, 10)
}

function time(value: string | null) {
  if (!value) return '—'
  const parsed = new Date(value)
  return Number.isNaN(parsed.valueOf()) ? value : parsed.toLocaleString('zh-CN')
}

function message(cause: unknown) {
  if (cause instanceof ApiRequestError) return cause.message || cause.code
  return cause instanceof Error ? cause.message : '日报请求失败，请稍后重试'
}

function hydrateEditor(report: WorkDailyReport | null) {
  selectedReport.value = report
  completedWork.value = report?.completedWork ?? ''
  plannedWork.value = report?.plannedWork ?? ''
  blockers.value = report?.blockers ?? ''
}

function reportForSelectedDate(items = reports.value) {
  const authorId = reportScope.value === 'ALL'
    ? reportMemberId.value
    : currentMemberId.value
  return items.find(report => (
    report.workDate === selectedDate.value
    && (!authorId || report.authorMemberId === authorId)
  )) ?? null
}

async function loadReports() {
  if (!canAccess.value) {
    reports.value = []
    reportTotal.value = 0
    return
  }
  const generation = ++loadGeneration
  loading.value = true
  error.value = ''
  try {
    const result = await workApi.listReports(props.systemId, {
      scope: canManage.value ? reportScope.value : 'SELF',
      ...(canManage.value && reportScope.value === 'ALL' && reportMemberId.value
        ? { memberId: reportMemberId.value }
        : {}),
      ...(dateFrom.value ? { dateFrom: dateFrom.value } : {}),
      ...(dateTo.value ? { dateTo: dateTo.value } : {}),
      status: reportStatus.value,
      page: reportPage.value,
      size: reportPageSize,
    })
    if (generation !== loadGeneration) return
    reports.value = result.items
    reportTotal.value = result.total
    const matchingSelected = selectedReport.value
      ? result.items.find(report => report.id === selectedReport.value?.id)
      : reportForSelectedDate(result.items)
    hydrateEditor(matchingSelected ?? reportForSelectedDate(result.items))
  } catch (cause) {
    if (generation !== loadGeneration) return
    error.value = message(cause)
  } finally {
    if (generation === loadGeneration) loading.value = false
  }
}

async function loadSummary() {
  if (!canAccess.value) {
    summary.value = null
    return
  }
  const generation = ++summaryGeneration
  summaryLoading.value = true
  summaryError.value = ''
  try {
    const result = await workApi.reportSummary(
      props.systemId,
      selectedDate.value,
      canManage.value && reportScope.value === 'ALL' && reportMemberId.value
        ? reportMemberId.value
        : undefined,
    )
    if (generation !== summaryGeneration) return
    summary.value = result
  } catch (cause) {
    if (generation !== summaryGeneration) return
    summaryError.value = message(cause)
  } finally {
    if (generation === summaryGeneration) summaryLoading.value = false
  }
}

async function selectReport(report: WorkDailyReport) {
  loading.value = true
  error.value = ''
  try {
    const detail = await workApi.report(props.systemId, report.id)
    selectedDate.value = detail.workDate
    hydrateEditor(detail)
  } catch (cause) {
    error.value = message(cause)
  } finally {
    loading.value = false
  }
}

function changeSelectedDate() {
  hydrateEditor(reportForSelectedDate())
}

function validNarratives() {
  return Boolean(completedWork.value.trim() && plannedWork.value.trim())
    && completedWork.value.length <= 4000
    && plannedWork.value.length <= 4000
    && blockers.value.length <= 4000
    && Boolean(selectedDate.value)
    && selectedDate.value <= localDate(new Date())
}

async function saveDraft() {
  if (!canEditDraft.value || !validNarratives()) return
  mutation.value = 'report:save'
  error.value = ''
  try {
    const current = selectedReport.value
    const saved = current
      ? await workApi.updateReport(props.systemId, current.id, {
          completedWork: completedWork.value.trim(),
          plannedWork: plannedWork.value.trim(),
          blockers: blockers.value.trim() || null,
          version: current.version,
        })
      : await workApi.createReport(props.systemId, {
          workDate: selectedDate.value,
          completedWork: completedWork.value.trim(),
          plannedWork: plannedWork.value.trim(),
          blockers: blockers.value.trim() || null,
        })
    hydrateEditor(saved)
    await Promise.all([loadReports(), loadSummary()])
  } catch (cause) {
    error.value = message(cause)
  } finally {
    mutation.value = ''
  }
}

async function submitReport() {
  const report = selectedReport.value
  if (!report || !canEditDraft.value || report.status !== 'DRAFT') return
  mutation.value = `report:submit:${report.id}`
  error.value = ''
  try {
    hydrateEditor(await workApi.submitReport(props.systemId, report.id, {
      version: report.version,
    }))
    await Promise.all([loadReports(), loadSummary()])
  } catch (cause) {
    error.value = message(cause)
  } finally {
    mutation.value = ''
  }
}

async function reopenReport() {
  const report = selectedReport.value
  if (!report || !canManage.value || report.status !== 'SUBMITTED') return
  mutation.value = `report:reopen:${report.id}`
  error.value = ''
  try {
    hydrateEditor(await workApi.reopenReport(props.systemId, report.id, {
      version: report.version,
    }))
    await Promise.all([loadReports(), loadSummary()])
  } catch (cause) {
    error.value = message(cause)
  } finally {
    mutation.value = ''
  }
}

async function openSummaryDay(reportId?: string | null) {
  if (!reportId) return
  const report = reports.value.find(item => item.id === reportId)
  if (report) await selectReport(report)
  else {
    loading.value = true
    try {
      const detail = await workApi.report(props.systemId, reportId)
      selectedDate.value = detail.workDate
      hydrateEditor(detail)
    } catch (cause) {
      error.value = message(cause)
    } finally {
      loading.value = false
    }
  }
}

function resetState() {
  reports.value = []
  reportTotal.value = 0
  reportPage.value = 1
  reportScope.value = 'SELF'
  reportMemberId.value = ''
  reportStatus.value = 'ALL'
  selectedDate.value = localDate(new Date())
  dateFrom.value = localDate(addDays(new Date(), -6))
  dateTo.value = localDate(new Date())
  hydrateEditor(null)
  error.value = ''
  summaryError.value = ''
  summary.value = null
}

watch(() => [props.systemId, props.tenantId], resetState, { flush: 'sync' })
watch(
  [
    () => props.systemId,
    () => props.tenantId,
    canAccess,
    canManage,
    reportScope,
    reportMemberId,
    dateFrom,
    dateTo,
    reportStatus,
    reportPage,
  ],
  () => void loadReports(),
  { immediate: true },
)
watch(
  [
    () => props.systemId,
    () => props.tenantId,
    canAccess,
    reportScope,
    reportMemberId,
    selectedDate,
  ],
  () => void loadSummary(),
  { immediate: true },
)
watch(canManage, (allowed) => {
  if (!allowed && reportScope.value === 'ALL') reportScope.value = 'SELF'
})
</script>

<template>
  <section class="work-daily-reports">
    <header class="work-report-heading">
      <div>
        <h2>工作日报</h2>
        <p>每天一份事实；提交后由经理重开才能再次编辑。</p>
      </div>
      <a-button class="work-report-refresh" :loading="loading" @click="loadReports">
        刷新日报
      </a-button>
    </header>

    <a-alert
      v-if="!canAccess"
      class="work-report-denied"
      type="warning"
      show-icon
      message="当前成员没有日报访问权限"
    />
    <template v-else>
      <a-alert
        v-if="error"
        class="work-report-error"
        type="error"
        show-icon
        closable
        :message="error"
        @close="error = ''"
      />
      <div class="work-report-filters">
        <select
          v-if="canManage"
          v-model="reportScope"
          class="work-report-scope"
          @change="reportPage = 1"
        >
          <option value="SELF">我的日报</option>
          <option value="ALL">团队日报</option>
        </select>
        <MemberPicker
          v-if="canManage && reportScope === 'ALL'"
          v-model:value="reportMemberId"
          class="work-report-member-filter"
          :system-id="systemId"
          placeholder="可选：筛选一个活跃成员"
        />
        <input v-model="dateFrom" class="work-report-date-from" type="date">
        <span>至</span>
        <input v-model="dateTo" class="work-report-date-to" type="date">
        <select v-model="reportStatus" class="work-report-status" @change="reportPage = 1">
          <option value="ALL">全部状态</option>
          <option value="DRAFT">草稿</option>
          <option value="SUBMITTED">已提交</option>
        </select>
      </div>

      <section class="work-report-summary">
        <header>
          <strong>七日状态</strong>
          <span v-if="summary">
            已提交 {{ summary.submittedCount }} · 草稿 {{ summary.draftCount }}
            · 缺失 {{ summary.missingCount }}
          </span>
        </header>
        <a-alert
          v-if="summaryError"
          type="error"
          show-icon
          :message="summaryError"
        />
        <a-spin :spinning="summaryLoading">
          <div v-if="summary?.days.length" class="work-report-summary-days">
            <button
              v-for="day in summary.days"
              :key="day.workDate"
              class="work-report-summary-day"
              :class="day.state.toLowerCase()"
              :data-date="day.workDate"
              :data-state="day.state"
              :disabled="!day.reportId"
              @click="openSummaryDay(day.reportId)"
            >
              <span>{{ day.workDate.slice(5) }}</span>
              <strong>{{ day.state }}</strong>
            </button>
          </div>
          <a-empty v-else-if="!summaryLoading" description="暂无七日状态" />
        </a-spin>
      </section>

      <div class="work-report-body">
        <a-spin :spinning="loading">
          <section class="work-report-list-panel">
            <h3>日报记录</h3>
            <a-empty v-if="!reports.length && !loading" description="当前筛选没有日报" />
            <article
              v-for="report in reports"
              v-else
              :key="report.id"
              class="work-report-list-item"
              :class="{ selected: selectedReport?.id === report.id }"
              :data-report-id="report.id"
              :data-report-version="report.version"
            >
              <div>
                <strong>{{ report.workDate }} · 成员 {{ report.authorMemberId }}</strong>
                <a-tag :color="report.status === 'SUBMITTED' ? 'green' : 'orange'">
                  {{ report.status }}
                </a-tag>
                <p>更新于 {{ time(report.updatedAt) }} · v{{ report.version }}</p>
              </div>
              <a-button class="work-report-select" @click="selectReport(report)">
                查看
              </a-button>
            </article>
            <a-pagination
              v-if="reportTotal > reportPageSize"
              class="work-report-pagination"
              :current="reportPage"
              :page-size="reportPageSize"
              :total="reportTotal"
              :show-size-changer="false"
              @change="reportPage = $event"
            />
          </section>
        </a-spin>

        <section class="work-report-editor">
          <header>
            <div>
              <h3>日报事实</h3>
              <p v-if="selectedReport">
                报告 {{ selectedReport.id }} · {{ selectedReport.status }}
                · v{{ selectedReport.version }}
              </p>
              <p v-else>该日期尚未创建日报</p>
            </div>
            <input
              v-model="selectedDate"
              class="work-report-work-date"
              type="date"
              :max="localDate(new Date())"
              @change="changeSelectedDate"
            >
          </header>
          <label>
            已完成工作
            <textarea
              v-model="completedWork"
              class="work-report-completed"
              maxlength="4000"
              rows="7"
              :readonly="!canEditDraft"
            />
          </label>
          <label>
            计划工作
            <textarea
              v-model="plannedWork"
              class="work-report-planned"
              maxlength="4000"
              rows="7"
              :readonly="!canEditDraft"
            />
          </label>
          <label>
            阻塞事项（可选）
            <textarea
              v-model="blockers"
              class="work-report-blockers"
              maxlength="4000"
              rows="5"
              :readonly="!canEditDraft"
            />
          </label>
          <div class="work-report-actions">
            <a-button
              v-if="canEditDraft"
              class="work-report-save"
              :loading="mutation === 'report:save'"
              @click="saveDraft"
            >
              保存草稿
            </a-button>
            <a-button
              v-if="canEditDraft && selectedReport?.status === 'DRAFT'"
              class="work-report-submit"
              type="primary"
              :loading="mutation === `report:submit:${selectedReport.id}`"
              @click="submitReport"
            >
              提交日报
            </a-button>
            <a-button
              v-if="canManage && selectedReport?.status === 'SUBMITTED'"
              class="work-report-reopen"
              :loading="mutation === `report:reopen:${selectedReport.id}`"
              @click="reopenReport"
            >
              重新打开
            </a-button>
          </div>
          <a-alert
            v-if="selectedReport?.status === 'SUBMITTED' && !canManage"
            class="work-report-immutable"
            type="info"
            show-icon
            message="日报已提交，内容不可修改"
          />
        </section>
      </div>
    </template>
  </section>
</template>

<style scoped>
.work-daily-reports,
.work-report-summary,
.work-report-list-panel,
.work-report-editor {
  display: grid;
  gap: 14px;
}

.work-report-heading,
.work-report-summary > header,
.work-report-editor > header,
.work-report-list-item,
.work-report-actions,
.work-report-filters {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.work-report-heading h2,
.work-report-list-panel h3,
.work-report-editor h3 {
  margin: 0;
}

.work-report-filters {
  justify-content: flex-start;
}

.work-report-filters input,
.work-report-filters select,
.work-report-work-date {
  min-height: 32px;
  padding: 4px 9px;
  border: 1px solid #d9d9d9;
  border-radius: 6px;
  background: #fff;
}

.work-report-summary,
.work-report-list-panel,
.work-report-editor {
  padding: 16px;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  background: #fff;
}

.work-report-summary-days {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  gap: 8px;
}

.work-report-summary-day {
  display: grid;
  gap: 4px;
  padding: 9px;
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  background: #f8fafc;
}

.work-report-summary-day.submitted { border-color: #22c55e; }
.work-report-summary-day.draft { border-color: #f59e0b; }
.work-report-summary-day.missing { color: #94a3b8; }

.work-report-body {
  display: grid;
  grid-template-columns: minmax(280px, 0.8fr) minmax(440px, 1.2fr);
  gap: 16px;
  align-items: start;
}

.work-report-list-item {
  padding: 12px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
}

.work-report-list-item.selected {
  border-color: #2563eb;
}

.work-report-editor label {
  display: grid;
  gap: 6px;
  font-weight: 600;
}

.work-report-editor textarea {
  width: 100%;
  padding: 10px;
  border: 1px solid #d9d9d9;
  border-radius: 8px;
  resize: vertical;
  font: inherit;
  font-weight: 400;
}

.work-report-actions {
  justify-content: flex-start;
}

@media (max-width: 900px) {
  .work-report-body { grid-template-columns: minmax(0, 1fr); }
  .work-report-heading { align-items: flex-start; }
  .work-report-filters { flex-wrap: wrap; }
  .work-report-summary { overflow-x: auto; }
  .work-report-summary-days { min-width: 680px; }
}

@media (max-width: 560px) {
  .work-daily-reports { gap: 12px; }
  .work-report-heading,.work-report-summary>header,.work-report-editor>header,.work-report-list-item { align-items: stretch; flex-direction: column; }
  .work-report-filters { display: grid; grid-template-columns: minmax(0,1fr); }
  .work-report-filters input,.work-report-filters select { width: 100%; min-width: 0; }
  .work-report-summary,.work-report-list-panel,.work-report-editor { padding: 14px; }
  .work-report-list-item { gap: 8px; }
  .work-report-actions { flex-wrap: wrap; }
}
</style>
