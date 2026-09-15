import { describe, expect, it } from 'vitest'
import { defaultEditor, fieldFamily, fieldTypes, filterOperators, isBasicRuntimeField } from './field-capabilities'

describe('field capability registry', () => {
  it('keeps each configurable field type unique and classifies the basic runtime family', () => {
    expect(new Set(fieldTypes.map((field) => field.type)).size).toBe(fieldTypes.length)
    expect(fieldFamily('TEXT')).toBe('TEXT')
    expect(fieldFamily('NUMBER')).toBe('NUMBER')
    expect(fieldFamily('DATE')).toBe('DATE')
    expect(fieldFamily('BOOLEAN')).toBe('BOOLEAN')
    expect(['TEXT', 'MULTILINE_TEXT', 'NUMBER', 'DATE', 'DATETIME', 'BOOLEAN'].every(isBasicRuntimeField)).toBe(true)
  })

  it('uses typed default editors and does not offer text operators for numeric or boolean fields', () => {
    expect(defaultEditor('NUMBER')).toBe('NUMBER')
    expect(defaultEditor('DATE')).toBe('DATE')
    expect(defaultEditor('DATETIME')).toBe('DATETIME')
    expect(defaultEditor('BOOLEAN')).toBe('BOOLEAN')
    expect(filterOperators('NUMBER').map((item) => item.value)).not.toContain('CONTAINS')
    expect(filterOperators('BOOLEAN').map((item) => item.value)).toEqual(['EQ', 'NE', 'EMPTY', 'NOT_EMPTY'])
    expect(filterOperators('TEXT').map((item) => item.value)).toContain('CONTAINS')
  })
})
