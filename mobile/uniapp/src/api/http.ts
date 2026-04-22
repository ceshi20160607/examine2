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

function onUnauthorized() {
  uni.removeStorageSync('token')
  uni.reLaunch({ url: '/pages/auth/login' })
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
  data?: any
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
          onUnauthorized()
          reject(new Error('unauthorized'))
          return
        }
        if (!r || typeof r.code !== 'number') {
          reject(new Error('bad api response'))
          return
        }
        if (r.code !== 0) {
          reject(new Error(r.message || `api error: ${r.code}`))
          return
        }
        resolve(r)
      },
      fail: (err) => reject(err)
    })
  })
}

