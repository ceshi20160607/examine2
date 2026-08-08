import { describe, expect, it } from 'vitest'

import platformAgentSource from '@/views/platform/PlatformAgentView.vue?raw'
import platformWorkbenchSource from '@/views/platform/PlatformWorkbenchView.vue?raw'
import platformAiSource from '@/views/platform/admin/PlatformAiAgentConfigView.vue?raw'
import aiSource from '@/views/system/admin/AiAgentConfigView.vue?raw'
import dashboardsSource from '@/views/system/admin/DashboardsView.vue?raw'
import dataSourcesSource from '@/views/system/admin/DataSourcesView.vue?raw'
import kpisSource from '@/views/system/admin/KpisView.vue?raw'
import openApiSource from '@/views/system/admin/OpenApiApplicationsView.vue?raw'
import reportsSource from '@/views/system/admin/ReportsView.vue?raw'

describe('UI112 responsive page contracts', () => {
  it.each([
    ['system Agent', aiSource, 'ai-mobile-gate'],
    ['platform Agent settings', platformAiSource, 'platform-ai-mobile-gate'],
    ['dashboard', dashboardsSource, 'dashboard-mobile-gate'],
    ['data source', dataSourcesSource, 'data-source-mobile-gate'],
    ['KPI', kpisSource, 'kpi-mobile-gate'],
    ['OpenAPI', openApiSource, 'openapi-mobile-gate'],
    ['report', reportsSource, 'report-mobile-gate'],
  ])('uses a real narrow-screen editor gate for %s', (_name, source, gateClass) => {
    expect(source).toContain('useAdminViewport()')
    expect(source).toContain(`class="${gateClass}"`)
    expect(source).toContain('v-if="isMobile"')
    expect(source).toContain('宽度大于 720px')
    expect(source).toContain('v-if="!isMobile"')
  })

  it('keeps the platform workbench and Agent usable without page-level mobile grids', () => {
    expect(platformWorkbenchSource).toContain('@media(max-width:720px)')
    expect(platformWorkbenchSource).toContain('aria-labelledby="platform-task-title"')
    expect(platformAgentSource).toContain('@media(max-width:720px)')
    expect(platformAgentSource).toContain('aria-label="发送给平台 Agent 的消息"')
    expect(platformAgentSource).toContain(':disabled="submitting || !messageText.trim()"')
  })

  it('contains explicit 1024-friendly local overflow boundaries for dense editors', () => {
    expect(dataSourcesSource).toContain('@media(max-width:1199px)')
    expect(dataSourcesSource).toContain('.editor-card{overflow-x:auto}')
    expect(dashboardsSource).toContain('.dashboard-version-history{overflow-x:auto}')
    expect(kpisSource).toContain('.version-section{overflow-x:auto}')
    expect(reportsSource).toContain('.pin-detail{min-width:0')
  })
})
