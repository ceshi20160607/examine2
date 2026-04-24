import { getBaseURL } from '@/config/env'

export type ApiResult<T> = {
  code: number
  message: string
  data: T
  requestId?: string
}

function genRequestId(): string {
  return Math.random().toString(16).slice(2) + Date.now().toString(16)
}

function getToken(): string | null {
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

function failMessage(err: any, fallback: string) {
  const m =
    (err && (err.errMsg as string)) ||
    (err && (err.message as string)) ||
    (typeof err === 'string' ? err : '') ||
    ''
  return m?.trim() ? m : fallback
}

function onUnauthorized() {
  uni.removeStorageSync('token')
  uni.reLaunch({ url: '/pages/auth/login' })
}

let refreshing: Promise<string | null> | null = null

function refreshToken(currentToken: string): Promise<string | null> {
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

export async function httpGet<T>(path: string): Promise<ApiResult<T>> {
  return httpRequest<T>('GET', path)
}

export async function httpPost<T>(path: string, body?: any): Promise<ApiResult<T>> {
  return httpRequest<T>('POST', path, body)
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
        const r = res.data as ApiResult<T>
        if (res.statusCode === 401 || r?.code === 401) {
          const canRetry = (opts?.retryOnUnauthorized ?? true) && !!token
          if (canRetry) {
            refreshToken(token as string)
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

