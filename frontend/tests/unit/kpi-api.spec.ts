import { beforeEach, describe, expect, it, vi } from 'vitest'

import { kpiAdminApi, kpiDtoMapper, runtimeKpiApi } from '@/services/kpi'

function envelope(data: unknown) {
  return new Response(JSON.stringify({ code: 'OK', message: '', data, requestId: 'r1', traceId: 't1', errors: [] }), {
    status: 200, headers: { 'Content-Type': 'application/json' },
  })
}

describe('KPI management and runtime API', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    document.cookie = 'EXAMINE_CSRF=csrf-token; path=/'
  })

  it('owns the complete definition, target, calculation and runtime endpoint set', async () => {
    const fetchMock = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) => Promise.resolve(envelope([])))
    vi.stubGlobal('fetch', fetchMock)
    const draft = {
      expectedVersion: 3, name: '订单金额', description: null, dataSourceId: '90071992547409931234',
      subjectType: 'MEMBER' as const, periodType: 'MONTH' as const, aggregation: 'SUM' as const,
      measureFieldCode: 'amount', timeFieldCode: 'createdAt', direction: 'AT_LEAST' as const,
      warningThreshold: '0.8',
    }

    await kpiAdminApi.list('10')
    await kpiAdminApi.create('10', {
      code: 'order_amount', name: '订单金额', description: null,
      dataSourceId: draft.dataSourceId, subjectType: draft.subjectType, periodType: draft.periodType,
      aggregation: draft.aggregation, measureFieldCode: draft.measureFieldCode, timeFieldCode: draft.timeFieldCode,
      direction: draft.direction, warningThreshold: draft.warningThreshold,
    })
    await kpiAdminApi.detail('10', '20/1')
    await kpiAdminApi.saveDraft('10', '20/1', draft)
    await kpiAdminApi.checkDraft('10', '20/1')
    await kpiAdminApi.publishDraft('10', '20/1', 4)
    await kpiAdminApi.versions('10', '20/1')
    await kpiAdminApi.targets('10', '20/1')
    await kpiAdminApi.createTarget('10', '20/1', { subjectId: '30/1', periodStart: '2026-08-01', targetValue: '12345678901234567890.123' })
    await kpiAdminApi.updateTarget('10', '40/1', { targetValue: '0', expectedVersion: 2 })
    await kpiAdminApi.recalculate('10', '40/1')
    await kpiAdminApi.history('10', '40/1')
    await runtimeKpiApi.list('10', { periodType: 'MONTH', periodStart: '2026-08-01' })

    const root = '/api/v1/systems/10/admin/kpis'
    const targetRoot = '/api/v1/systems/10/admin/kpi-targets/40%2F1'
    expect(fetchMock.mock.calls.map(call => call[0])).toEqual([
      root, root, `${root}/20%2F1`, `${root}/20%2F1/draft`, `${root}/20%2F1/draft:check`,
      `${root}/20%2F1/draft:publish`, `${root}/20%2F1/versions`, `${root}/20%2F1/targets`,
      `${root}/20%2F1/targets`, targetRoot, `${targetRoot}:calculate`, `${targetRoot}/calculations`,
      '/api/v1/systems/10/kpis?periodType=MONTH&periodStart=2026-08-01',
    ])
    const save = fetchMock.mock.calls[3]![1] as RequestInit
    const createTarget = fetchMock.mock.calls[8]![1] as RequestInit
    expect(JSON.parse(String(save.body))).toEqual(draft)
    expect(JSON.parse(String(createTarget.body))).toEqual({ subjectId: '30/1', periodStart: '2026-08-01', targetValue: '12345678901234567890.123' })
    expect((createTarget.headers as Headers).get('Idempotency-Key')).toBeTruthy()
    expect((fetchMock.mock.calls[10]![1] as RequestInit).method).toBe('POST')
  })

  it('centralizes DTO variants while preserving every ID and decimal as a string', () => {
    const target = kpiDtoMapper.target({
      id: 42,
      kpiId: '90071992547409931234',
      kpiVersionId: '52',
      kpiVersionNo: 7,
      kpiCode: 'sales',
      kpiName: '销售额',
      subjectType: 'ROLE',
      subjectId: 91,
      subjectDisplayName: '销售角色',
      period: { type: 'QUARTER', startInclusive: '2026-07-01', endExclusive: '2026-10-01' },
      targetValue: '12345678901234567890.123456789',
      version: 3,
      latestCalculation: {
        id: 70,
        target: { id: 42, targetValue: '12345678901234567890.123456789' },
        warningStatus: 'AT_RISK',
        actualValue: '10000000000000000000.000000001',
        attainment: '0.812345678901',
        statisticsQueryId: 'query-1',
        matchedRecordCount: 2,
        authorizationEpoch: 15,
        completedAt: '2026-08-03T01:02:03Z',
        subject: { memberIds: [100, '101'] },
        trend: [{ startInclusive: '2026-07-01', endExclusive: '2026-08-01', value: '1.01', recordCount: 2 }],
      },
    })

    expect(target.id).toBe('42')
    expect(target.kpiId).toBe('90071992547409931234')
    expect(target.subjectId).toBe('91')
    expect(target.kpiVersionNumber).toBe(7)
    expect(target.targetValue).toBe('12345678901234567890.123456789')
    expect(target.latestCalculation).toMatchObject({
      status: 'AT_RISK', actualValue: '10000000000000000000.000000001', attainment: '0.812345678901',
    })
    expect(target.latestCalculation?.explanation).toMatchObject({ statisticsQueryId: 'query-1', matchedCount: '2', authorizationEpoch: '15', subjectMemberIds: ['100', '101'] })
    expect(target.latestCalculation?.explanation.trend[0]?.matchedCount).toBe('2')
  })

  it('uses backend warning status and stable error code without calculating attainment in JavaScript', () => {
    const failed = kpiDtoMapper.calculation({
      id: '80', targetId: '42', status: 'COMPLETED', warningStatus: 'CALCULATION_FAILED',
      targetValue: '0', actualValue: null, attainment: null, errorCode: 'KPI_SOURCE_PERMISSION_DENIED',
    })
    expect(failed.status).toBe('CALCULATION_FAILED')
    expect(failed.errorCode).toBe('KPI_SOURCE_PERMISSION_DENIED')
    expect(failed.actualValue).toBeNull()
    expect(failed.attainment).toBeNull()
  })

  it('reads immutable source and role-member pins from nested historical snapshots', () => {
    const version = kpiDtoMapper.version({
      id: '71', kpiId: '70', versionNo: 2, sourceDraftVersion: 3, code: 'sales', name: '销售额',
      subjectType: 'ROLE', periodType: 'YEAR', aggregation: 'SUM', direction: 'AT_LEAST', warningThreshold: '0.8',
      source: {
        dataSourceId: '30', dataSourceCode: 'orders', dataSourceVersionId: '31', dataSourceVersionNumber: 4,
        moduleCode: 'orders', schemaVersionId: 'schema-4',
        measureField: { logicalFieldId: '91', code: 'amount', name: '金额', type: 'DECIMAL', queryType: 'DECIMAL' },
        timeField: { logicalFieldId: '92', code: 'createdAt', name: '创建时间', type: 'DATETIME', queryType: 'DATETIME' },
      },
      fingerprint: 'a'.repeat(64), publishedBy: '60', publishedAt: '2026-08-03T01:00:00Z', active: true,
    })
    const calculation = kpiDtoMapper.calculation({
      id: '90', warningStatus: 'ACHIEVED', actualValue: '2', attainment: '2', statisticsQueryId: 'query-2', matchedRecordCount: 1,
      authorizationEpoch: '10', completedAt: '2026-08-03T01:00:00Z',
      target: { targetId: '80', targetValue: '1' }, subject: { roleMemberIds: ['62', '61'] },
      definition: { aggregation: 'SUM', source: {
        dataSourceCode: 'orders', dataSourceVersionId: '31', dataSourceVersionNumber: 4, schemaVersionId: 'schema-4',
        measureField: version.measureField, timeField: version.timeField,
      } },
    })

    expect(version).toMatchObject({ dataSourceId: '30', dataSourceCode: 'orders', dataSourceVersionNumber: 4, schemaVersionId: 'schema-4' })
    expect(version.measureField?.logicalFieldId).toBe('91')
    expect(calculation.targetId).toBe('80')
    expect(calculation.explanation).toMatchObject({ dataSourceCode: 'orders', dataSourceVersionNumber: 4, subjectMemberIds: ['62', '61'] })
  })

  it('flattens an applicable runtime target without losing the immutable definition identity', () => {
    const target = kpiDtoMapper.target({
      target: {
        id: '80', kpiId: '70', kpiVersionId: '71', kpiVersionNumber: 3,
        subjectType: 'DEPARTMENT', subjectId: '61', subjectDisplayName: '销售部',
        period: { type: 'QUARTER', startInclusive: '2026-07-01', endExclusive: '2026-10-01' },
        targetValue: '100.01', version: 2,
      },
      definition: { id: '71', kpiId: '70', versionNumber: 3, code: 'sales', name: '销售额', subjectType: 'DEPARTMENT', periodType: 'QUARTER' },
      latestCalculation: { id: '90', target: { targetId: '80', targetValue: '100.01' }, warningStatus: 'ACHIEVED', actualValue: '101', attainment: '1.009899010099' },
    })

    expect(target).toMatchObject({ id: '80', kpiId: '70', kpiVersionId: '71', kpiVersionNumber: 3, kpiCode: 'sales', kpiName: '销售额', subjectType: 'DEPARTMENT', subjectName: '销售部', periodType: 'QUARTER', periodStart: '2026-07-01', periodEndExclusive: '2026-10-01' })
    expect(target.latestCalculation?.status).toBe('ACHIEVED')
  })
})
