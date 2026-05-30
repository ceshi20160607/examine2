import { getBaseURL } from '@/config/env'
import { clearSessionPayload } from '@/store/context'

export type ApiResult<T> = {
  code: number
  message: string
  data: T
  requestId?: string
}

function genRequestId(): string {
  return Math.random().toString(16).slice(2) + Date.now().toString(16)
}

export function getToken(): string | null {
  const t = uni.getStorageSync('token')
  return typeof t === 'string' && t.trim() ? t.trim() : null
}

export function buildApiUrl(path: string): string {
  const p = String(path || '')
  if (!p.startsWith('/')) {
    return getBaseURL().replace(/\/$/, '') + '/' + p
  }
  return getBaseURL().replace(/\/$/, '') + p
}

/**
 * 给 uni.request / uploadFile / downloadFile 用：与 httpRequest 一样携带 X-Request-Id + Bearer token。
 */
export function buildAuthHeaders(extra?: Record<string, string>): Record<string, string> {
  const requestId = genRequestId()
  const header: Record<string, string> = {
    'X-Request-Id': requestId
  }
  const token = getToken()
  if (token) {
    header['Authorization'] = `Bearer ${token}`
  }
  if (extra) {
    for (const [k, v] of Object.entries(extra)) {
      if (v != null) header[k] = v
    }
  }
  return header
}

export async function downloadAuthedToTemp(
  url: string,
  fallbackMessage = '下载失败',
  opts?: { retryOnUnauthorized?: boolean }
): Promise<string> {
  const dl: any = await new Promise((resolve, reject) => {
    uni.downloadFile({
      url,
      header: buildAuthHeaders(),
      success: resolve,
      fail: reject
    })
  })
  const statusCode = Number(dl?.statusCode || 0)
  if (statusCode === 401) {
    const token = getToken()
    const canRetry = (opts?.retryOnUnauthorized ?? true) && !!token
    if (canRetry) {
      const newToken = await refreshAuthToken(token as string)
      if (newToken) {
        return downloadAuthedToTemp(url, fallbackMessage, { retryOnUnauthorized: false })
      }
    }
    onUnauthorized()
    throw new Error('未登录或会话已过期')
  }
  if (statusCode < 200 || statusCode >= 300) {
    throw new Error(`${fallbackMessage} HTTP ${statusCode || 'unknown'}`)
  }
  const tempFilePath = dl?.tempFilePath
  if (!tempFilePath) {
    throw new Error(fallbackMessage)
  }
  return tempFilePath
}

function failMessage(err: any, fallback: string) {
  const m =
    (err && (err.errMsg as string)) ||
    (err && (err.message as string)) ||
    (typeof err === 'string' ? err : '') ||
    ''
  return m?.trim() ? m : fallback
}

let unauthorizedHandler: (() => void) | null = null

export function setUnauthorizedHandler(handler: (() => void) | null) {
  unauthorizedHandler = typeof handler === 'function' ? handler : null
}

export function onUnauthorized() {
  uni.removeStorageSync('token')
  clearSessionPayload()
  if (unauthorizedHandler) {
    unauthorizedHandler()
    return
  }
  uni.reLaunch({ url: '/pages/auth/login' })
}

let refreshing: Promise<string | null> | null = null

export function refreshAuthToken(currentToken: string): Promise<string | null> {
  if (refreshing) return refreshing
  const requestId = genRequestId()
  const url = getBaseURL().replace(/\/$/, '') + '/v1/platform/auth/refresh'
  refreshing = new Promise((resolve) => {
    uni.request({
      url,
      method: 'POST',
      data: {},
      header: {
        'X-Request-Id': requestId,
        Authorization: `Bearer ${currentToken}`,
        'Content-Type': 'application/json'
      },
      success: (res) => {
        try {
          const r = res.data as any
          const newToken = r?.data?.token
          if (res.statusCode === 200 && r?.code === 0 && typeof newToken === 'string' && newToken.trim()) {
            const t = newToken.trim()
            uni.setStorageSync('token', t)
            resolve(t)
            return
          }
        } catch {
          // ignore
        }
        resolve(null)
      },
      fail: () => resolve(null),
      complete: () => {
        refreshing = null
      }
    })
  })
  return refreshing
}

function toast(message: string) {
  try {
    uni.showToast({ title: message || '请求失败', icon: 'none', duration: 2500 })
  } catch {
    // ignore
  }
}

function parseApiResult<T>(value: unknown): ApiResult<T> | null {
  if (typeof value === 'string') {
    const text = value.trim()
    if (!text) return null
    try {
      return JSON.parse(text) as ApiResult<T>
    } catch {
      return null
    }
  }
  if (value && typeof value === 'object') {
    return value as ApiResult<T>
  }
  return null
}

export async function httpGet<T>(path: string): Promise<ApiResult<T>> {
  return httpRequest<T>('GET', path)
}

export async function httpPost<T>(path: string, body?: any): Promise<ApiResult<T>> {
  return httpRequest<T>('POST', path, body)
}

export async function httpPut<T>(path: string, body?: any): Promise<ApiResult<T>> {
  return httpRequest<T>('PUT', path, body)
}

export async function httpDelete<T>(path: string): Promise<ApiResult<T>> {
  return httpRequest<T>('DELETE', path)
}

export async function httpRequest<T>(
  method: 'GET' | 'POST' | 'PUT' | 'DELETE',
  path: string,
  data?: any,
  opts?: { retryOnUnauthorized?: boolean }
): Promise<ApiResult<T>> {
  const token = getToken()
  const requestId = genRequestId()
  const url = getBaseURL().replace(/\/$/, '') + path

  const header: Record<string, string> = {
    'X-Request-Id': requestId
  }
  if (token) {
    header['Authorization'] = `Bearer ${token}`
  }
  if (method !== 'GET') {
    header['Content-Type'] = 'application/json'
  }

  return new Promise((resolve, reject) => {
    uni.request({
      url,
      method,
      data,
      header,
      success: (res) => {
        const r = parseApiResult<T>(res.data)
        if (res.statusCode === 401 || r?.code === 401) {
          const canRetry = (opts?.retryOnUnauthorized ?? true) && !!token
          if (canRetry) {
            refreshAuthToken(token as string)
              .then((newToken) => {
                if (!newToken) {
                  onUnauthorized()
                  reject(new Error('unauthorized'))
                  return
                }
                httpRequest<T>(method, path, data, { retryOnUnauthorized: false }).then(resolve).catch(reject)
              })
              .catch(() => {
                onUnauthorized()
                reject(new Error('unauthorized'))
              })
            return
          }
          onUnauthorized()
          reject(new Error('unauthorized'))
          return
        }
        if (res.statusCode < 200 || res.statusCode >= 300) {
          const msg = r?.message || `HTTP ${res.statusCode || 'unknown'}`
          toast(msg)
          reject(new Error(msg))
          return
        }
        if (!r || typeof r.code !== 'number') {
          toast('接口响应格式错误')
          reject(new Error('bad api response'))
          return
        }
        if (r.code !== 0) {
          toast(r.message || `接口错误: ${r.code}`)
          reject(new Error(r.message || `api error: ${r.code}`))
          return
        }
        resolve(r)
      },
      fail: (err) => {
        const msg = failMessage(err, '网络错误')
        toast(msg)
        reject(new Error(msg))
      }
    })
  })
}

