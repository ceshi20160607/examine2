// 在同一后端和同一前端候选上回归已发布 OAuth2、SAML2 身份源的登录、系统准入与关联审计。
import { readFileSync, writeFileSync } from 'node:fs'

const base = 'http://127.0.0.1:15173'
const fixtures = [
  { code: 's19-oauth2', method: 'OAUTH2', setup: 's19-unified-sso-oauth2-setup.json' },
  { code: 's19-saml2', method: 'SAML2', setup: 's19-unified-sso-saml-setup.json' },
]

async function api(path, method = 'GET', body, token) {
  const response = await fetch(base + path, {
    method, headers: {
      ...(body === undefined ? {} : { 'content-type': 'application/json' }),
      ...(token ? { authorization: `Bearer ${token}` } : {}),
    }, body: body === undefined ? undefined : JSON.stringify(body),
  })
  const result = await response.json()
  if (!response.ok || result.code !== 'OK') throw new Error(`${path}: HTTP ${response.status} ${result.code}`)
  return { data: result.data, traceId: result.requestId }
}

function hidden(html, name) {
  const match = html.match(new RegExp(`<input[^>]+name="${name}"[^>]+value="([^"]+)"`))
  if (!match) throw new Error(`Fixture form is missing ${name}`)
  return match[1]
}

async function completeAuthorization(item) {
  const start = await api(`/api/auth/sso/${item.code}/start`, 'POST')
  const authorize = await fetch(start.data.authorizationUrl)
  if (!authorize.ok) throw new Error(`${item.code} authorization page: ${authorize.status}`)
  const page = await authorize.text()
  let callback
  if (item.method === 'OAUTH2' || item.method === 'OIDC') {
    const requestId = hidden(page, 'requestId')
    const consent = await fetch(`http://127.0.0.1:${item.method === 'OIDC' ? 18214 : 18212}/authorize`, {
      method: 'POST', redirect: 'manual',
      headers: { 'content-type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({ requestId }),
    })
    if (consent.status !== 302) throw new Error(`OAuth2 consent: ${consent.status}`)
    callback = new URL(consent.headers.get('location'))
  } else {
    const action = page.match(/<form[^>]+action="([^"]+)"/)?.[1]
    if (!action) throw new Error('SAML authorization form missing')
    const consent = await fetch(new URL(action, 'http://127.0.0.1:18213'), {
      method: 'POST', headers: { 'content-type': 'application/x-www-form-urlencoded' },
    })
    if (!consent.ok) throw new Error(`SAML consent: ${consent.status}`)
    const responsePage = await consent.text()
    const acs = await fetch(`${base}/api/auth/sso/${item.code}/saml/acs`, {
      method: 'POST', redirect: 'manual',
      headers: { 'content-type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({ SAMLResponse: hidden(responsePage, 'SAMLResponse'),
        RelayState: hidden(responsePage, 'RelayState') }),
    })
    if (acs.status !== 302) throw new Error(`SAML ACS: ${acs.status}`)
    callback = new URL(acs.headers.get('location'))
  }
  if (callback.origin !== base || callback.pathname !== '/sso/callback') {
    throw new Error(`${item.code} callback did not return to the single frontend`)
  }
  return api(`/api/auth/sso/${item.code}/complete`, 'POST', {
    code: callback.searchParams.get('code'), state: callback.searchParams.get('state'),
  })
}

