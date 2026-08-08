import { apiDownload, apiRequest } from './api'
import type {
  PrintTemplate,
  RuntimePrintPreview,
  RuntimePrintTask,
  RuntimePrintTaskPage,
  RuntimePrintTemplate,
} from '@/types/print'

function adminBase(systemId: string, moduleId: string) {
  return `/api/v1/systems/${encodeURIComponent(systemId)}/admin/config/modules/${encodeURIComponent(moduleId)}/print-templates`
}

function recordBase(systemId: string, moduleCode: string, recordId: string) {
  return `/api/v1/systems/${encodeURIComponent(systemId)}/runtime/modules/${encodeURIComponent(moduleCode)}/records/${encodeURIComponent(recordId)}`
}

function key() {
  return crypto.randomUUID()
}

export const printTemplateApi = {
  list: (systemId: string, moduleId: string) =>
    apiRequest<PrintTemplate[]>(adminBase(systemId, moduleId)),
  create: (systemId: string, moduleId: string, body: unknown) =>
    apiRequest<PrintTemplate>(adminBase(systemId, moduleId), { method: 'POST', body, idempotencyKey: key() }),
  update: (systemId: string, moduleId: string, templateId: string, body: unknown) =>
    apiRequest<PrintTemplate>(`${adminBase(systemId, moduleId)}/${encodeURIComponent(templateId)}`, { method: 'PUT', body }),
  publish: (systemId: string, moduleId: string, templateId: string, expectedVersion: number) =>
    apiRequest<PrintTemplate>(`${adminBase(systemId, moduleId)}/${encodeURIComponent(templateId)}:publish`, {
      method: 'POST',
      body: { expectedVersion },
      idempotencyKey: key(),
    }),
}

export const runtimePrintApi = {
  templates: (systemId: string, moduleCode: string, recordId: string) =>
    apiRequest<RuntimePrintTemplate[]>(`${recordBase(systemId, moduleCode, recordId)}/print-templates`),
  preview: (systemId: string, moduleCode: string, recordId: string, templateCode: string) =>
    apiRequest<RuntimePrintPreview>(`${recordBase(systemId, moduleCode, recordId)}/print-preview`, {
      method: 'POST', body: { templateCode },
    }),
  create: (systemId: string, moduleCode: string, recordId: string, templateCode: string, expectedRecordVersion: number) =>
    apiRequest<RuntimePrintTask>(`${recordBase(systemId, moduleCode, recordId)}/prints`, {
      method: 'POST', body: { templateCode, expectedRecordVersion }, idempotencyKey: key(),
    }),
  history: (systemId: string, moduleCode: string, recordId: string, page = 1, size = 20) =>
    apiRequest<RuntimePrintTaskPage>(`${recordBase(systemId, moduleCode, recordId)}/prints?page=${page}&size=${size}`),
  task: (systemId: string, moduleCode: string, recordId: string, printId: string) =>
    apiRequest<RuntimePrintTask>(`${recordBase(systemId, moduleCode, recordId)}/prints/${encodeURIComponent(printId)}`),
  resultPdf: (systemId: string, moduleCode: string, recordId: string, printId: string) =>
    apiDownload(`${recordBase(systemId, moduleCode, recordId)}/prints/${encodeURIComponent(printId)}/result.pdf`),
}
