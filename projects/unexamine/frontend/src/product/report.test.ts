import { describe, expect, it } from 'vitest'
import { buildReportDefinition, splitQualifiedField } from './report'

describe('Cycle 49 structured report definition', () => {
  it('builds a bounded multi-module relation tree without arbitrary SQL', () => {
    const definition = buildReportDefinition({
      primaryModuleCode: ' customer ', relatedModuleCode: 'orders', relationType: 'REFERENCE',
      primaryRelationField: 'id', relatedRelationField: 'customer_id',
      outputFields: ['primary.title', 'related.amount'], metricOperation: 'SUM',
      metricField: 'related.amount', dimensionType: 'STATUS', dimensionField: 'primary.status',
      filterField: 'primary.status', filterOperator: 'EQ', filterValue: 'ACTIVE',
      sortField: 'related.amount', sortDirection: 'DESC', timeField: 'primary.updatedAt',
      maxScanRows: 4000, limit: 200,
    })
    expect(definition.modules).toEqual([
      { alias: 'primary', moduleCode: 'customer' }, { alias: 'related', moduleCode: 'orders' },
    ])
    expect(definition.relations).toEqual([expect.objectContaining({ leftField: 'id', rightField: 'customer_id' })])
    expect(definition).toMatchObject({ maxScanRows: 1000, limit: 50 })
    expect(JSON.stringify(definition).toLowerCase()).not.toContain('select ')
  })

  it('keeps single-module count definitions explicit and parses qualified fields', () => {
    expect(splitQualifiedField('primary.status')).toEqual({ alias: 'primary', fieldCode: 'status' })
    expect(buildReportDefinition({ primaryModuleCode: 'customer', outputFields: ['primary.title'],
      metricOperation: 'COUNT', maxScanRows: 200, limit: 20 })).toMatchObject({
      relations: [], metric: { operation: 'COUNT' }, dimension: {}, filters: [],
    })
  })

  it('keeps a third module and multiple named filters in one bounded relation tree', () => {
    const definition = buildReportDefinition({
      primaryModuleCode: 'customers', relatedModuleCode: 'orders', thirdModuleCode: 'details',
      primaryRelationField: 'id', relatedRelationField: 'customer_id',
      thirdRelationParentAlias: 'related', thirdParentField: 'id', thirdRelationField: 'order_id',
      outputFields: ['primary.title', 'related.amount', 'third.quantity'],
      metricOperation: 'SUM', metricField: 'related.amount',
      filters: [
        { field: 'primary.status', operator: 'EQ', value: 'ACTIVE' },
        { field: 'third.quantity', operator: 'GTE', value: '2' },
      ],
      maxScanRows: 1000, limit: 20,
    })
    expect(definition.modules).toHaveLength(3)
    expect(definition.relations).toEqual([
      expect.objectContaining({ leftAlias: 'primary', rightAlias: 'related' }),
      expect.objectContaining({ leftAlias: 'related', rightAlias: 'third', leftField: 'id', rightField: 'order_id' }),
    ])
    expect(definition.filters).toEqual([
      { alias: 'primary', fieldCode: 'status', operator: 'EQ', value: 'ACTIVE' },
      { alias: 'third', fieldCode: 'quantity', operator: 'GTE', value: '2' },
    ])
  })

  it('does not silently discard an unfinished fixed filter', () => {
    const definition = buildReportDefinition({ primaryModuleCode: 'customers', outputFields: ['primary.title'],
      metricOperation: 'COUNT', filters: [{ field: '', operator: 'EQ', value: 'ACTIVE' }],
      maxScanRows: 100, limit: 20 })
    expect(definition.filters).toEqual([{ alias: '', fieldCode: '', operator: 'EQ', value: 'ACTIVE' }])
  })
})
