import type { OperationsDeployment, OperationsRuntimeManifest } from './types'

export function deployConfirmation(version: string, environment: string) {
  return `DEPLOY ${version} TO ${environment}`
}

export function rollbackConfirmation(deploymentId: number) {
  return `ROLLBACK ${deploymentId}`
}

export function runtimeRegistrationReady(runtime?: OperationsRuntimeManifest) {
  return Boolean(runtime?.ready && runtime.artifactDigestConfigured && /^[a-f0-9]{64}$/i.test(runtime.artifactSha256))
}

export function deploymentOutcome(item: OperationsDeployment) {
  if (item.status === 'SUCCESS') return '发布成功'
  if (item.status === 'ROLLBACK_READY') return '回滚点已就绪'
  if (item.status === 'FAILED') return `已阻断${item.failureCode ? ` · ${item.failureCode}` : ''}`
  return '执行中'
}

export function passedDeploymentSteps(item?: OperationsDeployment) {
  return item?.steps.filter(step => step.status === 'PASSED').length ?? 0
}
