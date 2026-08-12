export type PlatformWorkTaskKind = 'PROJECT' | 'GENERAL'
export type PlatformWorkTaskStatus = 'OPEN' | 'COMPLETED' | 'CANCELLED'
export interface PlatformWorkOverview { activeProjects: number; openTasks: number; overdueTasks: number; todayTasks: number; todayReportSubmitted: boolean }
export interface PlatformWorkProject { id: string; code: string; name: string; status: 'ACTIVE' | 'COMPLETED' | 'ARCHIVED'; startDate: string | null; dueDate: string | null; taskCount: number; completedTaskCount: number; updatedAt: string; version: number }
export interface PlatformWorkTask { taskId: string; kind: PlatformWorkTaskKind; projectId: string | null; projectName: string | null; title: string; description: string | null; dueAt: string | null; priority: 'LOW' | 'NORMAL' | 'HIGH' | 'URGENT'; status: PlatformWorkTaskStatus; labels: string[]; createdAt: string; updatedAt: string; version: number }
export interface PlatformWorkTaskPage { items: PlatformWorkTask[]; page: number; size: number; total: number }
export interface PlatformDailyReport { id: string; reportDate: string; status: 'DRAFT' | 'SUBMITTED'; completed: string; plan: string; risks: string | null; projectId: string | null; projectName: string | null; updatedAt: string; version: number }
export interface PlatformProjectInput { code: string; name: string; startDate?: string | null; dueDate?: string | null }
export interface PlatformWorkTaskInput { kind: PlatformWorkTaskKind; projectId?: string | null; title: string; description?: string | null; dueAt?: string | null; priority: PlatformWorkTask['priority']; labels: string[] }
export interface PlatformDailyReportInput { reportDate: string; completed: string; plan: string; risks?: string | null; projectId?: string | null; expectedVersion?: number | null }
