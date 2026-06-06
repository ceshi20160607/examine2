import { http, request, withContext } from './http';
import type { ModuleStatusValue } from './enums';
import type {
  AccountRoleAssignBO,
  AppApplicationSaveBO,
  AppManageVO,
  AppPublishBO,
  AttachmentCreateBO,
  FlowManageVO,
  FlowStartBO,
  FlowTaskHandleBO,
  FlowTemplatePublishBO,
  FlowTemplateSaveBO,
  Id,
  ModuleFieldSaveBO,
  ModuleManageVO,
  ModuleModelSaveBO,
  ModulePageSaveBO,
  ModuleRecordSaveBO,
  OpenApiClientSaveBO,
  OpenApiCredentialCreateBO,
  OpenApiIdempotentSaveBO,
  OpenApiIpWhitelistSaveBO,
  OpenApiScopeSaveBO,
  PlatformAccountSaveBO,
  PlatformLoginDTO,
  PlatformManageVO,
  PlatformPermissionSaveBO,
  PlatformRoleSaveBO,
  PlatformSystemSaveBO,
  PlatformTenantSaveBO,
  RequestContext,
  RolePermissionAssignBO,
  UploadFileCreateBO,
  UploadImportExportJobBO,
  UploadManageVO
} from './types';

type Query = Record<string, unknown>;
type AttachmentQuery = Pick<AttachmentCreateBO, 'bizType' | 'bizId'> & Partial<Pick<AttachmentCreateBO, 'fieldCode'>>;

function getList<T>(url: string, params?: Query) {
  return request<T[]>(http.get(url, { params }));
}

function withQueryContext(params: Query | undefined, required: Array<keyof RequestContext>): Query {
  return withContext(params ?? {}, required);
}

