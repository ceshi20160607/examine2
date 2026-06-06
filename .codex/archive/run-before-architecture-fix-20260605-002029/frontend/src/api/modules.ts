import { request } from './http';
import type { AnyRecord, AuthTokenVO, HealthVO, PageResult, UserVO } from './types';

export type Query = Record<string, string | number | boolean | undefined | null>;
export type Payload = Record<string, unknown>;

const CONTEXT_KEY = 'unique.examine.context';

function readContext() {
  const text = localStorage.getItem(CONTEXT_KEY);
  if (!text) return {};
  try {
    return JSON.parse(text) as { systemId?: number; tenantId?: number };
  } catch {
    return {};
  }
}

function cleanQuery(query: Query = {}) {
  return Object.fromEntries(
    Object.entries(query).filter(([, value]) => value !== undefined && value !== null && value !== '')
  ) as Query;
}

const pageParams = (query: Query = {}) => ({
  pageNo: 1,
  pageSize: 20,
  ...cleanQuery(query)
});

export const enrichQuery = (query: Query = {}) => {
  const context = readContext();
  return cleanQuery({
    systemId: context.systemId,
    tenantId: context.tenantId,
    ...cleanQuery(query)
  });
};

export const enrichPayload = <T extends Payload>(payload: T) => {
  const context = readContext();
  return {
    ...payload,
    systemId: payload.systemId ?? context.systemId,
    tenantId: payload.tenantId ?? context.tenantId
  };
};

const contextPageParams = (query?: Query) => pageParams(enrichQuery(query));

export const authApi = {
  login: (data: { account: string; password: string }) =>
    request<AuthTokenVO>({ method: 'POST', url: '/api/v1/auth/login', data }),
  register: (data: Payload) =>
    request<AuthTokenVO | UserVO>({ method: 'POST', url: '/api/v1/auth/register', data }),
  refresh: () => request<AuthTokenVO>({ method: 'POST', url: '/api/v1/auth/refresh' }),
  me: () => request<UserVO>({ method: 'GET', url: '/api/v1/auth/me' }),
  logout: () => request<void>({ method: 'POST', url: '/api/v1/auth/logout' })
};

export const platformApi = {
  tenants: (query?: Query) =>
    request<PageResult<AnyRecord>>({ method: 'GET', url: '/api/v1/platform/tenants', params: pageParams(query) }),
  createTenant: (data: Payload) =>
    request<AnyRecord>({ method: 'POST', url: '/api/v1/platform/tenants', data }),
  systems: (query?: Query) =>
    request<PageResult<AnyRecord>>({ method: 'GET', url: '/api/v1/platform/systems', params: pageParams(query) }),
  createSystem: (data: Payload) =>
    request<AnyRecord>({ method: 'POST', url: '/api/v1/platform/systems', data }),
  updateSystemStatus: (systemId: number, status: string) =>
    request<AnyRecord>({ method: 'PATCH', url: `/api/v1/platform/systems/${systemId}/status`, data: { status } }),
  enterSystem: (data: { systemId: number; tenantId?: number }) =>
    request<AuthTokenVO>({ method: 'POST', url: '/api/v1/platform/context/enter-system', data })
};

export const configApi = {
  apps: (query?: Query) => request<PageResult<AnyRecord>>({ method: 'GET', url: '/api/v1/system/config/apps', params: contextPageParams(query) }),
  createApp: (data: Payload) => request<AnyRecord>({ method: 'POST', url: '/api/v1/system/config/apps', data: enrichPayload(data) }),
  publishApp: (appId: number) => request<AnyRecord>({ method: 'POST', url: `/api/v1/system/config/apps/${appId}/publish` }),
  modules: (query?: Query) => request<PageResult<AnyRecord>>({ method: 'GET', url: '/api/v1/system/config/modules', params: contextPageParams(query) }),
  createModule: (data: Payload) => request<AnyRecord>({ method: 'POST', url: '/api/v1/system/config/modules', data: enrichPayload(data) }),
  fields: (query?: Query) => request<PageResult<AnyRecord>>({ method: 'GET', url: '/api/v1/system/config/fields', params: contextPageParams(query) }),
  createField: (data: Payload) => request<AnyRecord>({ method: 'POST', url: '/api/v1/system/config/fields', data: enrichPayload(data) }),
  createFieldOption: (data: Payload) => request<AnyRecord>({ method: 'POST', url: '/api/v1/system/config/field-options', data }),
  pages: (query?: Query) => request<PageResult<AnyRecord>>({ method: 'GET', url: '/api/v1/system/config/pages', params: contextPageParams(query) }),
  createPage: (data: Payload) => request<AnyRecord>({ method: 'POST', url: '/api/v1/system/config/pages', data: enrichPayload(data) }),
  publishPage: (pageId: number) => request<AnyRecord>({ method: 'POST', url: `/api/v1/system/config/pages/${pageId}/publish` }),
  menus: (query?: Query) => request<PageResult<AnyRecord>>({ method: 'GET', url: '/api/v1/system/config/menus', params: contextPageParams(query) }),
  createMenu: (data: Payload) => request<AnyRecord>({ method: 'POST', url: '/api/v1/system/config/menus', data: enrichPayload(data) }),
  dictionaries: (query?: Query) => request<PageResult<AnyRecord>>({ method: 'GET', url: '/api/v1/system/config/dictionaries', params: contextPageParams(query) }),
  createDictionary: (data: Payload) => request<AnyRecord>({ method: 'POST', url: '/api/v1/system/config/dictionaries', data: enrichPayload(data) }),
  createDictionaryItem: (data: Payload) => request<AnyRecord>({ method: 'POST', url: '/api/v1/system/config/dictionary-items', data })
};

