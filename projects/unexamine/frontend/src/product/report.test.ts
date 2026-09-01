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
})
