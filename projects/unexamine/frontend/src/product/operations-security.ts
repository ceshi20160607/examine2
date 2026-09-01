import type { OperationsOneTimeSecret, OperationsRotation, OperationsVerificationRun } from './types'

export function createSecretConfirmation(secretCode: string) {
  return `CREATE SECRET ${secretCode.trim()}`
}

export function prepareRotationConfirmation(secretCode: string) {
  return `ROTATE ${secretCode.trim()}`
}

export function activateRotationConfirmation(rotationId?: number) {
  return rotationId ? `ACTIVATE ROTATION ${rotationId}` : ''
}

export function oneTimeSecret(issue?: OperationsOneTimeSecret) {
  return issue?.displayOnce && issue.oneTimeSecret ? issue.oneTimeSecret : ''
}

export function rotationOutcome(rotation: OperationsRotation) {
  if (rotation.switched) return `${rotation.activeVersion} 已生效，旧版${rotation.oldVersionStatus === 'DISABLED' ? '已停用' : rotation.oldVersionStatus}`
  if (rotation.failureCode) return `${rotation.activeVersion} 继续服务：${rotation.failureCode}`
  return `${rotation.toVersion} 等待灰度验证`
}

export function performancePath(run: OperationsVerificationRun | undefined, code: string) {
  const paths = Array.isArray(run?.result?.paths) ? run.result.paths : []
  return paths.find(item => item && typeof item === 'object' && (item as Record<string, unknown>).code === code) as Record<string, unknown> | undefined
}