export const recordApi = {
  list: (query?: Query) => request<PageResult<AnyRecord>>({ method: 'GET', url: '/api/v1/system/records', params: contextPageParams(query) }),
  create: (data: Payload) => request<AnyRecord>({ method: 'POST', url: '/api/v1/system/records', data: enrichPayload(data) }),
  update: (recordId: number, data: Payload) => request<AnyRecord>({ method: 'PUT', url: `/api/v1/system/records/${recordId}`, data: enrichPayload(data) }),
  detail: (recordId: number) => request<AnyRecord>({ method: 'GET', url: `/api/v1/system/records/${recordId}` }),
  remove: (recordId: number) => request<void>({ method: 'DELETE', url: `/api/v1/system/records/${recordId}` }),
  comment: (data: Payload) => request<AnyRecord>({ method: 'POST', url: '/api/v1/system/records/comments', data: enrichPayload(data) })
};

export const workflowApi = {
  templates: (query?: Query) => request<PageResult<AnyRecord>>({ method: 'GET', url: '/api/v1/system/workflow/templates', params: contextPageParams(query) }),
  createTemplate: (data: Payload) => request<AnyRecord>({ method: 'POST', url: '/api/v1/system/workflow/templates', data: enrichPayload(data) }),
  createVersion: (data: Payload) => request<AnyRecord>({ method: 'POST', url: '/api/v1/system/workflow/versions', data: enrichPayload(data) }),
  publishVersion: (versionId: number) => request<AnyRecord>({ method: 'POST', url: `/api/v1/system/workflow/versions/${versionId}/publish` }),
  start: (data: Payload) => request<AnyRecord>({ method: 'POST', url: '/api/v1/system/workflow/instances/start', data: enrichPayload(data) }),
  tasks: (query?: Query) => request<PageResult<AnyRecord>>({ method: 'GET', url: '/api/v1/system/workflow/tasks', params: contextPageParams(query) }),
  handleTask: (taskId: number, data: Payload) => request<AnyRecord>({ method: 'POST', url: `/api/v1/system/workflow/tasks/${taskId}/handle`, data })
};

export const fileApi = {
  list: (query?: Query) => request<PageResult<AnyRecord>>({ method: 'GET', url: '/api/v1/system/files', params: contextPageParams(query) }),
  create: (data: Payload) => request<AnyRecord>({ method: 'POST', url: '/api/v1/system/files', data: enrichPayload(data) }),
  relation: (data: Payload) => request<AnyRecord>({ method: 'POST', url: '/api/v1/system/files/relations', data }),
  remove: (fileId: number) => request<void>({ method: 'DELETE', url: `/api/v1/system/files/${fileId}` }),
  tasks: (query?: Query) => request<PageResult<AnyRecord>>({ method: 'GET', url: '/api/v1/system/files/tasks', params: contextPageParams(query) }),
  createTask: (data: Payload) => request<AnyRecord>({ method: 'POST', url: '/api/v1/system/files/tasks', data: enrichPayload(data) })
};

export const openApiManageApi = {
  clients: (query?: Query) => request<PageResult<AnyRecord>>({ method: 'GET', url: '/api/v1/system/openapi/clients', params: contextPageParams(query) }),
  createClient: (data: Payload) => request<AnyRecord>({ method: 'POST', url: '/api/v1/system/openapi/clients', data: enrichPayload(data) }),
  createCredential: (data: Payload) => request<AnyRecord>({ method: 'POST', url: '/api/v1/system/openapi/credentials', data: enrichPayload(data) }),
  saveScope: (data: Payload) => request<AnyRecord>({ method: 'POST', url: '/api/v1/system/openapi/scopes', data: enrichPayload(data) }),
  saveIpWhitelist: (data: Payload) => request<AnyRecord>({ method: 'POST', url: '/api/v1/system/openapi/ip-whitelist', data: enrichPayload(data) })
};

export const opsApi = {
  health: () => request<HealthVO>({ method: 'GET', url: '/api/v1/ops/health' }),
  auditLogs: (query?: Query) => request<PageResult<AnyRecord>>({ method: 'GET', url: '/api/v1/ops/audit-logs', params: pageParams(query) }),
  configs: (query?: Query) => request<PageResult<AnyRecord>>({ method: 'GET', url: '/api/v1/ops/configs', params: pageParams(query) }),
  createConfig: (data: Payload) => request<AnyRecord>({ method: 'POST', url: '/api/v1/ops/configs', data })
};
