import { describe, expect, it } from 'vitest'

import type { DashboardWidgetDraft } from '@/types/dashboard'
import {
  cloneWidget,
  defaultWidgetGrid,
  defaultWidgetStatistics,
  gridsOverlap,
  validateDashboardWidgets,
  validateWidgetStatistics,
} from '@/views/system/admin/dashboardEditorModel'
import { statisticsCapabilities } from '@/views/system/admin/statisticsEditorModel'

function widget(overrides: Partial<DashboardWidgetDraft> = {}): DashboardWidgetDraft {
  return {
    code: 'count_1', type: 'STAT_COUNT', title: '订单数', dataSourceId: '30',
    rowLimit: null, grid: { x: 0, y: 0, width: 3, height: 2 }, ...overrides,
  }
}

describe('dashboard numeric desktop layout model', () => {
  it('places ordered cards in the first free 12-column rectangle', () => {
    const first = widget()
    expect(defaultWidgetGrid('STAT_COUNT', [first])).toEqual({ x: 3, y: 0, width: 3, height: 2 })
    const list = defaultWidgetGrid('DATA_LIST', [first, widget({ code: 'count_2', grid: { x: 3, y: 0, width: 3, height: 2 } })])
    expect(list).toEqual({ x: 6, y: 0, width: 6, height: 5 })
    expect(gridsOverlap(first.grid, list)).toBe(false)
  })

  it('rejects duplicate codes, overlap, invalid rowLimit, missing pins and out-of-bounds rectangles', () => {
    const issues = validateDashboardWidgets([
      widget(),
      widget({ type: 'DATA_LIST', title: '', dataSourceId: '', rowLimit: 21, grid: { x: 2, y: 0, width: 11, height: 4 } }),
    ])
    expect(issues.map(issue => issue.code)).toEqual(expect.arrayContaining([
      'WIDGET_CODE_DUPLICATE', 'WIDGET_TITLE_REQUIRED', 'WIDGET_SOURCE_REQUIRED', 'DATA_LIST_ROW_LIMIT_INVALID',
      'WIDGET_GRID_OUT_OF_BOUNDS', 'WIDGET_GRID_OVERLAP',
    ]))
  })

  it('validates metric, grouped charts and bounded aligned trends against exact version capabilities', () => {
    const capabilities = statisticsCapabilities([
      { code: 'title', name: '标题', type: 'TEXT', readable: true, numeric: false, temporal: false, groupable: true },
      { code: 'amount', name: '金额', type: 'DECIMAL', readable: true, numeric: true, temporal: false, groupable: false },
      { code: 'createdAt', name: '创建时间', type: 'DATETIME', readable: true, numeric: false, temporal: true, groupable: true },
    ])
    const stat = widget({ type: 'STAT_VALUE', statistics: { aggregation: 'SUM', measureFieldCode: 'amount', grouping: null, trend: null } })
    const bar = widget({ type: 'BAR_CHART', statistics: { aggregation: 'COUNT', measureFieldCode: null, grouping: { fieldCode: 'title', bucketLimit: 20 }, trend: null } })
    const line = widget({ type: 'LINE_TREND', statistics: { aggregation: 'COUNT', measureFieldCode: null, grouping: null, trend: { fieldCode: 'createdAt', grain: 'MONTH', startInclusive: '2026-01-01', endExclusive: '2026-07-01' } } })
    expect(validateWidgetStatistics(stat, capabilities)).toEqual([])
    expect(validateWidgetStatistics(bar, capabilities)).toEqual([])
    expect(validateWidgetStatistics(line, capabilities)).toEqual([])

    bar.statistics!.grouping!.fieldCode = 'amount'
    expect(validateWidgetStatistics(bar, capabilities).map(issue => issue.code)).toContain('GROUP_NOT_CAPABLE')
    expect(defaultWidgetStatistics('PIE_CHART')).toEqual({ aggregation: 'COUNT', measureFieldCode: null, grouping: { fieldCode: '', bucketLimit: 10 }, trend: null })
  })

  it('accepts one published KPI root and strips every data-source query field from the saved draft', () => {
    const kpi = widget({
      type: 'KPI_VALUE', kpiId: '90', dataSourceId: undefined, rowLimit: undefined, statistics: undefined,
      grid: { x: 0, y: 0, width: 6, height: 5 },
    })
    expect(validateDashboardWidgets([kpi])).toEqual([])
    expect(cloneWidget(kpi)).toEqual({
      code: 'count_1', type: 'KPI_VALUE', title: '订单数', kpiId: '90',
      behavior: { refreshSeconds: 0, clickThrough: null, styleVariant: 'STANDARD' },
      grid: { x: 0, y: 0, width: 6, height: 5 },
    })

    expect(validateDashboardWidgets([{ ...kpi, kpiId: '' }]).map(issue => issue.code)).toContain('WIDGET_KPI_REQUIRED')
    expect(validateDashboardWidgets([{ ...kpi, dataSourceId: '30' }]).map(issue => issue.code)).toContain('KPI_DATA_SOURCE_FORBIDDEN')
  })

  it('supports ranking, progress, todo and quick-entry behavior boundaries', () => {
    const ranking = widget({
      type: 'RANKING',
      statistics: { aggregation: 'COUNT', measureFieldCode: null, grouping: { fieldCode: 'status', bucketLimit: 10 }, trend: null },
      behavior: { refreshSeconds: 30, clickThrough: '/systems/10/orders', styleVariant: 'EMPHASIS' },
    })
    const progress = widget({
      code: 'progress_1', type: 'PROGRESS',
      statistics: { aggregation: 'AVG', measureFieldCode: 'progress', grouping: null, trend: null },
      grid: { x: 3, y: 0, width: 3, height: 2 },
    })
    const todo = widget({ code: 'todo_1', type: 'TODO_LIST', rowLimit: 10, grid: { x: 0, y: 3, width: 6, height: 5 } })
    const quick = widget({ code: 'quick_1', type: 'QUICK_ENTRY', rowLimit: 6, grid: { x: 6, y: 3, width: 6, height: 5 } })
    expect(validateDashboardWidgets([ranking, progress, todo, quick]).map(issue => issue.code)).not.toContain('DATA_LIST_ROW_LIMIT_INVALID')
    expect(validateDashboardWidgets([{ ...ranking, behavior: { refreshSeconds: 5, clickThrough: 'https://evil.example', styleVariant: 'STANDARD' } }])
      .map(issue => issue.code)).toEqual(expect.arrayContaining(['WIDGET_REFRESH_INVALID', 'WIDGET_CLICK_THROUGH_INVALID']))
    expect(defaultWidgetStatistics('RANKING')?.grouping).toEqual({ fieldCode: '', bucketLimit: 10 })
    expect(defaultWidgetStatistics('PROGRESS')?.grouping).toBeNull()
  })
})
