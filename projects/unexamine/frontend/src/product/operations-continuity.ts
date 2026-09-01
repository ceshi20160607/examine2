import type { OperationsBackup, OperationsUpgrade } from './types'

const requiredTypes = ['DATABASE', 'FILE_STORAGE', 'CONFIGURATION', 'SECRET_REFERENCES']

export function backupConfirmation(releaseId: number) {
  return `BACKUP ${releaseId}`
}

export function restoreDrillConfirmation(backupId: number, environment: string) {
  return `DRILL ${backupId} IN ${environment}`
}

export function upgradeConfirmation(releaseId: number, backupId: number) {
  return `UPGRADE ${releaseId} WITH BACKUP ${backupId}`
}

export function verifiedBackup(item?: OperationsBackup) {
  if (!item || item.status !== 'VERIFIED' || item.items.length !== 4) return false
  return requiredTypes.every(type => item.items.some(artifact => artifact.itemType === type
    && artifact.status === 'VERIFIED' && /^[a-f0-9]{64}$/i.test(artifact.sha256)))
}

export function upgradeOutcome(item: OperationsUpgrade) {
  if (item.status === 'SUCCESS') return '升级完成'
  if (item.status === 'RECOVERABLE_FAILED') return '失败但可恢复'
  return '执行中'
}
