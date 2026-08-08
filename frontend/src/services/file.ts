import { apiDownload, apiRequest, apiRequestForm, apiRequestRaw } from './api'
import type {
  FileAsset,
  FileAssetPage,
  FileAssetQuery,
  FileReferenceInput,
  FileStorageStatus,
  MultipartUploadAbortResult,
  MultipartUploadInitInput,
  MultipartUploadPart,
  MultipartUploadSession,
  RecordFileItem,
  RecordFilePage,
} from '@/types/file'

function root(systemId: string) {
  return `/api/v1/systems/${encodeURIComponent(systemId)}/files`
}

function assetPath(systemId: string, fileId: string) {
  return `${root(systemId)}/${encodeURIComponent(fileId)}`
}

function recordRoot(systemId: string, moduleCode: string, recordId: string) {
  return `/api/v1/systems/${encodeURIComponent(systemId)}/runtime/modules/${encodeURIComponent(moduleCode)}/records/${encodeURIComponent(recordId)}/files`
}

export const fileApi = {
  list(systemId: string, query: FileAssetQuery = {}) {
    const params = new URLSearchParams({
      page: String(query.page ?? 1),
      size: String(query.size ?? 20),
    })
    if (query.keyword?.trim()) params.set('keyword', query.keyword.trim())
    if (query.mediaType?.trim()) params.set('mediaType', query.mediaType.trim())
    return apiRequest<FileAssetPage>(`${root(systemId)}?${params}`)
  },
  storageStatus(systemId: string) {
    return apiRequest<FileStorageStatus>(`${root(systemId)}/storage-status`)
  },
  upload(systemId: string, file: File) {
    const form = new FormData()
    form.append('file', file, file.name)
    return apiRequestForm<FileAsset>(root(systemId), form, {
      method: 'POST',
      idempotencyKey: crypto.randomUUID(),
    })
  },
  get(systemId: string, fileId: string) {
    return apiRequest<FileAsset>(assetPath(systemId, fileId))
  },
  download(systemId: string, fileId: string) {
    return apiDownload(`${assetPath(systemId, fileId)}/content`)
  },
  preview(systemId: string, fileId: string) {
    return apiDownload(`${assetPath(systemId, fileId)}/preview`)
  },
  thumbnail(systemId: string, fileId: string, maxWidth = 64, maxHeight = 64) {
    const params = new URLSearchParams({ maxWidth: String(maxWidth), maxHeight: String(maxHeight) })
    return apiDownload(`${assetPath(systemId, fileId)}/thumbnail?${params}`)
  },
  thumbnailUrl(systemId: string, fileId: string, maxWidth = 64, maxHeight = 64) {
    const params = new URLSearchParams({ maxWidth: String(maxWidth), maxHeight: String(maxHeight) })
    return `${assetPath(systemId, fileId)}/thumbnail?${params}`
  },
  initMultipart(systemId: string, input: MultipartUploadInitInput) {
    return apiRequest<MultipartUploadSession>(`${root(systemId)}/multipart-uploads`, {
      method: 'POST',
      body: input,
      idempotencyKey: crypto.randomUUID(),
    })
  },
  uploadMultipartPart(
    systemId: string,
    uploadId: string,
    partNumber: number,
    content: Blob,
    sha256: string,
    signal?: AbortSignal,
  ) {
    return apiRequestRaw<MultipartUploadPart>(
      `${root(systemId)}/multipart-uploads/${encodeURIComponent(uploadId)}/parts/${partNumber}`,
      content,
      {
        method: 'PUT',
        headers: { 'Content-Type': 'application/octet-stream', 'X-Part-SHA256': sha256 },
        signal,
      },
    )
  },
  completeMultipart(systemId: string, uploadId: string, sha256: string) {
    return apiRequest<FileAsset>(
      `${root(systemId)}/multipart-uploads/${encodeURIComponent(uploadId)}:complete`,
      { method: 'POST', body: { sha256 }, idempotencyKey: crypto.randomUUID() },
    )
  },
  abortMultipart(systemId: string, uploadId: string) {
    return apiRequest<MultipartUploadAbortResult>(
      `${root(systemId)}/multipart-uploads/${encodeURIComponent(uploadId)}`,
      { method: 'DELETE', idempotencyKey: crypto.randomUUID() },
    )
  },
  addReference(systemId: string, fileId: string, input: FileReferenceInput) {
    return apiRequest<FileAsset>(`${assetPath(systemId, fileId)}/references`, {
      method: 'POST',
      body: input,
      idempotencyKey: crypto.randomUUID(),
    })
  },
  removeReference(systemId: string, fileId: string, input: FileReferenceInput) {
    return apiRequest<FileAsset>(`${assetPath(systemId, fileId)}/references`, {
      method: 'DELETE',
      body: input,
      idempotencyKey: crypto.randomUUID(),
    })
  },
  delete(systemId: string, fileId: string) {
    return apiRequest<FileAsset>(assetPath(systemId, fileId), {
      method: 'DELETE',
      idempotencyKey: crypto.randomUUID(),
    })
  },
  recordFiles(systemId: string, moduleCode: string, recordId: string, page = 1, size = 20) {
    const params = new URLSearchParams({ page: String(page), size: String(size) })
    return apiRequest<RecordFilePage>(`${recordRoot(systemId, moduleCode, recordId)}?${params}`)
  },
  uploadRecordFile(systemId: string, moduleCode: string, recordId: string, file: File) {
    const form = new FormData()
    form.append('file', file, file.name)
    return apiRequestForm<RecordFileItem>(recordRoot(systemId, moduleCode, recordId), form, {
      method: 'POST',
      idempotencyKey: crypto.randomUUID(),
    })
  },
  downloadRecordFile(systemId: string, moduleCode: string, recordId: string, fileId: string) {
    return apiDownload(`${recordRoot(systemId, moduleCode, recordId)}/${encodeURIComponent(fileId)}/content`)
  },
  downloadRecordFileBundle(systemId: string, moduleCode: string, recordId: string) {
    return apiDownload(`${recordRoot(systemId, moduleCode, recordId)}:bundle`)
  },
  detachRecordFile(systemId: string, moduleCode: string, recordId: string, fileId: string) {
    return apiRequest<RecordFileItem>(
      `${recordRoot(systemId, moduleCode, recordId)}/${encodeURIComponent(fileId)}`,
      { method: 'DELETE', idempotencyKey: crypto.randomUUID() },
    )
  },
}
