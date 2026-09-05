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
      .toEqual(expect.arrayContaining(['NOTIFICATION_RECIPIENT_MISSING', 'SPECIAL_PROPERTY_MISSING']))
    nodes[1]!.assigneePolicy = { type: 'PERSON', tenantMemberIds: [7] }
    nodes[1]!.config = { content: '流程 ${instanceTitle} 已更新' }
    expect(localFlowIssues(nodes, sequentialEdges(nodes))).toEqual([])
  })

  it('blocks decorative writeback nodes that have no action or field mapping', () => {
    const nodes = [node('start', 'START'), node('writeback', 'UPDATE_FIELD'), node('end', 'END')]
    expect(localFlowIssues(nodes, sequentialEdges(nodes)).map(issue => issue.location))
      .toEqual(expect.arrayContaining(['node:writeback.businessAction', 'node:writeback.fieldUpdates']))
    nodes[1]!.config = { businessAction: 'UPDATE', fieldUpdates: { approval_note: '${comment}' } }
    expect(localFlowIssues(nodes, sequentialEdges(nodes))).toEqual([])
  })
})
