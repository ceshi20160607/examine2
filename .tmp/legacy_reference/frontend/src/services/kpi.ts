import { apiRequest } from './api'
import type {
  CreateKpiInput,
  CreateKpiTargetInput,
  KpiAggregation,
  KpiAttainmentDirection,
  KpiCalculation,
  KpiCalculationExplanation,
  KpiCheckIssue,
  KpiCheckResult,
  KpiDetail,
  KpiDraft,
  KpiFieldPin,
  KpiPeriodType,
  KpiPublishResult,
  KpiResultStatus,
  KpiSubjectType,
  KpiSummary,
  KpiTarget,
  KpiTrendBucket,
  KpiVersion,
  RuntimeKpiTarget,
  SaveKpiDraftInput,
  UpdateKpiTargetInput,
} from '@/types/kpi'

type JsonRecord = Record<string, unknown>

const adminBase = (systemId: string) =>
  `/api/v1/systems/${encodeURIComponent(systemId)}/admin/kpis`
const kpiPath = (systemId: string, kpiId: string) =>
  `${adminBase(systemId)}/${encodeURIComponent(kpiId)}`
const targetPath = (systemId: string, targetId: string) =>
  `/api/v1/systems/${encodeURIComponent(systemId)}/admin/kpi-targets/${encodeURIComponent(targetId)}`
const runtimeBase = (systemId: string) =>
  `/api/v1/systems/${encodeURIComponent(systemId)}/kpis`

function record(value: unknown): JsonRecord {
  return value && typeof value === 'object' && !Array.isArray(value) ? value as JsonRecord : {}
}

function value(source: JsonRecord, ...keys: string[]) {
  for (const key of keys) if (source[key] !== undefined && source[key] !== null) return source[key]
  return undefined
}

function textValue(input: unknown, fallback = '') {
  return input === undefined || input === null ? fallback : String(input)
}

function nullableText(input: unknown) {
  return input === undefined || input === null || input === '' ? null : String(input)
}

function numberValue(input: unknown, fallback = 0) {
  const parsed = Number(input)
  return Number.isFinite(parsed) ? parsed : fallback
}

function booleanValue(input: unknown) {
  return input === true || input === 'true' || input === 1
}

function arrayValue(input: unknown): unknown[] {
  if (Array.isArray(input)) return input
  const payload = record(input)
  const nested = value(payload, 'items', 'content', 'records', 'definitions', 'targets', 'calculations', 'versions')
  return Array.isArray(nested) ? nested : []
}

function subjectType(input: unknown): KpiSubjectType {
  return ['DEPARTMENT', 'ROLE'].includes(String(input)) ? String(input) as KpiSubjectType : 'MEMBER'
}

function periodType(input: unknown): KpiPeriodType {
  return ['QUARTER', 'YEAR'].includes(String(input)) ? String(input) as KpiPeriodType : 'MONTH'
}

function aggregation(input: unknown): KpiAggregation {
  return ['SUM', 'AVG', 'MIN', 'MAX'].includes(String(input)) ? String(input) as KpiAggregation : 'COUNT'
}

function direction(input: unknown): KpiAttainmentDirection {
  return String(input) === 'AT_MOST' ? 'AT_MOST' : 'AT_LEAST'
}

function resultStatus(input: unknown, errorCode?: unknown): KpiResultStatus {
  if (errorCode || String(input) === 'CALCULATION_FAILED' || String(input) === 'FAILED') return 'CALCULATION_FAILED'
  return ['ACHIEVED', 'AT_RISK', 'MISSED'].includes(String(input)) ? String(input) as KpiResultStatus : 'MISSED'
}

function normalizeDraft(input: unknown): KpiDraft {
  const source = record(input)
  return {
    dataSourceId: textValue(value(source, 'dataSourceId', 'sourceId')),
    subjectType: subjectType(value(source, 'subjectType')),
    periodType: periodType(value(source, 'periodType')),
    aggregation: aggregation(value(source, 'aggregation')),
    measureFieldCode: nullableText(value(source, 'measureFieldCode', 'measureCode')),
    timeFieldCode: textValue(value(source, 'timeFieldCode', 'temporalFieldCode')),
    direction: direction(value(source, 'direction', 'attainmentDirection')),
    warningThreshold: textValue(value(source, 'warningThreshold'), '0.8'),
  }
}

