import { apiRequest } from './api'
import type {
  AssignWorkTaskInput,
  AddWorkProjectMemberInput,
  CreateWorkProjectInput,
  CreateWorkDailyReportInput,
  CreateWorkTaskInput,
  UpdateWorkProjectInput,
  UpdateWorkProjectMemberInput,
  UpdateWorkDailyReportInput,
  UpdateWorkTaskInput,
  WorkProject,
  WorkProjectListQuery,
  WorkProjectMember,
  WorkProjectPage,
  WorkDailyReport,
  WorkDailyReportListQuery,
  WorkDailyReportPage,
  WorkDailyReportSummary,
  WorkTask,
  WorkTaskListQuery,
  WorkTaskPage,
  WorkVersionInput,
  WorkConfigurationSnapshot,
  WorkConfigurationVersion,
  WorkObjectType,
  WorkPublishCheck,
  WorkRuntimeConfiguration,
} from '@/types/work'

function taskRoot(systemId: string) {
  return `/api/v1/systems/${encodeURIComponent(systemId)}/work/tasks`
}

function projectRoot(systemId: string) {
  return `/api/v1/systems/${encodeURIComponent(systemId)}/work/projects`
}

function reportRoot(systemId: string) {
  return `/api/v1/systems/${encodeURIComponent(systemId)}/work/reports`
}

function configurationRoot(systemId: string) {
  return `/api/v1/systems/${encodeURIComponent(systemId)}/work/configuration`
}

