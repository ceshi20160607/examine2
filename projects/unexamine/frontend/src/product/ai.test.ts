import { describe, expect, it } from 'vitest'
import { aiPreviewSummary, aiScopeLabel, aiToolTypeLabel, isWriteTool } from './ai'

describe('AI configuration presentation rules', () => {
  it('keeps scope and write confirmation semantics explicit', () => {
    expect(aiToolTypeLabel('WRITE')).toBe('写入')
    expect(aiScopeLabel('CURRENT')).toContain('当前成员')
    expect(isWriteTool({ toolType: 'QUERY', actionCode: 'DELETE' })).toBe(true)
  })

  it('reports a publication block when the preview contains issues', () => {
    expect(aiPreviewSummary({
      agentId: 1,
      draftRevision: 2,
      valid: false,
      issues: [{ code: 'AI_TOOL_SCOPE_EXCESSIVE', message: '范围过大' }],
      tools: [],
      permissionSnapshot: {},
      confirmationPolicy: {},
    })).toContain('禁止发布')
  })
})
