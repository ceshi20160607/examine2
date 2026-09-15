export type JsonValueType = 'OBJECT' | 'ARRAY' | 'STRING' | 'NUMBER' | 'BOOLEAN' | 'NULL'
export interface JsonProperty { key: string; label?: string; required?: boolean; schema: JsonSchema }
export interface JsonSchema {
  type: JsonValueType
  properties?: JsonProperty[]
  allowExtraProperties?: boolean
  items?: JsonSchema
  maxItems?: number
  minLength?: number
  maxLength?: number
  minimum?: number
  maximum?: number
}
export const jsonTypeOptions = [
  { value: 'OBJECT', label: '属性组' }, { value: 'ARRAY', label: '多行列表' },
  { value: 'STRING', label: '文本' }, { value: 'NUMBER', label: '数字' },
  { value: 'BOOLEAN', label: '是否' }, { value: 'NULL', label: '空值' },
]
export function newJsonSchema(type: JsonValueType = 'OBJECT'): JsonSchema {
  if (type === 'OBJECT') return { type, properties: [], allowExtraProperties: true }
  if (type === 'ARRAY') return { type, items: { type: 'STRING' } }
  return { type }
}
export function emptyJsonValue(schema: JsonSchema): unknown {
  if (schema.type === 'OBJECT') return {}
  if (schema.type === 'ARRAY') return []
  if (schema.type === 'BOOLEAN') return false
  if (schema.type === 'NULL') return null
  if (schema.type === 'NUMBER') return undefined
  return ''
}
export function inferJsonSchema(value: unknown): JsonSchema {
  if (value === null) return { type: 'NULL' }
  if (Array.isArray(value)) return { type: 'ARRAY' }
  if (typeof value === 'object') return newJsonSchema()
  return { type: typeof value === 'number' ? 'NUMBER' : typeof value === 'boolean' ? 'BOOLEAN' : 'STRING' }
}
export function jsonSchemaIssue(schema: JsonSchema, depth = 0): string {
  if (depth > 8) return '结构化内容最多支持 8 层'
  if (!jsonTypeOptions.some(option => option.value === schema.type)) return '请选择数据类型'
  if (schema.type === 'OBJECT') {
    const keys = new Set<string>()
    for (const property of schema.properties || []) {
      const key = property.key.trim()
      if (!key || key.length > 64 || ['__proto__', 'constructor', 'prototype'].includes(key) || keys.has(key)) return '属性名称必须填写且不能重复或使用保留名称'
      keys.add(key)
      const issue = jsonSchemaIssue(property.schema, depth + 1)
      if (issue) return `${property.label || key}：${issue}`
    }
  }
  if (schema.type === 'ARRAY') {
    if (schema.maxItems !== undefined && (!Number.isInteger(schema.maxItems) || schema.maxItems < 1 || schema.maxItems > 1000)) return '最大行数必须在 1 到 1000 之间'
    return jsonSchemaIssue(schema.items || { type: 'STRING' }, depth + 1)
  }
  if (schema.type === 'STRING' && schema.minLength !== undefined && schema.maxLength !== undefined && schema.minLength > schema.maxLength) return '最短长度不能大于最长长度'
  if (schema.type === 'NUMBER' && schema.minimum !== undefined && schema.maximum !== undefined && schema.minimum > schema.maximum) return '最小值不能大于最大值'
  return ''
}

const richTags = new Set(['P', 'DIV', 'BR', 'B', 'STRONG', 'I', 'EM', 'U', 'S', 'UL', 'OL', 'LI', 'BLOCKQUOTE', 'H1', 'H2', 'H3', 'H4', 'H5', 'H6', 'A'])
const blockedTags = new Set(['SCRIPT', 'STYLE', 'IFRAME', 'OBJECT', 'APPLET', 'SVG', 'MATH'])
export function sanitizeRichMarkup(markup: string): string {
  const parsed = new DOMParser().parseFromString(markup, 'text/html')
  const output = document.createElement('div')
  const append = (node: Node, target: Node) => {
    if (node.nodeType === Node.TEXT_NODE) { target.appendChild(document.createTextNode(node.textContent || '')); return }
    if (!(node instanceof Element) || blockedTags.has(node.tagName)) return
    let destination = target
    if (richTags.has(node.tagName)) {
      const element = document.createElement(node.tagName.toLowerCase())
      if (node.tagName === 'A') {
        try {
          const url = new URL(node.getAttribute('href') || '')
          if (['http:', 'https:'].includes(url.protocol)) { element.setAttribute('href', url.href); element.setAttribute('rel', 'noopener noreferrer') }
        } catch { /* Invalid links render as ordinary text. */ }
      }
      target.appendChild(element)
      destination = element
    }
    node.childNodes.forEach(child => append(child, destination))
  }
  parsed.body.childNodes.forEach(node => append(node, output))
  return output.innerHTML
}
export function richPlainText(markup: string): string {
  return new DOMParser().parseFromString(sanitizeRichMarkup(markup), 'text/html').body.textContent?.trim() || ''
}
export function structuredSummary(value: unknown, depth = 0): string {
  if (value === undefined || value === null) return '—'
  if (typeof value === 'boolean') return value ? '是' : '否'
  if (typeof value !== 'object') return String(value)
  if (depth >= 3) return Array.isArray(value) ? `${value.length} 项` : `${Object.keys(value).length} 个属性`
  const parts = Array.isArray(value) ? value.map(item => structuredSummary(item, depth + 1))
    : Object.entries(value).map(([key, item]) => `${key}：${structuredSummary(item, depth + 1)}`)
  return parts.slice(0, 4).join('；') + (parts.length > 4 ? '…' : '') || '未填写内容'
}
