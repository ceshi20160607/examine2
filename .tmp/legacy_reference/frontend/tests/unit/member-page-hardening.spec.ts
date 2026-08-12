import { describe, expect, it } from 'vitest'

import fileAssetsSource from '@/views/system/FileAssetsView.vue?raw'
import flowDraftCanvasSource from '@/views/system/FlowDraftCanvas.vue?raw'
import flowSource from '@/views/system/FlowView.vue?raw'
import messageInboxSource from '@/views/system/MessageInboxView.vue?raw'
import operationsDashboardSource from '@/views/system/OperationsDashboardView.vue?raw'
import systemDashboardSource from '@/views/system/SystemDashboardView.vue?raw'
import systemKpisSource from '@/views/system/SystemKpisView.vue?raw'
import systemReportsSource from '@/views/system/SystemReportsView.vue?raw'
import systemWorkbenchSource from '@/views/system/SystemWorkbenchView.vue?raw'
import todoActionCenterSource from '@/views/system/TodoActionCenterView.vue?raw'
import workDailyReportsSource from '@/views/system/WorkDailyReports.vue?raw'
import workTasksSource from '@/views/system/WorkTasksView.vue?raw'

const sources: Record<string, string> = {
  'FileAssetsView.vue': fileAssetsSource,
  'FlowDraftCanvas.vue': flowDraftCanvasSource,
  'FlowView.vue': flowSource,
  'MessageInboxView.vue': messageInboxSource,
  'OperationsDashboardView.vue': operationsDashboardSource,
  'SystemDashboardView.vue': systemDashboardSource,
  'SystemKpisView.vue': systemKpisSource,
  'SystemReportsView.vue': systemReportsSource,
  'SystemWorkbenchView.vue': systemWorkbenchSource,
  'TodoActionCenterView.vue': todoActionCenterSource,
  'WorkDailyReports.vue': workDailyReportsSource,
  'WorkTasksView.vue': workTasksSource,
}

function viewSource(name: string) {
  const source = sources[name]
  if (!source) throw new Error(`Unknown member view: ${name}`)
  return source
}

describe('member page hardening contract', () => {
  it.each([
    'SystemWorkbenchView.vue',
    'FlowView.vue',
    'FlowDraftCanvas.vue',
    'WorkTasksView.vue',
    'WorkDailyReports.vue',
    'TodoActionCenterView.vue',
    'MessageInboxView.vue',
    'FileAssetsView.vue',
    'OperationsDashboardView.vue',
    'SystemDashboardView.vue',
    'SystemKpisView.vue',
    'SystemReportsView.vue',
  ])('keeps %s within a page-level narrow-screen boundary', (name) => {
    const source = viewSource(name)

    expect(source).toMatch(/@media\s*\(max-width:\s*(?:560|640|720)px\)|@media\(max-width:(?:560|640|720)px\)/)
  })

  it('keeps record workbench icon commands accessible and the 1024 toolbar reflow explicit', () => {
    const source = viewSource('SystemWorkbenchView.vue')

    expect(source).toContain('aria-label="快速新建记录"')
    expect(source).toContain('aria-label="打开全局搜索"')
    expect(source).toContain('@media(max-width:1200px)')
    expect(source).toContain('.record-toolbar-actions{grid-column:1/-1')
  })

  it('keeps file and message icon/link actions named for assistive technology', () => {
    const files = viewSource('FileAssetsView.vue')
    const messages = viewSource('MessageInboxView.vue')

    expect(files).toContain('aria-label="关闭上传进度"')
    expect(files).toContain('aria-label="关闭文件详情"')
    expect(messages).toContain(':aria-label="item.targetPath ? `打开消息：${item.title}` : undefined"')
  })

  it('preserves member-facing loading, empty, error, permission and disabled states', () => {
    expect(viewSource('SystemDashboardView.vue')).toContain('data-state="loading"')
    expect(viewSource('SystemDashboardView.vue')).toContain('data-state="empty"')
    expect(viewSource('SystemReportsView.vue')).toContain('class="runtime-report-error"')
    expect(viewSource('FlowView.vue')).toContain('当前角色没有流程列表读取权限')
    expect(viewSource('TodoActionCenterView.vue')).toContain(':disabled="Boolean(mutation)"')
    expect(viewSource('FileAssetsView.vue')).toContain(':disabled="uploadBusy || uploadUnavailable"')
  })
})
