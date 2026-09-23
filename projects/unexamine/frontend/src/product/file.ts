import { api, ApiError, type ApiEnvelope } from './api'
import type { BusinessAttachmentView, ControlledFileView, FileUploadChunkView, FileUploadSessionView } from './types'

// 1 MB keeps each request below conservative reverse-proxy/body-buffer limits while large files remain resumable.
const uploadChunkSize = 1024 * 1024

export async function sha256Hex(file: Blob): Promise<string> {
  const digest = await crypto.subtle.digest('SHA-256', await file.arrayBuffer())
  return [...new Uint8Array(digest)].map(value => value.toString(16).padStart(2, '0')).join('')
}

export async function uploadControlledFile(
  file: File,
  token: string,
  onProgress?: (progress: number) => void,
): Promise<ControlledFileView> {
  onProgress?.(10)
  const expectedSha256 = await sha256Hex(file)
  const session = await api<FileUploadSessionView>('/api/files/uploads', {
    method: 'POST',
    body: JSON.stringify({
      originalName: file.name,
      contentType: file.type || 'application/octet-stream',
      expectedSize: file.size,
      expectedSha256,
    }),
  }, token)
  try {
    let offset = session.uploadedSize || 0
    while (offset < file.size) {
      const chunk = file.slice(offset, Math.min(offset + uploadChunkSize, file.size))
      const chunkSha256 = await sha256Hex(chunk)
      const written = await api<FileUploadChunkView>(`/api/files/uploads/${session.id}/chunks?offset=${offset}`, {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/octet-stream',
          'X-Upload-Token': session.uploadToken,
          'X-Chunk-Sha256': chunkSha256,
        },
        body: chunk,
      }, token)
      offset = written.uploadedSize
      onProgress?.(10 + Math.round((offset / file.size) * 80))
    }
    const response = await fetch(`/api/files/uploads/${session.id}/complete`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${token}`, 'X-Upload-Token': session.uploadToken },
    })
    const payload = await response.json() as ApiEnvelope<ControlledFileView> & { data?: { traceId?: string } }
    if (!response.ok || payload.code !== 'OK') {
      throw new ApiError(payload.code, payload.message || '上传失败', payload.data?.traceId ?? payload.requestId)
    }
    onProgress?.(100)
    return payload.data
  } catch (reason) {
    // 主动取消会回收服务端分片；网络中断时由服务端过期清理兜底。
    await api(`/api/files/uploads/${session.id}`, {
      method: 'DELETE', headers: { 'X-Upload-Token': session.uploadToken },
    }, token).catch(() => undefined)
    throw reason
  }
}

export async function referenceToAccount(fileId: number, accountId: number, token: string) {
  return api<ControlledFileView>(`/api/files/${fileId}/references`, {
    method: 'POST',
    body: JSON.stringify({
      ownerType: 'ACCOUNT', ownerId: String(accountId), fieldCode: 'managed_files', referenceType: 'DOCUMENT',
    }),
  }, token)
}

export async function uploadBusinessAttachment(
  file: File,
  path: string,
  token: string,
  onProgress?: (progress: number) => void,
): Promise<BusinessAttachmentView> {
  const controlled = await uploadControlledFile(file, token, value => onProgress?.(Math.round(value * 0.9)))
  try {
    const purpose = file.type.startsWith('image/') ? 'IMAGE' : 'DOCUMENT'
    return await api<BusinessAttachmentView>(`${path}/files/${controlled.id}?purpose=${purpose}`, {
      method: 'POST',
    }, token)
  } catch (reason) {
    // 绑定失败时清理没有业务引用的临时文件；若并发产生引用，服务端会安全拒绝删除。
    await api(`/api/files/${controlled.id}`, { method: 'DELETE' }, token).catch(() => undefined)
    throw reason
  } finally {
    onProgress?.(100)
  }
}

export async function authorizedBlob(path: string, token: string): Promise<Blob> {
  const response = await fetch(path, { headers: { Authorization: `Bearer ${token}` } })
  if (!response.ok) {
    const payload = await response.json().catch(() => ({ code: 'FILE_ACCESS_FAILED', message: '文件访问失败' }))
    throw new ApiError(payload.code, payload.message || '文件访问失败', payload.requestId)
  }
  return response.blob()
}

export function saveBlob(blob: Blob, fileName: string) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  link.click()
  window.setTimeout(() => URL.revokeObjectURL(url), 1000)
}

export function parseCsvHeaders(content: string): string[] {
  const firstLine = content.replace(/^\uFEFF/, '').split(/\r?\n/, 1)[0] || ''
  const values: string[] = []
  let cell = ''
  let quoted = false
  for (let index = 0; index < firstLine.length; index += 1) {
    const current = firstLine[index]
    if (current === '"') {
      if (quoted && firstLine[index + 1] === '"') { cell += '"'; index += 1 } else quoted = !quoted
    } else if (current === ',' && !quoted) { values.push(cell.trim()); cell = '' } else cell += current
  }
  values.push(cell.trim())
  return values.filter(Boolean)
}

export function fileScanLabel(status: string) {
  return ({ CLEAN: '扫描通过', BLOCKED: '已隔离', SCANNING: '扫描中', FAILED: '扫描失败' } as Record<string, string>)[status] || status
}

export function importRowLabel(status: string) {
  return ({ READY: '可执行', ERROR: '预演错误', SKIP: '将跳过', SUCCEEDED: '已成功', FAILED: '执行失败',
    SKIPPED: '已跳过', ROLLED_BACK: '已回滚', ROLLBACK_CONFLICT: '回滚冲突' } as Record<string, string>)[status] || status
}

export function exportStatusLabel(status: string) {
  return ({ QUEUED: '等待执行', RUNNING: '正在生成', COMPLETED: '可下载', FAILED: '导出失败' } as Record<string, string>)[status] || status
}

export function exportScopeLabel(scope: string) {
  return scope === 'SELECTED' ? '当前勾选记录' : '当前筛选结果'
}
