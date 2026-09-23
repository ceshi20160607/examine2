import { describe, expect, it } from 'vitest'
import { localFlowIssues, sequentialEdges } from './flow-designer'
import type { FlowNodeInput } from './types'

const node = (nodeKey: string, nodeType: FlowNodeInput['nodeType'], assigneePolicy?: Record<string, unknown>): FlowNodeInput => ({
  nodeKey, nodeType, name: nodeKey, positionX: 0, positionY: 0, assigneePolicy, config: {},
})

describe('flow designer validation', () => {
  it('marks missing approvers and unreachable nodes before a server round trip', () => {
    const nodes = [node('start', 'START'), node('approval', 'APPROVAL'), node('end', 'END'), node('orphan', 'NOTIFICATION')]
    const edges = sequentialEdges(nodes.slice(0, 3))
    expect(localFlowIssues(nodes, edges).map(issue => issue.code)).toEqual(expect.arrayContaining([
      'APPROVER_MISSING', 'UNREACHABLE_NODE', 'DEAD_END_NODE',
    ]))
  })

  it('builds a connected publishable sequence once approval properties exist', () => {
    const nodes = [node('start', 'START'), node('approval', 'APPROVAL', { type: 'PERSON', tenantMemberIds: [1] }), node('end', 'END')]
    expect(localFlowIssues(nodes, sequentialEdges(nodes))).toEqual([])
  })

  it('requires real recipients and content for notification nodes', () => {
    const nodes = [node('start', 'START'), node('notify', 'NOTIFICATION'), node('end', 'END')]
    expect(localFlowIssues(nodes, sequentialEdges(nodes)).map(issue => issue.code))
      .toEqual(expect.arrayContaining(['FLOW_MESSAGE_RECIPIENT_MISSING', 'SPECIAL_PROPERTY_MISSING']))
    nodes[1]!.assigneePolicy = { type: 'PERSON', tenantMemberIds: [7] }
    nodes[1]!.config = { schemaVersion: 1, subject: '流程通知', content: '流程已更新',
      appendInstanceTitle: true, destinationType: 'FLOW_INSTANCE' }
    expect(localFlowIssues(nodes, sequentialEdges(nodes))).toEqual([])
  })

  it('blocks decorative writeback nodes that have no action or field mapping', () => {
    const nodes = [node('start', 'START'), node('writeback', 'UPDATE_FIELD'), node('end', 'END')]
    expect(localFlowIssues(nodes, sequentialEdges(nodes)).map(issue => issue.location))
      .toEqual(expect.arrayContaining(['node:writeback.assignments']))
    nodes[1]!.config = { schemaVersion: 1, operation: 'UPDATE', targetRecordSource: 'CURRENT_RECORD',
      assignments: [{ fieldCode: 'approval_note', value: { source: 'FIXED', value: '已通过' } }] }
    expect(localFlowIssues(nodes, sequentialEdges(nodes))).toEqual([])
  })

  it('requires structured wait and timer values instead of raw duration text', () => {
    const wait = node('wait', 'WAIT'), end = node('end', 'END')
    let nodes = [node('start', 'START'), wait, end]
    expect(localFlowIssues(nodes, sequentialEdges(nodes)).map(issue => issue.code)).toContain('FLOW_WAIT_TIMER_CONFIG_INVALID')
    wait.config = { schemaVersion: 1, waitMode: 'DURATION', durationValue: 2, durationUnit: 'HOUR' }
    expect(localFlowIssues(nodes, sequentialEdges(nodes))).toEqual([])

    const timer = node('timer', 'TIMER')
    nodes = [node('start', 'START'), timer, end]
    timer.config = { schemaVersion: 1, waitMode: 'ABSOLUTE', resumeTime: '2000-01-01T08:00:00' }
    expect(localFlowIssues(nodes, sequentialEdges(nodes)).map(issue => issue.code)).toContain('FLOW_WAIT_TIMER_CONFIG_INVALID')
    timer.config.resumeTime = '2099-01-01T08:00:00'
    expect(localFlowIssues(nodes, sequentialEdges(nodes))).toEqual([])
  })
})
