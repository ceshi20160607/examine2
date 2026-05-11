import { httpGet, httpPost } from '@/api/http'
import type { ApiResult } from '@/api/http'

export type PlatAccountMe = { id?: number; username?: string }

export function login(username: string, password: string): Promise<ApiResult<{ token: string; account: any }>> {
  return httpPost<{ token: string; account: any }>('/v1/platform/auth/login', { username, password })
}

export function me(): Promise<ApiResult<PlatAccountMe>> {
  return httpGet<PlatAccountMe>('/v1/platform/auth/me')
}

export function refresh(): Promise<ApiResult<{ token: string }>> {
  return httpPost<{ token: string }>('/v1/platform/auth/refresh', {})
}

export function logout(): Promise<ApiResult<any>> {
  return httpPost<any>('/v1/platform/auth/logout', {})
}

