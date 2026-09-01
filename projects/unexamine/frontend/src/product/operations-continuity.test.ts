import { describe, expect, it } from 'vitest'
import { backupConfirmation, restoreDrillConfirmation, upgradeConfirmation, upgradeOutcome, verifiedBackup } from './operations-continuity'
import type { OperationsBackup, OperationsUpgrade } from './types'

describe('operations continuity helpers', () => {
  it('builds exact approval confirmations', () => {
    expect(backupConfirmation(8)).toBe('BACKUP 8')
    expect(restoreDrillConfirmation(4, 'restore-c55')).toBe('DRILL 4 IN restore-c55')
    expect(upgradeConfirmation(8, 4)).toBe('UPGRADE 8 WITH BACKUP 4')
  })

  it('requires four independently verified backup artifacts', () => {
    const backup = { status: 'VERIFIED', items: ['DATABASE', 'FILE_STORAGE', 'CONFIGURATION', 'SECRET_REFERENCES']
      .map((itemType, id) => ({ itemType, id, storageUri: `backup://${id}`, sizeBytes: 1, sha256: 'a'.repeat(64), status: 'VERIFIED' })) } as OperationsBackup
    expect(verifiedBackup(backup)).toBe(true)
    backup.items[0]!.sha256 = 'bad'
    expect(verifiedBackup(backup)).toBe(false)
  })

  it('keeps recoverable failure distinct from success', () => {
    expect(upgradeOutcome({ status: 'SUCCESS' } as OperationsUpgrade)).toBe('升级完成')
    expect(upgradeOutcome({ status: 'RECOVERABLE_FAILED' } as OperationsUpgrade)).toBe('失败但可恢复')
  })
})