function normalizeSummary(input: unknown): KpiSummary {
  const source = record(input)
  return {
    id: textValue(source.id),
    systemId: textValue(source.systemId),
    tenantId: textValue(source.tenantId),
    code: textValue(source.code),
    name: textValue(source.name),
    description: nullableText(source.description),
    draftVersion: numberValue(value(source, 'draftVersion', 'version'), 1),
    activeVersionId: nullableText(source.activeVersionId),
    activeVersionNumber: value(source, 'activeVersionNumber', 'activeVersionNo') === undefined
      ? null : numberValue(value(source, 'activeVersionNumber', 'activeVersionNo')),
    createdAt: textValue(source.createdAt),
    updatedAt: textValue(source.updatedAt),
    version: numberValue(source.version, 1),
  }
}

function normalizeDetail(input: unknown): KpiDetail {
  const source = record(input)
  return { ...normalizeSummary(source), draft: normalizeDraft(value(source, 'draft', 'draftSnapshot') ?? source) }
}

function normalizeFieldPin(input: unknown): KpiFieldPin | null {
  const source = record(input)
  if (!Object.keys(source).length) return null
  return {
    logicalFieldId: textValue(value(source, 'logicalFieldId', 'fieldId', 'id')),
    code: textValue(value(source, 'code', 'fieldCode')),
    name: textValue(value(source, 'name', 'fieldName')),
    type: textValue(value(source, 'type', 'fieldType')),
    queryType: textValue(value(source, 'queryType', 'fieldQueryType')),
  }
}

function normalizeVersion(input: unknown): KpiVersion {
  const source = record(input)
  const snapshot = record(source.snapshot)
  const sourcePin = record(value(source, 'source', 'sourcePin'))
  const measure = value(source, 'measureField', 'measureFieldPin')
    ?? value(sourcePin, 'measureField', 'measureFieldPin')
    ?? (value(source, 'measureFieldCode') ? {
      logicalFieldId: value(source, 'measureLogicalFieldId', 'measureFieldId'),
      code: source.measureFieldCode,
      name: source.measureFieldName,
      type: source.measureFieldType,
      queryType: source.measureFieldQueryType,
    } : null)
  const time = value(source, 'timeField', 'timeFieldPin') ?? value(sourcePin, 'timeField', 'timeFieldPin') ?? {
    logicalFieldId: value(source, 'timeLogicalFieldId', 'timeFieldId'),
    code: value(source, 'timeFieldCode', 'temporalFieldCode'),
    name: value(source, 'timeFieldName', 'temporalFieldName'),
    type: value(source, 'timeFieldType', 'temporalFieldType'),
    queryType: value(source, 'timeFieldQueryType', 'temporalFieldQueryType'),
  }
  return {
    id: textValue(source.id),
    kpiId: textValue(value(source, 'kpiId', 'definitionId')),
    versionNumber: numberValue(value(source, 'versionNumber', 'versionNo')),
    sourceDraftVersion: numberValue(source.sourceDraftVersion),
    code: textValue(value(source, 'code') ?? snapshot.code),
    name: textValue(value(source, 'name') ?? snapshot.name),
    description: nullableText(value(source, 'description') ?? snapshot.description),
    subjectType: subjectType(value(source, 'subjectType') ?? snapshot.subjectType),
    periodType: periodType(value(source, 'periodType') ?? snapshot.periodType),
    aggregation: aggregation(value(source, 'aggregation') ?? snapshot.aggregation),
    direction: direction(value(source, 'direction', 'attainmentDirection') ?? value(snapshot, 'direction', 'attainmentDirection')),
    warningThreshold: textValue(value(source, 'warningThreshold') ?? snapshot.warningThreshold, '0'),
    dataSourceId: textValue(value(source, 'dataSourceId', 'sourceId') ?? value(sourcePin, 'dataSourceId', 'sourceId')),
    dataSourceVersionId: textValue(value(source, 'dataSourceVersionId', 'sourceVersionId') ?? value(sourcePin, 'dataSourceVersionId', 'sourceVersionId')),
    dataSourceVersionNumber: numberValue(value(source, 'dataSourceVersionNumber', 'sourceVersionNumber', 'dataSourceVersionNo') ?? value(sourcePin, 'dataSourceVersionNumber', 'sourceVersionNumber', 'dataSourceVersionNo')),
    dataSourceCode: textValue(value(source, 'dataSourceCode', 'sourceCode') ?? value(sourcePin, 'dataSourceCode', 'sourceCode')),
    moduleCode: textValue(source.moduleCode ?? sourcePin.moduleCode),
    schemaVersionId: textValue(source.schemaVersionId ?? sourcePin.schemaVersionId),
    measureField: normalizeFieldPin(measure),
    timeField: normalizeFieldPin(time) ?? { logicalFieldId: '', code: '', name: '', type: '', queryType: '' },
    fingerprint: textValue(source.fingerprint),
    publishedBy: textValue(source.publishedBy),
    publishedAt: textValue(source.publishedAt),
    active: booleanValue(source.active),
  }
}

