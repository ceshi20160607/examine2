import { apiDownload, apiRequest, apiRequestForm } from './api'
import type {
  RuntimeImportBatch,
  RuntimeImportBatchPage,
  RuntimeImportPreviewInput,
  RuntimeImportTemplate,
} from '@/types/runtimeImport'

function base(systemId: string, moduleCode: string) {
  return `/api/v1/systems/${encodeURIComponent(systemId)}/runtime/modules/${encodeURIComponent(moduleCode)}`
}

export const runtimeImportApi = {
  template: (systemId: string, moduleCode: string) =>
    apiRequest<RuntimeImportTemplate>(`${base(systemId, moduleCode)}/imports/template`),
  preview: (systemId: string, moduleCode: string, body: RuntimeImportPreviewInput) =>
    apiRequest<RuntimeImportBatch>(`${base(systemId, moduleCode)}/imports:preview`, {
      method: 'POST',
      body,
    }),
  templateWorkbook: (systemId: string, moduleCode: string) =>
    apiDownload(`${base(systemId, moduleCode)}/imports/template.xlsx`),
  previewWorkbook: (systemId: string, moduleCode: string, file: File,
                    mode: RuntimeImportPreviewInput['mode'], matchFieldCode?: string) => {
    const body = new FormData()
    body.append('file', file)
    body.append('mode', mode)
    if (matchFieldCode) body.append('matchFieldCode', matchFieldCode)
    return apiRequestForm<RuntimeImportBatch>(`${base(systemId, moduleCode)}/imports:preview-xlsx`, body, {
      method: 'POST',
    })
  },
  history: (systemId: string, moduleCode: string, page = 1, size = 20) =>
    apiRequest<RuntimeImportBatchPage>(
      `${base(systemId, moduleCode)}/imports?page=${page}&size=${size}`,
    ),
  batch: (systemId: string, moduleCode: string, batchId: string) =>
    apiRequest<RuntimeImportBatch>(`${base(systemId, moduleCode)}/imports/${encodeURIComponent(batchId)}`),
  commit: (systemId: string, moduleCode: string, batchId: string) =>
    apiRequest<RuntimeImportBatch>(
      `${base(systemId, moduleCode)}/imports/${encodeURIComponent(batchId)}:commit`,
      { method: 'POST' },
    ),
  rollback: (systemId: string, moduleCode: string, batchId: string) =>
    apiRequest<RuntimeImportBatch>(
      `${base(systemId, moduleCode)}/imports/${encodeURIComponent(batchId)}:rollback`,
      { method: 'POST' },
    ),
  errorWorkbook: (systemId: string, moduleCode: string, batchId: string) =>
    apiDownload(`${base(systemId, moduleCode)}/imports/${encodeURIComponent(batchId)}/errors.xlsx`),
}
