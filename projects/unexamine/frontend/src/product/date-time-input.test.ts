import { describe, expect, it } from 'vitest'
import { dateTimeInputChange, dateTimeInputValue } from './date-time-input'

describe('北京时间 DATETIME 输入', () => {
  it.each([
    ['2026-09-19T02:00:00Z', '2026-09-19T10:00:00'],
    ['2026-09-19T10:00:00.123+08:00', '2026-09-19T10:00:00'],
    ['2026-09-19T10:00:00+05:30', '2026-09-19T12:30:00'],
    ['2026-09-19T20:00:00-04:00', '2026-09-20T08:00:00'],
    ['2026-09-19T00:00:00+14:00', '2026-09-18T18:00:00'],
    ['2026-09-19T10:00', '2026-09-19T10:00:00'],
    ['2026-09-19T10:00:01', '2026-09-19T10:00:01'],
  ])('将 %s 显示为北京本地秒级 %s', (source, expected) => {
    expect(dateTimeInputValue(source)).toBe(expected)
  })

  it.each(['America/New_York', 'Asia/Kolkata', 'UTC'])('不借用浏览器或进程时区 %s', zone => {
    const originalZone = process.env.TZ
    try {
      process.env.TZ = zone
      expect(dateTimeInputValue('2026-03-08T02:30')).toBe('2026-03-08T02:30:00')
      expect(dateTimeInputValue('2026-09-19T02:00:00Z')).toBe('2026-09-19T10:00:00')
    } finally {
      if (originalZone === undefined) delete process.env.TZ
      else process.env.TZ = originalZone
    }
  })

  it.each([
    '2026-09-19T02:00:00.123456789Z',
    '2026-09-19T10:00:00.987+08:00',
    '2026-09-19T04:30:00.123+02:30',
    '2026-09-19T10:00',
  ])('同秒控件事件保留原偏移、精度和格式 %s', source => {
    expect(dateTimeInputChange(source, dateTimeInputValue(source))).toBe(source)
  })

  it('真正修改输出北京本地秒级值', () => {
    expect(dateTimeInputChange('2026-09-19T02:00:00.123Z', '2026-09-19T11:00:00')).toBe('2026-09-19T11:00:00')
  })

  it.each([null, undefined, ''])('清空保持事件原空值语义 %s', empty => {
    expect(dateTimeInputChange('2026-09-19T02:00:00Z', empty)).toBe(empty)
  })

  it.each(['2026-02-30T10:00', '2026-09-19T24:00:00Z', '2026-09-19T10:00:00+24:00', '无效时间'])('不把无效值 %s 显示成其他日期', value => {
    expect(dateTimeInputValue(value)).toBeUndefined()
  })
})
