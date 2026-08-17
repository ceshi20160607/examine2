export interface ApiEnvelope<T> {
  code: string
  message: string
  data: T
}

export class ApiError extends Error {
  constructor(
    public readonly code: string,
    message: string,
    public readonly traceId?: string,
  ) {
    super(message)
  }
}

export async function api<T>(path: string, options: RequestInit = {}, token?: string): Promise<T> {
  const headers = new Headers(options.headers)
  if (options.body && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json')
  if (token) headers.set('Authorization', `Bearer ${token}`)
  const response = await fetch(path, { ...options, headers })
  const payload = (await response.json().catch(() => ({
    code: 'NETWORK_RESPONSE_INVALID',
    message: '服务响应格式不正确',
    data: null,
  }))) as ApiEnvelope<T> & { data?: { traceId?: string } }
  if (!response.ok || payload.code !== 'OK') {
    throw new ApiError(payload.code, payload.message || '请求失败', payload.data?.traceId)
  }
  return payload.data as T
}
