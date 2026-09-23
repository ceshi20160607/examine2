/** DATETIME 输入固定使用北京时间；仅转换控件显示，不改写来源值的偏移和小数精度。 */
export function dateTimeInputValue(value: unknown): string | undefined {
  if (typeof value !== 'string' || !value) return undefined
  const match = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2})(?:\.\d{1,9})?)?(Z|[+-]\d{2}:\d{2})?$/.exec(value)
  if (!match) return undefined
  const year = Number(match[1]), month = Number(match[2]), day = Number(match[3])
  const hour = Number(match[4]), minute = Number(match[5]), second = Number(match[6] || 0)
  const date = new Date(0)
  date.setUTCFullYear(year, month - 1, day)
  date.setUTCHours(hour, minute, second, 0)
  // UTC 分量校验避免浏览器时区、夏令时和无效日期自动滚动改变业务时间。
  if (date.getUTCFullYear() !== year || date.getUTCMonth() !== month - 1 || date.getUTCDate() !== day
    || date.getUTCHours() !== hour || date.getUTCMinutes() !== minute || date.getUTCSeconds() !== second) return undefined
  const zone = match[7]
  if (zone) {
    let offset = 0
    if (zone !== 'Z') {
      const hours = Number(zone.slice(1, 3)), minutes = Number(zone.slice(4, 6))
      if (hours > 23 || minutes > 59) return undefined
      offset = (zone[0] === '-' ? -1 : 1) * (hours * 60 + minutes)
    }
    date.setTime(date.getTime() + (8 * 60 - offset) * 60 * 1000)
  }
  if (date.getUTCFullYear() < 0 || date.getUTCFullYear() > 9999) return undefined
  return date.toISOString().slice(0, 19)
}

/** 相同秒级事件保留原值；真正修改才提交北京本地秒级时间，清空事件保持原空值语义。 */
export function dateTimeInputChange(original: unknown, value: unknown): unknown {
  if (value === null || value === undefined || value === '') return value
  const input = dateTimeInputValue(value)
  if (input && input === dateTimeInputValue(original)) return original
  return input ?? value
}
