/// <reference types="node" />
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

const productRoot = fileURLToPath(new URL('.', import.meta.url))
const fieldProperties = readFileSync(`${productRoot}/components/ModuleFieldProperties.vue`, 'utf8')
const moduleConfiguration = readFileSync(`${productRoot}/views/ModuleConfigurationView.vue`, 'utf8')
const moduleNavigation = readFileSync(`${productRoot}/module-navigation.ts`, 'utf8')

describe('low-code module lifecycle contract', () => {
  it('uses the five requirement-owned tasks as the only first-level workflow', () => {
    expect(moduleNavigation).toContain("type ModuleTask = 'INFO' | 'FIELDS' | 'ACTIONS' | 'FLOW' | 'APPLICATION'")
    expect(moduleNavigation).not.toContain("| 'PUBLISH'")
    ;['模块信息', '模块字段', '模块动作', '模块 Flow', '模块应用'].forEach(label => {
      expect(moduleConfiguration).toContain(`<span>${label}<small>`)
    })
    expect(moduleConfiguration).toContain("infoSection === 'PUBLICATION'")
    expect(moduleConfiguration).toContain('发布与版本')
  })

  it('keeps internal identifiers out of the ordinary configuration forms', () => {
    expect(moduleConfiguration).not.toContain('v-model:value="ruleForm.code"')
    expect(moduleConfiguration).not.toContain('v-model:value="indexForm.code"')
    expect(moduleConfiguration).not.toContain('{{ rule.code }}')
    expect(moduleConfiguration).not.toContain('{{ item.index.code }}')
    expect(moduleConfiguration).not.toContain('{{ item.projectionPlan }}')
    expect(moduleConfiguration).not.toContain('字段编码=来源字段编码')
    expect(moduleConfiguration).not.toContain('页面 Schema 不是有效 JSON')
  })

  it('provides business-name selectors for dependent configuration', () => {
    expect(fieldProperties).toContain('label="授权模型"')
    expect(moduleConfiguration).toContain('label="字段对应关系"')
    expect(moduleConfiguration).toContain(':options="conditionOperatorOptions"')
    expect(moduleConfiguration).toContain('{{ fieldName(condition.field) }}')
  })

  it('keeps field uniqueness on a structured switch and identifies its managed index', () => {
    expect(fieldProperties).toContain('v-model:checked="fieldForm.uniqueValue"')
    expect(fieldProperties).toContain('由数据库并发保证')
    expect(moduleConfiguration).toContain('字段配置自动维护')
    expect(moduleConfiguration).toContain("index.index.code.startsWith('field_unique_')")
  })

  it('configures text length and format through field-specific controls instead of JSON', () => {
    expect(fieldProperties).toContain('label="最短长度"')
    expect(fieldProperties).toContain('label="最长长度"')
    expect(fieldProperties).toContain('label="附加格式规则"')
    expect(fieldProperties).toContain('自定义正则表达式')
    expect(fieldProperties).toContain('builtInTextFormat(fieldForm.fieldType)')
  })
})