function normalizeCheck(input: unknown): KpiCheckResult {
  const source = record(input)
  const issues = arrayValue(source.issues).map((item): KpiCheckIssue => {
    const issue = record(item)
    return {
      severity: String(issue.severity) === 'WARNING' ? 'WARNING' : 'BLOCKER',
      code: textValue(issue.code),
      path: textValue(issue.path),
      message: textValue(issue.message),
    }
  })
  const blockerCount = numberValue(source.blockerCount, issues.filter(issue => issue.severity === 'BLOCKER').length)
  const warningCount = numberValue(source.warningCount, issues.filter(issue => issue.severity === 'WARNING').length)
  return {
    kpiId: textValue(value(source, 'kpiId', 'definitionId')),
    checkedDraftVersion: numberValue(source.checkedDraftVersion),
    valid: source.valid === undefined ? blockerCount === 0 : booleanValue(source.valid),
    blockerCount,
    warningCount,
    issues,
  }
}

function normalizeTrendBucket(input: unknown): KpiTrendBucket {
  const source = record(input)
  return {
    startInclusive: textValue(value(source, 'startInclusive', 'periodStart', 'bucketStart')),
    endExclusive: textValue(value(source, 'endExclusive', 'periodEndExclusive', 'bucketEnd')),
    value: textValue(value(source, 'value', 'actualValue'), '0'),
    matchedCount: textValue(value(source, 'matchedCount', 'recordCount'), '0'),
  }
}

