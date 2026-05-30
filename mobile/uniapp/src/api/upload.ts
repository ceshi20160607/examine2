import {
  buildApiUrl,
  buildAuthHeaders,
  downloadAuthedToTemp,
  getToken,
  httpGet,
  httpPost,
  onUnauthorized,
  refreshAuthToken
} from '@/api/http'
import type { ApiResult } from '@/api/http'
import { idToString, type IdValue } from '@/utils/id'

export type UploadRow = { id: string; originalName?: string; fileSize?: number; contentType?: string }

function isPickerUserCancel(err: any): boolean {
  const s = String(err?.errMsg ?? err?.message ?? err ?? '').toLowerCase()
  return s.includes('cancel') || s.includes('取消')
}

export async function pickSingleFilePath(): Promise<string> {
  // Prefer chooseFile (H5/App). Fallback chooseMessageFile (mp-weixin chat file). Last chooseImage.
  const u: any = uni as any

  const tryChooseFile = async (): Promise<string | null> => {
    if (typeof u.chooseFile !== 'function') return null
    try {
      const chooseRes: any = await new Promise((resolve, reject) => {
        u.chooseFile({ count: 1, success: resolve, fail: reject })
      })
      return chooseRes?.tempFilePaths?.[0] || null
    } catch (e) {
      if (isPickerUserCancel(e)) throw new Error('已取消选择')
      return null
    }
  }

  const tryChooseMessageFile = async (): Promise<string | null> => {
    if (typeof u.chooseMessageFile !== 'function') return null
    try {
      const chooseRes: any = await new Promise((resolve, reject) => {
        u.chooseMessageFile({ count: 1, type: 'file', success: resolve, fail: reject })
      })
      const file = chooseRes?.tempFiles?.[0]
      return file?.path || file?.tempFilePath || null
    } catch (e) {
      if (isPickerUserCancel(e)) throw new Error('已取消选择')
      return null
    }
  }

  const tryChooseImage = async (): Promise<string | null> => {
    if (typeof u.chooseImage !== 'function') return null
    try {
      const chooseRes: any = await new Promise((resolve, reject) => {
        u.chooseImage({ count: 1, success: resolve, fail: reject })
      })
      return chooseRes?.tempFilePaths?.[0] || null
    } catch (e) {
      if (isPickerUserCancel(e)) throw new Error('已取消选择')
      return null
    }
  }

  let fp = await tryChooseFile()
  if (fp) return fp
  fp = await tryChooseMessageFile()
  if (fp) return fp
  fp = await tryChooseImage()
  if (fp) return fp

  throw new Error('当前平台不支持选择文件')
}

export async function uploadOneFile(
  filePath: string,
  opts?: { retryOnUnauthorized?: boolean }
): Promise<ApiResult<{ fileId: string }>> {
  const uploadRes: any = await new Promise((resolve, reject) => {
    uni.uploadFile({
      url: buildApiUrl('/v1/system/uploads'),
      filePath,
      name: 'file',
      header: buildAuthHeaders(),
      success: resolve,
      fail: reject
    })
  })

  const statusCode = Number(uploadRes?.statusCode || 0)
  const raw = uploadRes?.data
  let json: any
  if (typeof raw === 'string') {
    try {
      json = JSON.parse(raw)
    } catch {
      if (statusCode === 401) {
        json = null
      } else if (statusCode < 200 || statusCode >= 300) {
        throw new Error(`上传失败 HTTP ${statusCode || 'unknown'}`)
      } else {
        throw new Error('上传响应不是 JSON（请检查网关/地址配置）')
      }
    }
  } else {
    json = raw
  }
  if (statusCode === 401 || json?.code === 401) {
    const token = getToken()
    const canRetry = (opts?.retryOnUnauthorized ?? true) && !!token
    if (canRetry) {
      const newToken = await refreshAuthToken(token as string)
      if (newToken) {
        return uploadOneFile(filePath, { retryOnUnauthorized: false })
      }
    }
    onUnauthorized()
    throw new Error('未登录或会话已过期')
  }
  if (statusCode < 200 || statusCode >= 300) {
    throw new Error(json?.message || `上传失败 HTTP ${statusCode || 'unknown'}`)
  }
  if (!json || json.code !== 0) {
    throw new Error(json?.message || '上传失败')
  }
  return json as ApiResult<{ fileId: string }>
}

export function pageUploads(page = 1, size = 20): Promise<ApiResult<any>> {
  return httpGet<any>(`/v1/system/uploads/page?page=${encodeURIComponent(String(page))}&size=${encodeURIComponent(String(size))}`)
}

export function deleteUpload(fileId: IdValue): Promise<ApiResult<any>> {
  return httpPost<any>(`/v1/system/uploads/${pathId(fileId)}/delete`, {})
}

export function buildUploadViewUrl(fileId: IdValue): string {
  return buildApiUrl(`/v1/system/uploads/${pathId(fileId)}/view`)
}

export function buildUploadDownloadUrl(fileId: IdValue): string {
  return buildApiUrl(`/v1/system/uploads/${pathId(fileId)}/download`)
}

export async function downloadUploadToTemp(url: string): Promise<string> {
  return downloadAuthedToTemp(url, '下载失败')
}

function pathId(value: IdValue): string {
  return encodeURIComponent(idToString(value))
}

