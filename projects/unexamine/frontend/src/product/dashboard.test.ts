import { describe, expect, it } from 'vitest'
import { dashboardComponentSpan, isCoreWorkspaceSummary, moveDashboardComponent, sourceDefinition } from './dashboard'
import type { DashboardComponentInput } from './types'

const component = (key: string, order: number): DashboardComponentInput => ({
  componentKey: key, componentType: 'METRIC', title: key, layout: {}, queryParameters: {},
  displayConfig: {}, sortOrder: order,
})

describe('Cycle 48 dashboard configuration rules', () => {
  it('reorders components and rewrites stable sort values', () => {
    expect(moveDashboardComponent([component('a', 0), component('b', 10), component('c', 20)], 2, 0)
      .map(item => [item.componentKey, item.sortOrder])).toEqual([['c', 0], ['a', 10], ['b', 20]])
  })

  it('keeps source definitions bounded and module context explicit', () => {
    expect(sourceDefinition('MODULE_RECORDS', ' customer ', 50)).toEqual({ moduleCode: 'customer', limit: 20 })
    expect(sourceDefinition('MESSAGE_ITEMS', '', 0)).toEqual({ limit: 5 })
  })

  it('bounds layout spans so malformed configuration cannot break the grid', () => {
    expect(dashboardComponentSpan({ width: 8 })).toBe(4)
    expect(dashboardComponentSpan({ width: 'bad' })).toBe(1)
  })

  it('does not repeat the workspace summary as a technical dashboard card', () => {
    expect(isCoreWorkspaceSummary('可进入系统')).toBe(true)
    expect(isCoreWorkspaceSummary('本月新增客户')).toBe(false)
  })
})
