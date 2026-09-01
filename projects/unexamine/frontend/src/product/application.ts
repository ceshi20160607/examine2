export function applicationStatusLabel(status: string) {
  return ({ DRAFT: '草稿', ACTIVE: '已启用', DISABLED: '已停用' } as Record<string, string>)[status] || status
}

export function applicationStatusColor(status: string) {
  return ({ DRAFT: 'blue', ACTIVE: 'green', DISABLED: 'red' } as Record<string, string>)[status] || 'default'
}

export function applicationTypeLabel(type: string) {
  return ({ SERVICE: '服务调用', WEBHOOK: '事件回调' } as Record<string, string>)[type] || type
}

export function parseApplicationObject(value: string, label: string) {
  let parsed: unknown
  try {
    parsed = JSON.parse(value)
  } catch {
    throw new Error(`${label}必须是有效 JSON`)
  }
  if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
    throw new Error(`${label}必须是 JSON 对象`)
  }
  return parsed as Record<string, unknown>
}

export function parseApplicationFields(value: string) {
  return [...new Set(value.split(',').map(item => item.trim()).filter(Boolean))]
    .map(fieldCode => ({ fieldCode, readable: true, writable: false, maskStrategy: 'NONE' }))
}

function hex(bytes: ArrayBuffer) {
  return [...new Uint8Array(bytes)].map(value => value.toString(16).padStart(2, '0')).join('')
}

export async function applicationRequestHash(rawBody: string) {
  return hex(await crypto.subtle.digest('SHA-256', new TextEncoder().encode(rawBody)))
}

export async function signApplicationRequest(input: {
  clientId: string
  clientSecret: string
  timestamp: string
  nonce: string
  idempotencyKey: string
  rawBody: string
}) {
  const requestHash = await applicationRequestHash(input.rawBody)
  const canonical = `${input.clientId}\n${input.timestamp}\n${input.nonce}\n${input.idempotencyKey}\n${requestHash}`
  const key = await crypto.subtle.importKey(
    'raw', new TextEncoder().encode(input.clientSecret), { name: 'HMAC', hash: 'SHA-256' }, false, ['sign'],
  )
  return hex(await crypto.subtle.sign('HMAC', key, new TextEncoder().encode(canonical)))
}

export function newApplicationNonce() {
  return crypto.randomUUID().replace(/-/g, '')
}
