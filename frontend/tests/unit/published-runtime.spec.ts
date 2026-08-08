import { describe, expect, it } from 'vitest'

import { evaluatePublishedRules, orderedPublishedFields, publishedPage } from '@/services/publishedRuntime'
import type { RuntimeFieldCapability, RuntimeRecordSchema } from '@/types/config'

const field = (code: string, required = false): RuntimeFieldCapability => ({
  fieldCode: code,
  fieldName: code,
  logicalFieldId: code,
  type: 'TEXT',
  mode: 'WRITABLE',
  readable: true,
  writable: true,
  sensitiveReadable: false,
  sensitiveQueryable: false,
  masked: false,
  operators: [],
  sortable: false,
  showInList: true,
  showInDetail: true,
  options: [],
  schema: { required },
})

function schema(): RuntimeRecordSchema {
  return {
    schemaVersionId: '88', moduleSnapshotId: '7', logicalModuleId: '7', checksum: 'a'.repeat(64),
    runtimeState: 'READY', authzEpoch: 3, fields: [field('status'), field('amount'), field('reason')],
    actions: ['CREATE'], queryLimits: { defaultSize: 50, maxSize: 200, maxSorts: 3 },
    publishedRuntime: {
      schemaVersionId: '88',
      pages: [{
        pageId: '10', pageCode: 'orders', type: 'LIST', density: 'COMPACT', columns: 24, gap: 8,
        labelPosition: 'TOP', stickyActions: true, pageSize: 20, sections: [],
        fields: [
          { fieldCode: 'amount', sortOrder: 1, gridRow: 1, gridColumn: 0, gridSpan: 8, width: 180, fixed: 'RIGHT' },
          { fieldCode: 'status', sortOrder: 2, gridRow: 1, gridColumn: 8, gridSpan: 8, width: 120, fixed: 'LEFT' },
        ],
      }],
      rules: [
        {
          ruleCode: 'high_amount', type: 'FIELD_REQUIRED', priority: 10,
          condition: { fieldCode: 'amount', operator: 'GT', value: 1000, children: [] },
          effects: [{ effect: 'REQUIRED', targetCode: 'reason', value: true }],
        },
        {
          ruleCode: 'closed', type: 'FIELD_READ_ONLY', priority: 20,
          condition: { fieldCode: 'status', operator: 'EQ', value: 'CLOSED', children: [] },
          effects: [
            { effect: 'READ_ONLY', targetCode: 'amount', value: true },
            { effect: 'DELETE_ALLOWED', value: false },
            { effect: 'APPROVAL_REQUIRED', value: true },
          ],
        },
      ],
    },
  }
}

describe('published member runtime model', () => {
  it('keeps the exact published page order, size, density, width and fixed-column metadata', () => {
    const current = schema()
    expect(publishedPage(current, 'LIST')).toMatchObject({ pageCode: 'orders', density: 'COMPACT', pageSize: 20 })
    expect(orderedPublishedFields(current, 'LIST', () => true).map((item) => item.fieldCode)).toEqual(['amount', 'status'])
  })

  it('evaluates required, readonly, delete and approval effects from one value state', () => {
    const decision = evaluatePublishedRules(schema(), { amount: 1500, status: 'CLOSED' })
    expect(decision.requiredFields.has('reason')).toBe(true)
    expect(decision.readonlyFields.has('amount')).toBe(true)
    expect(decision.deleteAllowed).toBe(false)
    expect(decision.approvalRequired).toBe(true)
  })

  it('does not apply conditional effects when the condition is false', () => {
    const decision = evaluatePublishedRules(schema(), { amount: 10, status: 'OPEN' })
    expect(decision.requiredFields.has('reason')).toBe(false)
    expect(decision.readonlyFields.has('amount')).toBe(false)
    expect(decision.deleteAllowed).toBe(true)
  })
})
