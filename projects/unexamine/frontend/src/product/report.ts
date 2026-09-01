export interface ReportDefinitionInput {
  primaryModuleCode: string
  relatedModuleCode?: string
  relationType?: 'REFERENCE' | 'SUBTABLE' | 'CASCADE'
  joinType?: 'INNER' | 'LEFT'
  primaryRelationField?: string
  relatedRelationField?: string
  outputFields: string[]
  metricOperation: 'COUNT' | 'SUM' | 'AVG' | 'MIN' | 'MAX'
  metricField?: string
  dimensionType?: 'FIELD' | 'TIME' | 'PERSON' | 'DEPARTMENT' | 'STATUS'
  dimensionField?: string
  filterField?: string
  filterOperator?: string
  filterValue?: string
  sortField?: string
  sortDirection?: 'ASC' | 'DESC'
  timeField?: string
  timeFrom?: string
  timeTo?: string
  maxScanRows: number
  limit: number
}

export function splitQualifiedField(value?: string): { alias: string; fieldCode: string } | undefined {
  if (!value) return undefined
  const separator = value.indexOf('.')
  if (separator < 1 || separator === value.length - 1) return undefined
  return { alias: value.slice(0, separator), fieldCode: value.slice(separator + 1) }
}

export function buildReportDefinition(input: ReportDefinitionInput): Record<string, unknown> {
  const modules = [{ alias: 'primary', moduleCode: input.primaryModuleCode.trim() }]
  const relations: Record<string, unknown>[] = []
  if (input.relatedModuleCode?.trim()) {
    modules.push({ alias: 'related', moduleCode: input.relatedModuleCode.trim() })
    relations.push({
      relationType: input.relationType || 'REFERENCE', joinType: input.joinType || 'INNER',
      leftAlias: 'primary', leftField: input.primaryRelationField || 'id',
      rightAlias: 'related', rightField: input.relatedRelationField || '',
    })
  }
  const metricField = splitQualifiedField(input.metricField)
  const dimensionField = splitQualifiedField(input.dimensionField)
  const filterField = splitQualifiedField(input.filterField)
  const sortField = splitQualifiedField(input.sortField)
  const timeField = splitQualifiedField(input.timeField)
  return {
    modules,
    relations,
    outputFields: input.outputFields.map(splitQualifiedField).filter(Boolean),
    metric: { operation: input.metricOperation, ...(metricField || {}) },
    dimension: dimensionField ? { type: input.dimensionType || 'FIELD', ...dimensionField } : {},
    filters: filterField ? [{ ...filterField, operator: input.filterOperator || 'EQ', value: input.filterValue || '' }] : [],
    sort: sortField ? { ...sortField, direction: input.sortDirection || 'DESC' } : {},
    timeField: timeField ? { ...timeField, from: input.timeFrom || '', to: input.timeTo || '' } : {},
    maxScanRows: Math.max(1, Math.min(1000, Math.round(input.maxScanRows || 500))),
    limit: Math.max(1, Math.min(50, Math.round(input.limit || 20))),
  }
}
