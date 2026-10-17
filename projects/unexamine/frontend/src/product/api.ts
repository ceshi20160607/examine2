import {
  clearSessionScope,
  replaceSessionTokens,
  sessionScopeForAccessToken,
  tokensForScope,
  type SessionScope,
  type SessionTokens,
} from './session'

export interface ApiEnvelope<T> {
  code: string
  message: string
  data: T
  requestId: string
}

export class ApiError extends Error {
  constructor(
    public readonly code: string,
    message: string,
    public readonly traceId?: string,
    public readonly details?: Record<string, unknown>,
    public readonly httpStatus?: number,
  ) {
    super(message)
  }
}

const refreshes: Partial<Record<SessionScope, Promise<string | null>>> = {}

export async function api<T>(path: string, options: RequestInit = {}, token?: string): Promise<T> {
  const scope = token ? sessionScopeForAccessToken(token) : null
  const activeToken = scope && token ? await refreshBeforeExpiry(scope, token) : token
  let { response, payload } = await request<T>(path, options, activeToken)
  if (scope && response.status === 401 && path !== '/api/auth/refresh') {
    const current = tokensForScope(scope)?.accessToken
    const refreshedToken = current && current !== activeToken ? current : await refreshSession(scope)
    if (refreshedToken) ({ response, payload } = await request<T>(path, options, refreshedToken))
  }
  return unwrap(response, payload)
}

async function request<T>(path: string, options: RequestInit, token?: string) {
  const headers = new Headers(options.headers)
  if (options.body && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json')
  if (token) headers.set('Authorization', `Bearer ${token}`)
  if (!headers.has('X-Client-Source')) headers.set('X-Client-Source', 'WEB')
  const response = await fetch(path, { ...options, headers })
  const payload = (await response.json().catch(() => ({
    code: 'NETWORK_RESPONSE_INVALID',
    message: '服务响应格式不正确',
    data: null,
  }))) as ApiEnvelope<T> & { data?: { traceId?: string } }
  return { response, payload }
}

function unwrap<T>(response: Response, payload: ApiEnvelope<T> & { data?: { traceId?: string } }) {
  if (!response.ok || payload.code !== 'OK') {
    throw new ApiError(
      payload.code,
      payload.message || '请求失败',
      payload.data?.traceId ?? payload.requestId,
      payload.data && typeof payload.data === 'object' ? payload.data as Record<string, unknown> : undefined,
      response.status,
    )
  }
  return payload.data as T
}

async function refreshBeforeExpiry(scope: SessionScope, fallback: string) {
  const tokens = tokensForScope(scope)
  const expiresAt = tokens ? Date.parse(tokens.accessExpireTime) : Number.NaN
  if (!Number.isFinite(expiresAt) || expiresAt > Date.now() + 5_000) return fallback
  return await refreshSession(scope) ?? fallback
}

async function refreshSession(scope: SessionScope): Promise<string | null> {
  if (refreshes[scope]) return refreshes[scope]
  const pending = rotateSession(scope)
  refreshes[scope] = pending
  try {
    return await pending
  } finally {
    delete refreshes[scope]
  }
}

async function rotateSession(scope: SessionScope): Promise<string | null> {
  const current = tokensForScope(scope)
  if (!current?.refreshToken || Date.parse(current.refreshExpireTime) <= Date.now()) {
    clearSessionScope(scope)
    return null
  }
  const { response, payload } = await request<SessionTokens>('/api/auth/refresh', {
    method: 'POST',
    body: JSON.stringify({ refreshToken: current.refreshToken }),
  })
  if (!response.ok || payload.code !== 'OK' || !payload.data?.accessToken) {
    clearSessionScope(scope)
    return null
  }
  replaceSessionTokens(scope, payload.data)
  return payload.data.accessToken
}
