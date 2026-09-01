export function todoStatusLabel(status: string) {
  return ({ PENDING: '待处理', SUSPENDED: '加签暂停', COMPLETED: '已完成', INVALID: '已失效', REASSIGNED: '已转交' } as Record<string, string>)[status] || status
}

export function todoTypeLabel(type: string) {
  return ({ APPROVAL: '审批', CONTACT: '今日联系', WORK: '工作任务', OTHER: '其他' } as Record<string, string>)[type] || type
}

export function todoActionLabel(action: string) {
  return ({ APPROVE: '同意', REJECT: '拒绝', RETURN: '退回', TRANSFER: '转交' } as Record<string, string>)[action] || action
}

export function todoNeedsComment(action: string) {
  return action === 'REJECT' || action === 'RETURN'
}

export function todoNeedsTarget(action: string) {
  return action === 'TRANSFER'
}
