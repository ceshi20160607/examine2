import { httpDelete, httpGet, httpPost } from '@/api/http'
import type { ApiResult } from '@/api/http'
import { idToString, type IdValue } from '@/utils/id'

export type FlowBindingRow = {
  binding: {
    id: string
    bizType?: string
    triggerAction?: string
    tempId?: string
    status?: number
  }
  tempCode?: string
  tempName?: string
}

export type FlowTempOption = { id: string; tempCode?: string; tempName?: string }

export function listModelFlowBindings(appId: IdValue, modelId: IdValue): Promise<ApiResult<FlowBindingRow[]>> {
  return httpGet<FlowBindingRow[]>(`/v1/system/module/flow-bindings/apps/${pathId(appId)}/models/${pathId(modelId)}`)
}

export function listFlowTempOptions(): Promise<ApiResult<FlowTempOption[]>> {
  return httpGet<FlowTempOption[]>('/v1/system/module/flow-bindings/flow-temps')
}

export function upsertModelFlowBinding(cmd: {
  id?: IdValue | null
  appId: IdValue
  modelId: IdValue
  triggerAction: string
  tempId: IdValue
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

export function deleteModelFlowBinding(id: IdValue): Promise<ApiResult<void>> {
  return httpDelete(`/v1/system/module/flow-bindings/${pathId(id)}`)
}

function pathId(value: IdValue): string {
  return encodeURIComponent(idToString(value))
}
