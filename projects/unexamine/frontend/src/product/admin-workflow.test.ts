/// <reference types="node" />
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

const productRoot = fileURLToPath(new URL('.', import.meta.url))
const systemSettings = readFileSync(`${productRoot}/views/SystemSettingsView.vue`, 'utf8')
const fixedConfigurations = readFileSync(`${productRoot}/views/FixedConfigurationsView.vue`, 'utf8')
const systemAdmin = readFileSync(`${productRoot}/views/SystemAdminHomeView.vue`, 'utf8')
const platformAdmin = readFileSync(`${productRoot}/components/PlatformAdminShell.vue`, 'utf8')

describe('admin task workflow contract', () => {
  it('shows one system information task at a time', () => {
    expect(systemSettings).toContain("type SettingsTask = 'basic' | 'workspaces' | 'domains' | 'access'")
    expect(systemSettings).toContain("v-if=\"activeTask === 'basic'\"")
    expect(systemSettings).toContain("v-else-if=\"activeTask === 'workspaces'\"")
    expect(systemSettings).toContain("v-else-if=\"activeTask === 'domains'\"")
    expect(systemSettings).not.toContain('申请成员 {{ migration.requestedByMemberId }}')
    expect(systemSettings).not.toContain('作业 #{{ migration.jobId }}')
  })

  it('separates configuration selection from editing', () => {
    expect(fixedConfigurations).toContain('v-else-if="!editorOpen"')
    expect(fixedConfigurations).toContain('v-else-if="!specialCategory"')
    expect(fixedConfigurations).toContain('← 返回{{ selectedName }}')
    expect(fixedConfigurations).toContain('保存并验证')
    expect(fixedConfigurations).not.toContain('<small>{{ category.code }}</small>')
  })

  it('keeps the required eleven-item admin information architecture', () => {
    const required = ['系统信息', '组织架构', '角色权限', '数据字典', '模板配置', '其他业务参数', '流程配置', '应用配置', '智能助手配置', '审计日志', '运维配置']
    required.forEach(label => expect(systemAdmin).toContain(`label: '${label}'`))
    expect((systemAdmin.match(/\{ key: '[^']+', label: '/g) ?? [])).toHaveLength(11)

    const platformRequired = ['平台信息', '组织架构', '角色权限', '数据字典', '系统配置', '其他业务参数', '流程配置', '应用配置', '智能助手配置', '审计日志', '运维配置']
    platformRequired.forEach(label => expect(platformAdmin).toContain(`label: '${label}'`))
    expect((platformAdmin.match(/\{ key: '[^']+', path: '[^']+'(?:, category: '[^']+')?, label: '/g) ?? [])).toHaveLength(11)
  })
})
