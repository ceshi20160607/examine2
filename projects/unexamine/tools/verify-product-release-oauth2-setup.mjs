// 在隔离发布包上通过正式 API 完成身份源发布及系统成员映射，记录可复查的业务结果。
import { writeFileSync } from 'node:fs'

const base = process.env.S19_OAUTH2_BASE || 'http://127.0.0.1:15184'
const providerBase = 'http://127.0.0.1:18212'
const callback = `${base}/sso/callback`
const adminPassword = process.env.S19_OAUTH2_ADMIN_PASSWORD
const ownerPassword = process.env.S19_OAUTH2_OWNER_PASSWORD
if (!adminPassword || !ownerPassword) throw new Error('Isolated acceptance credentials are required')

async function api(path, method = 'GET', body, token) {
  const response = await fetch(base + path, {
    method,
    headers: {
      ...(body === undefined ? {} : { 'content-type': 'application/json' }),
      ...(token ? { authorization: `Bearer ${token}` } : {}),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  const result = await response.json()
  if (!response.ok) throw new Error(`${path}: HTTP ${response.status}, ${result.code || result.message}`)
  return result.data
}

const admin = await api('/api/auth/login', 'POST', { username: 'admin', password: adminPassword })
const owner = await api('/api/auth/register', 'POST', {
  username: 's19_oauth_owner', password: ownerPassword, displayName: 'S19 OAuth 成员',
  email: 's19-oauth@example.test', systemName: 'S19 OAuth 系统', systemCode: 's19_oauth_system',
})
const adminToken = admin.accessToken
const systemToken = owner.tokens.accessToken
const before = await api('/api/auth/sso/providers')
if (before.some(item => item.code === 's19-oauth2')) throw new Error('Provider appeared before publication')

const draft = await api('/api/admin/platform/sso-providers', 'POST', {
  code: 's19-oauth2', name: 'S19 OAuth 企业身份', protocol: 'OAUTH2', issuer: providerBase,
  clientId: 's19-oauth-client', clientSecretRef: 'env:APP_SSO_OAUTH2_CLIENT_SECRET',
  protocolConfig: {
    authorizationEndpoint: `${providerBase}/authorize`, tokenEndpoint: `${providerBase}/token`,
    userinfoEndpoint: `${providerBase}/userinfo`, redirectUri: callback,
    healthEndpoint: `${providerBase}/health`, scopes: ['profile', 'email'],
  },
  allowedDomains: ['example.test'],
  attributeMapping: { externalUserId: 'employee_id', email: 'mail', emailVerified: 'mail_verified' },
  jitPolicy: { enabled: false }, mfaPolicy: { required: true, claim: 'mfa' }, callbackUris: [callback],
}, adminToken)
const providerId = draft.id
const versionId = draft.versions[0].id
const tested = await api(`/api/admin/platform/sso-providers/${providerId}/versions/${versionId}/test`,
  'POST', undefined, adminToken)
if (tested.status !== 'PASSED') throw new Error(`OAuth2 preflight failed: ${tested.failureCode}`)
const published = await api(`/api/admin/platform/sso-providers/${providerId}/versions/${versionId}/publish`,
  'POST', undefined, adminToken)
if (published.status !== 'PUBLISHED') throw new Error('OAuth2 provider was not published')
const after = await api('/api/auth/sso/providers')
if (!after.some(item => item.code === 's19-oauth2' && item.protocol === 'OAUTH2')) {
  throw new Error('Published OAuth2 provider is absent from login entry')
}

const departments = await api('/api/admin/system/identity-mapping/departments', 'GET', undefined, systemToken)
const root = departments.find(item => item.code === 'root')
if (!root) throw new Error('Target system has no root department')
await api('/api/admin/system/identity-mapping/configuration', 'POST', {
  providerId, providerVersionId: versionId, tenantDomain: 'example.test',
  departmentMappings: { HQ: root.id }, matchOrder: ['EMAIL'], jitPolicy: 'ACCESS_REQUEST',
}, systemToken)
const preflight = await api('/api/admin/system/identity-mapping/preflight', 'POST', {
  samples: [{ externalUserId: 's19-oauth-user', externalDepartment: 'HQ',
    email: 's19-oauth@example.test', displayName: 'S19 OAuth 成员', requestId: 's19-oauth-sample' }],
}, systemToken)
if (preflight.items[0].plannedAction !== 'BIND_EXISTING_MEMBER') {
  throw new Error(`Unexpected member mapping: ${preflight.items[0].plannedAction}`)
}
const confirmed = await api('/api/admin/system/identity-mapping/confirm', 'POST',
  { preflightId: preflight.preflightId, confirmed: true }, systemToken)
if (confirmed.status !== 'SUCCEEDED') throw new Error(`System identity mapping did not complete: ${confirmed.status}`)

const evidence = {
  step: process.env.S19_OAUTH2_STEP || 'S19.3', candidate: process.env.S19_OAUTH2_CANDIDATE_VERSION || '0.1.0-s19.3-dev1', mode: 'isolated-local-idp',
  providerId, versionId, providerCode: 's19-oauth2',
  draftHidden: true, preflightStatus: tested.status, publishedStatus: published.status,
  loginEntryVisible: true, systemId: owner.systemId, tenantId: owner.tenantId,
  systemMappingAction: preflight.items[0].plannedAction, systemMappingStatus: confirmed.status,
  idpBoundary: 'Local protocol-faithful OAuth2 fixture; no enterprise IdP credentials or connection claimed.',
}
writeFileSync(process.env.S19_OAUTH2_EVIDENCE_PATH || 'projects/unexamine/ai/evidence/s19-oauth2-live-setup.json', JSON.stringify(evidence, null, 2) + '\n')
process.stdout.write(JSON.stringify(evidence) + '\n')
