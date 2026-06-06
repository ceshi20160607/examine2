import { request } from './http';
const CONTEXT_KEY = 'unique.examine.context';
function readContext() {
    const text = localStorage.getItem(CONTEXT_KEY);
    if (!text)
        return {};
    try {
        return JSON.parse(text);
    }
    catch {
        return {};
    }
}
function cleanQuery(query = {}) {
    return Object.fromEntries(Object.entries(query).filter(([, value]) => value !== undefined && value !== null && value !== ''));
}
const pageParams = (query = {}) => ({
    pageNo: 1,
    pageSize: 20,
    ...cleanQuery(query)
});
export const enrichQuery = (query = {}) => {
    const context = readContext();
    return cleanQuery({
        systemId: context.systemId,
        tenantId: context.tenantId,
        ...cleanQuery(query)
    });
};
export const enrichPayload = (payload) => {
    const context = readContext();
    return {
        ...payload,
        systemId: payload.systemId ?? context.systemId,
        tenantId: payload.tenantId ?? context.tenantId
    };
};
const contextPageParams = (query) => pageParams(enrichQuery(query));
export const authApi = {
    login: (data) => request({ method: 'POST', url: '/api/v1/auth/login', data }),
    register: (data) => request({ method: 'POST', url: '/api/v1/auth/register', data }),
    refresh: () => request({ method: 'POST', url: '/api/v1/auth/refresh' }),
    me: () => request({ method: 'GET', url: '/api/v1/auth/me' }),
    logout: () => request({ method: 'POST', url: '/api/v1/auth/logout' })
};
export const platformApi = {
    tenants: (query) => request({ method: 'GET', url: '/api/v1/platform/tenants', params: pageParams(query) }),
    createTenant: (data) => request({ method: 'POST', url: '/api/v1/platform/tenants', data }),
    systems: (query) => request({ method: 'GET', url: '/api/v1/platform/systems', params: pageParams(query) }),
    createSystem: (data) => request({ method: 'POST', url: '/api/v1/platform/systems', data }),
    updateSystemStatus: (systemId, status) => request({ method: 'PATCH', url: `/api/v1/platform/systems/${systemId}/status`, data: { status } }),
    enterSystem: (data) => request({ method: 'POST', url: '/api/v1/platform/context/enter-system', data })
};
export const configApi = {
    apps: (query) => request({ method: 'GET', url: '/api/v1/system/config/apps', params: contextPageParams(query) }),
    createApp: (data) => request({ method: 'POST', url: '/api/v1/system/config/apps', data: enrichPayload(data) }),
    publishApp: (appId) => request({ method: 'POST', url: `/api/v1/system/config/apps/${appId}/publish` }),
    modules: (query) => request({ method: 'GET', url: '/api/v1/system/config/modules', params: contextPageParams(query) }),
    createModule: (data) => request({ method: 'POST', url: '/api/v1/system/config/modules', data: enrichPayload(data) }),
    fields: (query) => request({ method: 'GET', url: '/api/v1/system/config/fields', params: contextPageParams(query) }),
    createField: (data) => request({ method: 'POST', url: '/api/v1/system/config/fields', data: enrichPayload(data) }),
    createFieldOption: (data) => request({ method: 'POST', url: '/api/v1/system/config/field-options', data }),
    pages: (query) => request({ method: 'GET', url: '/api/v1/system/config/pages', params: contextPageParams(query) }),
    createPage: (data) => request({ method: 'POST', url: '/api/v1/system/config/pages', data: enrichPayload(data) }),
    publishPage: (pageId) => request({ method: 'POST', url: `/api/v1/system/config/pages/${pageId}/publish` }),
    menus: (query) => request({ method: 'GET', url: '/api/v1/system/config/menus', params: contextPageParams(query) }),
    createMenu: (data) => request({ method: 'POST', url: '/api/v1/system/config/menus', data: enrichPayload(data) }),
    dictionaries: (query) => request({ method: 'GET', url: '/api/v1/system/config/dictionaries', params: contextPageParams(query) }),
    createDictionary: (data) => request({ method: 'POST', url: '/api/v1/system/config/dictionaries', data: enrichPayload(data) }),
    createDictionaryItem: (data) => request({ method: 'POST', url: '/api/v1/system/config/dictionary-items', data })
};
export const recordApi = {
    list: (query) => request({ method: 'GET', url: '/api/v1/system/records', params: contextPageParams(query) }),
    create: (data) => request({ method: 'POST', url: '/api/v1/system/records', data: enrichPayload(data) }),
    update: (recordId, data) => request({ method: 'PUT', url: `/api/v1/system/records/${recordId}`, data: enrichPayload(data) }),
    detail: (recordId) => request({ method: 'GET', url: `/api/v1/system/records/${recordId}` }),
    remove: (recordId) => request({ method: 'DELETE', url: `/api/v1/system/records/${recordId}` }),
    comment: (data) => request({ method: 'POST', url: '/api/v1/system/records/comments', data: enrichPayload(data) })
};
export const workflowApi = {
    templates: (query) => request({ method: 'GET', url: '/api/v1/system/workflow/templates', params: contextPageParams(query) }),
    createTemplate: (data) => request({ method: 'POST', url: '/api/v1/system/workflow/templates', data: enrichPayload(data) }),
    createVersion: (data) => request({ method: 'POST', url: '/api/v1/system/workflow/versions', data: enrichPayload(data) }),
    publishVersion: (versionId) => request({ method: 'POST', url: `/api/v1/system/workflow/versions/${versionId}/publish` }),
    start: (data) => request({ method: 'POST', url: '/api/v1/system/workflow/instances/start', data: enrichPayload(data) }),
    tasks: (query) => request({ method: 'GET', url: '/api/v1/system/workflow/tasks', params: contextPageParams(query) }),
    handleTask: (taskId, data) => request({ method: 'POST', url: `/api/v1/system/workflow/tasks/${taskId}/handle`, data })
};
export const fileApi = {
    list: (query) => request({ method: 'GET', url: '/api/v1/system/files', params: contextPageParams(query) }),
    create: (data) => request({ method: 'POST', url: '/api/v1/system/files', data: enrichPayload(data) }),
    relation: (data) => request({ method: 'POST', url: '/api/v1/system/files/relations', data }),
    remove: (fileId) => request({ method: 'DELETE', url: `/api/v1/system/files/${fileId}` }),
    tasks: (query) => request({ method: 'GET', url: '/api/v1/system/files/tasks', params: contextPageParams(query) }),
    createTask: (data) => request({ method: 'POST', url: '/api/v1/system/files/tasks', data: enrichPayload(data) })
};
export const openApiManageApi = {
    clients: (query) => request({ method: 'GET', url: '/api/v1/system/openapi/clients', params: contextPageParams(query) }),
    createClient: (data) => request({ method: 'POST', url: '/api/v1/system/openapi/clients', data: enrichPayload(data) }),
    createCredential: (data) => request({ method: 'POST', url: '/api/v1/system/openapi/credentials', data: enrichPayload(data) }),
    saveScope: (data) => request({ method: 'POST', url: '/api/v1/system/openapi/scopes', data: enrichPayload(data) }),
    saveIpWhitelist: (data) => request({ method: 'POST', url: '/api/v1/system/openapi/ip-whitelist', data: enrichPayload(data) })
};
export const opsApi = {
    health: () => request({ method: 'GET', url: '/api/v1/ops/health' }),
    auditLogs: (query) => request({ method: 'GET', url: '/api/v1/ops/audit-logs', params: pageParams(query) }),
    configs: (query) => request({ method: 'GET', url: '/api/v1/ops/configs', params: pageParams(query) }),
    createConfig: (data) => request({ method: 'POST', url: '/api/v1/ops/configs', data })
};