function normalizeExplanation(input: unknown, calculationSource: JsonRecord): KpiCalculationExplanation {
  const source = record(input)
  const definition = record(calculationSource.definition)
  const sourcePin = record(value(definition, 'source', 'sourcePin'))
  const trend = value(source, 'trend', 'trendBuckets') ?? value(calculationSource, 'trend', 'trendBuckets')
  return {
    statisticsQueryId: textValue(value(source, 'statisticsQueryId', 'queryId') ?? value(calculationSource, 'statisticsQueryId', 'queryId')),
    matchedCount: textValue(value(source, 'matchedCount', 'matchedRecordCount') ?? value(calculationSource, 'matchedCount', 'matchedRecordCount'), '0'),
    aggregation: aggregation(value(source, 'aggregation') ?? calculationSource.aggregation ?? definition.aggregation),
    dataSourceCode: textValue(value(source, 'dataSourceCode', 'sourceCode') ?? value(calculationSource, 'dataSourceCode', 'sourceCode') ?? value(sourcePin, 'dataSourceCode', 'sourceCode')),
    dataSourceVersionId: textValue(value(source, 'dataSourceVersionId', 'sourceVersionId') ?? value(calculationSource, 'dataSourceVersionId', 'sourceVersionId') ?? value(sourcePin, 'dataSourceVersionId', 'sourceVersionId')),
    dataSourceVersionNumber: numberValue(value(source, 'dataSourceVersionNumber', 'sourceVersionNumber') ?? value(calculationSource, 'dataSourceVersionNumber', 'sourceVersionNumber') ?? value(sourcePin, 'dataSourceVersionNumber', 'sourceVersionNumber')),
    schemaVersionId: textValue(value(source, 'schemaVersionId') ?? calculationSource.schemaVersionId ?? sourcePin.schemaVersionId),
    measureField: normalizeFieldPin(value(source, 'measureField', 'measureFieldPin') ?? value(sourcePin, 'measureField', 'measureFieldPin')),
    timeField: normalizeFieldPin(value(source, 'timeField', 'timeFieldPin') ?? value(sourcePin, 'timeField', 'timeFieldPin')),
    authorizationEpoch: textValue(value(source, 'authorizationEpoch', 'authzEpoch') ?? value(calculationSource, 'authorizationEpoch', 'authzEpoch')),
    subjectMemberIds: arrayValue(
      value(source, 'subjectMemberIds', 'resolvedMemberIds')
      ?? value(record(source.subject), 'memberIds', 'resolvedMemberIds')
      ?? value(calculationSource, 'subjectMemberIds', 'resolvedMemberIds')
      ?? value(record(calculationSource.subject), 'roleMemberIds', 'memberIds', 'resolvedMemberIds'),
    ).map(item => textValue(item)),
    trend: arrayValue(trend).map(normalizeTrendBucket),
  }
}

function normalizeCalculation(input: unknown): KpiCalculation {
  const source = record(input)
  const target = record(source.target)
  const errorCode = nullableText(value(source, 'errorCode', 'failureCode'))
  return {
    id: textValue(source.id),
    targetId: textValue(value(source, 'targetId') ?? value(target, 'targetId', 'id')),
    status: resultStatus(value(source, 'warningStatus', 'resultStatus', 'status'), errorCode),
    errorCode,
    targetValue: textValue(value(source, 'targetValue') ?? target.targetValue, '0'),
    actualValue: nullableText(source.actualValue),
    attainment: nullableText(value(source, 'attainment', 'attainmentValue', 'attainmentRate')),
    calculatedAt: textValue(value(source, 'calculatedAt', 'completedAt', 'createdAt')),
    calculatedBy: textValue(value(source, 'calculatedBy', 'actorMemberId', 'createdBy')),
    explanation: normalizeExplanation(source.explanation, source),
  }
}

function normalizeTarget(input: unknown): KpiTarget {
  const payload = record(input)
  const nestedTarget = record(payload.target)
  const source = Object.keys(nestedTarget).length ? nestedTarget : payload
  const definition = record(value(payload, 'definition', 'kpiVersion'))
  const period = record(source.period)
  const latest = value(payload, 'latestCalculation', 'calculation') ?? value(source, 'latestCalculation', 'calculation')
  return {
    id: textValue(source.id),
    kpiId: textValue(value(source, 'kpiId', 'definitionId') ?? definition.kpiId),
    kpiVersionId: textValue(source.kpiVersionId ?? value(definition, 'id', 'kpiVersionId')),
    kpiVersionNumber: numberValue(value(source, 'kpiVersionNumber', 'kpiVersionNo') ?? value(definition, 'versionNumber', 'versionNo')),
    kpiCode: textValue(source.kpiCode ?? definition.code),
    kpiName: textValue(source.kpiName ?? definition.name),
    subjectType: subjectType(source.subjectType ?? definition.subjectType),
    subjectId: textValue(source.subjectId),
    subjectName: textValue(value(source, 'subjectName', 'subjectDisplayName')),
    periodType: periodType(value(source, 'periodType') ?? period.type ?? definition.periodType),
    periodStart: textValue(value(source, 'periodStart', 'startInclusive') ?? period.startInclusive),
    periodEndExclusive: textValue(value(source, 'periodEndExclusive', 'periodEnd', 'endExclusive') ?? period.endExclusive),
    targetValue: textValue(source.targetValue, '0'),
    version: numberValue(source.version, 1),
    createdAt: textValue(source.createdAt),
    updatedAt: textValue(source.updatedAt),
    latestCalculation: latest ? normalizeCalculation(latest) : null,
  }
}

