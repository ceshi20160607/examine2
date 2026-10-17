// 在独立候选包上通过正式 API 发布 SAML2 身份源并人工确认系统成员映射。
import { writeFileSync } from 'node:fs'

const base = process.env.S19_SAML_BASE || 'http://127.0.0.1:15185'
const idp = 'http://127.0.0.1:18213'
const code = 's19-saml2'
const acs = `${base}/api/auth/sso/${code}/saml/acs`
const adminPassword = process.env.S19_SAML_ADMIN_PASSWORD
const ownerPassword = process.env.S19_SAML_OWNER_PASSWORD
const ownerEmail = process.env.S19_SAML_EMAIL || 'member@example.test'
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
  username: 's19_saml_owner', password: ownerPassword, displayName: 'S19 SAML 成员',
  email: ownerEmail, systemName: 'S19 SAML 系统', systemCode: 's19_saml_system',
})
const before = await api('/api/auth/sso/providers')
if (before.some(item => item.code === code)) throw new Error('SAML provider appeared before publication')
const draft = await api('/api/admin/platform/sso-providers', 'POST', {
  code, name: 'S19 SAML 企业身份', protocol: 'SAML2', issuer: `${idp}/issuer`,
  clientId: 's19-saml-local-sp', clientSecretRef: null,
  protocolConfig: { ssoEndpoint: `${idp}/sso`, acsUri: acs,
    certificateRef: 'env:APP_SSO_SAML_CERTIFICATE', healthEndpoint: `${idp}/health` },
  allowedDomains: ['example.test'],
  attributeMapping: { externalUserId: 'sub', email: 'email', emailVerified: 'email_verified' },
  jitPolicy: { enabled: false }, mfaPolicy: { required: false }, callbackUris: [acs],
}, admin.accessToken)
const providerId = draft.id
const versionId = draft.versions[0].id
const tested = await api(`/api/admin/platform/sso-providers/${providerId}/versions/${versionId}/test`,
  'POST', undefined, admin.accessToken)
if (tested.status !== 'PASSED') throw new Error(`SAML preflight failed: ${JSON.stringify(tested.checks)}`)
const published = await api(`/api/admin/platform/sso-providers/${providerId}/versions/${versionId}/publish`,
  'POST', undefined, admin.accessToken)
if (published.status !== 'PUBLISHED') throw new Error('SAML provider was not published')
const after = await api('/api/auth/sso/providers')
if (!after.some(item => item.code === code && item.protocol === 'SAML2')) {
  throw new Error('Published SAML provider is absent from login entry')
}

const systemToken = owner.tokens.accessToken
const departments = await api('/api/admin/system/identity-mapping/departments', 'GET', undefined, systemToken)
const root = departments.find(item => item.code === 'root')
if (!root) throw new Error('Target system has no root department')
await api('/api/admin/system/identity-mapping/configuration', 'POST', {
  providerId, providerVersionId: versionId, tenantDomain: 'example.test',
  departmentMappings: { HQ: root.id }, matchOrder: ['EMAIL'], jitPolicy: 'ACCESS_REQUEST',
}, systemToken)
const preflight = await api('/api/admin/system/identity-mapping/preflight', 'POST', {
  samples: [{ externalUserId: 's19-saml-subject', externalDepartment: 'HQ',
    email: ownerEmail, displayName: 'S19 SAML 成员', requestId: 's19-saml-sample' }],
}, systemToken)
if (preflight.items[0].plannedAction !== 'BIND_EXISTING_MEMBER') {
  throw new Error(`Unexpected member mapping: ${preflight.items[0].plannedAction}`)
}
const confirmed = await api('/api/admin/system/identity-mapping/confirm', 'POST',
  { preflightId: preflight.preflightId, confirmed: true }, systemToken)
if (confirmed.status !== 'SUCCEEDED') throw new Error(`System identity mapping failed: ${confirmed.status}`)

const evidence = {
  step: process.env.S19_SAML_STEP || 'S19.4', candidate: process.env.S19_SAML_CANDIDATE_VERSION || '0.1.0-s19.4-dev1', mode: 'isolated-local-idp',
  providerId, versionId, providerCode: code, draftHidden: true,
  preflightStatus: tested.status, publishedStatus: published.status,
  loginEntryVisible: true, systemId: owner.systemId, tenantId: owner.tenantId,
  systemMappingAction: preflight.items[0].plannedAction, systemMappingStatus: confirmed.status,
  idpBoundary: 'Local signed SAML2 Response fixture; no enterprise IdP credentials or connection claimed.',
}
writeFileSync(process.env.S19_SAML_EVIDENCE_PATH || 'projects/unexamine/ai/evidence/s19-saml-live-setup.json', JSON.stringify(evidence, null, 2) + '\n')
process.stdout.write(JSON.stringify(evidence) + '\n')
