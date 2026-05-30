import { httpGet, httpPost, httpPut, httpRequest } from '@/api/http'
import type { ApiResult } from '@/api/http'
import { idToString, type IdValue } from '@/utils/id'

export type OpenAppClient = {
  id: string
  clientCode?: string
  clientName?: string
  contactName?: string
  contactMobile?: string
  contactEmail?: string
  status?: number
  remark?: string
  createTime?: string
  updateTime?: string
}

export type OpenAppClientDetail = {
  client?: OpenAppClient
  activeAccessKey?: string
}

export type OpenAppCredential = {
  clientId?: string
  accessKey?: string
  secret?: string
}

export function listOpenApps(): Promise<ApiResult<OpenAppClient[]>> {
  return httpGet<OpenAppClient[]>('/v1/platform/apps')
}

export function getOpenApp(id: IdValue): Promise<ApiResult<OpenAppClientDetail>> {
  return httpGet<OpenAppClientDetail>(`/v1/platform/apps/${pathId(id)}`)
}

export function createOpenApp(body: {
  clientCode: string
  clientName: string
  contactName?: string | null
  contactMobile?: string | null
  contactEmail?: string | null
  remark?: string | null
}): Promise<ApiResult<OpenAppCredential>> {
  return httpPost<OpenAppCredential>('/v1/platform/apps', body)
}

export function updateOpenApp(
  id: IdValue,
  body: {
    clientName: string
    contactName?: string | null
    contactMobile?: string | null
    contactEmail?: string | null
    remark?: string | null
  }
): Promise<ApiResult<void>> {
  return httpPut<void>(`/v1/platform/apps/${pathId(id)}`, body)
}

export function setOpenAppStatus(id: IdValue, status: 1 | 2): Promise<ApiResult<void>> {
  return httpPost<void>(`/v1/platform/apps/${pathId(id)}/status`, { status })
}

export function deleteOpenApp(id: IdValue): Promise<ApiResult<void>> {
  return httpRequest<void>('DELETE', `/v1/platform/apps/${pathId(id)}`)
}

export function rotateOpenAppSecret(id: IdValue): Promise<ApiResult<OpenAppCredential>> {
  return httpPost<OpenAppCredential>(`/v1/platform/apps/${pathId(id)}/rotate-secret`, {})
}

function pathId(value: IdValue): string {
  return encodeURIComponent(idToString(value))
}
