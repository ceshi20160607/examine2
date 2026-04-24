import { buildApiUrl, buildAuthHeaders, httpGet, httpPost } from '@/api/http'
import type { ApiResult } from '@/api/http'

export type UploadRow = { id: number; originalName?: string; fileSize?: number; contentType?: string }

export async function uploadOneFile(filePath: string): Promise<ApiResult<{ fileId: number }>> {
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

  const raw = uploadRes?.data
  let json: any
  if (typeof raw === 'string') {
    try {
      json = JSON.parse(raw)
    } catch {
      throw new Error('上传响应不是 JSON（请检查网关/地址配置）')
    }
  } else {
    json = raw
  }
  if (!json || json.code !== 0) {
    throw new Error(json?.message || '上传失败')
  }
  return json as ApiResult<{ fileId: number }>
}

export function pageUploads(page = 1, size = 20): Promise<ApiResult<any>> {
  return httpGet<any>(`/v1/system/uploads/page?page=${page}&size=${size}`)
}

export function deleteUpload(fileId: number): Promise<ApiResult<any>> {
  return httpPost<any>(`/v1/system/uploads/${fileId}/delete`, {})
}

export function buildUploadViewUrl(fileId: number): string {
  return buildApiUrl(`/v1/system/uploads/${fileId}/view`)
}

export function buildUploadDownloadUrl(fileId: number): string {
  return buildApiUrl(`/v1/system/uploads/${fileId}/download`)
}

export async function downloadUploadToTemp(url: string): Promise<string> {
  const dl: any = await new Promise((resolve, reject) => {
    uni.downloadFile({
      url,
      header: buildAuthHeaders(),
      success: resolve,
      fail: reject
    })
  })
  const tempFilePath = dl?.tempFilePath
  if (!tempFilePath) throw new Error('下载失败')
  return tempFilePath
}

