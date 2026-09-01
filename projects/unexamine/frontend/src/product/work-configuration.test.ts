import { describe, expect, it } from 'vitest'
import { parseFieldSettings, sourceLabel } from './work-configuration'

describe('work configuration presentation', () => {
  it('makes layered sources explicit', () => {
    expect(sourceLabel('PLATFORM_DEFAULT')).toBe('平台默认')
    expect(sourceLabel('SYSTEM_OVERRIDE')).toBe('系统覆盖')
  })
  it('rejects non-object field settings', () => {
    expect(parseFieldSettings('{"cardVisible":true}')).toEqual({ cardVisible: true })
    expect(() => parseFieldSettings('[]')).toThrow('JSON 对象')
  })
})
