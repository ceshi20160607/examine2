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

function genRequestId() {
  return Math.random().toString(16).slice(2) + Date.now().toString(16)
}

export async function httpRequest(method, path, body) {
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

  let data
  try {
    data = await res.json()
  } catch {
    throw new Error(`HTTP ${res.status}: invalid JSON`)
  }

  if (res.status === 401 || data?.code === 401) {
    setToken('')
    window.location.href = '/login'
    throw new Error('未登录或会话已过期')
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