async function setupOidc() {
  const admin = (await api('/api/auth/login', 'POST', { username: 'admin', password: '123123aa' })).data
  const owner = (await api('/api/auth/register', 'POST', { username: 's19_oidc_owner',
    password: 'S19OidcOwner123!', displayName: 'S19 OIDC 成员', email: 's19-oidc@example.test',
    systemName: 'S19 OIDC 系统', systemCode: 's19_oidc_system' })).data
  const providerBase = 'http://127.0.0.1:18214'
  const callback = `${base}/sso/callback`
  const draft = (await api('/api/admin/platform/sso-providers', 'POST', {
    code: 's19-oidc', name: 'S19 OIDC 企业身份', protocol: 'OIDC', issuer: providerBase,
    clientId: 's19-oidc-client', clientSecretRef: 'env:APP_SSO_OAUTH2_CLIENT_SECRET',
    protocolConfig: { authorizationEndpoint: `${providerBase}/authorize`, tokenEndpoint: `${providerBase}/token`,
      userinfoEndpoint: `${providerBase}/userinfo`, jwksUri: `${providerBase}/jwks`,
      redirectUri: callback, healthEndpoint: `${providerBase}/health`, scopes: ['openid', 'profile', 'email'] },
    allowedDomains: ['example.test'], attributeMapping: { externalUserId: 'sub', email: 'email', emailVerified: 'email_verified' },
    jitPolicy: { enabled: false }, mfaPolicy: { required: true, claim: 'amr' }, callbackUris: [callback],
  }, admin.accessToken)).data
  const providerId = draft.id
  const versionId = draft.versions[0].id
  const preflight = (await api(`/api/admin/platform/sso-providers/${providerId}/versions/${versionId}/test`,
    'POST', undefined, admin.accessToken)).data
  if (preflight.status !== 'PASSED') throw new Error(`OIDC preflight: ${JSON.stringify(preflight.checks)}`)
  const published = (await api(`/api/admin/platform/sso-providers/${providerId}/versions/${versionId}/publish`,
    'POST', undefined, admin.accessToken)).data
  if (published.status !== 'PUBLISHED') throw new Error('OIDC publication failed')
  const systemToken = owner.tokens.accessToken
  const departments = (await api('/api/admin/system/identity-mapping/departments', 'GET', undefined, systemToken)).data
  const root = departments.find(value => value.code === 'root')
  if (!root) throw new Error('OIDC system root department missing')
  await api('/api/admin/system/identity-mapping/configuration', 'POST', {
    providerId, providerVersionId: versionId, tenantDomain: 'example.test',
    departmentMappings: { HQ: root.id }, matchOrder: ['EMAIL'], jitPolicy: 'ACCESS_REQUEST',
  }, systemToken)
  const mapping = (await api('/api/admin/system/identity-mapping/preflight', 'POST', {
    samples: [{ externalUserId: 's19-oidc-subject', externalDepartment: 'HQ',
      email: 's19-oidc@example.test', displayName: 'S19 OIDC 成员', requestId: 's19-oidc-sample' }],
  }, systemToken)).data
  if (mapping.items[0].plannedAction !== 'BIND_EXISTING_MEMBER') throw new Error('OIDC member mapping failed')
  const confirmed = (await api('/api/admin/system/identity-mapping/confirm', 'POST',
    { preflightId: mapping.preflightId, confirmed: true }, systemToken)).data
  if (confirmed.status !== 'SUCCEEDED') throw new Error('OIDC member mapping was not confirmed')
  return { code: 's19-oidc', method: 'OIDC', setupData: { systemId: owner.systemId,
    providerId, versionId, preflightStatus: preflight.status, mappingStatus: confirmed.status } }
}

fixtures.push(await setupOidc())
const results = []
for (const item of fixtures) {
  const setup = item.setupData || JSON.parse(readFileSync(`projects/unexamine/ai/evidence/${item.setup}`, 'utf8'))
  const login = await completeAuthorization(item)
  const platform = (await api('/api/auth/me', 'GET', undefined, login.data.accessToken)).data
  if (platform.systemId !== null) throw new Error(`${item.code} platform login implied system context`)
  const entry = await api(`/api/systems/${setup.systemId}/enter`, 'POST', undefined, login.data.accessToken)
  const logs = (await api(`/api/admin/system/identity-mapping/logs?traceId=${entry.traceId}`,
    'GET', undefined, entry.data.tokens.accessToken)).data
  const row = logs.find(value => value.eventCode === 'SSO_LOGIN_SYSTEM_ENTER')
  if (!row || row.identityProvider !== item.code || row.authenticationMethod !== item.method
      || row.loginTraceId !== login.traceId || row.systemMemberMapping !== 'MATCHED'
      || row.accountBinding !== 'MATCHED' || row.systemMemberId !== entry.data.systemMemberId
      || row.resultCode !== 'SUCCESS') {
    throw new Error(`${item.code} audit regression: ${JSON.stringify(logs)}`)
  }
  results.push({ protocol: item.method, provider: item.code, systemId: setup.systemId,
    platformSessionId: platform.sessionId, memberId: row.systemMemberId,
    loginTraceId: login.traceId, systemEntryTraceId: entry.traceId, auditId: row.id })
}

const evidence = { step: 'S19.6', candidate: '0.1.0-s19.6-dev2', results }
writeFileSync('projects/unexamine/ai/evidence/s19-unified-sso-shared-protocols.json',
  JSON.stringify(evidence, null, 2) + '\n')
console.log(JSON.stringify(evidence, null, 2))
