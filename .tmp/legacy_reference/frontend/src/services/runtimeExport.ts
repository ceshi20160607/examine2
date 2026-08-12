import { apiDownload, apiRequest } from './api'
import type {
  RuntimeExportCreateInput,
  RuntimeExportTask,
  RuntimeExportTaskPage,
} from '@/types/runtimeExport'

function base(systemId: string, moduleCode: string) {
  return `/api/v1/systems/${encodeURIComponent(systemId)}/runtime/modules/${encodeURIComponent(moduleCode)}/exports`
}

export const runtimeExportApi = {
  create: (systemId: string, moduleCode: string, body: RuntimeExportCreateInput) =>
    apiRequest<RuntimeExportTask>(base(systemId, moduleCode), { method: 'POST', body }),
  history: (systemId: string, moduleCode: string, page = 1, size = 20) =>
    apiRequest<RuntimeExportTaskPage>(`${base(systemId, moduleCode)}?page=${page}&size=${size}`),
  task: (systemId: string, moduleCode: string, exportId: string) =>
    apiRequest<RuntimeExportTask>(`${base(systemId, moduleCode)}/${encodeURIComponent(exportId)}`),
  resultWorkbook: (systemId: string, moduleCode: string, exportId: string) =>
    apiDownload(`${base(systemId, moduleCode)}/${encodeURIComponent(exportId)}/result.xlsx`),
}
