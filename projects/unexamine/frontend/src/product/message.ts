export function messageStatusLabel(status: string) {
  return ({ UNREAD: '未读', READ: '已读', ARCHIVED: '已归档' } as Record<string, string>)[status] || status
}

export function messageChannelLabel(channel: string) {
  return ({ IN_APP: '站内', EMAIL: '邮件', SMS: '短信', WEBHOOK: 'Webhook' } as Record<string, string>)[channel] || channel
}

export function deliveryStatusLabel(status: string) {
  return ({ DELIVERED: '已送达', RETRY_PENDING: '等待重试', FAILED: '失败', SENT: '已发送' } as Record<string, string>)[status] || status
}

export function parseTemplateVariables(value: string) {
  return [...new Set(value.split(',').map((item) => item.trim()).filter(Boolean))]
}

export function parseMessageVariables(value: string) {
  const parsed = JSON.parse(value || '{}') as unknown
  if (!parsed || Array.isArray(parsed) || typeof parsed !== 'object') throw new Error('模板变量必须是 JSON 对象')
  return parsed as Record<string, unknown>
}
