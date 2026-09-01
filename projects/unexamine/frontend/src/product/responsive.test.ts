import { describe, expect, it } from 'vitest'
import {
  MINIMUM_TOUCH_TARGET_PX,
  isHeavyConfigurationSection,
  responsiveMode,
  statusTone,
} from './responsive'

describe('Cycle 52 responsive design contract', () => {
  it('uses one application with a deterministic mobile breakpoint and touch target', () => {
    expect(responsiveMode(901)).toBe('desktop')
    expect(responsiveMode(900)).toBe('mobile')
    expect(MINIMUM_TOUCH_TARGET_PX).toBeGreaterThanOrEqual(48)
  })

  it('guides heavy configuration to desktop while keeping usage journeys mobile', () => {
    expect(['organization', 'templates', 'flow', 'dashboard', 'print'].every(isHeavyConfigurationSection)).toBe(true)
    expect(isHeavyConfigurationSection('system-info')).toBe(false)
    expect(isHeavyConfigurationSection('audit')).toBe(false)
  })

  it('keeps core status semantics stable across pages', () => {
    expect(statusTone('DRAFT')).toBe('neutral')
    expect(statusTone('IN_PROGRESS')).toBe('processing')
    expect(statusTone('WAITING_ACCEPTANCE')).toBe('attention')
    expect(statusTone('SUCCEEDED')).toBe('positive')
    expect(statusTone('REJECTED')).toBe('critical')
    expect(statusTone('unknown_extension_status')).toBe('neutral')
  })
})
