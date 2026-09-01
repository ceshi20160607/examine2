import type { AiAgentPreview, AiTool } from './types'

export function aiToolTypeLabel(type: string) {
  return ({ QUERY: '查询', WRITE: '写入', FLOW_DRAFT: 'Flow 草稿', REPORT: '报表', ERROR_EXPLAIN: '异常解释' } as Record<string, string>)[type] || type
}

export function aiScopeLabel(scope: string) {
  return scope === 'ALL' ? '全部数据（发布时必须拥有 ALL）' : '当前成员数据范围'
}

export function aiPreviewSummary(preview?: AiAgentPreview) {
  if (!preview) return '尚未执行发布预览'
  if (!preview.valid) return `${preview.issues.length} 项越权或配置问题，禁止发布`
  return `${preview.finalModel?.name || '模型'} · ${preview.tools.length} 个工具 · 权限快照已收敛`
}

export function isWriteTool(tool: Pick<AiTool, 'toolType' | 'actionCode'>) {
  return tool.toolType === 'WRITE' || ['CREATE', 'UPDATE', 'DELETE', 'CONVERT', 'IMPORT', 'EXECUTE'].includes(tool.actionCode)
}
