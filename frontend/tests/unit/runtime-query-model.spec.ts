import { describe, expect, it } from 'vitest'

import { defaultRuntimeListState, parseRuntimeListQuery, runtimeListRouteQuery } from '../../src/views/system/runtimeQueryModel'

describe('runtime query URL model', () => {
  it('round-trips canonical UTF-8 query state', () => {
    const state = defaultRuntimeListState()
    state.page = 3
    state.recordScope = 'archived'
    state.q = '现场 工单'
    state.columns = ['subject', 'amount']
    state.sort = [{ fieldCode: 'amount', direction: 'DESC', nulls: 'LAST', currency: 'CNY' }]
    state.filter = { kind: 'AND', children: [
      { kind: 'PREDICATE', fieldCode: 'subject', operator: 'CONTAINS', value: '巡检' },
      { kind: 'NOT', children: [{ kind: 'PREDICATE', fieldCode: 'amount', operator: 'LT', value: 100 }] },
    ] }
    const route = runtimeListRouteQuery('work_order', state, { mode: 'view', record: '9' })
    const parsed = parseRuntimeListQuery(route)
    expect(parsed.invalid).toBe(false)
    expect(parsed.state).toEqual(state)
    expect(route.mode).toBe('view')
    expect(route.record).toBe('9')
  })

  it('drops malformed values and reports one recoverable invalid state', () => {
    const parsed = parseRuntimeListQuery({ module: 'work_order', page: '0', size: '999', recordScope: 'all', sort: '$bad', unknown: '1' })
    expect(parsed.invalid).toBe(true)
    expect(parsed.state).toEqual(defaultRuntimeListState())
  })

  it('round-trips the dedicated draft scope', () => {
    const state = defaultRuntimeListState()
    state.recordScope = 'draft'
    state.page = 2
    state.q = '我的 草稿'

    const route = runtimeListRouteQuery('work_order', state)
    const parsed = parseRuntimeListQuery(route)

    expect(route.recordScope).toBe('draft')
    expect(parsed.invalid).toBe(false)
    expect(parsed.state).toEqual(state)
  })

  it('rejects URLs beyond the share limit', () => {
    const state = defaultRuntimeListState()
    state.q = 'x'.repeat(100)
    state.filter = { kind: 'PREDICATE', fieldCode: 'subject', operator: 'IN', value: Array.from({ length: 100 }, (_, index) => `值-${index}-${'z'.repeat(60)}`) }
    expect(() => runtimeListRouteQuery('work_order', state)).toThrow('URL_QUERY_TOO_LONG')
  })
})
