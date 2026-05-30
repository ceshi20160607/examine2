import type { SessionPayload } from '@/api/platform'
import { idToString } from '@/utils/id'

const KEY = 'sessionPayload'

export function getSessionPayload(): SessionPayload | null {
  const raw = uni.getStorageSync(KEY)
  if (!raw) return null
  try {
    const obj = typeof raw === 'string' ? JSON.parse(raw) : raw
    if (!obj || !obj.systemId) return null
    return normalizeSessionPayload(obj as SessionPayload)
  } catch {
    return null
  }
}

export function setSessionPayload(p: SessionPayload) {
  uni.setStorageSync(KEY, JSON.stringify(normalizeSessionPayload(p)))
}

export function clearSessionPayload() {
  uni.removeStorageSync(KEY)
}

function normalizeSessionPayload(p: SessionPayload): SessionPayload {
  return {
    ...p,
    platId: idToString(p.platId),
    systemId: idToString(p.systemId || '0') || '0',
    tenantId: idToString(p.tenantId || '0') || '0'
  }
}

