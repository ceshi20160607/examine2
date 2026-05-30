const TOKEN_KEY = 'examine_token'

export function getToken() {
  return localStorage.getItem(TOKEN_KEY) || ''
}

export function setToken(token) {
  if (token) localStorage.setItem(TOKEN_KEY, token)
  else localStorage.removeItem(TOKEN_KEY)
}

export function getBaseUrl() {
  const base = import.meta.env.VITE_API_BASE || ''
  return String(base).replace(/\/$/, '')
}

export function genRequestId() {
  return Math.random().toString(16).slice(2) + Date.now().toString(16)
}

let unauthorizedHandler = null

export function setUnauthorizedHandler(handler) {
  unauthorizedHandler = typeof handler === 'function' ? handler : null
}

export function redirectToLogin() {
  setToken('')
  const current = `${window.location.pathname}${window.location.search}${window.location.hash}`
  const target = { path: '/login', query: {} }
  if (current && current !== '/login') {
    target.query.redirect = current
  }
  if (unauthorizedHandler) unauthorizedHandler(target)
}

let refreshing = null

export async function refreshAuthToken(currentToken) {
  if (refreshing) return refreshing
  refreshing = (async () => {
    const res = await fetch(getBaseUrl() + '/v1/platform/auth/refresh', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-Request-Id': genRequestId(),
        Authorization: `Bearer ${currentToken}`
      },
      body: '{}'
    }).catch(() => null)
    if (!res || res.status === 401) return null
    const data = await readJsonResponse(res).catch(() => null)
    const token = data?.code === 0 && typeof data?.data?.token === 'string' ? data.data.token.trim() : ''
    if (!res.ok || !token) return null
    setToken(token)
    return token
  })().finally(() => {
    refreshing = null
  })
  return refreshing
}

export async function httpRequest(method, path, body, opts = {}) {
  const url = getBaseUrl() + path
  const headers = {
    'Content-Type': 'application/json',
    'X-Request-Id': genRequestId()
  }
  const token = getToken()
  if (token) headers.Authorization = `Bearer ${token}`

  const res = await fetch(url, {
    method,
    headers,
    body: body != null ? JSON.stringify(body) : undefined
  })

  const data = await readJsonResponse(res).catch(() => null)

  if (res.status === 401 || data?.code === 401) {
    const canRetry = (opts.retryOnUnauthorized ?? true) && !!token && path !== '/v1/platform/auth/refresh'
    if (canRetry) {
      const newToken = await refreshAuthToken(token)
      if (newToken) {
        return httpRequest(method, path, body, { retryOnUnauthorized: false })
      }
    }
    redirectToLogin()
    throw new Error('未登录或会话已过期')
  }

  if (!res.ok) {
    throw new Error(data?.message || `HTTP ${res.status}`)
  }

  if (!data || typeof data.code !== 'number') {
    throw new Error('接口响应格式错误')
  }
  if (data.code !== 0) {
    throw new Error(data.message || `接口错误 ${data.code}`)
  }
  return data
}

export const httpGet = (path) => httpRequest('GET', path)
export const httpPost = (path, body) => httpRequest('POST', path, body)
export const httpPut = (path, body) => httpRequest('PUT', path, body)
export const httpDelete = (path) => httpRequest('DELETE', path)

export function buildApiUrl(path) {
  return getBaseUrl() + path
}

async function readJsonResponse(res) {
  const text = await res.text().catch(() => '')
  if (!text) {
    throw new Error(`HTTP ${res.status}: empty response`)
  }
  try {
    return JSON.parse(text)
  } catch {
    throw new Error(`HTTP ${res.status}: invalid JSON`)
  }
}

export async function httpBlob(path, opts = {}) {
  const headers = { 'X-Request-Id': genRequestId() }
  const token = getToken()
  if (token) headers.Authorization = `Bearer ${token}`

  const res = await fetch(buildApiUrl(path), { headers })
  if (res.status === 401) {
    const canRetry = (opts.retryOnUnauthorized ?? true) && !!token
    if (canRetry) {
      const newToken = await refreshAuthToken(token)
      if (newToken) {
        return httpBlob(path, { retryOnUnauthorized: false })
      }
    }
    redirectToLogin()
    throw new Error('未登录或会话已过期')
  }
  if (!res.ok) {
    const message = await readErrorMessage(res)
    throw new Error(message || `HTTP ${res.status}`)
  }
  return {
    blob: await res.blob(),
    filename: parseContentDispositionFilename(res.headers.get('Content-Disposition')),
    contentType: res.headers.get('Content-Type') || ''
  }
}

export function saveBlob(blob, filename = 'download') {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename || 'download'
  document.body.appendChild(a)
  a.click()
  a.remove()
  setTimeout(() => URL.revokeObjectURL(url), 60_000)
}

async function readErrorMessage(res) {
  const text = await res.text().catch(() => '')
  if (!text) return ''
  try {
    const data = JSON.parse(text)
    return data?.message || text
  } catch {
    return text
  }
}

function parseContentDispositionFilename(header) {
  if (!header) return ''
  const utf8 = /filename\*=UTF-8''([^;]+)/i.exec(header)
  if (utf8?.[1]) {
    try {
      return decodeURIComponent(utf8[1].trim())
    } catch {
      return utf8[1].trim()
    }
  }
  const plain = /filename="?([^";]+)"?/i.exec(header)
  return plain?.[1]?.trim() || ''
}
