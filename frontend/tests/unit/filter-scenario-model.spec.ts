import { describe, expect, it } from 'vitest'

import type { RuntimeDefinition, RuntimeRecordSchema, SharedFilterScenario } from '@/types/config'
import {
  buildScenarioConfig,
  copyScenarioQuery,
  sharedFilterScenarioConfig,
  sharedFilterScenarioSupported,
} from '@/views/system/filterScenarioModel'

const urgent: SharedFilterScenario = {
  code: 'urgent_first',
  name: '紧急优先',
  filter: { kind: 'PREDICATE', fieldCode: 'priority', operator: 'EQ', value: 'HIGH' },
  sort: [{ fieldCode: 'created_at', direction: 'DESC', nulls: 'LAST' }],
}

function definition(page: Record<string, unknown>): RuntimeDefinition {
  return {
    activeVersionId: '1', versionNo: '1', module: {}, fields: [], pages: [page], components: [], actions: [], rules: [],
    dictionaries: [], dictionaryItems: [], recordsAvailable: true,
  }
}

function schema(): RuntimeRecordSchema {
  return {
    schemaVersionId: '1', moduleSnapshotId: '2', logicalModuleId: '2', checksum: 'checksum', runtimeState: 'READY', authzEpoch: 1,
    actions: [], queryLimits: { defaultSize: 50, maxSize: 200, maxSorts: 3 },
    fields: [
      { fieldCode: 'priority', fieldName: '优先级', logicalFieldId: '3', type: 'TEXT', mode: 'WRITABLE', readable: true, writable: true, sensitiveReadable: false, sensitiveQueryable: false, masked: false, operators: ['EQ'], sortable: false, showInList: true, showInDetail: true, options: [], schema: {} },
      { fieldCode: 'created_at', fieldName: '创建时间', logicalFieldId: '4', type: 'DATETIME', mode: 'READONLY', readable: true, writable: false, sensitiveReadable: false, sensitiveQueryable: false, masked: false, operators: [], sortable: true, showInList: true, showInDetail: true, options: [], schema: {} },
    ],
  }
}

describe('shared filter scenario model', () => {
  it('builds the exact canonical model from authored JSON', () => {
    const result = buildScenarioConfig([{
      code: ' urgent_first ',
      name: ' 紧急优先 ',
      filterJson: JSON.stringify(urgent.filter),
      sortJson: JSON.stringify(urgent.sort),
    }], 'urgent_first')

    expect(result).toEqual({ scenarios: [urgent], defaultCode: 'urgent_first' })
  })

  it('rejects malformed, duplicate, empty and over-limit authored scenarios', () => {
    const draft = { code: 'same', name: '同名', filterJson: 'null', sortJson: JSON.stringify(urgent.sort) }
    expect(() => buildScenarioConfig([{ ...draft, filterJson: '{bad' }], null)).toThrow('不是有效的 JSON')
    expect(() => buildScenarioConfig([{ ...draft, sortJson: '[]' }], null)).toThrow('必须至少包含筛选或排序')
    expect(() => buildScenarioConfig([draft, draft], null)).toThrow('编码不能重复')
    expect(() => buildScenarioConfig(Array.from({ length: 11 }, (_, index) => ({ ...draft, code: `item_${index}` })), null)).toThrow('最多 10 项')
    expect(() => buildScenarioConfig([draft], 'missing')).toThrow('必须引用当前页面中的方案')
    expect(() => buildScenarioConfig([{ ...draft, filterJson: '{"kind":"PREDICATE","fieldCode":"bad field","operator":"eq"}' }], null)).toThrow('canonical filter')
    expect(() => buildScenarioConfig([{ ...draft, filterJson: JSON.stringify({ kind: 'AND', children: Array.from({ length: 21 }, () => urgent.filter) }) }], null)).toThrow('canonical filter')
  })

  it('reads only the published enabled default LIST layout and honors schema sanitization', () => {
    const config = sharedFilterScenarioConfig(definition({
      page_type: 'LIST', desired_status: 'ENABLED', is_default: true,
      layout_json: { filterScenarios: [urgent], defaultFilterScenarioCode: 'urgent_first' },
    }))
    expect(config).toEqual({ scenarios: [urgent], defaultCode: 'urgent_first' })
    expect(sharedFilterScenarioSupported(urgent, schema())).toBe(true)
    expect(sharedFilterScenarioSupported({ ...urgent, filter: { kind: 'PREDICATE', fieldCode: 'secret', operator: 'EQ' } }, schema())).toBe(false)

    const copied = copyScenarioQuery(urgent)
    ;(copied.filter as { value: string }).value = 'LOW'
    copied.sort[0]!.direction = 'ASC'
    expect((urgent.filter as { value: string }).value).toBe('HIGH')
    expect(urgent.sort[0]!.direction).toBe('DESC')

    expect(sharedFilterScenarioConfig(definition({
      page_type: 'FORM', desired_status: 'ENABLED', is_default: true,
      layout_json: { filterScenarios: [urgent], defaultFilterScenarioCode: 'urgent_first' },
    })).scenarios).toEqual([])
  })
})
