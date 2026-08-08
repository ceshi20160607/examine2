import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { flowApi } from '@/services/flow'
import type { FlowExtensionNodeCatalogEntry } from '@/types/flow'
import FlowExtensionEditor from '@/views/system/FlowExtensionEditor.vue'

vi.mock('@/services/flow', () => ({
  flowApi: {
    extensionNodeCatalog: vi.fn(),
    extensionDraft: vi.fn(),
    extensionPublishImpact: vi.fn(),
    saveExtensionDraft: vi.fn(),
  },
}))

const types = [
  'START', 'APPROVAL', 'CONDITIONAL_APPROVAL', 'COPY', 'CONDITIONAL_BRANCH',
  'PARALLEL_GATEWAY', 'INCLUSIVE_GATEWAY', 'MERGE_GATEWAY', 'SUBFLOW', 'AUTOMATION',
  'FORM', 'TASK', 'NOTIFICATION', 'FIELD_UPDATE', 'DATA_CREATE_UPDATE', 'WAIT',
  'TIMER', 'MESSAGE', 'WEBHOOK', 'EXTERNAL', 'AI_ASSIST', 'END',
] as const

const catalog: FlowExtensionNodeCatalogEntry[] = types.map((type, index) => ({
  type,
  name: `节点 ${index + 1}`,
  family: index % 2 ? 'HUMAN' : 'CONTROL',
  executor: type.toLowerCase(),
  requiresBusinessRecord: ['APPROVAL', 'FORM', 'FIELD_UPDATE', 'AI_ASSIST'].includes(type),
  requiresHumanContinuation: ['APPROVAL', 'FORM', 'TASK', 'AI_ASSIST'].includes(type),
}))

const impact = {
  definitionId: '20', draftRevision: 3, ready: true,
  outboundDependencies: [], inboundConsumers: [], issues: [],
}

describe('FlowExtensionEditor', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(flowApi.extensionNodeCatalog).mockResolvedValue(catalog)
    vi.mocked(flowApi.extensionDraft).mockResolvedValue(null)
    vi.mocked(flowApi.extensionPublishImpact).mockResolvedValue(impact)
    vi.mocked(flowApi.saveExtensionDraft).mockImplementation(async (_systemId, definitionId, revision, graph) => ({
      definitionId, definitionVersion: null, sourceRevision: revision, graph,
      checksum: '1234567890abcdef', actorId: '9', occurredAt: '2026-08-07T00:00:00Z',
    }))
  })

  it('renders all 22 server catalog entries and saves configured graph plus impact', async () => {
    const wrapper = mount(FlowExtensionEditor, {
      props: { systemId: '10', definitionId: '20', revision: 3 },
    })
    await flushPromises()

    expect(wrapper.findAll('.flow-extension-catalog-item')).toHaveLength(22)
    const task = wrapper.findAll('.flow-extension-catalog-item')
      .find(item => item.text().includes('TASK'))!
    await task.trigger('click')
    expect((wrapper.get('.flow-extension-inspector textarea').element as HTMLTextAreaElement).value)
      .toContain('WORK_TASK')

    await wrapper.get('button.primary').trigger('click')
    await flushPromises()

    expect(flowApi.saveExtensionDraft).toHaveBeenCalledWith(
      '10', '20', 3,
      expect.objectContaining({
        nodes: expect.arrayContaining([expect.objectContaining({ type: 'TASK' })]),
      }),
    )
    expect(flowApi.extensionPublishImpact).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('可以发布')
    expect(wrapper.text()).toContain('扩展草稿已保存')
  })

  it('shows dependency blockers returned by the publication impact preview', async () => {
    vi.mocked(flowApi.extensionPublishImpact).mockResolvedValue({
      ...impact,
      ready: false,
      issues: [{
        severity: 'BLOCKER', code: 'FLOW_DEPENDENCY_INCOMPATIBLE',
        path: '/nodes/subflow/dependencies', message: '目标版本不存在',
      }],
    })
    const wrapper = mount(FlowExtensionEditor, {
      props: { systemId: '10', definitionId: '20', revision: 3 },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('存在阻断')
    expect(wrapper.text()).toContain('FLOW_DEPENDENCY_INCOMPATIBLE')
    expect(wrapper.text()).toContain('目标版本不存在')
  })
})
