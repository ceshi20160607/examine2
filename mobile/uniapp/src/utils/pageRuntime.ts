import type { ModuleField } from '@/api/meta'

export type PageFieldOverride = {
  fieldCode: string
  hidden?: boolean
  required?: boolean
  sortNo?: number
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
  fieldOverrides?: PageFieldOverride[]
}

export function overrideMap(runtime: PageRuntime | null | undefined): Map<string, PageFieldOverride> {
  const map = new Map<string, PageFieldOverride>()
  for (const o of runtime?.fieldOverrides || []) {
    if (o?.fieldCode) map.set(o.fieldCode, o)
  }
  return map
}

/** 应用页面 form_fields_json 覆盖：隐藏、必填、排序 */
export function applyPageFieldOverrides<T extends ModuleField>(
  fields: T[],
  runtime: PageRuntime | null | undefined
): T[] {
  const omap = overrideMap(runtime)
  if (!omap.size) {
    return [...fields].sort((a, b) => Number(a.sortNo ?? 0) - Number(b.sortNo ?? 0))
  }

  let list = fields.filter((f) => {
    if (!f.fieldCode) return false
    const o = omap.get(f.fieldCode)
    if (o?.hidden === true) return false
    if (f.hiddenFlag === 1 && o?.hidden !== false) return false
    return true
  })

  list = list.map((f) => {
    const o = omap.get(f.fieldCode!)
    if (!o) return f
    const copy = { ...f } as T
    if (o.required === true) copy.requiredFlag = 1
    if (o.required === false) copy.requiredFlag = 0
    if (o.sortNo != null) copy.sortNo = o.sortNo
    return copy
  })

  return list.sort((a, b) => {
    const sa = omap.get(a.fieldCode!)?.sortNo ?? a.sortNo ?? 0
    const sb = omap.get(b.fieldCode!)?.sortNo ?? b.sortNo ?? 0
    return Number(sa) - Number(sb)
  })
}

export function pageQuerySuffix(pageId?: number | null): string {
  if (!pageId) return ''
  return `&pageId=${pageId}`
}
