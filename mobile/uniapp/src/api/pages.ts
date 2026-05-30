import { httpGet, httpPost } from '@/api/http'
import type { ApiResult } from '@/api/http'
import { idToString, type IdValue } from '@/utils/id'

export type ModulePage = {
  id: string
  appId?: string
  pageCode?: string
  pageName?: string
  pageType?: string
  routePath?: string | null
  configJson?: string | null
  formFieldsJson?: string | null
  status?: number
}

export type ModulePageBlock = {
  id: string
  appId?: string
  pageId?: string
  blockType?: string
  sortNo?: number
  configJson?: string | null
}

export type PagePickerOption = {
  value: string
  text: string
  pageCode?: string
  pageType?: string
}

export function listPagesByApp(appId: IdValue): Promise<ApiResult<ModulePage[]>> {
  return httpGet<ModulePage[]>(`/v1/system/module/pages/apps/${pathId(appId)}`)
}

export function listPagePickerOptions(appId: IdValue): Promise<ApiResult<PagePickerOption[]>> {
  return httpGet<PagePickerOption[]>(`/v1/system/module/pages/apps/${pathId(appId)}/picker`)
}

export function getPageDetail(pageId: IdValue): Promise<ApiResult<{ page: ModulePage; blocks: ModulePageBlock[] }>> {
  return httpGet<{ page: ModulePage; blocks: ModulePageBlock[] }>(`/v1/system/module/pages/${pathId(pageId)}/detail`)
}

export type PageRuntime = {
  pageId: string
  appId: string
  pageCode?: string
  pageName?: string
  pageType?: string
  routePath?: string | null
  modelId?: string | null
  listViewId?: string | null
  searchFieldCode?: string | null
  titleFieldCodes?: string[]
  columnFieldCodes?: string[]
  fieldOverrides?: Array<{ fieldCode: string; hidden?: boolean; required?: boolean; sortNo?: number }>
}

export function getPageRuntime(pageId: IdValue): Promise<ApiResult<PageRuntime>> {
  return httpGet<PageRuntime>(`/v1/system/module/pages/${pathId(pageId)}/runtime`)
}

export function upsertPage(cmd: {
  id?: IdValue | null
  appId: IdValue
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

export function deletePages(ids: IdValue[]): Promise<ApiResult<void>> {
  return httpPost<void>('/v1/system/module/pages/delete', { ids })
}

export function upsertPageBlock(cmd: {
  id?: IdValue | null
  appId: IdValue
  pageId: IdValue
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

export function deletePageBlocks(ids: IdValue[]): Promise<ApiResult<void>> {
  return httpPost<void>('/v1/system/module/pages/blocks/delete', { ids })
}

function pathId(value: IdValue): string {
  return encodeURIComponent(idToString(value))
}
