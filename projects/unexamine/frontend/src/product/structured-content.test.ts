import { describe, expect, it } from 'vitest'
import { emptyJsonValue, inferJsonSchema, jsonSchemaIssue, newJsonSchema, structuredSummary, type JsonSchema } from './structured-content'
import { filterOperators, isSharedRuntimeField, supportsContentSort, supportsFieldSearch } from './field-capabilities'

describe('structured field editor protocol', () => {
  it('keeps nested objects, arrays and false values typed without JSON parsing by users', () => {
    const schema: JsonSchema = { type: 'OBJECT', properties: [{ key: '联系人', required: true, schema: { type: 'ARRAY', items: { type: 'OBJECT', properties: [{ key: '姓名', schema: { type: 'STRING' } }] } } }] }
    expect(jsonSchemaIssue(schema)).toBe('')
    expect(emptyJsonValue(schema)).toEqual({})
    expect(emptyJsonValue(newJsonSchema('ARRAY'))).toEqual([])
    expect(emptyJsonValue(newJsonSchema('BOOLEAN'))).toBe(false)
    expect(inferJsonSchema([1, '二'])).toEqual({ type: 'ARRAY' })
    expect(structuredSummary({ 姓名: '张三', 启用: false, 明细: [12.5] })).toBe('姓名：张三；启用：否；明细：12.5')
  })
  it('rejects duplicate, reserved and invalid nested configuration', () => {
    expect(jsonSchemaIssue({ type: 'OBJECT', properties: [{ key: '__proto__', schema: { type: 'STRING' } }] })).toContain('保留名称')
    expect(jsonSchemaIssue({ type: 'OBJECT', properties: [{ key: '金额', schema: { type: 'NUMBER', minimum: 10, maximum: 2 } }] })).toContain('金额：最小值')
    expect(jsonSchemaIssue({ type: 'ARRAY', maxItems: 0, items: { type: 'STRING' } })).toContain('最大行数')
  })
  it('offers meaningful content queries instead of misleading structural sorting', () => {
    expect(isSharedRuntimeField('RICH_TEXT')).toBe(true)
    expect(isSharedRuntimeField('JSON')).toBe(true)
    expect(supportsFieldSearch('RICH_TEXT')).toBe(true)
    expect(supportsFieldSearch('JSON')).toBe(false)
    expect(supportsContentSort('JSON')).toBe(false)
    expect(supportsContentSort('RICH_TEXT')).toBe(false)
    expect(filterOperators('JSON').map(item => item.value)).toEqual(['EMPTY', 'NOT_EMPTY'])
    expect(filterOperators('RICH_TEXT').map(item => item.value)).toContain('CONTAINS')
  })
})
