export interface ConfiguredModuleGroup {
  id: number
  code: string
  name: string
  sortOrder: number
  status: string
}

export interface ConfiguredModule {
  id: number
  groupId: number
  code: string
  name: string
  status: string
  draftRevision: number
  version: number
}

export interface ConfiguredField {
  id: number
  code: string
  name: string
  fieldType: string
  required: boolean
  sortOrder: number
  status: string
  configJson: string
  version: number
}

export interface ConfiguredPage {
  id: number
  pageType: string
  name: string
  layoutJson: string
  version: number
}

export interface ConfiguredAction {
  id: number
  code: string
  name: string
  location: string
  status: string
  configJson: string
  version: number
}

export interface ModuleOverview {
  groups: ConfiguredModuleGroup[]
  modules: ConfiguredModule[]
}

export interface ModuleDraft {
  module: ConfiguredModule
  fields: ConfiguredField[]
  pages: ConfiguredPage[]
  actions: ConfiguredAction[]
  published: boolean
}

export interface PublicationIssue {
  path: string
  code: string
  message: string
}

export interface PublicationCheck {
  valid: boolean
  draftRevision: number
  issues: PublicationIssue[]
}

export interface RuntimeModuleCatalogItem {
  groupId: number
  groupCode: string
  groupName: string
  groupSortOrder: number
  moduleId: number
  moduleCode: string
  moduleName: string
}

export interface RuntimeField extends ConfiguredField {}
export interface RuntimeAction extends ConfiguredAction {}

export interface RuntimeModuleConfiguration {
  moduleId: number
  versionId: number
  versionNumber: number
  publicationVersion: number
  configuration: {
    module: ConfiguredModule
    fields: RuntimeField[]
    pages: ConfiguredPage[]
    actions: RuntimeAction[]
  }
}

export interface RuntimeRecord {
  id: number
  recordNumber?: string
  title: string
  status: string
  ownerMemberId?: number
  departmentId?: number
  participantMemberIds: number[]
  version: number
  createdAt: string
  updatedAt: string
  fields: Record<string, unknown>
}

export interface RuntimeRecordList {
  records: RuntimeRecord[]
  total: number
  page: number
  pageSize: number
}

export interface AuditEvent {
  id: number
  traceId: string
  actorAccountId?: number
  memberId?: number
  eventCode: string
  objectType?: string
  objectId?: string
  resultCode: string
  permissionSnapshot?: string
  detailJson?: string
  occurredAt: string
}

export interface AuditEventList {
  events: AuditEvent[]
  total: number
  page: number
  pageSize: number
}

export interface SystemSettings {
  systemId: number
  code: string
  name: string
  tenantMode: 'SINGLE' | 'MULTI'
  status: string
  version: number
}

export interface SystemTenant {
  id: number
  code: string
  name: string
  main: boolean
  status: 'ACTIVE' | 'DISABLED'
  current: boolean
  version: number
}
