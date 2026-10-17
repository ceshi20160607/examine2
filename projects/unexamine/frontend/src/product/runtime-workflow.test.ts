/// <reference types="node" />
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

const productRoot = fileURLToPath(new URL('.', import.meta.url))
const runtime = readFileSync(`${productRoot}/views/RuntimeWorkspaceView.vue`, 'utf8')
const runtimeInput = readFileSync(`${productRoot}/components/RuntimeFieldInput.vue`, 'utf8')
const styles = readFileSync(`${productRoot}/product.css`, 'utf8')

describe('runtime task recovery contract', () => {
  it('keeps a failed form editable and removes its draft only after a successful save', () => {
    expect(runtime).toContain('applyFormError(reason)')
    expect(runtime).toContain('localStorage.removeItem(formDraftKey())')
    expect(runtime.indexOf('localStorage.removeItem(formDraftKey())')).toBeGreaterThan(runtime.indexOf("const saved = await api<RuntimeRecord>"))
    expect(runtime).toContain('草稿已经自动保存在当前浏览器')
  })

  it('submits only rendered writable fields and maps unique conflicts back to the field', () => {
    expect(runtime).toContain('const writableCodes = new Set(formFields.value.map((field) => field.code))')
    expect(runtime).toContain('writableCodes.has(code)')
    expect(runtime).toContain("['FIELD_VALIDATION_FAILED', 'UNIQUE_RULE_VIOLATION'].includes(reason.code)")
    expect(runtime).toContain("formFields.value.find((field) => field.code === code)?.name || '字段'")
    expect(runtime).toContain('请检查填写内容：')
    expect(runtime).toContain("!field.fieldType.startsWith('SYSTEM_')")
    expect(runtime).toContain('!Boolean(fieldConfig(field).readOnly)')
  })

  it('closes inaccessible details and refreshes the list after permission loss', () => {
    expect(runtime).toContain("'PERMISSION_DENIED', 'RECORD_NOT_FOUND'")
    expect(runtime).toContain('closeDetail()')
    expect(runtime).toContain('detailOpen.value = false')
    expect(runtime).toContain('detail.value = undefined')
    expect(runtime).toContain('记录已不可访问，已关闭表单和详情并刷新列表')
  })

  it('keeps the mobile detail action bar inside the viewport', () => {
    expect(styles).toContain('.detail-drawer .drawer-footer { width: 100%; display: grid;')
    expect(styles).toContain('.detail-drawer .drawer-footer .ant-btn { min-width: 0;')
  })

  it('uses the shared typed text input with published length and semantic hints', () => {
    expect(runtime).not.toContain("field.fieldType === 'PHONE'")
    expect(runtimeInput).toContain("PHONE: 'tel'")
    expect(runtimeInput).toContain("EMAIL: 'email'")
    expect(runtimeInput).toContain("URL: 'url'")
    expect(runtimeInput).toContain(':maxlength="maxLength"')
    expect(runtimeInput).toContain("field.fieldType === 'ID_CARD' ? 'off'")
  })
})
