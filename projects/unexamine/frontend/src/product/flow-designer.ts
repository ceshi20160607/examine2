import type { FlowEdgeInput, FlowIssue, FlowNodeInput } from './types'

export function localFlowIssues(nodes: FlowNodeInput[], edges: FlowEdgeInput[]): FlowIssue[] {
  const issues: FlowIssue[] = []
  const start = nodes.filter(node => node.nodeType === 'START')
  if (start.length !== 1) issues.push({ code: 'START_NODE_COUNT_INVALID', location: 'graph', message: '必须且只能有一个开始节点' })
  if (!nodes.some(node => node.nodeType === 'END')) issues.push({ code: 'END_NODE_MISSING', location: 'graph', message: '至少需要一个结束节点' })
  const outgoing = new Map<string, string[]>()
  for (const edge of edges) outgoing.set(edge.sourceNodeKey, [...(outgoing.get(edge.sourceNodeKey) || []), edge.targetNodeKey])
  for (const node of nodes) {
    if (node.nodeType !== 'END' && !(outgoing.get(node.nodeKey)?.length)) {
      issues.push({ code: 'DEAD_END_NODE', location: `node:${node.nodeKey}`, message: '节点没有后续连线' })
    }
    if (node.nodeType === 'APPROVAL' && !Object.keys(node.assigneePolicy || {}).length) {
      issues.push({ code: 'APPROVER_MISSING', location: `node:${node.nodeKey}`, message: '审批节点尚未配置审批人' })
    }
    if (node.nodeType === 'GATEWAY') {
      const expressions = edges.filter(edge => edge.sourceNodeKey === node.nodeKey).map(edge => edge.conditionExpression || '')
      if (expressions.some(expression => expression && !expression.match(/^\w+\s*(==|!=)\s*.+$/))) {
        issues.push({ code: 'CONDITION_INVALID', location: `node:${node.nodeKey}`, message: '条件需使用“变量 == 值”或“变量 != 值”' })
      }
    }
    const required: Partial<Record<FlowNodeInput['nodeType'], string[]>> = {
      SUBFLOW: ['flowId'], WEBHOOK: ['url'], AI: ['model', 'prompt'], UPDATE_FIELD: ['fieldUpdates'],
    }
    for (const key of required[node.nodeType] || []) {
      if (!node.config[key]) issues.push({ code: 'SPECIAL_PROPERTY_MISSING', location: `node:${node.nodeKey}.${key}`, message: `${node.name}缺少 ${key}` })
    }
    if (node.nodeType === 'WAIT_TIMER' && !node.config.duration && !node.config.resumeAt) {
      issues.push({ code: 'SPECIAL_PROPERTY_MISSING', location: `node:${node.nodeKey}`, message: `${node.name}缺少等待时长或恢复时间` })
    }
  }
  if (start.length === 1) {
    const reachable = new Set<string>()
    const queue = [start[0]!.nodeKey]
    while (queue.length) {
      const key = queue.shift()!
      if (reachable.has(key)) continue
      reachable.add(key)
      queue.push(...(outgoing.get(key) || []))
    }
    nodes.filter(node => !reachable.has(node.nodeKey)).forEach(node =>
      issues.push({ code: 'UNREACHABLE_NODE', location: `node:${node.nodeKey}`, message: '节点无法从开始节点到达' }))
  }
  return issues
}

export function sequentialEdges(nodes: FlowNodeInput[]): FlowEdgeInput[] {
  return nodes.slice(0, -1).map((node, index) => {
    const target = nodes[index + 1]!
    return {
      edgeKey: `edge_${node.nodeKey}_${target.nodeKey}`,
      sourceNodeKey: node.nodeKey,
      targetNodeKey: target.nodeKey,
      conditionExpression: node.nodeType === 'GATEWAY' ? 'amount != 0' : undefined,
      priorityOrder: (index + 1) * 10,
      config: {},
    }
  })
}