export const workApi = {
  workConfigurationHistory(systemId: string) {
    return apiRequest<{ items: WorkConfigurationVersion[] }>(
      `${configurationRoot(systemId)}/history`,
    )
  },
  activeWorkConfiguration(systemId: string) {
    return apiRequest<WorkConfigurationVersion>(
      `${configurationRoot(systemId)}/active`,
    )
  },
  createWorkConfigurationDraft(systemId: string, snapshot: WorkConfigurationSnapshot) {
    return apiRequest<WorkConfigurationVersion>(`${configurationRoot(systemId)}/drafts`, {
      method: 'POST',
      body: { snapshot },
      idempotencyKey: crypto.randomUUID(),
    })
  },
  checkWorkConfiguration(systemId: string, configurationId: string, version: number) {
    return apiRequest<WorkPublishCheck>(
      `${configurationRoot(systemId)}/drafts/${encodeURIComponent(configurationId)}:check`,
      { method: 'POST', body: { version }, idempotencyKey: crypto.randomUUID() },
    )
  },
  publishWorkConfiguration(systemId: string, configurationId: string, version: number) {
    return apiRequest<WorkConfigurationVersion>(
      `${configurationRoot(systemId)}/drafts/${encodeURIComponent(configurationId)}:publish`,
      { method: 'POST', body: { version }, idempotencyKey: crypto.randomUUID() },
    )
  },
  rollbackWorkConfiguration(systemId: string, targetRevision: number) {
    return apiRequest<WorkConfigurationVersion>(`${configurationRoot(systemId)}:rollback`, {
      method: 'POST',
      body: { targetRevision },
      idempotencyKey: crypto.randomUUID(),
    })
  },
  workRuntimeConfiguration(systemId: string, objectType: WorkObjectType) {
    return apiRequest<WorkRuntimeConfiguration>(
      `${configurationRoot(systemId)}/runtime/${objectType}`,
    )
  },
  list(systemId: string, query: WorkTaskListQuery = {}) {
    const params = new URLSearchParams({
      keyword: query.keyword?.trim() ?? '',
      status: query.status ?? 'ALL',
      role: query.role ?? 'PARTICIPATING',
      page: String(query.page ?? 1),
      size: String(query.size ?? 20),
    })
    if (query.projectId) params.set('projectId', query.projectId)
    if (query.dueFrom) params.set('dueFrom', query.dueFrom)
    if (query.dueTo) params.set('dueTo', query.dueTo)
    if (query.dueBefore) params.set('dueBefore', query.dueBefore)
    if (query.createdFrom) params.set('createdFrom', query.createdFrom)
    if (query.createdBefore) params.set('createdBefore', query.createdBefore)
    if (query.updatedFrom) params.set('updatedFrom', query.updatedFrom)
    if (query.updatedBefore) params.set('updatedBefore', query.updatedBefore)
    if (query.assigneeMemberId) params.set('assigneeMemberId', query.assigneeMemberId)
    return apiRequest<WorkTaskPage>(`${taskRoot(systemId)}?${params.toString()}`)
  },
  create(systemId: string, input: CreateWorkTaskInput) {
    return apiRequest<WorkTask>(taskRoot(systemId), {
      method: 'POST',
      body: input,
      idempotencyKey: crypto.randomUUID(),
    })
  },
  task(systemId: string, taskId: string) {
    return apiRequest<WorkTask>(
      `${taskRoot(systemId)}/${encodeURIComponent(taskId)}`,
    )
  },
  update(systemId: string, taskId: string, input: UpdateWorkTaskInput) {
    return apiRequest<WorkTask>(
      `${taskRoot(systemId)}/${encodeURIComponent(taskId)}`,
      {
        method: 'PUT',
        body: input,
        idempotencyKey: crypto.randomUUID(),
      },
    )
  },
  retryTaskReminder(systemId: string, taskId: string, input: WorkVersionInput) {
    return apiRequest<WorkTask>(
      `${taskRoot(systemId)}/${encodeURIComponent(taskId)}/reminder:retry`,
      {
        method: 'POST',
        body: input,
        idempotencyKey: crypto.randomUUID(),
      },
    )
  },
  assign(systemId: string, taskId: string, input: AssignWorkTaskInput) {
    return apiRequest<WorkTask>(`${taskRoot(systemId)}/${encodeURIComponent(taskId)}:assign`, {
      method: 'POST',
      body: input,
      idempotencyKey: crypto.randomUUID(),
    })
  },
  complete(systemId: string, taskId: string) {
    return apiRequest<WorkTask>(`${taskRoot(systemId)}/${encodeURIComponent(taskId)}:complete`, {
      method: 'POST',
      idempotencyKey: crypto.randomUUID(),
    })
  },
  reopen(systemId: string, taskId: string) {
    return apiRequest<WorkTask>(`${taskRoot(systemId)}/${encodeURIComponent(taskId)}:reopen`, {
      method: 'POST',
      idempotencyKey: crypto.randomUUID(),
    })
  },
  listProjects(systemId: string, query: WorkProjectListQuery = {}) {
    const params = new URLSearchParams({
      keyword: query.keyword?.trim() ?? '',
      status: query.status ?? 'ALL',
      page: String(query.page ?? 1),
      size: String(query.size ?? 20),
    })
    return apiRequest<WorkProjectPage>(`${projectRoot(systemId)}?${params.toString()}`)
  },
  createProject(systemId: string, input: CreateWorkProjectInput) {
    return apiRequest<WorkProject>(projectRoot(systemId), {
      method: 'POST',
      body: input,
      idempotencyKey: crypto.randomUUID(),
    })
  },
  project(systemId: string, projectId: string) {
    return apiRequest<WorkProject>(
      `${projectRoot(systemId)}/${encodeURIComponent(projectId)}`,
    )
  },
  updateProject(systemId: string, projectId: string, input: UpdateWorkProjectInput) {
    return apiRequest<WorkProject>(
      `${projectRoot(systemId)}/${encodeURIComponent(projectId)}`,
      {
        method: 'PUT',
        body: input,
        idempotencyKey: crypto.randomUUID(),
      },
    )
  },
  archiveProject(systemId: string, projectId: string, input: WorkVersionInput) {
    return apiRequest<WorkProject>(
      `${projectRoot(systemId)}/${encodeURIComponent(projectId)}:archive`,
      {
        method: 'POST',
        body: input,
        idempotencyKey: crypto.randomUUID(),
      },
    )
  },
  reopenProject(systemId: string, projectId: string, input: WorkVersionInput) {
    return apiRequest<WorkProject>(
      `${projectRoot(systemId)}/${encodeURIComponent(projectId)}:reopen`,
      {
        method: 'POST',
        body: input,
        idempotencyKey: crypto.randomUUID(),
      },
    )
  },
  listProjectMembers(systemId: string, projectId: string) {
    return apiRequest<WorkProjectMember[]>(
      `${projectRoot(systemId)}/${encodeURIComponent(projectId)}/members`,
    )
  },
  addProjectMember(
    systemId: string,
    projectId: string,
    input: AddWorkProjectMemberInput,
  ) {
    return apiRequest<WorkProjectMember>(
      `${projectRoot(systemId)}/${encodeURIComponent(projectId)}/members`,
      {
        method: 'POST',
        body: input,
        idempotencyKey: crypto.randomUUID(),
      },
    )
  },
  updateProjectMember(
    systemId: string,
    projectId: string,
    memberId: string,
    input: UpdateWorkProjectMemberInput,
  ) {
    return apiRequest<WorkProjectMember>(
      `${projectRoot(systemId)}/${encodeURIComponent(projectId)}`
        + `/members/${encodeURIComponent(memberId)}`,
      {
        method: 'PUT',
        body: input,
        idempotencyKey: crypto.randomUUID(),
      },
    )
  },
  removeProjectMember(
    systemId: string,
    projectId: string,
    memberId: string,
    input: WorkVersionInput,
  ) {
    return apiRequest<WorkProjectMember>(
      `${projectRoot(systemId)}/${encodeURIComponent(projectId)}`
        + `/members/${encodeURIComponent(memberId)}:remove`,
      {
        method: 'POST',
        body: input,
        idempotencyKey: crypto.randomUUID(),
      },
    )
  },
  listReports(systemId: string, query: WorkDailyReportListQuery = {}) {
    const params = new URLSearchParams({
      scope: query.scope ?? 'SELF',
      status: query.status ?? 'ALL',
      page: String(query.page ?? 1),
      size: String(query.size ?? 20),
    })
    if (query.memberId) params.set('memberId', query.memberId)
    if (query.dateFrom) params.set('dateFrom', query.dateFrom)
    if (query.dateTo) params.set('dateTo', query.dateTo)
    return apiRequest<WorkDailyReportPage>(`${reportRoot(systemId)}?${params.toString()}`)
  },
  createReport(systemId: string, input: CreateWorkDailyReportInput) {
    return apiRequest<WorkDailyReport>(reportRoot(systemId), {
      method: 'POST',
      body: input,
      idempotencyKey: crypto.randomUUID(),
    })
  },
  report(systemId: string, reportId: string) {
    return apiRequest<WorkDailyReport>(
      `${reportRoot(systemId)}/${encodeURIComponent(reportId)}`,
    )
  },
  updateReport(systemId: string, reportId: string, input: UpdateWorkDailyReportInput) {
    return apiRequest<WorkDailyReport>(
      `${reportRoot(systemId)}/${encodeURIComponent(reportId)}`,
      {
        method: 'PUT',
        body: input,
        idempotencyKey: crypto.randomUUID(),
      },
    )
  },
  submitReport(systemId: string, reportId: string, input: WorkVersionInput) {
    return apiRequest<WorkDailyReport>(
      `${reportRoot(systemId)}/${encodeURIComponent(reportId)}:submit`,
      {
        method: 'POST',
        body: input,
        idempotencyKey: crypto.randomUUID(),
      },
    )
  },
  reopenReport(systemId: string, reportId: string, input: WorkVersionInput) {
    return apiRequest<WorkDailyReport>(
      `${reportRoot(systemId)}/${encodeURIComponent(reportId)}:reopen`,
      {
        method: 'POST',
        body: input,
        idempotencyKey: crypto.randomUUID(),
      },
    )
  },
  reportSummary(systemId: string, endDate: string, memberId?: string) {
    const params = new URLSearchParams({ endDate })
    if (memberId) params.set('memberId', memberId)
    return apiRequest<WorkDailyReportSummary>(
      `${reportRoot(systemId)}/summary?${params.toString()}`,
    )
  },
}
