/// <reference types="node" />
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'
import pageHeader from './components/ProductPageHeader.vue?raw'
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

  it('never falls through to an internal English status code', () => {
    expect(productStatus('WAITING_CONFIRMATION').label).toBe('等待确认')
    expect(productStatus('SOME_INTERNAL_STATE').label).toBe('状态待确认')
    expect(productStatus('等待客户回复').label).toBe('等待客户回复')
  })
})
