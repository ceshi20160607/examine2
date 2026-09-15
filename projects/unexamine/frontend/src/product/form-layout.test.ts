import { describe, expect, it } from 'vitest'
import { fieldSpan, formSections, materializeFormLayout, placeFormField } from './form-layout'
import type { ConfiguredField } from './types'
const fields = ['name', 'phone', 'email', 'disabled'].map(code => ({ code, status: code === 'disabled' ? 'DISABLED' : 'ACTIVE' }) as ConfiguredField)

describe('published form layout consumed by designer and runtime', () => {
  it('supports legacy layouts and crops fields unavailable to this runtime identity', () => {
    expect(formSections({}, fields)[0]!.fieldCodes).toEqual(['name', 'phone', 'email'])
    expect(formSections({ fieldCodes: ['email', 'disabled', 'name'] }, fields.slice(1))[0]!.fieldCodes).toEqual(['email'])
  })
  it('moves a field between groups before a target and preserves unrelated metadata on save/reopen', () => {
    const original = { fieldCodes: ['name', 'phone', 'email'], rules: { retained: true }, components: [
      { id: 'first', type: 'SECTION', title: '身份', columns: 2, help: '保留说明', fieldCodes: ['name', 'phone'] },
      { id: 'contact', type: 'SECTION', title: '联系', columns: 1, fieldCodes: ['email'] },
      { id: 'hint', type: 'HELP', message: '已有组件' },
    ] }
    const moved = placeFormField(formSections(original, fields), 'phone', 'contact', 'email')
    const saved = materializeFormLayout(original, moved, { phone: 2, disabled: 1 })
    expect(saved.fieldCodes).toEqual(['name', 'phone', 'email'])
    expect(saved.rules).toEqual({ retained: true })
    expect(saved.components?.[0]).toEqual(original.components[2])
    expect(saved.components?.[1]?.help).toBe('保留说明')
    expect(saved.fieldSpans).toEqual({ phone: 2 })
    expect(formSections(JSON.stringify(saved), fields).map(section => section.fieldCodes)).toEqual([['name'], ['phone', 'email']])
    expect(fieldSpan(saved, 'phone', 1)).toBe(1)
  })
  it('places a pending type before a saved field without duplicating it across groups', () => {
    const sections = formSections({ fieldCodes: ['name', 'phone', 'email'] }, fields)
    const pending = placeFormField(sections, '__pending_field__', 'main', 'phone')
    expect(pending[0]!.fieldCodes).toEqual(['name', '__pending_field__', 'phone', 'email'])
    expect(placeFormField(pending, 'email', 'main', 'name')[0]!.fieldCodes).toEqual(['email', 'name', '__pending_field__', 'phone'])
  })
})
