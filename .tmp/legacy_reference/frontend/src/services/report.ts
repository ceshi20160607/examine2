import { apiDownload, apiRequest } from './api'
import type {
  CreateReportInput,
  ReportCheckResult,
  ReportDetail,
  ReportExportTask,
  ReportExportTaskPage,
  ReportPublishResult,
  ReportSchedule,
  ReportSchedulePage,
  ReportScheduledRun,
  ReportScheduledRunPage,
  ReportSchedulePreview,
  ReportSchedulePreviewInput,
  ReportSummary,
  ReportVersion,
  RuntimeReportMetadata,
  RuntimeReportRows,
  SaveReportDraftInput,
  CreateReportScheduleInput,
  UpdateReportScheduleInput,
} from '@/types/report'

const adminBase = (systemId: string) =>
  `/api/v1/systems/${encodeURIComponent(systemId)}/admin/reports`
const runtimeBase = (systemId: string) =>
  `/api/v1/systems/${encodeURIComponent(systemId)}/reports`
const reportPath = (systemId: string, reportId: string) =>
  `${adminBase(systemId)}/${encodeURIComponent(reportId)}`
const exportBase = (systemId: string, code: string) =>
  `${runtimeBase(systemId)}/${encodeURIComponent(code)}/exports`
const scheduleBase = (systemId: string, reportId: string) =>
  `${reportPath(systemId, reportId)}/schedules`
const scheduledRunBase = (systemId: string, code: string) =>
  `${runtimeBase(systemId)}/${encodeURIComponent(code)}/scheduled-runs`

function items<T>(value: T[] | { items?: T[]; content?: T[] }) {
  return Array.isArray(value) ? value : value.items ?? value.content ?? []
}

export const reportAdminApi = {
  async list(systemId: string) {
    return items(await apiRequest<ReportSummary[] | { items?: ReportSummary[]; content?: ReportSummary[] }>(adminBase(systemId)))
  },
  create(systemId: string, input: CreateReportInput) {
    return apiRequest<ReportDetail>(adminBase(systemId), {
      method: 'POST', body: input, idempotencyKey: crypto.randomUUID(),
    })
  },
  detail(systemId: string, reportId: string) {
    return apiRequest<ReportDetail>(reportPath(systemId, reportId))
  },
  saveDraft(systemId: string, reportId: string, input: SaveReportDraftInput) {
    return apiRequest<ReportDetail>(`${reportPath(systemId, reportId)}/draft`, {
      method: 'PUT', body: input,
    })
  },
  checkDraft(systemId: string, reportId: string) {
    return apiRequest<ReportCheckResult>(`${reportPath(systemId, reportId)}/draft:check`, {
      method: 'POST',
    })
  },
  publishDraft(systemId: string, reportId: string, expectedVersion: number) {
    return apiRequest<ReportPublishResult>(`${reportPath(systemId, reportId)}/draft:publish`, {
      method: 'POST', body: { expectedVersion }, idempotencyKey: crypto.randomUUID(),
    })
  },
  versions(systemId: string, reportId: string) {
    return apiRequest<ReportVersion[]>(`${reportPath(systemId, reportId)}/versions`)
  },
  version(systemId: string, reportId: string, versionNumber: number) {
    return apiRequest<ReportVersion>(
      `${reportPath(systemId, reportId)}/versions/${encodeURIComponent(String(versionNumber))}`,
    )
  },
  async schedules(systemId: string, reportId: string, page = 1, size = 100) {
    const query = new URLSearchParams({ page: String(page), size: String(size) })
    const result = await apiRequest<ReportSchedulePage | ReportSchedule[]>(
      `${scheduleBase(systemId, reportId)}?${query}`,
    )
    return Array.isArray(result) ? result : result.items ?? []
  },
  createSchedule(systemId: string, reportId: string, input: CreateReportScheduleInput) {
    return apiRequest<ReportSchedule>(scheduleBase(systemId, reportId), {
      method: 'POST', body: input, idempotencyKey: crypto.randomUUID(),
    })
  },
  schedule(systemId: string, reportId: string, scheduleId: string) {
    return apiRequest<ReportSchedule>(
      `${scheduleBase(systemId, reportId)}/${encodeURIComponent(scheduleId)}`,
    )
  },
  updateSchedule(systemId: string, reportId: string, scheduleId: string, input: UpdateReportScheduleInput) {
    return apiRequest<ReportSchedule>(
      `${scheduleBase(systemId, reportId)}/${encodeURIComponent(scheduleId)}`,
      { method: 'PUT', body: input },
    )
  },
  setScheduleEnabled(
    systemId: string,
    reportId: string,
    scheduleId: string,
    enabled: boolean,
    expectedVersion: number,
  ) {
    return apiRequest<ReportSchedule>(
      `${scheduleBase(systemId, reportId)}/${encodeURIComponent(scheduleId)}:${enabled ? 'enable' : 'disable'}`,
      { method: 'POST', body: { expectedVersion }, idempotencyKey: crypto.randomUUID() },
    )
  },
  previewSchedule(systemId: string, reportId: string, input: ReportSchedulePreviewInput) {
    return apiRequest<ReportSchedulePreview>(`${scheduleBase(systemId, reportId)}/next-fire:preview`, {
      method: 'POST', body: input,
    })
  },
}

