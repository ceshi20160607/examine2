import { describe, expect, it } from 'vitest'
import { deployConfirmation, deploymentOutcome, passedDeploymentSteps, rollbackConfirmation, runtimeRegistrationReady } from './operations-deployment'
import type { OperationsDeployment, OperationsRuntimeManifest } from './types'

const runtime: OperationsRuntimeManifest = {
  environmentCode: 'test', backendVersion: '0.1.0-C54', frontendVersion: '0.1.0-C54', databaseVersion: '34',
  configVersion: 'test-v54', artifactSha256: 'a'.repeat(64), frontendSmokeUrl: 'https://test.example/',
  artifactDigestConfigured: true, ready: true,
}

const deployment = {
  id: 9, releaseId: 3, releaseVersion: '0.1.0-C54', environmentCode: 'test', deploymentType: 'DEPLOY',
  status: 'FAILED', failureCode: 'FRONTEND_SMOKE_FAILED', failureMessage: '入口不可用',
  approval: { reference: 'CAB-1', approvedByAccountId: 1, approvedAt: 'now', confirmation: 'confirm' },
  steps: [
    { code: 'MANIFEST', name: '清单', version: 'C54', durationMillis: 2, status: 'PASSED', health: 'READY', realEntrySmoke: 'N/A', evidence: 'ok' },
    { code: 'FRONTEND_SMOKE', name: '入口', version: 'C54', durationMillis: 5, status: 'FAILED', health: 'BLOCKED', realEntrySmoke: 'FAILED', evidence: '503' },
  ], rollbackPoint: {}, requestedByAccountId: 1, startedAt: 'now', finishedAt: 'now', createdAt: 'now',
} satisfies OperationsDeployment

describe('operations deployment', () => {
  it('builds exact approval confirmations', () => {
    expect(deployConfirmation('0.1.0-C54', 'test')).toBe('DEPLOY 0.1.0-C54 TO test')
    expect(rollbackConfirmation(9)).toBe('ROLLBACK 9')
  })

  it('only allows a complete immutable runtime manifest', () => {
    expect(runtimeRegistrationReady(runtime)).toBe(true)
    expect(runtimeRegistrationReady({ ...runtime, artifactSha256: '' })).toBe(false)
    expect(runtimeRegistrationReady({ ...runtime, ready: false })).toBe(false)
  })

  it('explains blocked deployment and counts passed steps', () => {
    expect(deploymentOutcome(deployment)).toBe('已阻断 · FRONTEND_SMOKE_FAILED')
    expect(passedDeploymentSteps(deployment)).toBe(1)
  })
})
