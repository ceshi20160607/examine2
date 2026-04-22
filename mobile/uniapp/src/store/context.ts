import type { SessionPayload } from '@/api/platform'

const KEY = 'sessionPayload'

export function getSessionPayload(): SessionPayload | null {
  const raw = uni.getStorageSync(KEY)
  if (!raw) return null
  try {
    const obj = typeof raw === 'string' ? JSON.parse(raw) : raw
    if (!obj || typeof obj.systemId !== 'number') return null
    return obj as SessionPayload
  } catch {
    return null
  }
}

export function setSessionPayload(p: SessionPayload) {
  uni.setStorageSync(KEY, JSON.stringify(p))
}

export function clearSessionPayload() {
  uni.removeStorageSync(KEY)
}

