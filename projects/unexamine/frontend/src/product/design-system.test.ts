/// <reference types="node" />
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'
import pageHeader from './components/ProductPageHeader.vue?raw'
import detailDrawer from './components/ProductDetailDrawer.vue?raw'
import formDrawer from './components/ProductFormDrawer.vue?raw'
import listWorkspace from './components/ProductListWorkspace.vue?raw'
import ordinaryTasks from './views/OrdinaryTasksView.vue?raw'
import runtimeWorkspace from './views/RuntimeWorkspaceView.vue?raw'
import { productStatus } from './presentation'

const productRoot = fileURLToPath(new URL('.', import.meta.url))
const designCss = readFileSync(`${productRoot}/design-system.css`, 'utf8')
const legacyCss = readFileSync(`${productRoot}/product.css`, 'utf8')

describe('product design system contract', () => {
  it('keeps semantic tokens in one product source', () => {
    expect(designCss).toContain('--ux-color-primary:')
    expect(designCss).toContain('--ux-status-critical-surface:')
    expect(designCss).toContain('--ux-control-height:')
    expect(designCss).toContain('[data-density="runtime"]')
    expect(designCss).toContain('[data-density="configuration"]')
    expect(legacyCss).not.toMatch(/--ux-color-primary\s*:/)
  })

  it('provides a single structural primary-action position', () => {
    expect(pageHeader.match(/<slot name="primary"/g)).toHaveLength(1)
    expect(pageHeader).toContain('product-page-header__secondary')
  })

  it('reuses one list, detail and form structure across runtime and fixed pages', () => {
    expect(listWorkspace).toContain('ProductState v-if="loading && !hasContent"')
    expect(listWorkspace).toContain('name="batch"')
    expect(formDrawer.match(/<a-button type="primary"/g)).toHaveLength(1)
    expect(detailDrawer.match(/<slot name="primary"/g)).toHaveLength(1)
    for (const page of [runtimeWorkspace, ordinaryTasks]) {
      expect(page).toContain('<ProductListWorkspace')
      expect(page).toContain('<ProductDetailDrawer')
      expect(page).toContain('<ProductFormDrawer')
    }
  })

  it('never falls through to an internal English status code', () => {
    expect(productStatus('WAITING_CONFIRMATION').label).toBe('等待确认')
    expect(productStatus('SOME_INTERNAL_STATE').label).toBe('状态待确认')
    expect(productStatus('等待客户回复').label).toBe('等待客户回复')
  })
})
