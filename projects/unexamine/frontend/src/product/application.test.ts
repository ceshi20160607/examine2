import { describe, expect, it } from 'vitest'
import { applicationRequestHash, applicationStatusLabel, parseApplicationFields, parseApplicationObject, signApplicationRequest } from './application'

describe('Cycle 40 application configuration state', () => {
  it('keeps application status and access configuration explicit', () => {
    expect(applicationStatusLabel('ACTIVE')).toBe('已启用')
    expect(parseApplicationObject('{"type":"SELF"}', '数据范围')).toEqual({ type: 'SELF' })
    expect(() => parseApplicationObject('[]', '数据范围')).toThrow('数据范围必须是 JSON 对象')
    expect(parseApplicationFields('name, email, name')).toEqual([
      { fieldCode: 'name', readable: true, writable: false, maskStrategy: 'NONE' },
      { fieldCode: 'email', readable: true, writable: false, maskStrategy: 'NONE' },
    ])
  })

  it('builds a stable SHA-256 body hash and HMAC signature contract', async () => {
    const input = {
      clientId: 'app_client', clientSecret: 'test-secret', timestamp: '1700000000000',
      nonce: 'nonce-1', idempotencyKey: 'idem-1', rawBody: '{"resourceType":"FLOW"}',
    }
    expect(await applicationRequestHash(input.rawBody)).toHaveLength(64)
    expect(await signApplicationRequest(input)).toBe(await signApplicationRequest(input))
    expect(await signApplicationRequest({ ...input, nonce: 'nonce-2' })).not.toBe(await signApplicationRequest(input))
  })
})