export const kpiDtoMapper = {
  summary: normalizeSummary,
  detail: normalizeDetail,
  version: normalizeVersion,
  check: normalizeCheck,
  target: normalizeTarget,
  calculation: normalizeCalculation,
  summaries: (input: unknown) => arrayValue(input).map(normalizeSummary),
  versions: (input: unknown) => arrayValue(input).map(normalizeVersion),
  targets: (input: unknown) => arrayValue(input).map(normalizeTarget),
  calculations: (input: unknown) => arrayValue(input).map(normalizeCalculation),
}

export const kpiAdminApi = {
  async list(systemId: string) {
    return kpiDtoMapper.summaries(await apiRequest<unknown>(adminBase(systemId)))
  },
  async create(systemId: string, input: CreateKpiInput) {
    return kpiDtoMapper.detail(await apiRequest<unknown>(adminBase(systemId), {
      method: 'POST', body: input, idempotencyKey: crypto.randomUUID(),
    }))
  },
  async detail(systemId: string, kpiId: string) {
    return kpiDtoMapper.detail(await apiRequest<unknown>(kpiPath(systemId, kpiId)))
  },
  async saveDraft(systemId: string, kpiId: string, input: SaveKpiDraftInput) {
    return kpiDtoMapper.detail(await apiRequest<unknown>(`${kpiPath(systemId, kpiId)}/draft`, {
      method: 'PUT', body: input,
    }))
  },
  async checkDraft(systemId: string, kpiId: string) {
    return kpiDtoMapper.check(await apiRequest<unknown>(`${kpiPath(systemId, kpiId)}/draft:check`, { method: 'POST' }))
  },
  async publishDraft(systemId: string, kpiId: string, expectedVersion: number) {
    const payload = record(await apiRequest<unknown>(`${kpiPath(systemId, kpiId)}/draft:publish`, {
      method: 'POST', body: { expectedVersion }, idempotencyKey: crypto.randomUUID(),
    }))
    return {
      kpi: normalizeDetail(value(payload, 'kpi', 'definition')),
      version: normalizeVersion(payload.version),
    } satisfies KpiPublishResult
  },
  async versions(systemId: string, kpiId: string) {
    return kpiDtoMapper.versions(await apiRequest<unknown>(`${kpiPath(systemId, kpiId)}/versions`))
  },
  async targets(systemId: string, kpiId: string) {
    return kpiDtoMapper.targets(await apiRequest<unknown>(`${kpiPath(systemId, kpiId)}/targets`))
  },
  async createTarget(systemId: string, kpiId: string, input: CreateKpiTargetInput) {
    return normalizeTarget(await apiRequest<unknown>(`${kpiPath(systemId, kpiId)}/targets`, {
      method: 'POST', body: input, idempotencyKey: crypto.randomUUID(),
    }))
  },
  async updateTarget(systemId: string, targetId: string, input: UpdateKpiTargetInput) {
    return normalizeTarget(await apiRequest<unknown>(targetPath(systemId, targetId), { method: 'PUT', body: input }))
  },
  async recalculate(systemId: string, targetId: string) {
    return normalizeCalculation(await apiRequest<unknown>(`${targetPath(systemId, targetId)}:calculate`, {
      method: 'POST', idempotencyKey: crypto.randomUUID(),
    }))
  },
  async history(systemId: string, targetId: string) {
    return kpiDtoMapper.calculations(await apiRequest<unknown>(`${targetPath(systemId, targetId)}/calculations`))
  },
}

export const runtimeKpiApi = {
  async list(systemId: string, query: { periodType: KpiPeriodType; periodStart: string }) {
    const search = new URLSearchParams({ periodType: query.periodType, periodStart: query.periodStart })
    return kpiDtoMapper.targets(await apiRequest<unknown>(`${runtimeBase(systemId)}?${search}`)) as RuntimeKpiTarget[]
  },
}
