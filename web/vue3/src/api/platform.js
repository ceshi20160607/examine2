import { httpGet, httpPost } from './http'
import { setSession } from '../store/session'

export function listSystems() {
  return httpGet('/v1/platform/systems')
}

export function createSystem(name, multiTenantEnabled = 0) {
  return httpPost('/v1/platform/systems', { name, multiTenantEnabled })
}

export function enterSystem(systemId) {
  return httpPost('/v1/platform/context/enter-system', { systemId }).then((r) => {
    if (r?.data) setSession(r.data)
    return r
  })
}

export function listTenants(systemId) {
  return httpGet(`/v1/platform/tenants?systemId=${systemId}`)
}

export function selectTenant(tenantId) {
  return httpPost('/v1/platform/context/select-tenant', { tenantId }).then((r) => {
    if (r?.data) setSession(r.data)
    return r
  })
}

export function listPlatformMessages(limit = 50) {
  return httpGet(`/v1/platform/messages?limit=${limit}`)
}

export function listPlatformTodos(limit = 50, systemId, tenantId) {
  const q = [`limit=${limit}`]
  if (systemId) q.push(`systemId=${systemId}`)
  if (tenantId) q.push(`tenantId=${tenantId}`)
  return httpGet(`/v1/platform/todos?${q.join('&')}`)
}

export function listPlatformCc(limit = 50, onlyUnread, systemId, tenantId) {
  const q = [`limit=${limit}`]
  if (onlyUnread != null) q.push(`onlyUnread=${onlyUnread}`)
  if (systemId) q.push(`systemId=${systemId}`)
  if (tenantId) q.push(`tenantId=${tenantId}`)
  return httpGet(`/v1/platform/cc?${q.join('&')}`)
}

export function readPlatformCc(taskId) {
  return httpPost(`/v1/platform/cc/${taskId}/read`, {})
}
