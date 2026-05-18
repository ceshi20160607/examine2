import { httpGet, httpPost } from '@/api/http'
import type { ApiResult } from '@/api/http'

export type ModulePage = {
  id: number
  appId?: number
  pageCode?: string
  pageName?: string
  pageType?: string
  routePath?: string | null
  configJson?: string | null
  formFieldsJson?: string | null
  status?: number
}

export type ModulePageBlock = {
  id: number
  appId?: number
  pageId?: number
  blockType?: string
  sortNo?: number
  configJson?: string | null
}

export type PagePickerOption = {
  value: number
  text: string
  pageCode?: string
  pageType?: string
}

export function listPagesByApp(appId: number): Promise<ApiResult<ModulePage[]>> {
  return httpGet<ModulePage[]>(`/v1/system/module/pages/apps/${appId}`)
}

export function listPagePickerOptions(appId: number): Promise<ApiResult<PagePickerOption[]>> {
  return httpGet<PagePickerOption[]>(`/v1/system/module/pages/apps/${appId}/picker`)
}

export function getPageDetail(pageId: number): Promise<ApiResult<{ page: ModulePage; blocks: ModulePageBlock[] }>> {
  return httpGet<{ page: ModulePage; blocks: ModulePageBlock[] }>(`/v1/system/module/pages/${pageId}/detail`)
}

export type PageRuntime = {
  pageId: number
  appId: number
  pageCode?: string
  pageName?: string
  pageType?: string
  routePath?: string | null
  modelId?: number | null
  listViewId?: number | null
  searchFieldCode?: string | null
  titleFieldCodes?: string[]
  columnFieldCodes?: string[]
  fieldOverrides?: Array<{ fieldCode: string; hidden?: boolean; required?: boolean; sortNo?: number }>
}

export function getPageRuntime(pageId: number): Promise<ApiResult<PageRuntime>> {
  return httpGet<PageRuntime>(`/v1/system/module/pages/${pageId}/runtime`)
}

export function upsertPage(cmd: {
  id?: number | null
  appId: number
  pageCode: string
  pageName: string
  pageType: string
  routePath?: string | null
  configJson?: string | null
  formFieldsJson?: string | null
  status?: number
}): Promise<ApiResult<ModulePage>> {
  return httpPost<ModulePage>('/v1/system/module/pages/upsert', {
    id: cmd.id ?? null,
    appId: cmd.appId,
    pageCode: cmd.pageCode,
    pageName: cmd.pageName,
    pageType: cmd.pageType,
    routePath: cmd.routePath ?? null,
    configJson: cmd.configJson ?? null,
    formFieldsJson: cmd.formFieldsJson ?? null,
    status: cmd.status ?? 1
  })
}

export function deletePages(ids: number[]): Promise<ApiResult<void>> {
  return httpPost<void>('/v1/system/module/pages/delete', { ids })
}

export function upsertPageBlock(cmd: {
  id?: number | null
  appId: number
  pageId: number
  blockType: string
  sortNo?: number
  configJson?: string | null
}): Promise<ApiResult<ModulePageBlock>> {
  return httpPost<ModulePageBlock>('/v1/system/module/pages/blocks/upsert', {
    id: cmd.id ?? null,
    appId: cmd.appId,
    pageId: cmd.pageId,
    blockType: cmd.blockType,
    sortNo: cmd.sortNo ?? 0,
    configJson: cmd.configJson ?? null
  })
}

export function deletePageBlocks(ids: number[]): Promise<ApiResult<void>> {
  return httpPost<void>('/v1/system/module/pages/blocks/delete', { ids })
}
