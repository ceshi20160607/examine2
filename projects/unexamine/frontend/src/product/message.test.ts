import { describe, expect, it } from 'vitest'
import { deliveryStatusLabel, messageChannelLabel, messageStatusLabel, parseMessageVariables, parseTemplateVariables } from './message'

describe('Cycle 42 message center state', () => {
  it('keeps inbox and channel results explicit', () => {
    expect(messageStatusLabel('UNREAD')).toBe('未读')
    expect(messageChannelLabel('IN_APP')).toBe('站内')
    expect(deliveryStatusLabel('RETRY_PENDING')).toBe('等待重试')
  })

  it('normalizes template variables without hiding malformed payloads', () => {
    expect(parseTemplateVariables('title, actor, title')).toEqual(['title', 'actor'])
    expect(parseMessageVariables('{"title":"合同"}')).toEqual({ title: '合同' })
    expect(() => parseMessageVariables('[]')).toThrow('模板变量必须是 JSON 对象')
  })
})
