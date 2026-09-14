import { describe, expect, it } from 'vitest'
import platformShell from './components/PlatformShell.vue?raw'
import systemShell from './components/SystemShell.vue?raw'
import platformAdminShell from './components/PlatformAdminShell.vue?raw'
import systemAdminShell from './components/SystemAdminShell.vue?raw'
import workHub from './views/WorkHubView.vue?raw'
import productPage from './components/ProductPage.vue?raw'

describe('workspace shell navigation contract', () => {
  it('keeps platform aggregation in the platform shell', () => {
    expect(platformShell).toContain('scope="platform"')
    expect(platformShell).toContain("router.push('/platform/todos')")
    expect(platformShell).toContain("router.push('/platform/messages')")
    expect(platformShell).not.toContain('系统待办')
  })

  it('separates current-system collaboration from global aggregation', () => {
    expect(systemShell).toContain('scope="system"')
    expect(systemShell).toContain('系统待办')
    expect(systemShell).toContain('系统消息')
    expect(systemShell).toContain('全部待办')
    expect(systemShell).toContain('全部消息')
    expect(systemShell).toContain('aria-label="切换系统"')
    expect(systemShell).not.toContain('业务指标')
  })

  it('gives each admin workspace one explicit return path', () => {
    expect(platformAdminShell).toContain('scope="platform-admin"')
    expect(platformAdminShell.match(/返回工作区/g)).toHaveLength(1)
    expect(platformAdminShell).not.toContain('系统目录')
    expect(systemAdminShell).toContain('scope="system-admin"')
    expect(systemAdminShell.match(/返回系统/g)).toHaveLength(1)
    expect(systemAdminShell).not.toContain('平台首页')
  })

  it('places platform tasks in the platform shell and keeps configuration in administration', () => {
    expect(workHub).toContain("props.context === 'platform' ? PlatformShell : 'div'")
    expect(workHub).not.toContain("value: 'configuration'")
    expect(platformAdminShell).toContain("path: '/platform/admin/work'")
  })

  it('uses one document main landmark per shell', () => {
    expect(productPage).toContain('<section class="product-page"')
    expect(productPage).not.toContain('<main class="product-page"')
  })
})
