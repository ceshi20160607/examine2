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
    const nodes = [node('start', 'START'), node('approval', 'APPROVAL', { type: 'ACCOUNT', accountIds: [1] }), node('end', 'END')]
    expect(localFlowIssues(nodes, sequentialEdges(nodes))).toEqual([])
  })
})
