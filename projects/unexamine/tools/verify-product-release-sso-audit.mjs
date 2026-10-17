// 在同一 LDAP 候选环境检验企业登录与系统成员成功/拒绝的审计关联。
import { writeFileSync } from 'node:fs'
import { execFileSync } from 'node:child_process'

const base = 'http://127.0.0.1:15173'
async function request(path, method = 'GET', body, token) {
  const response = await fetch(base + path, {
    method, headers: {
      ...(body === undefined ? {} : { 'content-type': 'application/json' }),
      ...(token ? { authorization: `Bearer ${token}` } : {}),
    }, body: body === undefined ? undefined : JSON.stringify(body),
  })
  const result = await response.json()
  return { status: response.status, code: result.code, data: result.data, traceId: result.requestId }
}
async function api(path, method = 'GET', body, token) {
  const result = await request(path, method, body, token)
  if (result.status !== 200 || result.code !== 'OK') {
    throw new Error(`${path}: HTTP ${result.status} ${result.code} ${result.traceId}`)
  }
  return result
}

const admin = (await api('/api/auth/login', 'POST', { username: 'admin', password: '123123aa' })).data
const adminSystems = (await api('/api/systems', 'GET', undefined, admin.accessToken)).data
const deniedSystem = adminSystems.find(item => item.systemCode === 's19_sso_audit_denied' || item.code === 's19_sso_audit_denied')
  || (await api('/api/systems', 'POST', {
    name: 'S19 SSO 审计无权系统', code: 's19_sso_audit_denied',
  }, admin.accessToken)).data
const login = await api('/api/auth/sso/s19-ldap/directory/login', 'POST', {
  username: 'alice', password: 'fixture-user-password',
})
const platformToken = login.data.accessToken
const context = (await api('/api/auth/me', 'GET', undefined, platformToken)).data
if (!context.sessionId || context.username !== 's19_ldap_owner') throw new Error('SSO platform session missing')
const entered = await api('/api/systems/1/enter', 'POST', undefined, platformToken)
const rejected = await request(`/api/systems/${deniedSystem.systemId}/enter`, 'POST', undefined, platformToken)
if (rejected.status !== 403 || rejected.code !== 'SYSTEM_ACCESS_DENIED') {
  throw new Error(`Unauthorized system accepted: ${rejected.status} ${rejected.code}`)
}
const tenantRejected = await request('/api/systems/1/tenants/999/enter', 'POST', undefined, platformToken)
if (tenantRejected.status !== 409 || tenantRejected.code !== 'TENANT_SWITCH_NOT_AVAILABLE') {
  throw new Error(`Single-tenant switch accepted: ${tenantRejected.status} ${tenantRejected.code}`)
}
// 仅在隔离验收库短暂停用已知成员，验证真实准入拒绝；无论断言结果如何都恢复原状态。
function fixtureSql(sql) {
  return execFileSync('docker', ['exec', 'mysql', 'sh', '-c',
    `mysql -uroot -p\$MYSQL_ROOT_PASSWORD -N -D unexamine_s19_ldap -e "${sql}"`], { encoding: 'utf8' }).trim()
}
const originalStatus = fixtureSql(`SELECT status FROM un_sys_member WHERE id = ${entered.data.systemMemberId}`)
if (originalStatus !== 'ACTIVE') throw new Error(`Fixture member status is ${originalStatus}`)
let disabledRejected
try {
  fixtureSql(`UPDATE un_sys_member SET status = 'DISABLED' WHERE id = ${entered.data.systemMemberId} AND status = 'ACTIVE'`)
  disabledRejected = await request('/api/systems/1/enter', 'POST', undefined, platformToken)
  if (disabledRejected.status !== 403 || disabledRejected.code !== 'SYSTEM_ACCESS_DENIED') {
    throw new Error(`Disabled member entered system: ${disabledRejected.status} ${disabledRejected.code}`)
  }
} finally {
  fixtureSql(`UPDATE un_sys_member SET status = 'ACTIVE' WHERE id = ${entered.data.systemMemberId} AND status = 'DISABLED'`)
}
const localContext = (await api('/api/auth/me', 'GET', undefined, admin.accessToken)).data
const localEntry = await api(`/api/systems/${deniedSystem.systemId}/enter`, 'POST', undefined, admin.accessToken)
async function audited(traceId, token, resultCode, mapping) {
  const rows = (await api(`/api/admin/system/identity-mapping/logs?traceId=${traceId}`, 'GET', undefined, token)).data
  const row = rows.find(item => item.eventCode === 'SSO_LOGIN_SYSTEM_ENTER')
  if (!row || row.loginTraceId !== login.traceId || row.identityProvider !== 's19-ldap'
      || row.externalUserId !== 'alice' || row.resultCode !== resultCode
      || row.systemMemberMapping !== mapping || row.accountBinding !== 'MATCHED'
      || row.authenticationMethod !== 'LDAP' || !row.ip || !row.device
      || row.requestId !== traceId || row.traceId !== traceId) {
    throw new Error(`System identity log mismatch: ${JSON.stringify(rows)}`)
  }
  return row.id
}
const authorizedAuditId = await audited(entered.traceId, entered.data.tokens.accessToken, 'SUCCESS', 'MATCHED')
const tenantDeniedAuditId = await audited(tenantRejected.traceId, entered.data.tokens.accessToken, 'TENANT_SWITCH_NOT_AVAILABLE', 'DENIED')
const disabledMemberAuditId = await audited(disabledRejected.traceId, entered.data.tokens.accessToken, 'SYSTEM_ACCESS_DENIED', 'DENIED')
const deniedAuditId = await audited(rejected.traceId, localEntry.data.tokens.accessToken, 'SYSTEM_ACCESS_DENIED', 'DENIED')
const localLogs = (await api('/api/admin/system/identity-mapping/logs', 'GET', undefined, localEntry.data.tokens.accessToken)).data
if (localLogs.some(item => item.traceId === localEntry.traceId && item.eventCode === 'SSO_LOGIN_SYSTEM_ENTER')) {
  throw new Error('Local login incorrectly created enterprise entry audit')
}
const evidence = {
  step: 'S19.6', candidate: '0.1.0-s19.6-dev2',
  enterprisePlatformSessionId: context.sessionId, localPlatformSessionId: localContext.sessionId,
  loginTraceId: login.traceId, acceptedEntryTraceId: entered.traceId,
  deniedEntryTraceId: rejected.traceId, authorizedSystemId: 1,
  tenantDeniedTraceId: tenantRejected.traceId, tenantDeniedCode: tenantRejected.code,
  disabledMemberTraceId: disabledRejected.traceId, disabledMemberCode: disabledRejected.code,
  authorizedMemberId: entered.data.systemMemberId, deniedSystemId: deniedSystem.systemId,
  deniedStatus: rejected.status, deniedCode: rejected.code,
  authorizedAuditId, deniedAuditId, tenantDeniedAuditId, disabledMemberAuditId,
  localEntryDistinct: true,
}
writeFileSync('projects/unexamine/ai/evidence/s19-unified-sso-api.json', JSON.stringify(evidence, null, 2) + '\n')
console.log(JSON.stringify(evidence, null, 2))
