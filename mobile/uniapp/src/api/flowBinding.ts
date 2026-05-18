import { httpDelete, httpGet, httpPost } from '@/api/http'
import type { ApiResult } from '@/api/http'

export type FlowBindingRow = {
  binding: {
    id: number
    bizType?: string
    triggerAction?: string
    tempId?: number
    status?: number
  }
  tempCode?: string
  tempName?: string
}

export type FlowTempOption = { id: number; tempCode?: string; tempName?: string }

export function listModelFlowBindings(appId: number, modelId: number): Promise<ApiResult<FlowBindingRow[]>> {
  return httpGet<FlowBindingRow[]>(`/v1/system/module/flow-bindings/apps/${appId}/models/${modelId}`)
}

export function listFlowTempOptions(): Promise<ApiResult<FlowTempOption[]>> {
  return httpGet<FlowTempOption[]>('/v1/system/module/flow-bindings/flow-temps')
}

export function upsertModelFlowBinding(cmd: {
  id?: number | null
  appId: number
  modelId: number
  triggerAction: string
  tempId: number
  status?: number
}): Promise<ApiResult<any>> {
  return httpPost('/v1/system/module/flow-bindings/upsert', {
    id: cmd.id ?? null,
    appId: cmd.appId,
    modelId: cmd.modelId,
    triggerAction: cmd.triggerAction,
    tempId: cmd.tempId,
    status: cmd.status ?? 1
  })
}

export function deleteModelFlowBinding(id: number): Promise<ApiResult<void>> {
  return httpDelete(`/v1/system/module/flow-bindings/${id}`)
}
