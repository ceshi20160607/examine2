import { describe, expect, it } from 'vitest'
import { listPageSettings, materializeListLayout } from './page-layout'
import { formSections, materializeFormLayout } from './form-layout'
import type { RuntimeField } from './types'
const fields = ['name', 'phone', 'email'].map(code => ({ code, name: code, status: 'ACTIVE' }) as RuntimeField)
const columns = [{ code: 'title', label: '标题', width: 210 }, ...fields.map(field => ({ code: field.code, label: field.name, width: 140, field })), { code: 'status', label: '状态', width: 90 }]

describe('list and detail layout publication consumption', () => {
  it('preserves legacy defaults and uses every explicitly configured readable column in its configured order', () => {
    expect(listPageSettings({}, columns).visible).toEqual(['title', 'name', 'phone', 'status'])
    const saved = { fieldCodes: ['email', 'name', 'forbidden'], fixedFieldCodes: ['forbidden', 'name'], columnWidths: { name: 240, email: 60 }, density: 'compact' }
    const read = listPageSettings(saved, columns)
    expect(read.visible).toEqual(['title', 'email', 'name', 'status'])
    expect(read.fixed).toEqual(['name'])
    expect(read.columns.find(column => column.code === 'name')?.width).toBe(240)
    expect(read.columns.find(column => column.code === 'email')?.width).toBe(140)
    expect(read.density).toBe('compact')
  })
  it('retains other components and metadata while removing stale widths and fixed columns on save/reopen', () => {
    const original = { notes: 'retained', components: [{ type: 'HELP', text: '说明' }, { id: 'table', type: 'TABLE', custom: true }] }
    const saved = materializeListLayout(original, ['email', 'name'], { email: 300, forbidden: 200 }, ['title', 'name', 'forbidden'], '客户列表', 'compact')
    expect(saved.components[0]).toEqual(original.components[0])
    expect(saved.components[1]).toMatchObject({ id: 'table', custom: true, title: '客户列表' })
    expect(saved.notes).toBe('retained')
    expect(saved.columnWidths).toEqual({ email: 300 })
    expect(listPageSettings(JSON.stringify(saved), columns).fixed).toEqual(['title', 'name'])
  })
  it('saves independent detail groups without changing the existing form schema or exposing unavailable fields', () => {
    const form = { layoutType: 'FORM', fieldCodes: ['name', 'phone', 'email'] }
    const detail = materializeFormLayout(form, [{ id: 'contact', title: '联系方式', columns: 1, fieldCodes: ['email', 'phone'], extra: {} }], { email: 2 }, 'DETAIL')
    expect(form.fieldCodes).toEqual(['name', 'phone', 'email'])
    expect(form.layoutType).toBe('FORM')
    expect(detail.layoutType).toBe('DETAIL')
    expect(formSections(detail, fields.filter(field => field.code !== 'phone'))[0]).toMatchObject({ title: '联系方式', columns: 1, fieldCodes: ['email'] })
  })
})
