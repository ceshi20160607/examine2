import { apiRequest } from '@/services/api'
import type { PlatformDailyReport, PlatformDailyReportInput, PlatformProjectInput, PlatformWorkOverview, PlatformWorkProject, PlatformWorkTask, PlatformWorkTaskInput, PlatformWorkTaskPage, PlatformWorkTaskStatus } from '@/types/platformWork'

const root = '/api/v1/platform/work'
export const platformWorkApi = {
  overview: () => apiRequest<PlatformWorkOverview>(`${root}/overview`),
  projects: () => apiRequest<PlatformWorkProject[]>(`${root}/projects`),
  createProject: (input: PlatformProjectInput) => apiRequest<PlatformWorkProject>(`${root}/projects`, { method: 'POST', body: input }),
  tasks(query: { kind?: 'PROJECT' | 'GENERAL'; status?: 'ALL' | PlatformWorkTaskStatus; page?: number; size?: number } = {}) {
    const params = new URLSearchParams({ status: query.status ?? 'ALL', page: String(query.page ?? 1), size: String(query.size ?? 100) })
    if (query.kind) params.set('kind', query.kind)
    return apiRequest<PlatformWorkTaskPage>(`${root}/tasks?${params}`)
  },
  createTask: (input: PlatformWorkTaskInput) => apiRequest<PlatformWorkTask>(`${root}/tasks`, { method: 'POST', body: input, idempotencyKey: crypto.randomUUID() }),
  reports: () => apiRequest<PlatformDailyReport[]>(`${root}/reports`),
  saveReport: (input: PlatformDailyReportInput) => apiRequest<PlatformDailyReport>(`${root}/reports`, { method: 'POST', body: input }),
  submitReport: (id: string, version: number) => apiRequest<PlatformDailyReport>(`${root}/reports/${encodeURIComponent(id)}:submit`, { method: 'POST', body: { version } }),
}
