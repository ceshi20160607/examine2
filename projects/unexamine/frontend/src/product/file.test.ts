import { describe, expect, it } from 'vitest'
import { exportScopeLabel, exportStatusLabel, fileScanLabel, importRowLabel, parseCsvHeaders } from './file'

describe('controlled file and import helpers', () => {
  it('parses quoted csv headers without losing commas', () => {
    expect(parseCsvHeaders('title,"customer,name",amount\nA,B,1')).toEqual(['title', 'customer,name', 'amount'])
  })

  it('renders security scan status in product language', () => {
    expect(fileScanLabel('CLEAN')).toBe('扫描通过')
    expect(fileScanLabel('BLOCKED')).toBe('已隔离')
  })

  it('distinguishes preview, execution and rollback rows', () => {
    expect(importRowLabel('READY')).toBe('可执行')
    expect(importRowLabel('FAILED')).toBe('执行失败')
    expect(importRowLabel('ROLLBACK_CONFLICT')).toBe('回滚冲突')
  })

  it('describes asynchronous export scope and terminal state', () => {
    expect(exportScopeLabel('SELECTED')).toBe('当前勾选记录')
    expect(exportScopeLabel('CURRENT_FILTER')).toBe('当前筛选结果')
    expect(exportStatusLabel('COMPLETED')).toBe('可下载')
    expect(exportStatusLabel('FAILED')).toBe('导出失败')
  })
})
