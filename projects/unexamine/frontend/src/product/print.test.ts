import { describe, expect, it } from 'vitest'
import { cyclePrintField, printFieldMode, printPreviewMatchesDraft } from './print'

describe('Cycle 51 print template rules', () => {
  it('cycles a field through body, detail and unused without duplication', () => {
    let state = cyclePrintField('amount', [], [])
    expect(state).toEqual({ fieldCodes: ['amount'], detailFieldCodes: [] })
    state = cyclePrintField('amount', state.fieldCodes, state.detailFieldCodes)
    expect(state).toEqual({ fieldCodes: [], detailFieldCodes: ['amount'] })
    state = cyclePrintField('amount', state.fieldCodes, state.detailFieldCodes)
    expect(state).toEqual({ fieldCodes: [], detailFieldCodes: [] })
  })

  it('gives detail selection precedence when reading malformed local state', () => {
    expect(printFieldMode('amount', ['amount'], ['amount'])).toBe('detail')
  })

  it('allows publish only for a paginated preview of the current draft', () => {
    const preview = { draftRevision: 3, pages: [{ pageNumber: 1 }] } as never
    expect(printPreviewMatchesDraft(preview, 3)).toBe(true)
    expect(printPreviewMatchesDraft(preview, 4)).toBe(false)
    expect(printPreviewMatchesDraft({ draftRevision: 3, pages: [] } as never, 3)).toBe(false)
  })
})