export const runtimeReportApi = {
  async list(systemId: string) {
    return items(await apiRequest<RuntimeReportMetadata[] | { items?: RuntimeReportMetadata[]; content?: RuntimeReportMetadata[] }>(runtimeBase(systemId)))
  },
  metadata(systemId: string, code: string) {
    return apiRequest<RuntimeReportMetadata>(
      `${runtimeBase(systemId)}/${encodeURIComponent(code)}`,
    )
  },
  async rows(systemId: string, code: string, page = 1, size = 20) {
    const query = new URLSearchParams({ page: String(page), size: String(size) })
    const result = await apiRequest<RuntimeReportRows>(
      `${runtimeBase(systemId)}/${encodeURIComponent(code)}/rows?${query}`,
    )
    return {
      ...result,
      rows: result.rows ?? result.items ?? [],
      items: result.items ?? result.rows ?? [],
      page: result.page || page,
      size: result.size || size,
      total: result.total ?? 0,
      queryHash: result.queryHash ?? null,
    }
  },
  startExport(systemId: string, code: string, requestKey: string = crypto.randomUUID()) {
    return apiRequest<ReportExportTask>(exportBase(systemId, code), {
      method: 'POST', body: { requestKey }, idempotencyKey: requestKey,
    })
  },
  exports(systemId: string, code: string, page = 1, size = 20) {
    const query = new URLSearchParams({ page: String(page), size: String(size) })
    return apiRequest<ReportExportTaskPage>(`${exportBase(systemId, code)}?${query}`)
  },
  exportTask(systemId: string, code: string, exportId: string) {
    return apiRequest<ReportExportTask>(
      `${exportBase(systemId, code)}/${encodeURIComponent(exportId)}`,
    )
  },
  downloadExport(systemId: string, code: string, exportId: string) {
    return apiDownload(
      `${exportBase(systemId, code)}/${encodeURIComponent(exportId)}/result.xlsx`,
    )
  },
  scheduledRuns(systemId: string, code: string, page = 1, size = 20) {
    const query = new URLSearchParams({ page: String(page), size: String(size) })
    return apiRequest<ReportScheduledRunPage>(`${scheduledRunBase(systemId, code)}?${query}`)
  },
  scheduledRun(systemId: string, code: string, occurrenceId: string) {
    return apiRequest<ReportScheduledRun>(
      `${scheduledRunBase(systemId, code)}/${encodeURIComponent(occurrenceId)}`,
    )
  },
  downloadScheduledRun(systemId: string, code: string, occurrenceId: string) {
    return apiDownload(
      `${scheduledRunBase(systemId, code)}/${encodeURIComponent(occurrenceId)}/result.xlsx`,
    )
  },
}
