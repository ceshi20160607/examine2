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
  ) {
    super(message)
  }
}

export async function api<T>(path: string, options: RequestInit = {}, token?: string): Promise<T> {
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
  if (!response.ok || payload.code !== 'OK') {
    throw new ApiError(
      payload.code,
      payload.message || '请求失败',
      payload.data?.traceId ?? payload.requestId,
      payload.data && typeof payload.data === 'object' ? payload.data as Record<string, unknown> : undefined,
    )
  }
  return payload.data as T
}
