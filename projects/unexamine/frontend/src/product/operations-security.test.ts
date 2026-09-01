import { describe, expect, it } from 'vitest'
import { activateRotationConfirmation, createSecretConfirmation, oneTimeSecret, performancePath, prepareRotationConfirmation, rotationOutcome } from './operations-security'
import type { OperationsOneTimeSecret, OperationsRotation, OperationsVerificationRun } from './types'

describe('Cycle 56 operations security contract', () => {
  it('builds exact high-risk confirmations', () => {
    expect(createSecretConfirmation(' webhook.primary ')).toBe('CREATE SECRET webhook.primary')
    expect(prepareRotationConfirmation('webhook.primary')).toBe('ROTATE webhook.primary')
    expect(activateRotationConfirmation(19)).toBe('ACTIVATE ROTATION 19')
  })

  it('exposes issued material only from the one-time response', () => {
    expect(oneTimeSecret({ displayOnce: true, oneTimeSecret: 'issued-once' } as OperationsOneTimeSecret)).toBe('issued-once')
    expect(oneTimeSecret({ displayOnce: false, oneTimeSecret: 'must-not-show' } as OperationsOneTimeSecret)).toBe('')
    expect(oneTimeSecret()).toBe('')
  })

  it('keeps failed rotations distinct from switched versions', () => {
    expect(rotationOutcome({ switched: false, activeVersion: 'v1', toVersion: 'v2', failureCode: 'CONSUMER_CANARY_FAILED' } as OperationsRotation))
      .toContain('v1 继续服务')
    expect(rotationOutcome({ switched: true, activeVersion: 'v2', oldVersionStatus: 'DISABLED' } as OperationsRotation))
      .toBe('v2 已生效，旧版已停用')
  })

  it('reads the named bounded performance path without inventing results', () => {
    const run = { result: { paths: [{ code: 'LIST_QUERY', index: 'idx_context' }, { code: 'JOB_QUEUE', queued: 2 }] } } as unknown as OperationsVerificationRun
    expect(performancePath(run, 'LIST_QUERY')?.index).toBe('idx_context')
    expect(performancePath(run, 'FILE_STREAM')).toBeUndefined()
  })
})
