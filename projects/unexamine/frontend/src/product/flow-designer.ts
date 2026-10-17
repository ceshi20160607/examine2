import type { FlowEdgeInput, FlowIssue, FlowNodeInput } from './types'

export function localFlowIssues(nodes: FlowNodeInput[], edges: FlowEdgeInput[]): FlowIssue[] {
  const issues: FlowIssue[] = []
  const start = nodes.filter(node => node.nodeType === 'START')
  if (start.length !== 1) issues.push({ code: 'START_NODE_COUNT_INVALID', location: 'graph', message: '必须且只能有一个开始节点' })
  if (!nodes.some(node => node.nodeType === 'END')) issues.push({ code: 'END_NODE_MISSING', location: 'graph', message: '至少需要一个结束节点' })
  const outgoing = new Map<string, string[]>()
  const incoming = new Map<string, string[]>()
  for (const edge of edges) outgoing.set(edge.sourceNodeKey, [...(outgoing.get(edge.sourceNodeKey) || []), edge.targetNodeKey])
  for (const edge of edges) incoming.set(edge.targetNodeKey, [...(incoming.get(edge.targetNodeKey) || []), edge.sourceNodeKey])
  for (const node of nodes) {
    if (node.nodeType !== 'END' && !(outgoing.get(node.nodeKey)?.length)) {
      issues.push({ code: 'DEAD_END_NODE', location: `node:${node.nodeKey}`, message: '节点没有后续连线' })
    }
    if (node.nodeType === 'APPROVAL') {
      const policy = node.assigneePolicy || {}
      const type = String(policy.type || '')
      // 平台和系统指定人员有各自身份集合，作用域有效性仍由服务端发布校验确认。
      const personIds = policy.platformAccountIds ?? policy.tenantMemberIds
      const missingSelection = type === 'PERSON' && !(Array.isArray(personIds) && personIds.length)
        || ['DEPARTMENT', 'DEPARTMENT_MANAGER'].includes(type) && !((policy.departmentIds as number[] | undefined)?.length)
        || type === 'PERSON_FIELD' && !String(policy.fieldCode || '').trim()
        || type === 'EXTERNAL_PERSON' && (!Number.isSafeInteger(Number(policy.applicationId))
          || Number(policy.applicationId) < 1 || !String(policy.externalApproverId || '').trim())
      if (!type || missingSelection) {
        issues.push({ code: 'APPROVER_MISSING', location: `node:${node.nodeKey}`, message: '审批节点尚未完整配置审批人来源' })
      }
    }
    if (['CC', 'NOTIFICATION', 'MESSAGE'].includes(node.nodeType)) {
      const ids = node.assigneePolicy?.platformAccountIds ?? node.assigneePolicy?.tenantMemberIds
      if (!(Array.isArray(ids) && ids.length)) {
        issues.push({ code: 'FLOW_MESSAGE_RECIPIENT_MISSING', location: `node:${node.nodeKey}`, message: '消息节点尚未选择接收人' })
      }
    }
    if (node.nodeType === 'GATEWAY') {
      const outputs = edges.filter(edge => edge.sourceNodeKey === node.nodeKey)
      const expressions = outputs.map(edge => edge.conditionExpression || '')
      if (expressions.some(expression => expression && !expression.match(/^\w+\s*(==|!=)\s*.+$/))) {
        issues.push({ code: 'CONDITION_INVALID', location: `node:${node.nodeKey}`, message: '条件需使用“变量 == 值”或“变量 != 值”' })
      }
      const mode = String(node.config.gatewayMode || 'EXCLUSIVE').toUpperCase()
      if (!['EXCLUSIVE', 'PARALLEL', 'INCLUSIVE', 'MERGE'].includes(mode)) {
        issues.push({ code: 'FLOW_GATEWAY_MODE_INVALID', location: `node:${node.nodeKey}`, message: '请选择有效的网关方式' })
      } else if (mode === 'PARALLEL' || mode === 'INCLUSIVE') {
        if ((incoming.get(node.nodeKey)?.length || 0) !== 1 || outputs.length < 2) {
          issues.push({ code: 'FLOW_GATEWAY_SPLIT_TOPOLOGY_INVALID', location: `node:${node.nodeKey}`, message: '分支网关需要一条入线和至少两条出线' })
        }
        const mergeKey = String(node.config.mergeNodeKey || '')
        const merge = nodes.find(candidate => candidate.nodeKey === mergeKey && candidate.nodeType === 'GATEWAY'
          && String(candidate.config.gatewayMode || 'EXCLUSIVE').toUpperCase() === 'MERGE')
        if (!merge || merge.config.splitNodeKey !== node.nodeKey) {
          issues.push({ code: 'FLOW_GATEWAY_PAIR_MISMATCH', location: `node:${node.nodeKey}`, message: '请选择并相互配对一个汇合网关' })
        }
        const conditioned = outputs.filter(edge => edge.config.condition !== undefined || Boolean(edge.conditionExpression))
        if (mode === 'PARALLEL' && conditioned.length) {
          issues.push({ code: 'FLOW_GATEWAY_PARALLEL_CONDITION_INVALID', location: `node:${node.nodeKey}`, message: '并行网关的出线不能配置条件' })
        }
        if (mode === 'INCLUSIVE' && outputs.length - conditioned.length !== 1) {
          issues.push({ code: 'FLOW_GATEWAY_INCLUSIVE_DEFAULT_INVALID', location: `node:${node.nodeKey}`, message: '包容网关需要且只能有一条无条件兜底分支' })
        }
      } else if (mode === 'MERGE') {
        if ((incoming.get(node.nodeKey)?.length || 0) < 2 || outputs.length !== 1) {
          issues.push({ code: 'FLOW_GATEWAY_MERGE_TOPOLOGY_INVALID', location: `node:${node.nodeKey}`, message: '汇合网关至少需要两条入线和一条出线' })
        }
        if (outputs.some(edge => edge.config.condition !== undefined || Boolean(edge.conditionExpression))) {
          issues.push({ code: 'FLOW_GATEWAY_MERGE_CONDITION_INVALID', location: `node:${node.nodeKey}`, message: '汇合后的出线不能配置条件' })
        }
        const splitKey = String(node.config.splitNodeKey || '')
        const split = nodes.find(candidate => candidate.nodeKey === splitKey && candidate.nodeType === 'GATEWAY'
          && ['PARALLEL', 'INCLUSIVE'].includes(String(candidate.config.gatewayMode || 'EXCLUSIVE').toUpperCase()))
        if (!split || split.config.mergeNodeKey !== node.nodeKey) {
          issues.push({ code: 'FLOW_GATEWAY_PAIR_MISMATCH', location: `node:${node.nodeKey}`, message: '请选择并相互配对一个分支网关' })
        }
      }
    }
    const required: Partial<Record<FlowNodeInput['nodeType'], string[]>> = {
      SUBFLOW: ['flowId'], WEBHOOK: ['applicationId', 'callbackId'], EXTERNAL: ['applicationId', 'callbackId'],
      AI: ['modelGrantId', 'prompt'],
      CC: ['subject', 'content'], NOTIFICATION: ['subject', 'content'], MESSAGE: ['subject', 'content'],
    }
    for (const key of required[node.nodeType] || []) {
      if (!node.config[key]) issues.push({ code: 'SPECIAL_PROPERTY_MISSING', location: `node:${node.nodeKey}.${key}`, message: `${node.name}缺少 ${key}` })
    }
    if (node.nodeType === 'AI') {
      const grantId = Number(node.config.modelGrantId)
      if (!Number.isSafeInteger(grantId) || grantId < 1) {
        issues.push({ code: 'FLOW_AI_MODEL_INVALID', location: `node:${node.nodeKey}.modelGrantId`, message: '请选择当前系统获授权的模型' })
      }
      const prompt = String(node.config.prompt || '')
      if (prompt && [...prompt.matchAll(/{{\s*([^{}]+?)\s*}}/g)].some(match => !['instanceTitle', 'flowName'].includes(match[1]!))) {
        issues.push({ code: 'FLOW_AI_PROMPT_INVALID', location: `node:${node.nodeKey}.prompt`, message: '提示模板只支持实例标题和流程名称变量' })
      }
      const outputVariable = String(node.config.outputVariable || '').trim()
      if (outputVariable && !/^[A-Za-z][A-Za-z0-9_]{0,63}$/.test(outputVariable)) {
        issues.push({ code: 'FLOW_AI_OUTPUT_INVALID', location: `node:${node.nodeKey}.outputVariable`, message: '结果变量须以英文字母开头，只能包含字母、数字和下划线' })
      }
    }
    if (['UPDATE_FIELD', 'DATA_ACTION'].includes(node.nodeType)) {
      const assignments = Array.isArray(node.config.assignments) ? node.config.assignments
        : Object.entries((node.config.fieldUpdates as Record<string, unknown> | undefined) || {})
      if (!assignments.length) issues.push({ code: 'SPECIAL_PROPERTY_MISSING', location: `node:${node.nodeKey}.assignments`, message: `${node.name}至少要选择一个目标字段` })
      if (node.nodeType === 'DATA_ACTION' && (!node.config.moduleId || !['CREATE', 'UPDATE'].includes(String(node.config.operation)))) {
        issues.push({ code: 'SPECIAL_PROPERTY_MISSING', location: `node:${node.nodeKey}.operation`, message: `${node.name}缺少目标模块或数据动作` })
      }
    }
    if (node.nodeType === 'WAIT') {
      const value = Number(node.config.durationValue), unit = String(node.config.durationUnit || '')
      const multiplier = unit === 'DAY' ? 1440 : unit === 'HOUR' ? 60 : unit === 'MINUTE' ? 1 : 0
      if (!Number.isInteger(value) || value <= 0 || multiplier === 0 || value * multiplier > 525600) {
        issues.push({ code: 'FLOW_WAIT_TIMER_CONFIG_INVALID', location: `node:${node.nodeKey}`, message: '请填写不超过一年的正整数等待时长并选择单位' })
      }
    }
    if (node.nodeType === 'TIMER') {
      const resume = String(node.config.resumeTime || ''), time = Date.parse(resume)
      if (!resume || Number.isNaN(time) || time <= Date.now()) {
        issues.push({ code: 'FLOW_WAIT_TIMER_CONFIG_INVALID', location: `node:${node.nodeKey}`, message: '请选择晚于当前时间的恢复日期和时间' })
      }
    }
    if (node.nodeType === 'WAIT_TIMER' && !node.config.durationMinutes && !node.config.duration
      && !node.config.resumeAt && !node.config.resumeTime) {
      issues.push({ code: 'FLOW_WAIT_TIMER_CONFIG_INVALID', location: `node:${node.nodeKey}`, message: `${node.name}缺少等待时长或恢复时间` })
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
