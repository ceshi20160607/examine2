import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { flowApi } from '@/services/flow'
import FlowNodeRuntimePanel from '@/views/system/FlowNodeRuntimePanel.vue'

vi.mock('@/services/flow', () => ({
  flowApi: {
    nodeForm: vi.fn(),
    nodeFormHistory: vi.fn(),
    extensionNodeHistory: vi.fn(),
    writeNodeForm: vi.fn(),
    executeExtensionNode: vi.fn(),
    resumeExtensionNode: vi.fn(),
  },
}))

const initialForm = {
  instanceId: '30', nodeCode: 'form_review', definitionVersion: 2,
  moduleCode: 'purchase_order', recordId: '40', recordVersion: 5, snapshotVersion: 1,
  fields: [
    { fieldCode: 'title', mode: 'REQUIRED' as const, editable: true, required: true, value: 'Draft' },
    { fieldCode: 'owner', mode: 'VISIBLE' as const, editable: false, required: false, value: 'Alice' },
  ],
}

describe('FlowNodeRuntimePanel', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(flowApi.nodeForm).mockResolvedValue(initialForm)
    vi.mocked(flowApi.nodeFormHistory).mockResolvedValue([])
    vi.mocked(flowApi.extensionNodeHistory).mockResolvedValue([])
  })

  it('loads field policies, writes editable values with optimistic versions, and refreshes history', async () => {
    vi.mocked(flowApi.writeNodeForm).mockResolvedValue({
      form: { ...initialForm, recordVersion: 6, snapshotVersion: 2 },
      history: {
        sequence: 1, actorId: '8', recordVersionBefore: 5, recordVersionAfter: 6,
        changes: { title: 'Approved' }, before: { title: 'Draft' }, after: { title: 'Approved' },
        occurredAt: '2026-08-07T00:00:00Z',
      },
    })
    vi.mocked(flowApi.nodeFormHistory).mockResolvedValueOnce([]).mockResolvedValueOnce([{
      sequence: 1, actorId: '8', recordVersionBefore: 5, recordVersionAfter: 6,
      changes: { title: 'Approved' }, before: { title: 'Draft' }, after: { title: 'Approved' },
      occurredAt: '2026-08-07T00:00:00Z',
    }])
    const wrapper = mount(FlowNodeRuntimePanel, {
      props: { systemId: '10', instanceId: '30', canDecide: true },
    })
    await wrapper.get('[aria-label="节点代码"]').setValue('form_review')
    await wrapper.get('.flow-node-selector button').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('title REQUIRED')
    expect(wrapper.text()).toContain('owner VISIBLE')
    expect(wrapper.findAll('.flow-node-form textarea')).toHaveLength(1)
    await wrapper.get('.flow-node-form textarea').setValue('Approved')
    await wrapper.get('.flow-node-form-save').trigger('click')
    await flushPromises()

    expect(flowApi.writeNodeForm).toHaveBeenCalledWith(
      '10', '30', 'form_review',
      { expectedSnapshotVersion: 1, expectedRecordVersion: 5, values: { title: 'Approved' } },
      expect.stringMatching(/^flow-form-/),
    )
    expect(wrapper.text()).toContain('记录 v5 → v6')
    expect(wrapper.text()).toContain('表单已写回业务记录 v6')
  })

  it('executes then resumes a waiting node using explicit independent idempotency keys', async () => {
    vi.mocked(flowApi.nodeForm).mockRejectedValue(new Error('not a form'))
    vi.mocked(flowApi.executeExtensionNode).mockResolvedValue({
      instanceId: '30', nodeCode: 'wait_event', nodeType: 'WAIT', status: 'WAITING_EVENT',
      result: { eventKey: 'ready' }, version: 1, actorId: '8', updatedAt: '2026-08-07T00:00:00Z',
    })
    vi.mocked(flowApi.resumeExtensionNode).mockResolvedValue({
      instanceId: '30', nodeCode: 'wait_event', nodeType: 'WAIT', status: 'CONTINUED',
      result: { resumed: true }, version: 2, actorId: '8', updatedAt: '2026-08-07T00:01:00Z',
    })
    vi.mocked(flowApi.extensionNodeHistory)
      .mockResolvedValueOnce([])
      .mockResolvedValueOnce([{
        sequence: 1, fromStatus: null, toStatus: 'WAITING_EVENT', input: {}, result: {},
        actorId: '8', occurredAt: '2026-08-07T00:00:00Z',
      }])
      .mockResolvedValueOnce([{
        sequence: 1, fromStatus: null, toStatus: 'WAITING_EVENT', input: {}, result: {},
        actorId: '8', occurredAt: '2026-08-07T00:00:00Z',
      }, {
        sequence: 2, fromStatus: 'WAITING_EVENT', toStatus: 'CONTINUED', input: {}, result: {},
        actorId: '8', occurredAt: '2026-08-07T00:01:00Z',
      }])
    const wrapper = mount(FlowNodeRuntimePanel, {
      props: { systemId: '10', instanceId: '30', canDecide: true },
    })
    await wrapper.get('[aria-label="节点代码"]').setValue('wait_event')
    await wrapper.get('.flow-node-selector button').trigger('click')
    await flushPromises()
    await wrapper.get('.flow-node-execute').trigger('click')
    await flushPromises()
    await wrapper.get('.flow-node-resume').trigger('click')
    await flushPromises()

    expect(flowApi.executeExtensionNode).toHaveBeenCalledWith(
      '10', '30', 'wait_event', null, {}, expect.stringMatching(/^flow-execute-/),
    )
    expect(flowApi.resumeExtensionNode).toHaveBeenCalledWith(
      '10', '30', 'wait_event', 1, {}, expect.stringMatching(/^flow-resume-/),
    )
    const executeKey = vi.mocked(flowApi.executeExtensionNode).mock.calls[0]![5]
    const resumeKey = vi.mocked(flowApi.resumeExtensionNode).mock.calls[0]![5]
    expect(executeKey).not.toBe(resumeKey)
    expect(wrapper.text()).toContain('WAITING_EVENT → CONTINUED')
  })
})
