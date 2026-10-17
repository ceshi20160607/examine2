// 在现有 MySQL/Redis、一个本地后端和本机 LDAPS 夹具上验收目录身份主路径。
import { writeFileSync } from 'node:fs'

const base = process.env.S19_LDAP_BASE || 'http://127.0.0.1:15173'
const adminPassword = process.env.S19_LDAP_ADMIN_PASSWORD || '123123aa'
const ownerPassword = process.env.S19_LDAP_OWNER_PASSWORD || 'correct-password'
const code = 's19-ldap'
const candidate = '0.1.0-s19.5-dev1'
const fixturePassword = 'fixture-user-password'

async function request(path, method = 'GET', body, token) {
  const response = await fetch(base + path, {
    method,
    headers: {
      ...(body === undefined ? {} : { 'content-type': 'application/json' }),
      ...(token ? { authorization: `Bearer ${token}` } : {}),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  const result = await response.json()
  return { status: response.status, code: result.code, data: result.data, traceId: result.requestId }
}

async function api(path, method = 'GET', body, token) {
  const result = await request(path, method, body, token)
  if (result.status !== 200 || result.code !== 'OK') {
    throw new Error(`${path}: HTTP ${result.status}, ${result.code}, trace ${result.traceId}`)
  }
  return result.data
}

async function denied(path, body, expectedCode) {
  const result = await request(path, 'POST', body)
  if (result.code !== expectedCode || result.status < 400) {
    throw new Error(`${path}: expected ${expectedCode}, got HTTP ${result.status} ${result.code}`)
  }
  return { status: result.status, code: result.code, traceId: result.traceId }
}

const admin = await api('/api/auth/login', 'POST', { username: 'admin', password: adminPassword })
const owner = await api('/api/auth/register', 'POST', {
  username: 's19_ldap_owner', password: ownerPassword, displayName: 'S19 LDAP 成员',
  email: 'member@example.test', systemName: 'S19 LDAP 系统', systemCode: 's19_ldap_system',
})
const draftBody = (providerCode, override = {}) => ({
  code: providerCode, name: 'S19 LDAP 企业身份', protocol: 'LDAP',
  issuer: 'ldaps://127.0.0.1:1636', clientId: 's19-ldap-local-directory', clientSecretRef: null,
  protocolConfig: {
    directoryEndpoint: 'ldaps://127.0.0.1:1636', bindDn: 'cn=Directory Manager',
    bindSecretRef: 'env:TEST_SSO_BIND', searchBase: 'ou=people,dc=example,dc=test',
    loginAttribute: 'uid', ...(override.protocolConfig || {}),
  },
  allowedDomains: ['example.test'],
  attributeMapping: { externalUserId: 'uid', email: 'mail' },
  jitPolicy: { enabled: false }, mfaPolicy: { required: false, ...(override.mfaPolicy || {}) },
  callbackUris: [],
})

const draft = await api('/api/admin/platform/sso-providers', 'POST', draftBody(code), admin.accessToken)
const providerId = draft.id
const versionId = draft.versions[0].id
if ((await api('/api/auth/sso/providers')).some(item => item.code === code)) {
  throw new Error('Unpublished LDAP provider appeared on login page')
}
const testPath = (id, version) => `/api/admin/platform/sso-providers/${id}/versions/${version}/test`
const tested = await api(testPath(providerId, versionId), 'POST', undefined, admin.accessToken)
if (tested.status !== 'PASSED' || !tested.checks.some(check => check.code === 'DIRECTORY_BIND_SEARCH' && check.status === 'PASSED')) {
  throw new Error(`Trusted LDAPS preflight failed: ${JSON.stringify(tested.checks)}`)
}
const published = await api(`/api/admin/platform/sso-providers/${providerId}/versions/${versionId}/publish`,
  'POST', undefined, admin.accessToken)
if (published.status !== 'PUBLISHED' || !(await api('/api/auth/sso/providers')).some(item => item.code === code)) {
  throw new Error('Tested LDAP provider was not published to login page')
}

const systemToken = owner.tokens.accessToken
const departments = await api('/api/admin/system/identity-mapping/departments', 'GET', undefined, systemToken)
const root = departments.find(item => item.code === 'root')
if (!root) throw new Error('Target system root department missing')
await api('/api/admin/system/identity-mapping/configuration', 'POST', {
  providerId, providerVersionId: versionId, tenantDomain: 'example.test',
  departmentMappings: { HQ: root.id }, matchOrder: ['EMAIL'], jitPolicy: 'ACCESS_REQUEST',
}, systemToken)
const mapping = await api('/api/admin/system/identity-mapping/preflight', 'POST', {
  samples: [{ externalUserId: 'alice', externalDepartment: 'HQ',
    email: 'member@example.test', displayName: 'S19 LDAP 成员', requestId: 's19-ldap-sample' }],
}, systemToken)
if (mapping.items[0].plannedAction !== 'BIND_EXISTING_MEMBER') {
  throw new Error(`Unexpected mapping action ${mapping.items[0].plannedAction}`)
}
const confirmed = await api('/api/admin/system/identity-mapping/confirm', 'POST',
  { preflightId: mapping.preflightId, confirmed: true }, systemToken)
if (confirmed.status !== 'SUCCEEDED') throw new Error(`Mapping failed: ${confirmed.status}`)

const loginPath = `/api/auth/sso/${code}/directory/login`
const login = await api(loginPath, 'POST', { username: 'alice', password: fixturePassword })
const current = await api('/api/auth/me', 'GET', undefined, login.accessToken)
if (current.username !== 's19_ldap_owner' || current.systemId !== null) {
  throw new Error('LDAP account did not enter the bound platform account')
}
const entry = await api(`/api/systems/${owner.systemId}/enter`, 'POST', undefined, login.accessToken)
if (entry.systemId !== owner.systemId || entry.tenantId !== owner.tenantId) {
  throw new Error('LDAP account did not enter the confirmed system member')
}
const deniedSystem = await api('/api/systems', 'POST', {
  name: 'S19 LDAP 无权系统', code: 's19_ldap_denied',
}, admin.accessToken)
const unrelated = await request(`/api/systems/${deniedSystem.systemId}/enter`, 'POST', undefined, login.accessToken)
if (unrelated.status !== 403 || unrelated.code !== 'SYSTEM_ACCESS_DENIED') {
  throw new Error(`Unrelated system entry was accepted: ${unrelated.status} ${unrelated.code}`)
}

const failures = {
  wrongPassword: await denied(loginPath, { username: 'alice', password: 'wrong-password' }, 'SSO_DIRECTORY_CREDENTIAL_INVALID'),
  missingIdentity: await denied(loginPath, { username: 'ghost', password: fixturePassword }, 'SSO_DIRECTORY_IDENTITY_MISSING'),
  conflictingIdentity: await denied(loginPath, { username: 'duplicate', password: fixturePassword }, 'SSO_DIRECTORY_IDENTITY_CONFLICT'),
  disallowedDomain: await denied(loginPath, { username: 'outside', password: fixturePassword }, 'SSO_DOMAIN_NOT_ALLOWED'),
  missingMapping: await denied(loginPath, { username: 'unmapped', password: fixturePassword }, 'SSO_MAPPING_MISSING'),
  unauthorizedSystem: { status: unrelated.status, code: unrelated.code, traceId: unrelated.traceId },
}

const unsafe = await api('/api/admin/platform/sso-providers', 'POST', draftBody('s19-ldap-unsafe', {
  protocolConfig: { directoryEndpoint: 'ldap://127.0.0.1:1636' },
}), admin.accessToken)
const unsafeTest = await api(testPath(unsafe.id, unsafe.versions[0].id), 'POST', undefined, admin.accessToken)
if (unsafeTest.status !== 'FAILED' || !unsafeTest.checks.some(item => item.code === 'ENDPOINT_DIRECTORYENDPOINT' && item.status === 'FAILED')) {
  throw new Error(`Plain LDAP was not rejected: ${JSON.stringify(unsafeTest.checks)}`)
}
const mfa = await api('/api/admin/platform/sso-providers', 'POST', draftBody('s19-ldap-mfa', {
  mfaPolicy: { required: true },
}), admin.accessToken)
const mfaTest = await api(testPath(mfa.id, mfa.versions[0].id), 'POST', undefined, admin.accessToken)
if (mfaTest.status !== 'FAILED' || !mfaTest.checks.some(item => item.code === 'DIRECTORY_MFA_NOT_READY')) {
  throw new Error('Unsupported directory MFA configuration passed preflight')
}
const local = await api('/api/auth/login', 'POST', { username: 'admin', password: adminPassword })
if (!local.accessToken) throw new Error('Local login stopped working')

const evidence = {
  step: 'S19.5', candidate, frontend: base, database: 'unexamine_s19_ldap',
  providerId, versionId, systemId: owner.systemId, tenantId: owner.tenantId,
  draftHidden: true, trustedPreflight: tested.status, published: published.status,
  mappingAction: mapping.items[0].plannedAction, mappingStatus: confirmed.status,
  platformAccount: current.username, systemMemberId: entry.memberId,
  failures, plainLdapPreflight: unsafeTest.status, unsupportedMfaPreflight: mfaTest.status,
  localLoginAvailable: true,
  directoryBoundary: 'Local LDAPS fixture with temporary trusted certificate; real LDAP/AD not claimed.',
}
writeFileSync('projects/unexamine/ai/evidence/s19-ldap-api.json', JSON.stringify(evidence, null, 2) + '\n')
console.log(JSON.stringify({ ...evidence, failures: Object.fromEntries(Object.entries(failures).map(([key, value]) => [key, value.code])) }, null, 2))
