// 在相同候选环境建立无权系统，核对无效 SAML 交接码无法创建平台会话。
import { writeFileSync } from 'node:fs'

const base = 'http://127.0.0.1:15185'
const password = process.env.S19_SAML_ADMIN_PASSWORD
if (!password) throw new Error('Isolated administrator credential is required')
const login = await fetch(base + '/api/auth/login', {
  method: 'POST', headers: { 'content-type': 'application/json' },
  body: JSON.stringify({ username: 'admin', password }),
})
if (!login.ok) throw new Error('Isolated administrator login failed')
const token = (await login.json()).data.accessToken
const created = await fetch(base + '/api/systems', {
  method: 'POST', headers: { 'content-type': 'application/json', authorization: `Bearer ${token}` },
  body: JSON.stringify({ name: 'S19 SAML 无权系统', code: 's19_saml_denied' }),
})
if (!created.ok) throw new Error(`Denied target creation failed: ${created.status}`)
const deniedSystemId = (await created.json()).data.systemId
const invalidCallback = await fetch(base + '/api/auth/sso/s19-saml2/complete', {
  method: 'POST', headers: { 'content-type': 'application/json' },
  body: JSON.stringify({ code: 'invalid-code', state: 'invalid-state' }),
})
if (invalidCallback.status !== 401) throw new Error(`Invalid SAML ticket was not rejected: ${invalidCallback.status}`)
const evidence = { step: 'S19.4', deniedSystemId, invalidTicketHttpStatus: invalidCallback.status }
writeFileSync('projects/unexamine/ai/evidence/s19-saml-live-denials.json', JSON.stringify(evidence, null, 2) + '\n')
process.stdout.write(JSON.stringify(evidence) + '\n')
