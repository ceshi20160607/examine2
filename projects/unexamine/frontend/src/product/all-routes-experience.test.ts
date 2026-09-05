/// <reference types="node" />
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

const productRoot = fileURLToPath(new URL('.', import.meta.url))
const read = (path: string) => readFileSync(`${productRoot}/${path}`, 'utf8')
const template = (path: string) => read(path).split('<template>')[1]?.split('</template>')[0] || ''

const coreConfigurationRoutes = [
  'views/AiConfigurationView.vue',
  'views/DashboardConfigurationView.vue',
  'views/DictionaryConfigurationView.vue',
  'views/FlowDesignerView.vue',
  'views/PrintTemplateConfigurationView.vue',
  'views/TenantExtensionsView.vue',
  'views/WorkConfigurationView.vue',
]

describe('all-route product experience contract', () => {
  it('keeps internal revisions, hashes, raw status and JSON out of core configuration templates', () => {
    const forbidden = ['草稿 r', '发布版 v', 'JSON.stringify(record.raw)', 'snapshotHash.slice',
      'previewHash', '{{ item.status }}', '{{ field.code }} · {{ field.fieldType }}']
    for (const path of coreConfigurationRoutes) {
      const source = template(path)
      forbidden.forEach(value => expect(source, `${path} exposes ${value}`).not.toContain(value))
    }
  })

  it('uses one business vocabulary for workspaces, field types, actions and versions', () => {
    const presentation = read('presentation.ts')
    expect(presentation).toContain("MULTI' ? '多工作空间' : '单工作空间'")
    expect(presentation).toContain("TEXT: '单行文本'")
    expect(presentation).toContain("LIST: '查看列表'")
    expect(presentation).toContain('`第 ${version} 版`')
    expect(template('views/TenantExtensionsView.vue')).not.toContain('主租户')
    expect(template('views/FlowBindingsView.vue')).not.toContain('当前租户')
  })

  it('keeps loading, empty and error feedback in every high-density configuration route', () => {
    for (const path of coreConfigurationRoutes) {
      const source = template(path)
      expect(source, `${path} lacks loading feedback`).toMatch(/loading|spinning|is-loading/)
      expect(source, `${path} lacks empty feedback`).toContain('a-empty')
      expect(source, `${path} lacks error feedback`).toMatch(/type="error"|v-if="error"/)
    }
  })

  it('keeps the shared responsive shell safe at tablet and phone widths', () => {
    const css = read('product.css')
    expect(css).toContain('@media (max-width: 900px)')
    expect(css).toContain('@media (max-width: 560px)')
    expect(css).toContain('.ant-drawer-content-wrapper { max-width: 100vw; }')
    expect(css).toMatch(/\.side-nav, \.system-nav[^}]+overflow-x: auto/)
  })
})
