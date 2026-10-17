// 通过发布包的单个前端入口验证静态页面、同源 API 代理和后端系统创建/进入。
import { writeFileSync } from 'node:fs'

const base = 'http://127.0.0.1:15173'
async function api(path, method = 'GET', body, token) {
  const response = await fetch(base + path, { method, headers: {
    ...(body === undefined ? {} : { 'content-type': 'application/json' }),
    ...(token ? { authorization: `Bearer ${token}` } : {}),
  }, body: body === undefined ? undefined : JSON.stringify(body) })
  const result = await response.json()
  if (!response.ok || result.code !== 'OK') throw new Error(`${path}: HTTP ${response.status} ${result.code}`)
  return { data: result.data, traceId: result.requestId }
}

const page = await fetch(base + '/')
if (!page.ok || !(await page.text()).includes('<div id="app">')) throw new Error('Built frontend was not served')
const admin = (await api('/api/auth/login', 'POST', { username: 'admin', password: '123123aa' })).data
const created = await api('/api/systems', 'POST', {
  name: 'S19 直连部署系统', code: 's19_direct_deploy_system',
}, admin.accessToken)
const entered = await api(`/api/systems/${created.data.systemId}/enter`, 'POST', undefined, admin.accessToken)
if (entered.data.systemId !== created.data.systemId || !entered.data.tokens?.accessToken) {
  throw new Error('Direct deployment system entry failed')
}
const health = await fetch(base + '/actuator/health/readiness')
if (!health.ok) throw new Error('Frontend health proxy failed')
const evidence = { step: 'S19.7', candidate: '0.1.0-s19.7-dev1', frontend: base,
  staticPage: true, apiProxy: true, healthProxy: true, systemId: created.data.systemId,
  tenantId: entered.data.tenantId, memberId: entered.data.systemMemberId,
  createTraceId: created.traceId, enterTraceId: entered.traceId }
writeFileSync('projects/unexamine/ai/evidence/s19-direct-deployment-api.json', JSON.stringify(evidence, null, 2) + '\n')
console.log(JSON.stringify(evidence, null, 2))
