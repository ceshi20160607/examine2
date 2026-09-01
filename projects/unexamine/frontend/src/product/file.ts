import { api, ApiError, type ApiEnvelope } from './api'
import type { ControlledFileView, FileUploadSessionView } from './types'

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
  onProgress?.(45)
  const response = await fetch(`/api/files/uploads/${session.id}/content`, {
    method: 'PUT',
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/octet-stream',
      'X-Upload-Token': session.uploadToken,
    },
    body: file,
  })
  const payload = await response.json() as ApiEnvelope<ControlledFileView> & { data?: { traceId?: string } }
  if (!response.ok || payload.code !== 'OK') {
    throw new ApiError(payload.code, payload.message || '上传失败', payload.data?.traceId ?? payload.requestId)
  }
  onProgress?.(100)
  return payload.data
}

export async function referenceToAccount(fileId: number, accountId: number, token: string) {
  return api<ControlledFileView>(`/api/files/${fileId}/references`, {
    method: 'POST',
    body: JSON.stringify({
      ownerType: 'ACCOUNT', ownerId: String(accountId), fieldCode: 'managed_files', referenceType: 'DOCUMENT',
    }),
  }, token)
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
