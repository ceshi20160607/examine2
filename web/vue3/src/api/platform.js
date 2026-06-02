import { httpGet, httpPost, httpRequest } from './http'
import { setSession } from '../store/session'
import { idToString } from '../utils/id'

export function listSystems() {
  return httpGet('/v1/platform/systems')
}

export function listPlatformPermissions() {
  return httpGet('/v1/platform/permissions/me')
}

export function createSystem(name, multiTenantEnabled = 0) {
  return httpPost('/v1/platform/systems', { name, multiTenantEnabled })
}

export function setSystemStatus(systemId, status) {
  return httpPost(`/v1/platform/systems/${encodeURIComponent(idToString(systemId))}/status`, { status })
}

export function deleteSystem(systemId) {
  return httpRequest('DELETE', `/v1/platform/systems/${encodeURIComponent(idToString(systemId))}`)
}

export function enterSystem(systemId) {
  return httpPost('/v1/platform/context/enter-system', { systemId: idToString(systemId) }).then((r) => {
    if (r?.data) setSession(r.data)
    return r
  })
}

export function listTenants(systemId) {
  return httpGet(`/v1/platform/tenants?systemId=${encodeURIComponent(idToString(systemId))}`)
}

export function selectTenant(tenantId) {
  return httpPost('/v1/platform/context/select-tenant', { tenantId: idToString(tenantId) }).then((r) => {
    if (r?.data) setSession(r.data)
    return r
  })
}

export function listPlatformMessages(limit = 50) {
  return httpGet(`/v1/platform/messages?limit=${encodeURIComponent(String(limit))}`)
}

export function listPlatformTodos(limit = 50, systemId, tenantId) {
  const q = [`limit=${encodeURIComponent(String(limit))}`]
  const sid = idToString(systemId)
  const tid = idToString(tenantId)
  if (sid) q.push(`systemId=${encodeURIComponent(sid)}`)
  if (tid) q.push(`tenantId=${encodeURIComponent(tid)}`)
  return httpGet(`/v1/platform/todos?${q.join('&')}`)
}

export function listPlatformCc(limit = 50, onlyUnread, systemId, tenantId) {
  const q = [`limit=${encodeURIComponent(String(limit))}`]
  const sid = idToString(systemId)
  const tid = idToString(tenantId)
  if (onlyUnread != null) q.push(`onlyUnread=${onlyUnread}`)
  if (sid) q.push(`systemId=${encodeURIComponent(sid)}`)
  if (tid) q.push(`tenantId=${encodeURIComponent(tid)}`)
  return httpGet(`/v1/platform/cc?${q.join('&')}`)
}

export function readPlatformCc(taskId) {
  return httpPost(`/v1/platform/cc/${encodeURIComponent(idToString(taskId))}/read`, {})
}
