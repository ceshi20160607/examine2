import { describe, expect, it } from 'vitest'
import { aiOutcomePresentation, aiScopeSummary } from './ai-context'

describe('AI contextual query presentation', () => {
  it('keeps refusal and degradation explicit', () => {
    expect(aiOutcomePresentation('REFUSED')).toMatchObject({ label: '已拒绝越权请求', alert: 'error' })
    expect(aiOutcomePresentation('DEGRADED')).toMatchObject({ label: '模型不可用，未生成结果', alert: 'warning' })
  })

  it('states the exact runtime boundary used by the answer', () => {
    const summary = aiScopeSummary({
      systemId: 10, tenantId: 12, moduleCode: 'customer', actionCode: 'LIST', lifecycleState: 'ACTIVE',
      tenantScope: 'ALL', search: '', filters: [], fieldCodes: ['customer_name'], dataScopes: {}, sourcePath: '/systems/10',
    })
    expect(summary).toContain('系统 #10')
    expect(summary).toContain('租户 #12')
    expect(summary).toContain('customer/LIST')
    expect(summary).toContain('本租户及获权共享')
    expect(summary).toContain('无组合筛选')
  })
})
