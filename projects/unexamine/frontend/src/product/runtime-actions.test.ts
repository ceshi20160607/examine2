import { describe, expect, it } from 'vitest'
import { conversionTargetCodes } from './runtime-actions'

describe('runtime conversion actions', () => {
  it('extracts unique configured targets in their published order', () => {
    expect(conversionTargetCodes(JSON.stringify({ targets: [
      { moduleCode: 'opportunity' }, { moduleCode: 'order' }, { moduleCode: 'opportunity' },
    ] }))).toEqual(['opportunity', 'order'])
  })

  it('supports the compact single-target form and rejects invalid configuration', () => {
    expect(conversionTargetCodes('{"targetModuleCode":"lead"}')).toEqual(['lead'])
    expect(conversionTargetCodes('{broken')).toEqual([])
    expect(conversionTargetCodes('{"targetModuleCode":"Other System"}')).toEqual([])
  })
})
