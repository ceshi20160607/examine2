export interface FileReference {
  targetType: string
  targetId: string
  createdByMemberId: string
  createdAt: string
}

export interface FileAsset {
  id: string
  originalName: string
  mediaType: string
  size: number
  sha256: string
  uploaderMemberId: string
  createdAt: string
  version: number
  references: FileReference[]
}

export interface FileAssetPage {
  items: FileAsset[]
  page: number
  size: number
  total: number
  totalPages: number
}

export interface FileAssetQuery {
  page?: number
  size?: number
  keyword?: string
  mediaType?: string
}

export interface FileStorageStatus {
  mode: 'LOCAL' | 'S3'
  location: string
  maxSingleUploadBytes: number
  maxMultipartUploadBytes: number
  maxPartBytes: number
  available: boolean
}

export interface MultipartUploadInitInput {
  originalName: string
  mediaType: string
  sizeBytes: number
  sha256: string
}

export interface MultipartUploadSession {
  uploadId: string
  originalName: string
  mediaType: string
  sizeBytes: number
  sha256: string
  partSizeBytes: number
  partCount: number
  expiresAt: string
  status: 'OPEN'
}

export interface MultipartUploadPart {
  uploadId: string
  partNumber: number
  sizeBytes: number
  sha256: string
  replay: boolean
  uploadedPartCount: number
  partCount: number
}

export interface MultipartUploadAbortResult {
  uploadId: string
  status: 'ABORTED'
}

export interface FileReferenceInput {
  targetType: string
  targetId: string
}

export interface RecordFileItem {
  fileId: string
  originalName: string
  mediaType: string
  sizeBytes: number
  sha256: string
  uploaderMemberId: string
  createdAt: string
  referencedByMemberId: string
  referencedAt: string
  downloadUrl: string
}

export interface RecordFilePage {
  items: RecordFileItem[]
  page: number
  size: number
  total: number
}
