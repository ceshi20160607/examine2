import { idToString } from '../utils/id'
import {
  buildApiUrl,
  genRequestId,
  getToken,
  httpBlob,
  httpGet,
  httpPost,
  redirectToLogin,
  refreshAuthToken
} from './http'

function pathId(value) {
  return encodeURIComponent(idToString(value))
}

export function pageUploads(params = {}) {
  const page = params.page ?? 1
  const size = params.size ?? 20
  const keyword = String(params.keyword || '').trim()
  const qs = [`page=${page}`, `size=${size}`]
  if (keyword) qs.push(`keyword=${encodeURIComponent(keyword)}`)
  return httpGet(`/v1/system/uploads/page?${qs.join('&')}`)
}

export function getUploadDetail(fileId) {
  return httpGet(`/v1/system/uploads/${pathId(fileId)}`)
}

export function deleteUpload(fileId) {
  return httpPost(`/v1/system/uploads/${pathId(fileId)}/delete`)
}

export function fetchUploadViewBlob(fileId) {
  return httpBlob(`/v1/system/uploads/${pathId(fileId)}/view`)
}

export function fetchUploadDownloadBlob(fileId) {
  return httpBlob(`/v1/system/uploads/${pathId(fileId)}/download`)
}

export async function uploadFile(file, opts = {}) {
  const url = buildApiUrl('/v1/system/uploads')
  const headers = { 'X-Request-Id': genRequestId() }
  const token = getToken()
  if (token) headers.Authorization = `Bearer ${token}`

  const form = new FormData()
  form.append('file', file)

  const res = await fetch(url, { method: 'POST', headers, body: form })
  const data = await readUploadResponse(res, { allowInvalid: !res.ok })
  if (res.status === 401 || data?.code === 401) {
    const canRetry = (opts.retryOnUnauthorized ?? true) && !!token
    if (canRetry) {
      const newToken = await refreshAuthToken(token)
      if (newToken) {
        return uploadFile(file, { retryOnUnauthorized: false })
      }
    }
    redirectToLogin()
    throw new Error('未登录或会话已过期')
  }
  if (!res.ok) {
    throw new Error(data?.message || `上传失败 HTTP ${res.status}`)
  }
  if (!data || data.code !== 0) {
    throw new Error(data?.message || `上传失败 ${res.status}`)
  }
  return data
}

async function readUploadResponse(res, opts = {}) {
  const text = await res.text().catch(() => '')
  if (!text) return null
  try {
    return JSON.parse(text)
  } catch {
    if (opts.allowInvalid) return null
    throw new Error(`上传响应不是 JSON（HTTP ${res.status}）`)
  }
}
