export type WorkTaskStatus = 'OPEN' | 'COMPLETED'
export type WorkTaskStatusFilter = 'ALL' | WorkTaskStatus
export type WorkTaskViewMode = 'LIST' | 'KANBAN' | 'CALENDAR'
export type WorkObjectType = 'PROJECT_TASK' | 'ORDINARY_TASK' | 'DAILY_REPORT'
export type WorkFieldType = 'TEXT' | 'NUMBER' | 'BOOLEAN' | 'DATE' | 'DATETIME' | 'SELECT' | 'MULTI_SELECT'
export type WorkKanbanRole = 'NONE' | 'COLUMN' | 'GROUP' | 'NUMERIC'
export type WorkFieldValue = string | number | boolean | string[] | null

export interface WorkConfigurationField {
  code: string
  name: string
  type: WorkFieldType
  required: boolean
  dictionaryCode: string | null
  readPermission: string | null
  editPermission: string | null
  cardVisible: boolean
  kanbanRole: WorkKanbanRole
}

export interface WorkConfigurationSnapshot {
  fields: Record<WorkObjectType, WorkConfigurationField[]>
}

export interface WorkConfigurationVersion {
  id: string
  revision: number
  status: 'DRAFT' | 'PUBLISHED' | 'RETIRED'
  snapshot: WorkConfigurationSnapshot
  rollbackFromRevision: number | null
  createdBy: string
  createdAt: string
  publishedBy: string | null
  publishedAt: string | null
  version: number
}

export interface WorkPublishCheck {
  revision: number
  ready: boolean
  checkedBy: number
  checkedAt: string
}

export interface WorkRuntimeField {
  code: string
  name: string
  type: WorkFieldType
  required: boolean
  dictionaryCode: string | null
  editable: boolean
  cardVisible: boolean
  kanbanRole: WorkKanbanRole
}

export interface WorkRuntimeConfiguration {
  activeRevision: number
  storedRevision: number
  objectType: WorkObjectType
  fields: WorkRuntimeField[]
  values: Record<string, WorkFieldValue>
  cardValues: Record<string, WorkFieldValue>
  kanban: {
    columnFieldCode: string | null
    groupFieldCode: string | null
    numericFieldCode: string | null
    columnValue: WorkFieldValue
    groupValue: WorkFieldValue
    numericValue: WorkFieldValue
  }
}
export type WorkTaskReminderStatus =
  | 'PENDING'
  | 'PROCESSING'
  | 'SENT'
  | 'CANCELLED'
  | 'FAILED'

export interface WorkTaskReminder {
  generation: number
  scheduledAt: string
  status: WorkTaskReminderStatus
  attemptCount: number
  sentAt: string | null
  failureCode: string | null
  version: number
}
export type WorkTaskRoleFilter =
  | 'PARTICIPATING'
  | 'CREATED_BY_ME'
  | 'ASSIGNED_TO_ME'
  | 'ALL'

export interface WorkTask {
  id: string
  systemId: string
  tenantId: string
  creatorMemberId: string
  assigneeMemberId: string
  title: string
  projectId: string | null
  description: string | null
  dueAt: string | null
  reminderAt?: string | null
  reminder?: WorkTaskReminder | null
  status: WorkTaskStatus
  createdAt: string
  updatedAt: string
  version: number
  runtime?: WorkRuntimeConfiguration | null
}

export interface CreateWorkTaskInput {
  title: string
  assigneeMemberId: string
  projectId?: string | null
  description?: string | null
  dueAt?: string | null
  reminderAt?: string | null
  customFields?: Record<string, WorkFieldValue>
}

export interface UpdateWorkTaskInput {
  title: string
  projectId?: string | null
  description?: string | null
  dueAt?: string | null
  reminderAt?: string | null
  version: number
  customFields?: Record<string, WorkFieldValue>
}

export interface AssignWorkTaskInput {
  assigneeMemberId: string
}

export interface WorkTaskListQuery {
  keyword?: string
  status?: WorkTaskStatusFilter
  role?: WorkTaskRoleFilter
  projectId?: string
  dueFrom?: string
  dueTo?: string
  dueBefore?: string
  createdFrom?: string
  createdBefore?: string
  updatedFrom?: string
  updatedBefore?: string
  assigneeMemberId?: string
  page?: number
  size?: number
}

export interface WorkTaskPage {
  items: WorkTask[]
  page: number
  size: number
  total: number
}

export type WorkProjectStatus = 'ACTIVE' | 'ARCHIVED'
export type WorkProjectStatusFilter = 'ALL' | WorkProjectStatus
export type WorkProjectMemberRole = 'OWNER' | 'MEMBER'
export type WorkProjectMemberStatus = 'ACTIVE' | 'REMOVED'

export interface WorkProject {
  id: string
  systemId: string
  tenantId: string
  creatorMemberId: string
  title: string
  description: string | null
  status: WorkProjectStatus
  createdAt: string
  updatedAt: string
  version: number
}

export interface WorkProjectMember {
  projectId: string
  memberId: string
  role: WorkProjectMemberRole
  status: WorkProjectMemberStatus
  joinedAt: string
  updatedAt: string
  version: number
}

export interface WorkProjectListQuery {
  keyword?: string
  status?: WorkProjectStatusFilter
  page?: number
  size?: number
}

export interface WorkProjectPage {
  items: WorkProject[]
  page: number
  size: number
  total: number
}

export interface CreateWorkProjectInput {
  title: string
  description: string | null
}

export interface UpdateWorkProjectInput extends CreateWorkProjectInput {
  version: number
}

export interface WorkVersionInput {
  version: number
}

export interface AddWorkProjectMemberInput {
  memberId: string
  role: WorkProjectMemberRole
}

export interface UpdateWorkProjectMemberInput {
  role: WorkProjectMemberRole
  version: number
}

export type WorkDailyReportStatus = 'DRAFT' | 'SUBMITTED'
export type WorkDailyReportStatusFilter = 'ALL' | WorkDailyReportStatus
export type WorkDailyReportScope = 'SELF' | 'ALL'
export type WorkDailyReportDayState = WorkDailyReportStatus | 'MISSING'

export interface WorkDailyReport {
  id: string
  systemId: string
  tenantId: string
  authorMemberId: string
  workDate: string
  completedWork: string
  plannedWork: string
  blockers: string | null
  status: WorkDailyReportStatus
  createdAt: string
  updatedAt: string
  submittedAt: string | null
  version: number
  runtime?: WorkRuntimeConfiguration | null
}

export interface CreateWorkDailyReportInput {
  workDate: string
  completedWork: string
  plannedWork: string
  blockers: string | null
  customFields?: Record<string, WorkFieldValue>
}

export interface UpdateWorkDailyReportInput {
  completedWork: string
  plannedWork: string
  blockers: string | null
  version: number
  customFields?: Record<string, WorkFieldValue>
}

export interface WorkDailyReportListQuery {
  scope?: WorkDailyReportScope
  memberId?: string
  dateFrom?: string
  dateTo?: string
  status?: WorkDailyReportStatusFilter
  page?: number
  size?: number
}

export interface WorkDailyReportPage {
  items: WorkDailyReport[]
  page: number
  size: number
  total: number
}

export interface WorkDailyReportSummaryDay {
  workDate: string
  state: WorkDailyReportDayState
  reportId?: string | null
  version?: number | null
}

export interface WorkDailyReportSummary {
  memberId: string
  dateFrom: string
  dateTo: string
  submittedCount: number
  draftCount: number
  missingCount: number
  days: WorkDailyReportSummaryDay[]
}
