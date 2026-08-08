export interface ApiErrorItem {
  code: string
  path?: string
  message: string
}

export interface ApiEnvelope<T> {
  code: string
  message: string
  data: T
  requestId: string
  traceId: string
  errors: ApiErrorItem[]
}

export class ApiRequestError extends Error {
  readonly status: number
  readonly code: string
  readonly requestId: string
  readonly errors: ApiErrorItem[]
  readonly data: unknown

  constructor(status: number, envelope: ApiEnvelope<unknown>) {
    super(envelope.message || '请求失败')
    this.name = 'ApiRequestError'
    this.status = status
    this.code = envelope.code
    this.requestId = envelope.requestId
    this.errors = envelope.errors ?? []
    this.data = envelope.data
  }
}

type RequestOptions = Omit<RequestInit, 'body'> & {
  body?: unknown
  idempotencyKey?: string
}

export interface ApiDownload {
  blob: Blob
  filename: string
  mediaType: string
}

function csrfToken(): string | undefined {
  return document.cookie
    .split('; ')
    .find((item) => item.startsWith('EXAMINE_CSRF='))
    ?.split('=')[1]
}

function requestHeaders(options: Pick<RequestOptions, 'headers' | 'method' | 'idempotencyKey'>, accept: string) {
  const headers = new Headers(options.headers)
  headers.set('Accept', accept)
  headers.set('X-Request-ID', crypto.randomUUID())
  if (options.idempotencyKey) {
    headers.set('Idempotency-Key', options.idempotencyKey)
  }
  const csrf = csrfToken()
  if (csrf && options.method && !['GET', 'HEAD', 'OPTIONS'].includes(options.method)) {
    headers.set('X-CSRF-Token', decodeURIComponent(csrf))
  }
  return headers
}

async function responseEnvelope<T>(response: Response): Promise<ApiEnvelope<T>> {
  try {
    return await response.json() as ApiEnvelope<T>
  } catch {
    return {
      code: response.ok ? 'INVALID_RESPONSE' : 'HTTP_ERROR',
      message: response.ok ? '服务返回了无法识别的响应' : `请求失败（HTTP ${response.status}）`,
      data: undefined as T,
      requestId: response.headers.get('X-Request-ID') ?? '',
      traceId: '',
      errors: [],
    }
  }
}

function assertEnvelope<T>(response: Response, envelope: ApiEnvelope<T>) {
  if (!response.ok || envelope.code !== 'OK') {
    throw new ApiRequestError(response.status, envelope as ApiEnvelope<unknown>)
  }
  return envelope.data
}

export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const headers = requestHeaders(options, 'application/json')
  if (options.body !== undefined) {
    headers.set('Content-Type', 'application/json')
  }

  const response = await fetch(path, {
    ...options,
    headers,
    credentials: 'include',
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
  })
  return assertEnvelope(response, await responseEnvelope<T>(response))
}

export async function apiRequestForm<T>(
  path: string,
  body: FormData,
  options: Omit<RequestOptions, 'body'> = {},
): Promise<T> {
  const headers = requestHeaders(options, 'application/json')
  const response = await fetch(path, {
    ...options,
    headers,
    credentials: 'include',
    body,
  })
  return assertEnvelope(response, await responseEnvelope<T>(response))
}

export async function apiRequestRaw<T>(
  path: string,
  body: BodyInit,
  options: Omit<RequestOptions, 'body'> = {},
): Promise<T> {
  const headers = requestHeaders(options, 'application/json')
  const response = await fetch(path, {
    ...options,
    headers,
    credentials: 'include',
    body,
  })
  return assertEnvelope(response, await responseEnvelope<T>(response))
}

export async function apiDownload(path: string, options: Omit<RequestOptions, 'body'> = {}): Promise<ApiDownload> {
  const headers = requestHeaders(options, '*/*')
  const response = await fetch(path, {
    ...options,
    headers,
    credentials: 'include',
  })
  if (!response.ok) {
    throw new ApiRequestError(response.status, await responseEnvelope<unknown>(response))
  }
  return {
    blob: await response.blob(),
    filename: downloadFilename(response.headers.get('Content-Disposition')),
    mediaType: response.headers.get('Content-Type') ?? 'application/octet-stream',
  }
}

function downloadFilename(disposition: string | null) {
  if (!disposition) return 'download'
  const encoded = disposition.match(/filename\*=UTF-8''([^;]+)/i)?.[1]
  if (encoded) {
    try {
      return decodeURIComponent(encoded)
    } catch {
      return encoded
    }
  }
  return disposition.match(/filename="?([^";]+)"?/i)?.[1] ?? 'download'
}
