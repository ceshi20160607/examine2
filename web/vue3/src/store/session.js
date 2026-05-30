import { hasId, idToString } from '../utils/id'

const SESSION_KEY = 'examine_session'

function normalizeSession(payload) {
  if (!payload || typeof payload !== 'object') return payload
  return {
    ...payload,
    platId: idToString(payload.platId),
    systemId: idToString(payload.systemId || '0') || '0',
    tenantId: idToString(payload.tenantId || '0') || '0'
  }
}

export function getSession() {
  try {
    const raw = localStorage.getItem(SESSION_KEY)
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

export function setSession(payload) {
  if (payload) localStorage.setItem(SESSION_KEY, JSON.stringify(normalizeSession(payload)))
  else localStorage.removeItem(SESSION_KEY)
}

export function hasSystemContext() {
  const s = getSession()
  return !!(s && hasId(s.systemId))
}