export const api = {
  auth: {
    login: (body: PlatformLoginDTO) =>
      request<PlatformManageVO>(http.post('/api/plat/auth/login', body))
  },
  platform: {
    systems: (params?: Query) => getList<PlatformManageVO>('/api/plat/systems', params),
    createSystem: (body: PlatformSystemSaveBO) =>
      request<PlatformManageVO>(http.post('/api/plat/systems', body)),
    setSystemStatus: (id: Id, status: string) =>
      request<PlatformManageVO>(http.patch('/api/plat/systems/status', { status }, { params: { id } })),
    tenants: (params?: Query) => getList<PlatformManageVO>('/api/plat/tenants', params),
    createTenant: (body: PlatformTenantSaveBO) =>
      request<PlatformManageVO>(http.post('/api/plat/tenants', body)),
    setTenantStatus: (id: Id, status: string) =>
      request<PlatformManageVO>(http.patch('/api/plat/tenants/status', { status }, { params: { id } })),
    accounts: (params?: Query) => getList<PlatformManageVO>('/api/plat/accounts', params),
    createAccount: (body: PlatformAccountSaveBO) =>
      request<PlatformManageVO>(http.post('/api/plat/accounts', body)),
    setAccountStatus: (id: Id, status: string) =>
      request<PlatformManageVO>(http.patch('/api/plat/accounts/status', { status }, { params: { id } })),
    roles: (params?: Query) => getList<PlatformManageVO>('/api/plat/roles', params),
    createRole: (body: Omit<PlatformRoleSaveBO, 'tenantId' | 'systemId'>) =>
      request<PlatformManageVO>(
        http.post('/api/plat/roles', withContext(body, ['tenantId', 'systemId']))
      ),
    permissions: (params?: Query) => getList<PlatformManageVO>('/api/plat/permissions', params),
    createPermission: (body: Omit<PlatformPermissionSaveBO, 'tenantId' | 'systemId'>) =>
      request<PlatformManageVO>(
        http.post('/api/plat/permissions', withContext(body, ['tenantId', 'systemId']))
      ),
    assignRolePermissions: (body: RolePermissionAssignBO) =>
      request<PlatformManageVO>(http.post('/api/plat/roles/permissions', body)),
    assignAccountRoles: (body: Omit<AccountRoleAssignBO, 'tenantId' | 'systemId'>) =>
      request<PlatformManageVO>(
        http.post('/api/plat/accounts/roles', withContext(body, ['tenantId', 'systemId']))
      )
  },
  apps: {
    list: (params?: Query) => getList<AppManageVO>('/api/apps', params),
    create: (body: Omit<AppApplicationSaveBO, 'tenantId' | 'systemId'>) =>
      request<AppManageVO>(http.post('/api/apps', withContext(body, ['tenantId', 'systemId']))),
    publish: (body: AppPublishBO) => request<AppManageVO>(http.post('/api/apps/publish', body)),
    clients: (params?: Query) => getList<AppManageVO>('/api/apps/openapi/clients', params),
    createClient: (body: Omit<OpenApiClientSaveBO, 'tenantId' | 'systemId'>) =>
      request<AppManageVO>(
        http.post('/api/apps/openapi/clients', withContext(body, ['tenantId', 'systemId']))
      ),
    createCredential: (body: OpenApiCredentialCreateBO) =>
      request<AppManageVO>(http.post('/api/apps/openapi/credentials', body)),
    createScope: (body: OpenApiScopeSaveBO) =>
      request<AppManageVO>(http.post('/api/apps/openapi/scopes', body)),
    createIpWhitelist: (body: OpenApiIpWhitelistSaveBO) =>
      request<AppManageVO>(http.post('/api/apps/openapi/ip-whitelist', body)),
    createIdempotent: (body: OpenApiIdempotentSaveBO) =>
      request<AppManageVO>(http.post('/api/apps/openapi/idempotents', body)),
    accessLogs: (params?: Query) => getList<AppManageVO>('/api/apps/openapi/access-logs', params)
  },
  modules: {
    models: (params?: Query) => getList<ModuleManageVO>('/api/modules/models', params),
    createModel: (body: Omit<ModuleModelSaveBO, 'tenantId' | 'systemId' | 'appId'>) =>
      request<ModuleManageVO>(
        http.post('/api/modules/models', withContext(body, ['tenantId', 'systemId', 'appId']))
      ),
    setModelStatus: (id: Id, status: ModuleStatusValue) =>
      request<ModuleManageVO>(http.patch('/api/modules/models/status', { status }, { params: { id } })),
    fields: (params?: Query) =>
      getList<ModuleManageVO>('/api/modules/fields', withQueryContext(params, ['moduleId'])),
    createField: (body: Omit<ModuleFieldSaveBO, 'moduleId'>) =>
      request<ModuleManageVO>(http.post('/api/modules/fields', withContext(body, ['moduleId']))),
    pages: (params?: Query) =>
      getList<ModuleManageVO>('/api/modules/pages', withQueryContext(params, ['moduleId'])),
    createPage: (body: Omit<ModulePageSaveBO, 'moduleId'>) =>
      request<ModuleManageVO>(http.post('/api/modules/pages', withContext(body, ['moduleId']))),
    records: (params?: Query) =>
      getList<ModuleManageVO>('/api/modules/records', withQueryContext(params, ['moduleId'])),
    createRecord: (body: Omit<ModuleRecordSaveBO, 'moduleId'>) =>
      request<ModuleManageVO>(http.post('/api/modules/records', withContext(body, ['moduleId']))),
    detail: (id: Id) => request<ModuleManageVO>(http.get('/api/modules/records/detail', { params: { id } })),
    updateRecord: (id: Id, body: Omit<ModuleRecordSaveBO, 'moduleId'>) =>
      request<ModuleManageVO>(
        http.put('/api/modules/records', withContext(body, ['moduleId']), { params: { id } })
      ),
    deleteRecord: (id: Id) => request<void>(http.delete('/api/modules/records', { params: { id } })),
    createExportJob: (body: Omit<UploadImportExportJobBO, 'tenantId' | 'moduleId' | 'jobType'>) =>
      request<ModuleManageVO>(
        http.post('/api/modules/export-jobs', {
          ...withContext(body, ['tenantId', 'moduleId']),
          jobType: 'EXPORT'
        })
      )
  },
  flows: {
    templates: (params?: Query) => getList<FlowManageVO>('/api/flows/templates', params),
    createTemplate: (body: Omit<FlowTemplateSaveBO, 'tenantId' | 'appId' | 'moduleId'>) =>
      request<FlowManageVO>(
        http.post('/api/flows/templates', withContext(body, ['tenantId', 'appId', 'moduleId']))
      ),
    publishTemplate: (body: FlowTemplatePublishBO) =>
      request<FlowManageVO>(http.post('/api/flows/templates/publish', body)),
    start: (body: Omit<FlowStartBO, 'tenantId' | 'moduleId'>) =>
      request<FlowManageVO>(
        http.post('/api/flows/instances', withContext(body, ['tenantId', 'moduleId']))
      ),
    tasks: (params?: Query) => getList<FlowManageVO>('/api/flows/tasks', params),
    handleTask: (taskId: Id, body: FlowTaskHandleBO) =>
      request<FlowManageVO>(http.patch('/api/flows/tasks/handle', body, { params: { taskId } }))
  },
  uploads: {
    files: (params?: Query) => getList<UploadManageVO>('/api/uploads/files', params),
    createFile: (body: Omit<UploadFileCreateBO, 'tenantId'>) =>
      request<UploadManageVO>(http.post('/api/uploads/files', withContext(body, ['tenantId']))),
    deleteFile: (id: Id) => request<void>(http.delete('/api/uploads/files', { params: { id } })),
    attachments: (params: AttachmentQuery) => getList<UploadManageVO>('/api/uploads/attachments', params),
    createAttachment: (body: AttachmentCreateBO) =>
      request<UploadManageVO>(http.post('/api/uploads/attachments', body)),
    createJob: (body: Omit<UploadImportExportJobBO, 'tenantId' | 'moduleId'>) =>
      request<UploadManageVO>(
        http.post('/api/uploads/jobs', withContext(body, ['tenantId', 'moduleId']))
      )
  }
};
