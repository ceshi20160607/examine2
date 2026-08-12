import { describe, expect, it } from 'vitest'

import {
  canonicalReportFieldCodes,
  MAX_REPORT_FIELDS,
  moveReportField,
  reportFieldLabel,
  validateReportFields,
} from '@/views/system/admin/reportEditorModel'

describe('report editor model', () => {
  it('preserves field order while rejecting empty and duplicate definitions', () => {
    expect(canonicalReportFieldCodes([' amount ', 'recordNo', 'amount', '']))
      .toEqual(['amount', 'recordNo'])
    expect(validateReportFields([])).toContain('至少')
    expect(validateReportFields(['amount', 'amount'])).toContain('重复')
    expect(validateReportFields(['amount', 'recordNo'])).toBe('')
  })

  it('bounds definitions and moves only within the ordered list', () => {
    const oversized = Array.from({ length: MAX_REPORT_FIELDS + 1 }, (_, index) => `f${index}`)
    expect(canonicalReportFieldCodes(oversized)).toHaveLength(MAX_REPORT_FIELDS)
    expect(validateReportFields(oversized)).toContain('最多')
    expect(moveReportField(['a', 'b', 'c'], 1, -1)).toEqual(['b', 'a', 'c'])
    expect(moveReportField(['a', 'b', 'c'], 0, -1)).toEqual(['a', 'b', 'c'])
  })

  it('labels capability fields without changing their backend types', () => {
    expect(reportFieldLabel({ code: 'amount', name: '金额', type: 'DECIMAL' }))
      .toBe('金额 · amount · DECIMAL')
  })
})
