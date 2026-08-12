import { describe, expect, it } from 'vitest'

import {
  buildCondition,
  buildDerivedExpressionAst,
  buildFieldProperties,
  createCondition,
  createConditionGroup,
  defaultModesForField,
  derivedCyclePath,
  derivedExpressionDependencies,
  fieldPermissionCode,
  inferDerivedExpressionSchema,
  indexModesForField,
  isAiFillSourceField,
  readCondition,
  readFieldProperties,
  supportsFieldWritePermission,
} from '../../src/views/system/admin/configStudioModel'

describe('config studio model', () => {
  it('builds canonical field permission codes and rejects write declarations for owner-computed fields', () => {
    expect(fieldPermissionCode('tickets', 'secret_note', 'read'))
      .toBe('module.tickets.field.secret_note.read')
    expect(fieldPermissionCode(' tickets ', ' secret_note ', 'write'))
      .toBe('module.tickets.field.secret_note.write')

    expect(supportsFieldWritePermission('TEXT', false)).toBe(true)
    expect(supportsFieldWritePermission('TEXT', true)).toBe(false)
    for (const type of ['REFERENCE', 'FORMULA', 'SUMMARY', 'CALCULATED', 'LOOKUP', 'AGGREGATE',
      'AI_FILL', 'TENANT', 'AUTO_NUMBER', 'CREATED_BY', 'CREATED_AT', 'UPDATED_BY', 'UPDATED_AT']) {
      expect(supportsFieldWritePermission(type, false), type).toBe(false)
    }
  })

  it('round-trips nested AND/OR rule trees without dropping condition groups', () => {
    const amount = { ...createCondition('amount'), operator: 'BETWEEN', value: 100, valueEnd: 500 }
    const title = { ...createCondition('title'), operator: 'CONTAINS', value: 'urgent' }
    const enabled = { ...createCondition('enabled'), operator: 'EQ', value: 'true' }
    const nested = createConditionGroup('title', [title, enabled])
    nested.join = 'OR'
    const root = createConditionGroup('amount', [amount, nested])

    const fieldTypes: Record<string, string> = { amount: 'MONEY', title: 'TEXT', enabled: 'SWITCH' }
    const built = buildCondition(root, (id) => fieldTypes[id])
    const rebuilt = buildCondition(readCondition(built, 'title'), (id) => fieldTypes[id])

    expect(rebuilt).toEqual(built)
    expect(rebuilt.children[1]).toMatchObject({ join: 'OR' })
    expect(rebuilt.children[0].value).toEqual([100, 500])
    expect(rebuilt.children[1].children[1].value).toBe(true)
  })

  it('emits only the selected field type schema', () => {
    expect(buildFieldProperties({
      type: 'TEXT', maxLength: 120, trim: true, formula: 'should_not_leak', sensitive: false,
    })).toEqual({ sensitive: false, maxLength: 120, trim: true })

    expect(buildFieldProperties({
      type: 'MONEY', minimum: 0, maximum: 999, precision: 18, scale: 2, step: 0.01,
      roundingMode: 'HALF_UP', thousandsSeparator: true, currency: 'CNY',
    })).toMatchObject({ currency: 'CNY', step: 0.01, precision: 18, scale: 2 })

    expect(buildFieldProperties({
      type: 'JSON', jsonSchemaText: '{"type":"object","additionalProperties":false}', queryPaths: [],
    })).toEqual({ jsonSchema: { type: 'object', additionalProperties: false }, queryPaths: [] })

    expect(buildFieldProperties({
      type: 'SUBTABLE', minRows: 1, maxRows: 20, columnFieldIds: ['description'],
      allowRowCreate: true, allowRowUpdate: true, allowRowDelete: false, allowRowReorder: true,
    })).toEqual({
      minRows: 1, maxRows: 20, columnFieldIds: ['description'],
      allowRowCreate: true, allowRowUpdate: true, allowRowDelete: false, allowRowReorder: true,
    })
  })

  it('round-trips relation filters and emits exact P4-C3 field contracts', () => {
    const filter = createConditionGroup('target_status')
    filter.children[0] = { ...createCondition('target_status'), operator: 'EQ', value: 'ACTIVE' }
    const properties = buildFieldProperties({
      type: 'RELATION', multiple: true, useRelationFilter: true, relationFilter: filter,
      displayFieldId: 'target_name', allowCreate: true, reverseRelation: true,
    }, (id) => id === 'target_status' ? 'STATUS' : 'TEXT')

    expect(properties).toEqual({
      multiple: true, displayFieldId: 'target_name', allowCreate: true, reverseRelation: true,
      filter: { join: 'AND', children: [{ fieldId: 'target_status', operator: 'EQ', value: 'ACTIVE', children: [] }] },
    })

    const form: Record<string, unknown> = {}
    readFieldProperties(form, properties)
    expect(form).toMatchObject({
      multiple: true, displayFieldId: 'target_name', allowCreate: true, useRelationFilter: true,
    })
    expect(buildCondition(form.relationFilter as ReturnType<typeof createConditionGroup>, () => 'STATUS'))
      .toEqual(properties.filter)

    expect(buildFieldProperties({
      type: 'REFERENCE', sourceFieldId: 'customer', targetFieldId: 'customer_name',
      defaultMode: 'FIXED', defaultValue: 'illegal', unique: false,
    })).toEqual({ sourceFieldId: 'customer', targetFieldId: 'customer_name' })

    expect(buildFieldProperties({
      type: 'SUBTABLE', columnFieldIds: ['description', 'amount'], minRows: 1, maxRows: 20,
      allowRowCreate: true, allowRowUpdate: true, allowRowDelete: true, allowRowReorder: true,
      aggregates: [
        { id: 'row_count', function: 'COUNT', columnFieldId: 'must_not_leak' },
        { id: 'total_amount', function: 'SUM', columnFieldId: 'amount' },
      ],
      allowImport: true, allowExport: true, cascadeDelete: true,
    })).toEqual({
      columnFieldIds: ['description', 'amount'], minRows: 1, maxRows: 20,
      allowRowCreate: true, allowRowUpdate: true, allowRowDelete: true, allowRowReorder: true,
      aggregates: [
        { id: 'row_count', function: 'COUNT' },
        { id: 'total_amount', function: 'SUM', columnFieldId: 'amount' },
      ],
    })
  })

  it('emits typed fixed defaults and limits default sources by field type', () => {
    expect(buildFieldProperties({ type: 'NUMBER', defaultMode: 'FIXED', defaultValue: 42 }))
      .toMatchObject({ defaultMode: 'FIXED', defaultValue: 42 })
    expect(buildFieldProperties({ type: 'SWITCH', defaultMode: 'FIXED', defaultValue: false }))
      .toMatchObject({ defaultMode: 'FIXED', defaultValue: false })
    expect(buildFieldProperties({ type: 'DATE_RANGE', defaultMode: 'FIXED', defaultValueRange: ['2026-01-01', '2026-01-31'] }))
      .toMatchObject({ defaultMode: 'FIXED', defaultValue: ['2026-01-01', '2026-01-31'] })
    expect(buildFieldProperties({ type: 'JSON', defaultMode: 'FIXED', defaultValue: '{"enabled":true}' }))
      .toMatchObject({ defaultMode: 'FIXED', defaultValue: { enabled: true } })

    expect(defaultModesForField('MEMBER')).toContain('CURRENT_USER')
    expect(defaultModesForField('MEMBER')).not.toContain('FIXED')
    expect(defaultModesForField('DEPARTMENT')).toContain('CURRENT_DEPARTMENT')
    expect(defaultModesForField('DATE')).toContain('CURRENT_DATE')
    expect(defaultModesForField('CREATED_AT')).toEqual(['NONE'])
  })

  it('emits multi-currency money properties and constrains P4-C1 index modes', () => {
    expect(buildFieldProperties({
      type: 'MONEY', currencies: ['CNY', 'USD'], fixedCurrency: '', scale: 2,
      defaultMode: 'FIXED', defaultMoneyAmount: '10.00', defaultMoneyCurrency: 'USD',
    })).toMatchObject({
      currencies: ['CNY', 'USD'],
      defaultValue: { amount: '10.00', currency: 'USD' },
    })
    expect(indexModesForField('MONEY')).toEqual(['NONE', 'FILTER', 'SORT', 'UNIQUE'])
    expect(indexModesForField('MULTI_SELECT')).toEqual(['NONE', 'FILTER'])
    expect(indexModesForField('TEXT')).toContain('STATISTIC')
  })

  it('emits the frozen P4-C2 properties and exact index capabilities', () => {
    expect(buildFieldProperties({ type: 'PHONE', defaultCountry: 'cn' }))
      .toEqual({ defaultCountry: 'CN' })
    expect(buildFieldProperties({ type: 'IDENTITY', identityKind: 'GENERIC', pattern: '^[A-Z0-9]{6,32}$' }))
      .toEqual({ identityKind: 'GENERIC', pattern: '^[A-Z0-9]{6,32}$' })
    expect(buildFieldProperties({ type: 'GEO', coordinateSystem: 'GCJ02' }))
      .toEqual({ coordinateSystem: 'WGS84' })
    expect(buildFieldProperties({ type: 'BARCODE', symbologies: ['CODE128', 'EAN13', 'CODE128'] }))
      .toEqual({ symbologies: ['CODE128', 'EAN13'] })
    expect(buildFieldProperties({ type: 'RICH_TEXT', sanitize: false }))
      .toEqual({ sanitize: true })
    expect(buildFieldProperties({ type: 'SECRET', minLength: 4, maxLength: 40, trim: true }))
      .toEqual({ minLength: 1, maxLength: 4096 })
    expect(buildFieldProperties({
      type: 'STATUS', initialStateIds: ['101'], transitions: [{ from: '101', to: '102' }],
    })).toEqual({ initialStateIds: ['101'], transitions: [{ from: '101', to: '102' }] })

    expect(defaultModesForField('SECRET')).not.toContain('FIXED')
    expect(indexModesForField('ADDRESS')).toEqual(['NONE', 'FILTER'])
    expect(indexModesForField('IDENTITY')).toEqual(['NONE', 'FILTER', 'UNIQUE'])
    expect(indexModesForField('BARCODE')).toEqual(['NONE', 'FILTER', 'SORT', 'UNIQUE'])
    expect(indexModesForField('STATUS')).toEqual(['NONE', 'FILTER', 'SORT'])
  })

  it('emits exact P4-C4 derived contracts without legacy free-form properties', () => {
    expect(buildFieldProperties({
      type: 'FORMULA', resultSchema: 'DATE', expressionOperator: 'ADD_DAYS',
      expressionOperands: [
        { kind: 'FIELD', fieldId: 'date_1', literalType: 'STRING', literalValue: '' },
        { kind: 'LITERAL', fieldId: '', literalType: 'INTEGER', literalValue: 3 },
      ],
    })).toEqual({
      resultSchema: 'DATE', astVersion: 1,
      expressionAst: { op: 'ADD_DAYS', args: [{ fieldId: 'date_1' }, { literalType: 'INTEGER', value: 3 }] },
    })
    const calculated = buildFieldProperties({
      type: 'CALCULATED', resultSchema: 'DECIMAL', expressionOperator: 'ADD',
      expressionOperands: [
        { kind: 'FIELD', fieldId: '101', literalType: 'STRING', literalValue: '' },
        { kind: 'LITERAL', fieldId: '', literalType: 'INTEGER', literalValue: 2 },
      ],
      formula: 'must not leak', expression: 'must not leak', sourceFieldIds: ['101'],
      minimum: 0, sensitive: false, defaultMode: 'FIXED', defaultValue: 3,
    })
    expect(calculated).toEqual({
      resultSchema: 'DECIMAL', astVersion: 1,
      expressionAst: { op: 'ADD', args: [{ fieldId: '101' }, { literalType: 'INTEGER', value: 2 }] },
    })

    expect(buildFieldProperties({
      type: 'SUMMARY', resultSchema: 'INTEGER', relationFieldId: '201', targetFieldId: 'must-not-leak',
      reduction: 'COUNT', multiple: true, targetModuleId: '999',
    })).toEqual({ resultSchema: 'INTEGER', relationFieldId: '201', reduction: 'COUNT' })
    expect(buildFieldProperties({
      type: 'LOOKUP', resultSchema: 'STRING', relationFieldId: '201', targetFieldId: '202', distinct: true,
    })).toEqual({ resultSchema: 'STRING', relationFieldId: '201', targetFieldId: '202', distinct: true })
    expect(buildFieldProperties({
      type: 'AGGREGATE', resultSchema: 'STRING', subtableFieldId: '301', aggregateId: 'lineTotal',
    })).toEqual({ resultSchema: 'DECIMAL', subtableFieldId: '301', aggregateId: 'lineTotal' })
    expect(indexModesForField('LOOKUP')).toEqual(['NONE', 'FILTER'])
    expect(indexModesForField('FORMULA')).toEqual(['NONE', 'FILTER', 'SORT'])
    expect(defaultModesForField('FORMULA')).toEqual(['NONE'])
  })

  it('emits the governed AI_FILL scalar/source/model/confidence/overwrite contract only', () => {
    const properties = buildFieldProperties({
      type: 'AI_FILL', resultSchema: 'STRING', sourceFieldIds: ['101', '102', '101'],
      promptTemplate: '根据命名来源生成摘要', modelPolicy: 'SYSTEM_DEFAULT',
      minConfidence: 0.8, overwriteMode: 'CONFIRM', defaultMode: 'FIXED',
      defaultValue: 'must-not-leak', sensitive: true, unique: true,
    })
    expect(properties).toEqual({
      resultSchema: 'STRING', sourceFieldIds: ['101', '102'],
      promptTemplate: '根据命名来源生成摘要', modelPolicy: 'SYSTEM_DEFAULT',
      minConfidence: 0.8, overwriteMode: 'CONFIRM',
    })
    const form: Record<string, unknown> = { type: 'AI_FILL' }
    readFieldProperties(form, properties)
    expect(form).toMatchObject(properties)
    expect(defaultModesForField('AI_FILL')).toEqual(['NONE'])
    expect(indexModesForField('AI_FILL')).toEqual(['NONE'])
    expect(isAiFillSourceField({ type: 'TEXT' })).toBe(true)
    expect(isAiFillSourceField({ type: 'TEXT', hidden: true })).toBe(false)
    expect(isAiFillSourceField({ type: 'SECRET' })).toBe(false)
    expect(isAiFillSourceField({ type: 'FORMULA', properties: { resultSchema: 'STRING' } })).toBe(false)
    expect(isAiFillSourceField({ type: 'AI_FILL', properties: { resultSchema: 'STRING' } })).toBe(false)

    expect(() => buildFieldProperties({ ...properties, type: 'AI_FILL', minConfidence: 0.49 }))
      .toThrow('0.50 到 1.00')
    expect(() => buildFieldProperties({ ...properties, type: 'AI_FILL', sourceFieldIds: [] }))
      .toThrow('1 到 16')
  })

  it('round-trips structured expression AST and infers schema and dependencies', () => {
    const properties = {
      resultSchema: 'DECIMAL', astVersion: 1,
      expressionAst: { op: 'MULTIPLY', args: [{ fieldId: 'price' }, { fieldId: 'quantity' }] },
    }
    const form: Record<string, any> = { type: 'FORMULA' }
    readFieldProperties(form, properties)
    const ast = buildDerivedExpressionAst(form)
    expect(ast).toEqual(properties.expressionAst)
    expect(derivedExpressionDependencies(ast)).toEqual(['price', 'quantity'])
    expect(inferDerivedExpressionSchema(ast, (id) => id === 'quantity' ? 'INTEGER' : 'DECIMAL')).toBe('DECIMAL')
    expect(inferDerivedExpressionSchema(
      { op: 'IF', args: [{ literalType: 'BOOLEAN', value: true }, { fieldId: 'price' }, { fieldId: 'quantity' }] },
      (id) => id === 'quantity' ? 'INTEGER' : 'DECIMAL',
    )).toBeUndefined()
    for (const schema of ['STRING', 'DECIMAL', 'INTEGER', 'DATE', 'DATETIME', 'BOOLEAN'] as const) {
      const literal = schema === 'BOOLEAN' ? true : schema === 'DECIMAL' || schema === 'INTEGER' ? 1 : 'value'
      expect(inferDerivedExpressionSchema({ literalType: schema, value: literal }, () => undefined)).toBe(schema)
    }

    expect(derivedCyclePath('price', ['subtotal'], [
      { id: 'price', type: 'FORMULA', properties: { expressionAst: { fieldId: 'subtotal' } } },
      { id: 'subtotal', type: 'CALCULATED', properties: { expressionAst: { fieldId: 'taxed' } } },
      { id: 'taxed', type: 'FORMULA', properties: { expressionAst: { fieldId: 'price' } } },
    ])).toEqual(['price', 'subtotal', 'taxed', 'price'])
  })
})
