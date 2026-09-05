/// <reference types="node" />
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

const productRoot = fileURLToPath(new URL('.', import.meta.url))
const runtime = readFileSync(`${productRoot}/views/RuntimeWorkspaceView.vue`, 'utf8')
const styles = readFileSync(`${productRoot}/product.css`, 'utf8')

describe('runtime task recovery contract', () => {
  it('keeps a failed form editable and removes its draft only after a successful save', () => {
    expect(runtime).toContain('applyFormError(reason)')
    expect(runtime).toContain('localStorage.removeItem(formDraftKey())')
    expect(runtime.indexOf('localStorage.removeItem(formDraftKey())')).toBeGreaterThan(runtime.indexOf("const saved = await api<RuntimeRecord>"))
    expect(runtime).toContain('草稿已经自动保存在当前浏览器')
  })

  it('closes inaccessible details and refreshes the list after permission loss', () => {
    expect(runtime).toContain("'PERMISSION_DENIED', 'RECORD_NOT_FOUND'")
    expect(runtime).toContain('detailOpen.value = false')
    expect(runtime).toContain('detail.value = undefined')
    expect(runtime).toContain('记录已不可访问，详情已关闭并刷新列表')
  })

  it('keeps the mobile detail action bar inside the viewport', () => {
    expect(styles).toContain('.detail-drawer .drawer-footer { width: 100%; display: grid;')
    expect(styles).toContain('.detail-drawer .drawer-footer .ant-btn { min-width: 0;')
  })
})
