import { getSessionPayload } from '@/store/context'

export function hasToken(): boolean {
  const t = uni.getStorageSync('token')
  return typeof t === 'string' && !!t.trim()
}

export function ensureLogin(): boolean {
  if (hasToken()) return true
  uni.reLaunch({ url: '/pages/auth/login' })
  return false
}

export function ensureSystemContext(): boolean {
  if (!ensureLogin()) return false
  const p = getSessionPayload()
  if (p && typeof p.systemId === 'number' && p.systemId > 0) return true
  uni.reLaunch({ url: '/pages/platform/systems' })
  return false
}

