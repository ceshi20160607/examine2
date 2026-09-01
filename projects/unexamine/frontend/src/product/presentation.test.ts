import { describe, expect, it } from 'vitest'
import { dashboardComponentLabel, isVerificationArtifactName, productDateTime, productStatus, tenantModeLabel, userFacingDateTime, userFacingWorkspaceName } from './presentation'

describe('Cycle 58 product presentation contract', () => {
  it('presents internal statuses as stable user language', () => {
    expect(productStatus('ACTIVE')).toMatchObject({ label: '启用', tone: 'positive' })
    expect(productStatus('READY')).toMatchObject({ label: '就绪', tone: 'positive' })
    expect(productStatus('WAITING_ACCEPTANCE')).toMatchObject({ label: '等待验收', tone: 'attention' })
    expect(productStatus('unknown_internal_code').label).toBe('状态待确认')
    expect(productStatus('客户确认中').label).toBe('客户确认中')
  })

  it('presents dashboard component types without implementation codes', () => {
    expect(dashboardComponentLabel('METRIC')).toBe('关键指标')
    expect(dashboardComponentLabel('QUICK_ENTRY')).toBe('快捷入口')
    expect(dashboardComponentLabel('unknown_widget')).toBe('信息卡片')
  })

  it('presents system modes as product language', () => {
    expect(tenantModeLabel('SINGLE')).toBe('单组织')
    expect(tenantModeLabel('MULTI')).toBe('多组织')
  })

  it('keeps verification fixtures and tenant implementation terms out of the default workspace', () => {
    expect(isVerificationArtifactName('C45 AI Agent 验收系统')).toBe(true)
    expect(isVerificationArtifactName('周期十六迁移验收系统')).toBe(true)
    expect(isVerificationArtifactName('客户运营中心')).toBe(false)
    expect(userFacingWorkspaceName('默认主租户')).toBe('主工作空间')
    expect(userFacingWorkspaceName('华东团队')).toBe('华东团队')
  })

  it('does not render invalid timestamps as invalid date', () => {
    expect(userFacingDateTime()).toBe('更新时间未知')
    expect(userFacingDateTime('not-a-date')).toBe('更新时间未知')
    expect(userFacingDateTime('2026-08-31T10:30:00+08:00')).toContain('更新于')
    expect(productDateTime('not-a-date')).toBe('—')
  })
})
